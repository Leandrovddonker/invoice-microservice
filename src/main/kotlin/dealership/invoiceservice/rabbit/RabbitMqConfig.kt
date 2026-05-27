package dealership.invoiceservice.rabbit

import dealership.invoiceservice.config.RabbitConstants
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitMqConfig {
    @Bean
    fun repairCompletedQueue(): Queue = Queue(RabbitConstants.REPAIR_COMPLETED_QUEUE, true)

    @Bean
    fun repairCompletedExchange(): DirectExchange =
        DirectExchange(RabbitConstants.REPAIR_COMPLETED_EXCHANGE, true, false)

    @Bean
    fun repairCompletedBinding(
        @Qualifier("repairCompletedQueue") repairCompletedQueue: Queue,
        @Qualifier("repairCompletedExchange") repairCompletedExchange: DirectExchange
    ): Binding = BindingBuilder
        .bind(repairCompletedQueue)
        .to(repairCompletedExchange)
        .with(RabbitConstants.REPAIR_COMPLETED_ROUTING_KEY)

    @Bean
    fun chargebackRequestedQueue(): Queue = Queue(RabbitConstants.CHARGEBACK_REQUESTED_QUEUE)

    @Bean
    fun chargebackRequestedExchange(): DirectExchange =
        DirectExchange(RabbitConstants.CHARGEBACK_REQUESTED_EXCHANGE)

    @Bean
    fun chargebackRequestedBinding(
        @Qualifier("chargebackRequestedQueue") chargebackRequestedQueue: Queue,
        @Qualifier("chargebackRequestedExchange") chargebackRequestedExchange: DirectExchange
    ): Binding = BindingBuilder
        .bind(chargebackRequestedQueue)
        .to(chargebackRequestedExchange)
        .with(RabbitConstants.CHARGEBACK_REQUESTED_ROUTING_KEY)

    @Bean
    fun jacksonJsonMessageConverter(): MessageConverter = JacksonJsonMessageConverter()
}
