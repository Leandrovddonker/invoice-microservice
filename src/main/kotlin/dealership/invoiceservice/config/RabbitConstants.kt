package dealership.invoiceservice.config

object RabbitConstants {
    const val REPAIR_COMPLETED_QUEUE = "repair.completed.queue"
    const val REPAIR_COMPLETED_EXCHANGE = "repair.completed.exchange"
    const val REPAIR_COMPLETED_ROUTING_KEY = "repair.completed"

    const val CHARGEBACK_REQUESTED_QUEUE = "chargeback.requested.queue"
    const val CHARGEBACK_REQUESTED_EXCHANGE = "chargeback.requested.exchange"
    const val CHARGEBACK_REQUESTED_ROUTING_KEY = "chargeback.requested"

    const val INVOICE_CREATED_QUEUE = "invoice.created.queue"
    const val INVOICE_CREATED_EXCHANGE = "invoice.created.exchange"
    const val INVOICE_CREATED_ROUTING_KEY = "invoice.created"

    const val INVOICE_COMPLETED_QUEUE = "invoice.completed.queue"
    const val INVOICE_COMPLETED_EXCHANGE = "invoice.completed.exchange"
    const val INVOICE_COMPLETED_ROUTING_KEY = "invoice.completed"
}
