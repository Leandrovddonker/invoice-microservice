package dealership.invoiceservice.integration

import com.fasterxml.jackson.databind.ObjectMapper
import dealership.invoiceservice.dto.InvoiceResponseDto
import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceRepository
import dealership.invoiceservice.rabbit.InvoiceEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.Date

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "eureka.client.enabled=false",
        "services.user.base-url=http://localhost:8083",
        "spring.datasource.url=jdbc:h2:mem:completion-flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
    ]
)
class CompleteInvoiceFlowIntegrationTest {

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var invoiceRepository: InvoiceRepository

    @MockitoBean
    lateinit var invoiceEventPublisher: InvoiceEventPublisher

    private val httpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun setUp() {
        invoiceRepository.deleteAll()
    }

    @Test
    fun `manager can complete pending invoice and completion event is published`() {
        val invoice = seedInvoice(
            invoiceNumber = "VA-2026-0042",
            status = InvoiceStatus.PENDING
        )

        val response = patch(
            path = "/api/invoices/${invoice.invoiceNumber}/complete",
            authorization = bearerToken(userId = 9, role = "MANAGER")
        )

        assertEquals(200, response.statusCode())

        val responseBody = objectMapper.readTree(response.body())
        assertEquals(invoice.invoiceNumber, responseBody.path("invoiceNumber").asText())
        assertEquals("COMPLETED", responseBody.path("status").asText())
        assertEquals("SALE", responseBody.path("type").asText())
        assertEquals(invoice.customerId, responseBody.path("customerId").asLong())

        val persistedInvoice = invoiceRepository.findByInvoiceNumber(invoice.invoiceNumber!!)
        assertNotNull(persistedInvoice)
        assertEquals(InvoiceStatus.COMPLETED, persistedInvoice!!.status)

        val publishInvocations = Mockito.mockingDetails(invoiceEventPublisher).invocations
            .filter { it.method.name == "publishInvoiceCompleted" }
        assertEquals(1, publishInvocations.size)
        val publishedInvoice = publishInvocations.single().arguments.single() as InvoiceResponseDto
        assertEquals(invoice.invoiceNumber, publishedInvoice.invoiceNumber)
        assertEquals(InvoiceStatus.COMPLETED, publishedInvoice.status)
    }

    @Test
    fun `completing an already completed invoice is idempotent and does not republish`() {
        val invoice = seedInvoice(
            invoiceNumber = "VA-2026-0043",
            status = InvoiceStatus.COMPLETED
        )

        val response = patch(
            path = "/api/invoices/${invoice.invoiceNumber}/complete",
            authorization = bearerToken(userId = 9, role = "MANAGER")
        )

        assertEquals(200, response.statusCode())

        val responseBody = objectMapper.readTree(response.body())
        assertEquals("COMPLETED", responseBody.path("status").asText())
        assertEquals(invoice.invoiceNumber, responseBody.path("invoiceNumber").asText())

        val persistedInvoice = invoiceRepository.findByInvoiceNumber(invoice.invoiceNumber!!)
        assertNotNull(persistedInvoice)
        assertEquals(InvoiceStatus.COMPLETED, persistedInvoice!!.status)
        verifyNoInteractions(invoiceEventPublisher)
    }

    @Test
    fun `customer token is forbidden for invoice completion endpoint`() {
        val invoice = seedInvoice(
            invoiceNumber = "VA-2026-0044",
            status = InvoiceStatus.PENDING
        )

        val response = patch(
            path = "/api/invoices/${invoice.invoiceNumber}/complete",
            authorization = bearerToken(userId = 14, role = "CUSTOMER")
        )

        assertEquals(403, response.statusCode())

        val persistedInvoice = invoiceRepository.findByInvoiceNumber(invoice.invoiceNumber!!)
        assertNotNull(persistedInvoice)
        assertEquals(InvoiceStatus.PENDING, persistedInvoice!!.status)
        verifyNoInteractions(invoiceEventPublisher)
    }

    private fun seedInvoice(
        invoiceNumber: String,
        status: InvoiceStatus
    ): Invoice =
        invoiceRepository.save(
            Invoice(
                invoiceNumber = invoiceNumber,
                type = InvoiceType.SALE,
                status = status,
                customerId = 14L,
                customerName = "Ada Lovelace",
                customerEmail = "ada@example.com",
                subtotal = BigDecimal("1000.00"),
                vatAmount = BigDecimal("210.00"),
                totalAmount = BigDecimal("1210.00"),
                sourceReference = "Sale-AB-123-CD",
                createdAt = Date()
            )
        )

    private fun patch(path: String, authorization: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Authorization", authorization)
            .method("PATCH", HttpRequest.BodyPublishers.noBody())
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun bearerToken(userId: Long, role: String): String =
        "Bearer ${jwtToken("""{"sub":"$userId","role":"$role"}""")}"

    private fun jwtToken(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        val encodedPayload = encoder.encodeToString(payload.toByteArray())
        return "$header.$encodedPayload.signature"
    }
}
