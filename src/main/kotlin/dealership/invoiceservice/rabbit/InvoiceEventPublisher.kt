package dealership.invoiceservice.rabbit

import dealership.invoiceservice.config.RabbitConstants
import dealership.invoiceservice.dto.InvoiceResponseDto
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component

@Component
class InvoiceEventPublisher(
    private val rabbitTemplate: RabbitTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishInvoiceCreated(invoice: InvoiceResponseDto) {
        rabbitTemplate.convertAndSend(
            RabbitConstants.INVOICE_CREATED_EXCHANGE,
            RabbitConstants.INVOICE_CREATED_ROUTING_KEY,
            invoice
        )
        log.info("Published invoice created event for invoiceNumber={}", invoice.invoiceNumber)
    }

    fun publishInvoiceCompleted(invoice: InvoiceResponseDto) {
        rabbitTemplate.convertAndSend(
            RabbitConstants.INVOICE_COMPLETED_EXCHANGE,
            RabbitConstants.INVOICE_COMPLETED_ROUTING_KEY,
            invoice
        )
        log.info("Published invoice completed event for invoiceNumber={}", invoice.invoiceNumber)
    }
}
