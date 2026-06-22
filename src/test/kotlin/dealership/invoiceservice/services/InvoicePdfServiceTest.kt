package dealership.invoiceservice.services

import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceRepository
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.Date

class InvoicePdfServiceTest {

    @Test
    fun `generate invoice pdf returns rendered pdf bytes for chargeback invoice`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val templateEngine = mock(TemplateEngine::class.java)
        val service = InvoicePdfService(invoiceRepository, templateEngine)
        val invoice = invoice(type = InvoiceType.CHARGEBACK, invoiceNumber = "VA-2026-0005")

        `when`(invoiceRepository.findDetailedByInvoiceNumber("VA-2026-0005")).thenReturn(invoice)
        `when`(templateEngine.process(eq("invoice-template"), any(Context::class.java))).thenReturn(
            """
            <html>
            <body><h1>Chargeback invoice</h1><p>VA-2026-0005</p></body>
            </html>
            """.trimIndent()
        )

        val pdf = service.generateInvoicePdf("VA-2026-0005")

        assertTrue(pdf.isNotEmpty())
        assertArrayEquals("%PDF".toByteArray(), pdf.copyOfRange(0, 4))

        val contextCaptor = ArgumentCaptor.forClass(Context::class.java)
        org.mockito.Mockito.verify(templateEngine).process(eq("invoice-template"), contextCaptor.capture())
        assertEquals("Chargeback Invoice", contextCaptor.value.getVariable("invoiceTitle"))
        assertEquals(true, contextCaptor.value.getVariable("isChargebackInvoice"))
        assertEquals(invoice, contextCaptor.value.getVariable("invoice"))
    }

    @Test
    fun `generate customer invoice pdf uses customer scoped lookup`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val templateEngine = mock(TemplateEngine::class.java)
        val service = InvoicePdfService(invoiceRepository, templateEngine)
        val invoice = invoice(type = InvoiceType.SALE, invoiceNumber = "VA-2026-0007")

        `when`(invoiceRepository.findDetailedByInvoiceNumberAndCustomerId("VA-2026-0007", 12L)).thenReturn(invoice)
        `when`(templateEngine.process(eq("invoice-template"), any(Context::class.java))).thenReturn(
            "<html><body><h1>Sales invoice</h1></body></html>"
        )

        val pdf = service.generateCustomerInvoicePdf("VA-2026-0007", 12L)

        assertTrue(pdf.isNotEmpty())

        val contextCaptor = ArgumentCaptor.forClass(Context::class.java)
        org.mockito.Mockito.verify(templateEngine).process(eq("invoice-template"), contextCaptor.capture())
        assertEquals("Sales Invoice", contextCaptor.value.getVariable("invoiceTitle"))
        assertEquals(false, contextCaptor.value.getVariable("isChargebackInvoice"))
    }

    @Test
    fun `generate invoice pdf uses repair invoice title for repair invoice`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val templateEngine = mock(TemplateEngine::class.java)
        val service = InvoicePdfService(invoiceRepository, templateEngine)
        val invoice = invoice(type = InvoiceType.REPAIR, invoiceNumber = "VA-2026-0008")

        `when`(invoiceRepository.findDetailedByInvoiceNumber("VA-2026-0008")).thenReturn(invoice)
        `when`(templateEngine.process(eq("invoice-template"), any(Context::class.java))).thenReturn(
            "<html><body><h1>Repair invoice</h1></body></html>"
        )

        val pdf = service.generateInvoicePdf("VA-2026-0008")

        assertTrue(pdf.isNotEmpty())

        val contextCaptor = ArgumentCaptor.forClass(Context::class.java)
        org.mockito.Mockito.verify(templateEngine).process(eq("invoice-template"), contextCaptor.capture())
        assertEquals("Repair Invoice", contextCaptor.value.getVariable("invoiceTitle"))
        assertEquals(false, contextCaptor.value.getVariable("isChargebackInvoice"))
        assertEquals(invoice, contextCaptor.value.getVariable("invoice"))
    }

    @Test
    fun `generate invoice pdf returns 404 when invoice does not exist`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val templateEngine = mock(TemplateEngine::class.java)
        val service = InvoicePdfService(invoiceRepository, templateEngine)

        `when`(invoiceRepository.findDetailedByInvoiceNumber("VA-2026-9999")).thenReturn(null)

        val exception = assertThrows(ResponseStatusException::class.java) {
            service.generateInvoicePdf("VA-2026-9999")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
        assertEquals("Invoice not found", exception.reason)
    }

    @Test
    fun `generate customer invoice pdf returns 404 when customer invoice does not exist`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val templateEngine = mock(TemplateEngine::class.java)
        val service = InvoicePdfService(invoiceRepository, templateEngine)

        `when`(invoiceRepository.findDetailedByInvoiceNumberAndCustomerId("VA-2026-9999", 12L)).thenReturn(null)

        val exception = assertThrows(ResponseStatusException::class.java) {
            service.generateCustomerInvoicePdf("VA-2026-9999", 12L)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
        assertEquals("Invoice not found", exception.reason)
    }

    private fun invoice(type: InvoiceType, invoiceNumber: String): Invoice =
        Invoice(
            id = 1L,
            invoiceNumber = invoiceNumber,
            type = type,
            status = InvoiceStatus.COMPLETED,
            customerId = 12L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "source-$invoiceNumber",
            createdAt = Date()
        )
}
