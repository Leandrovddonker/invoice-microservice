package dealership.invoiceservice.mapper

import dealership.invoiceservice.dto.InvoiceLineResponseDto
import dealership.invoiceservice.models.InvoiceLine
import org.springframework.stereotype.Component

@Component
class InvoiceLineMapper {
    fun invoiceLineToDto(invoiceLine: InvoiceLine): InvoiceLineResponseDto =
        InvoiceLineResponseDto(
            description = invoiceLine.description,
            quantity = invoiceLine.quantity,
            unitPrice = invoiceLine.unitPrice,
            lineTotal = invoiceLine.lineTotal,
            vatAmount = invoiceLine.vatAmount,
            vatRate = invoiceLine.vatRate,
            lineType = invoiceLine.lineType
        )
}
