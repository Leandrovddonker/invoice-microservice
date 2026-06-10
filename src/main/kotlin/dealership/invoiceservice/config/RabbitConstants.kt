package dealership.invoiceservice.config

object RabbitConstants {
    const val REPAIR_COMPLETED_QUEUE = "repair.completed.queue"
    const val REPAIR_COMPLETED_EXCHANGE = "repair.completed.exchange"
    const val REPAIR_COMPLETED_ROUTING_KEY = "repair.completed"

    const val CHARGEBACK_REQUESTED_QUEUE = "chargeback.requested.queue"
    const val CHARGEBACK_REQUESTED_EXCHANGE = "chargeback.requested.exchange"
    const val CHARGEBACK_REQUESTED_ROUTING_KEY = "chargeback.requested"
}
