package dealership.invoiceservice.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class ChargebackResponseDto(
    var amount: BigDecimal? = null,
    var reason: String? = null,
    var createdAt: LocalDateTime? = null,
    var invoice: InvoiceResponseDto? = null
)
