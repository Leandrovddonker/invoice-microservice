package dealership.invoiceservice.mapper

import dealership.invoiceservice.dto.InvoiceResponseDto
import dealership.invoiceservice.models.Invoice
import org.springframework.stereotype.Component

@Component
class InvoiceMapper(
    private val invoiceLineMapper: InvoiceLineMapper
) {
    fun invoiceToDto(invoice: Invoice): InvoiceResponseDto =
        InvoiceResponseDto(
            invoiceNumber = invoice.invoiceNumber,
            type = invoice.type,
            customerId = invoice.customerId,
            customerName = invoice.customerName,
            customerEmail = invoice.customerEmail,
            lines = invoice.lines.map(invoiceLineMapper::invoiceLineToDto),
            subtotal = invoice.subtotal,
            vatAmount = invoice.vatAmount,
            totalAmount = invoice.totalAmount,
            sourceReference = invoice.sourceReference,
            createdAt = invoice.createdAt
        )

    fun dtoToInvoice(responseInvoice: InvoiceResponseDto): Invoice {
        val invoice = Invoice(
            invoiceNumber = responseInvoice.invoiceNumber,
            type = responseInvoice.type,
            customerId = responseInvoice.customerId,
            customerName = responseInvoice.customerName,
            customerEmail = responseInvoice.customerEmail,
            subtotal = responseInvoice.subtotal,
            vatAmount = responseInvoice.vatAmount,
            totalAmount = responseInvoice.totalAmount,
            sourceReference = responseInvoice.sourceReference,
            createdAt = responseInvoice.createdAt
        )

        responseInvoice.lines.forEach { line ->
            invoice.addLine(
                dealership.invoiceservice.models.InvoiceLine(
                    description = line.description,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    vatAmount = line.vatAmount,
                    vatRate = line.vatRate,
                    lineTotal = line.lineTotal,
                    lineType = line.lineType
                )
            )
        }

        return invoice
    }

    fun listOfInvoicesToDto(invoices: List<Invoice>): List<InvoiceResponseDto> =
        invoices.map(::invoiceToDto)
}
