package dealership.invoiceservice.rabbit

import dealership.invoiceservice.config.RabbitConstants
import dealership.invoiceservice.dto.CreateChargebackRequestDto
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
        log.info("Received legacy chargeback request for invoice {}", event.invoiceNumber)
        chargebackService.createChargeback(
            CreateChargebackRequestDto(
                invoiceNumber = event.invoiceNumber.orEmpty(),
                amount = event.amount,
                reason = event.reason.orEmpty()
            )
        )
        log.info("Chargeback request for invoice {} successfully processed", event.invoiceNumber)
    }
}
