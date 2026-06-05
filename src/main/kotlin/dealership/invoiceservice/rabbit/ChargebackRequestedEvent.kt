package dealership.invoiceservice.rabbit

import java.math.BigDecimal

data class ChargebackRequestedEvent(
    var invoiceNumber: String? = null,
    var amount: BigDecimal? = null,
    var reason: String? = null
)
