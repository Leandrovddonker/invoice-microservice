package dealership.invoiceservice.mapper

import dealership.invoiceservice.dto.InvoiceResponseDto
import dealership.invoiceservice.enums.InvoiceStatus
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
            status = invoice.status,
            customerId = invoice.customerId,
            customerName = invoice.customerName,
            customerEmail = invoice.customerEmail,
            lines = invoice.lines.map(invoiceLineMapper::invoiceLineToDto),
            subtotal = invoice.subtotal,
            vatAmount = invoice.vatAmount,
            totalAmount = invoice.totalAmount,
            hasChargeback = invoice.status == InvoiceStatus.REFUNDED,
            sourceReference = invoice.sourceReference,
            reason = invoice.reason,
            createdAt = invoice.createdAt,
            chargebackRequestStatus = invoice.chargebackRequestStatus,
            chargebackRequestedAmount = invoice.chargebackRequestedAmount,
            chargebackRequestReason = invoice.chargebackRequestReason,
            chargebackRequestedAt = invoice.chargebackRequestedAt,
            chargebackReviewedAt = invoice.chargebackReviewedAt
        )

    fun dtoToInvoice(responseInvoice: InvoiceResponseDto): Invoice {
        val invoice = Invoice(
            invoiceNumber = responseInvoice.invoiceNumber,
            type = responseInvoice.type,
            status = responseInvoice.status,
            customerId = responseInvoice.customerId,
            customerName = responseInvoice.customerName,
            customerEmail = responseInvoice.customerEmail,
            subtotal = responseInvoice.subtotal,
            vatAmount = responseInvoice.vatAmount,
            totalAmount = responseInvoice.totalAmount,
            sourceReference = responseInvoice.sourceReference,
            reason = responseInvoice.reason,
            createdAt = responseInvoice.createdAt,
            chargebackRequestStatus = responseInvoice.chargebackRequestStatus,
            chargebackRequestedAmount = responseInvoice.chargebackRequestedAmount,
            chargebackRequestReason = responseInvoice.chargebackRequestReason,
            chargebackRequestedAt = responseInvoice.chargebackRequestedAt,
            chargebackReviewedAt = responseInvoice.chargebackReviewedAt
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
