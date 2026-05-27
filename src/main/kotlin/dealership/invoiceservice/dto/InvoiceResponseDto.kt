package dealership.invoiceservice.dto

import dealership.invoiceservice.enums.InvoiceType
import java.math.BigDecimal
import java.util.Date

data class InvoiceResponseDto(
    var invoiceNumber: String? = null,
    var type: InvoiceType? = null,
    var customerId: Long? = null,
    var customerName: String? = null,
    var customerEmail: String? = null,
    var lines: List<InvoiceLineResponseDto> = emptyList(),
    var subtotal: BigDecimal? = null,
    var vatAmount: BigDecimal? = null,
    var totalAmount: BigDecimal? = null,
    var sourceReference: String? = null,
    var createdAt: Date? = null
)
