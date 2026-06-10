package dealership.invoiceservice.dto

import java.math.BigDecimal

data class RepairPartDto(
    var name: String? = null,
    var price: BigDecimal? = null,
    var quantity: Int? = null
)
