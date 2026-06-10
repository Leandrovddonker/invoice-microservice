package dealership.invoiceservice.factory

import dealership.invoiceservice.models.Invoice

interface InvoiceFactory<T> {
    fun createInvoice(source: T): Invoice
}
