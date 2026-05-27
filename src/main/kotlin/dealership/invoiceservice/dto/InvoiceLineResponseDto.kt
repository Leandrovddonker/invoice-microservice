package dealership.invoiceservice.dto

import dealership.invoiceservice.enums.LineType
import java.math.BigDecimal

data class InvoiceLineResponseDto(
    var description: String? = null,
    var quantity: Int = 0,
    var unitPrice: BigDecimal? = null,
    var lineTotal: BigDecimal? = null,
    var vatAmount: BigDecimal? = null,
    var vatRate: BigDecimal? = null,
    var lineType: LineType? = null
)
