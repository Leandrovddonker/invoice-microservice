package dealership.invoiceservice.factory

import dealership.invoiceservice.dto.CustomerResponseDto
import dealership.invoiceservice.rabbit.RepairCompletedEvent

data class RepairInvoiceInput(
    val event: RepairCompletedEvent,
    val customer: CustomerResponseDto
)
