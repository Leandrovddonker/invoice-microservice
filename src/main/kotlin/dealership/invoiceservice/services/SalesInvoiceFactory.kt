package dealership.invoiceservice.services

import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.factory.InvoiceFactory
import dealership.invoiceservice.factory.SaleInvoiceRequest
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceLine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

@Component
class SalesInvoiceFactory : InvoiceFactory<SaleInvoiceRequest> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun createInvoice(source: SaleInvoiceRequest): Invoice {
        val invoice = Invoice(
            invoiceNumber = UUID.randomUUID().toString(),
            type = InvoiceType.SALE,
            customerId = source.customerId,
            customerName = source.customerName,
            customerEmail = source.customerEmail,
            sourceReference = "sale-${source.licensePlate}",
            createdAt = Date()
        )
        invoice.addLine(saleLine(source))

        val subtotal = source.salePrice
        val vat = subtotal.multiply(BigDecimal("0.21"))
        val total = subtotal.add(vat)

        invoice.subtotal = subtotal
        invoice.vatAmount = vat
        invoice.totalAmount = total
        log.info("Sales invoice: {}", invoice)
        return invoice
    }

    internal fun saleLine(request: SaleInvoiceRequest): InvoiceLine =
        InvoiceLine(
            description = "${request.carBrand} ${request.carModel} ${request.licensePlate}",
            unitPrice = request.salePrice,
            quantity = 1,
            lineTotal = request.salePrice
        )
}
