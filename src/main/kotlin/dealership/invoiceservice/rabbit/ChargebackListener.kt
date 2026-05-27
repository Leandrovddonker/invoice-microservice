package dealership.invoiceservice.rabbit

import dealership.invoiceservice.config.RabbitConstants
import dealership.invoiceservice.services.ChargebackService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class ChargebackListener(
    private val chargebackService: ChargebackService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [RabbitConstants.CHARGEBACK_REQUESTED_QUEUE])
    fun processChargebackRequested(event: ChargebackRequestedEvent) {
        log.info("Received chargeback request for source reference {}", event.sourceReference)
        chargebackService.createChargeback(event)
        log.info("Chargeback request for {} successfully processed", event.sourceReference)
    }
}
