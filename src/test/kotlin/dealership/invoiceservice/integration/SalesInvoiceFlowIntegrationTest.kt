package dealership.invoiceservice.integration

import com.fasterxml.jackson.databind.ObjectMapper
import dealership.invoiceservice.models.InvoiceRepository
import dealership.invoiceservice.rabbit.InvoiceEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.client.NoOpResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.Year
import java.util.Base64

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "eureka.client.enabled=false",
        "services.user.base-url=http://localhost:8083",
        "spring.datasource.url=jdbc:h2:mem:sales-flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
    ]
)
class SalesInvoiceFlowIntegrationTest {

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var invoiceRepository: InvoiceRepository

    @MockitoBean
    lateinit var invoiceEventPublisher: InvoiceEventPublisher

    private val restTemplate = RestTemplate().apply {
        errorHandler = NoOpResponseErrorHandler()
    }

    @BeforeEach
    fun setUp() {
        invoiceRepository.deleteAll()
    }

    @Test
    fun `manager can create sales invoice and it is persisted with generated invoice number`() {
        val request = mapOf(
            "customerId" to 14,
            "customerName" to "Ada Lovelace",
            "customerEmail" to "ada@example.com",
            "carBrand" to "Volvo",
            "carModel" to "XC40",
            "licensePlate" to "AB-123-CD",
            "salePrice" to "1000.00"
        )

        val response = restTemplate.postForEntity(
            url("/api/invoices/sales"),
            jsonRequestEntity(request, bearerToken(userId = 9, role = "MANAGER")),
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)

        val responseBody = objectMapper.readTree(response.body)
        val invoiceNumber = responseBody.path("invoiceNumber").asText()

        assertTrue(Regex("""VA-${Year.now().value}-\d{4}""").matches(invoiceNumber))
        assertEquals("SALE", responseBody.path("type").asText())
        assertEquals("PENDING", responseBody.path("status").asText())
        assertEquals(14L, responseBody.path("customerId").asLong())
        assertEquals("Ada Lovelace", responseBody.path("customerName").asText())
        assertEquals("ada@example.com", responseBody.path("customerEmail").asText())
        assertEquals(1000.00, responseBody.path("subtotal").asDouble())
        assertEquals(210.00, responseBody.path("vatAmount").asDouble())
        assertEquals(1210.00, responseBody.path("totalAmount").asDouble())
        assertEquals(false, responseBody.path("hasChargeback").asBoolean())
        assertEquals("Sale-AB-123-CD", responseBody.path("sourceReference").asText())
        assertEquals("NONE", responseBody.path("chargebackRequestStatus").asText())
        assertEquals(1, responseBody.path("lines").size())
        assertEquals("Volvo XC40 AB-123-CD", responseBody.path("lines")[0].path("description").asText())
        assertEquals(1, responseBody.path("lines")[0].path("quantity").asInt())
        assertEquals(1000.00, responseBody.path("lines")[0].path("unitPrice").asDouble())
        assertEquals(1000.00, responseBody.path("lines")[0].path("lineTotal").asDouble())
        assertEquals(210.00, responseBody.path("lines")[0].path("vatAmount").asDouble())
        assertEquals(0.21, responseBody.path("lines")[0].path("vatRate").asDouble())
        assertEquals("SALE", responseBody.path("lines")[0].path("lineType").asText())

        val persistedInvoice = invoiceRepository.findDetailedByInvoiceNumber(invoiceNumber)
        requireNotNull(persistedInvoice)
        assertEquals(1, invoiceRepository.count())
        assertNotNull(persistedInvoice.id)
        assertEquals(invoiceNumber, persistedInvoice.invoiceNumber)
        assertEquals(BigDecimal("1000.00"), persistedInvoice.subtotal)
        assertEquals(BigDecimal("210.00"), persistedInvoice.vatAmount)
        assertEquals(BigDecimal("1210.00"), persistedInvoice.totalAmount)
        assertEquals("Sale-AB-123-CD", persistedInvoice.sourceReference)
        assertEquals(1, persistedInvoice.lines.size)
        assertEquals("Volvo XC40 AB-123-CD", persistedInvoice.lines.single().description)

        val publishInvocations = Mockito.mockingDetails(invoiceEventPublisher).invocations
            .filter { it.method.name == "publishInvoiceCreated" }
        assertEquals(1, publishInvocations.size)
        val publishedInvoice = publishInvocations.single().arguments.single() as dealership.invoiceservice.dto.InvoiceResponseDto
        assertEquals(invoiceNumber, publishedInvoice.invoiceNumber)
    }

    @Test
    fun `customer token is forbidden for staff sales invoice endpoint`() {
        val request = mapOf(
            "customerId" to 14,
            "customerName" to "Ada Lovelace",
            "customerEmail" to "ada@example.com",
            "salePrice" to "1000.00"
        )

        val response = restTemplate.postForEntity(
            url("/api/invoices/sales"),
            jsonRequestEntity(request, bearerToken(userId = 14, role = "CUSTOMER")),
            String::class.java
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(0, invoiceRepository.count())
        verifyNoInteractions(invoiceEventPublisher)
    }

    @Test
    fun `invalid sales invoice request returns bad request and does not persist`() {
        val invalidRequest = mapOf(
            "customerId" to 14,
            "customerName" to "",
            "customerEmail" to "not-an-email",
            "salePrice" to "0"
        )

        val response = restTemplate.postForEntity(
            url("/api/invoices/sales"),
            jsonRequestEntity(invalidRequest, bearerToken(userId = 9, role = "MANAGER")),
            String::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(0, invoiceRepository.count())
        verifyNoInteractions(invoiceEventPublisher)
    }

    private fun jsonRequestEntity(body: Any, authorization: String): HttpEntity<String> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set(HttpHeaders.AUTHORIZATION, authorization)
        }

        return HttpEntity(objectMapper.writeValueAsString(body), headers)
    }

    private fun url(path: String): String = "http://localhost:$port$path"

    private fun bearerToken(userId: Long, role: String): String =
        "Bearer ${jwtToken("""{"sub":"$userId","role":"$role"}""")}"

    private fun jwtToken(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        val encodedPayload = encoder.encodeToString(payload.toByteArray())
        return "$header.$encodedPayload.signature"
    }
}
