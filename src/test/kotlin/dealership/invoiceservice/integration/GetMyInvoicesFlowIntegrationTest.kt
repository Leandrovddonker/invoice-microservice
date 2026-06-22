package dealership.invoiceservice.integration

import com.fasterxml.jackson.databind.ObjectMapper
import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.enums.LineType
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceLine
import dealership.invoiceservice.models.InvoiceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
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
        "spring.datasource.url=jdbc:h2:mem:get-my-invoices-flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
    ]
)
class GetMyInvoicesFlowIntegrationTest {

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var invoiceRepository: InvoiceRepository

    private val httpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun setUp() {
        invoiceRepository.deleteAll()
    }

    @Test
    fun `customer gets only their invoices in descending creation order`() {
        seedInvoice(
            invoiceNumber = "VA-2026-0001",
            customerId = 14L,
            createdAt = Date(1_000L),
            description = "Older customer invoice"
        )
        seedInvoice(
            invoiceNumber = "VA-2026-0002",
            customerId = 99L,
            createdAt = Date(2_000L),
            description = "Different customer invoice"
        )
        seedInvoice(
            invoiceNumber = "VA-2026-0003",
            customerId = 14L,
            createdAt = Date(3_000L),
            description = "Newest customer invoice"
        )

        val response = get(
            path = "/api/invoices/me",
            authorization = bearerToken(userId = 14, role = "CUSTOMER")
        )

        assertEquals(200, response.statusCode())

        val responseBody = objectMapper.readTree(response.body())
        assertEquals(2, responseBody.size())

        assertEquals("VA-2026-0003", responseBody[0].path("invoiceNumber").asText())
        assertEquals(14L, responseBody[0].path("customerId").asLong())
        assertEquals("Newest customer invoice", responseBody[0].path("lines")[0].path("description").asText())
        assertEquals("VA-2026-0001", responseBody[1].path("invoiceNumber").asText())
        assertEquals(14L, responseBody[1].path("customerId").asLong())
        assertEquals("Older customer invoice", responseBody[1].path("lines")[0].path("description").asText())
    }

    @Test
    fun `get my invoices requires authentication`() {
        seedInvoice(
            invoiceNumber = "VA-2026-0004",
            customerId = 14L,
            createdAt = Date(4_000L),
            description = "Protected invoice"
        )

        val response = get(path = "/api/invoices/me")

        assertEquals(401, response.statusCode())
    }

    private fun seedInvoice(
        invoiceNumber: String,
        customerId: Long,
        createdAt: Date,
        description: String
    ): Invoice {
        val invoice = Invoice(
            invoiceNumber = invoiceNumber,
            type = InvoiceType.SALE,
            status = InvoiceStatus.COMPLETED,
            customerId = customerId,
            customerName = "Customer $customerId",
            customerEmail = "customer$customerId@example.com",
            subtotal = BigDecimal("1000.00"),
            vatAmount = BigDecimal("210.00"),
            totalAmount = BigDecimal("1210.00"),
            sourceReference = "Sale-$invoiceNumber",
            createdAt = createdAt
        )

        invoice.addLine(
            InvoiceLine(
                description = description,
                quantity = 1,
                unitPrice = BigDecimal("1000.00"),
                lineTotal = BigDecimal("1000.00"),
                vatAmount = BigDecimal("210.00"),
                vatRate = BigDecimal("0.21"),
                lineType = LineType.SALE
            )
        )

        return invoiceRepository.save(invoice)
    }

    private fun get(path: String, authorization: String? = null): HttpResponse<String> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .GET()

        if (authorization != null) {
            requestBuilder.header("Authorization", authorization)
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
