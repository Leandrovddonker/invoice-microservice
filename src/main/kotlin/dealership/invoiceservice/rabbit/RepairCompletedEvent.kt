package dealership.invoiceservice.rabbit

import dealership.invoiceservice.dto.RepairPartDto
import java.math.BigDecimal

data class RepairCompletedEvent(
    var repairId: Long? = null,
    var customerId: Long? = null,
    var laborHours: Int = 0,
    var laborPrice: BigDecimal? = null,
    var parts: List<RepairPartDto> = emptyList()
)
