package dealership.invoiceservice.integration

import com.fasterxml.jackson.databind.ObjectMapper
import dealership.invoiceservice.enums.ChargebackRequestStatus
import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.enums.LineType
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceLine
import dealership.invoiceservice.models.InvoiceRepository
import dealership.invoiceservice.rabbit.InvoiceEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
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
        "spring.datasource.url=jdbc:h2:mem:customer-chargeback-flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
    ]
)
class CustomerChargebackRequestFlowIntegrationTest {

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
    fun `customer can request chargeback for their own completed invoice`() {
        val invoice = seedCompletedInvoice(
            invoiceNumber = "VA-2026-0050",
            customerId = 14L,
            totalAmount = BigDecimal("1210.00")
        )

        val response = postChargebackRequest(
            invoiceNumber = invoice.invoiceNumber!!,
            authorization = bearerToken(userId = 14, role = "CUSTOMER"),
            body = mapOf(
                "amount" to "25.00",
                "reason" to "Wrong amount charged"
            )
        )

        assertEquals(201, response.statusCode())

        val responseBody = objectMapper.readTree(response.body())
        assertEquals("VA-2026-0050", responseBody.path("invoiceNumber").asText())
        assertEquals("COMPLETED", responseBody.path("status").asText())
        assertEquals("REQUESTED", responseBody.path("chargebackRequestStatus").asText())
        assertEquals(25.00, responseBody.path("chargebackRequestedAmount").asDouble())
        assertEquals("Wrong amount charged", responseBody.path("chargebackRequestReason").asText())
        assertNotNull(responseBody.path("chargebackRequestedAt"))
        assertEquals(true, responseBody.path("chargebackReviewedAt").isNull)

        val persistedInvoice = invoiceRepository.findDetailedByInvoiceNumber("VA-2026-0050")
        requireNotNull(persistedInvoice)
        assertEquals(1, invoiceRepository.count())
        assertEquals(InvoiceStatus.COMPLETED, persistedInvoice.status)
        assertEquals(ChargebackRequestStatus.REQUESTED, persistedInvoice.chargebackRequestStatus)
        assertEquals(BigDecimal("25.00"), persistedInvoice.chargebackRequestedAmount)
        assertEquals("Wrong amount charged", persistedInvoice.chargebackRequestReason)
        assertNotNull(persistedInvoice.chargebackRequestedAt)
        assertNull(persistedInvoice.chargebackReviewedAt)

        verifyNoInteractions(invoiceEventPublisher)
    }

    @Test
    fun `customer chargeback request endpoint requires authentication`() {
        seedCompletedInvoice(
            invoiceNumber = "VA-2026-0051",
            customerId = 14L,
            totalAmount = BigDecimal("1210.00")
        )

        val response = postChargebackRequest(
            invoiceNumber = "VA-2026-0051",
            authorization = null,
            body = mapOf(
                "amount" to "25.00",
                "reason" to "Wrong amount charged"
            )
        )

        assertEquals(401, response.statusCode())

        val persistedInvoice = invoiceRepository.findDetailedByInvoiceNumber("VA-2026-0051")
        requireNotNull(persistedInvoice)
        assertEquals(ChargebackRequestStatus.NONE, persistedInvoice.chargebackRequestStatus)
        assertNull(persistedInvoice.chargebackRequestedAt)
        verifyNoInteractions(invoiceEventPublisher)
    }

    private fun seedCompletedInvoice(
        invoiceNumber: String,
        customerId: Long,
        totalAmount: BigDecimal
    ): Invoice {
        val subtotal = BigDecimal("1000.00")
        val vatAmount = totalAmount.subtract(subtotal)
        val invoice = Invoice(
            invoiceNumber = invoiceNumber,
            type = InvoiceType.SALE,
            status = InvoiceStatus.COMPLETED,
            customerId = customerId,
            customerName = "Customer $customerId",
            customerEmail = "customer$customerId@example.com",
            subtotal = subtotal,
            vatAmount = vatAmount,
            totalAmount = totalAmount,
            sourceReference = "Sale-$invoiceNumber",
            createdAt = Date()
        )

        invoice.addLine(
            InvoiceLine(
                description = "Invoice $invoiceNumber",
                quantity = 1,
                unitPrice = subtotal,
                lineTotal = subtotal,
                vatAmount = vatAmount,
                vatRate = BigDecimal("0.21"),
                lineType = LineType.SALE
            )
        )

        return invoiceRepository.save(invoice)
    }

    private fun postChargebackRequest(
        invoiceNumber: String,
        authorization: String?,
        body: Map<String, String>
    ): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api/invoices/me/$invoiceNumber/chargeback-request"))
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))

        if (authorization != null) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, authorization)
        }

        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
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
