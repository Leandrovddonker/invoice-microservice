package dealership.invoiceservice.services

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.io.ByteArrayOutputStream
import java.util.Locale

@Service
class InvoicePdfService(
    private val invoiceRepository: InvoiceRepository,
    private val templateEngine: TemplateEngine
) {
    @Transactional(readOnly = true)
    fun generateInvoicePdf(invoiceNumber: String): ByteArray {
        val invoice = invoiceRepository.findDetailedByInvoiceNumber(invoiceNumber)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found")

        return renderPdf(invoice)
    }

    @Transactional(readOnly = true)
    fun generateCustomerInvoicePdf(invoiceNumber: String, customerId: Long): ByteArray {
        val invoice = invoiceRepository.findDetailedByInvoiceNumberAndCustomerId(invoiceNumber, customerId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found")

        return renderPdf(invoice)
    }

    private fun renderPdf(invoice: Invoice): ByteArray {
        val html = templateEngine.process(
            "invoice-template",
            Context(
                Locale.getDefault(),
                mapOf(
                    "invoice" to invoice,
                    "invoiceTitle" to invoiceTitle(invoice),
                    "isChargebackInvoice" to (invoice.type == InvoiceType.CHARGEBACK)
                )
            )
        )

        return ByteArrayOutputStream().use { outputStream ->
            PdfRendererBuilder()
                .useFastMode()
                .withHtmlContent(html, null)
                .toStream(outputStream)
                .run()
            outputStream.toByteArray()
        }
    }

    private fun invoiceTitle(invoice: Invoice): String =
        when (invoice.type) {
            InvoiceType.SALE -> "Sales Invoice"
            InvoiceType.REPAIR -> "Repair Invoice"
            InvoiceType.CHARGEBACK -> "Chargeback Invoice"
            null -> "Invoice"
        }
}
