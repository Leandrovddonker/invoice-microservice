package dealership.invoiceservice.rabbit

import java.math.BigDecimal

data class ChargebackRequestedEvent(
    var sourceReference: String? = null,
    var amount: BigDecimal? = null,
    var reason: String? = null
)
