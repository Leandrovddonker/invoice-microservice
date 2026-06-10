package dealership.invoiceservice.rabbit

import dealership.invoiceservice.config.RabbitConstants
import dealership.invoiceservice.services.InvoiceService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class RepairCompletedListener(
    private val invoiceService: InvoiceService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [RabbitConstants.REPAIR_COMPLETED_QUEUE])
    fun handleRepairCompleted(event: RepairCompletedEvent) {
        log.info(
            "Received repair completed event for repairId={}, customerId={}",
            event.repairId,
            event.customerId
        )
        invoiceService.createRepairInvoice(event)
        log.info("Repair invoice creation finished for repairId={}", event.repairId)
    }
}
