package dealership.invoiceservice.services

import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.enums.LineType
import dealership.invoiceservice.factory.InvoiceFactory
import dealership.invoiceservice.factory.SaleInvoiceRequest
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceLine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date
import java.util.UUID

@Component
class SalesInvoiceFactory(
    private val taxService: TaxService
) : InvoiceFactory<SaleInvoiceRequest> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun createInvoice(source: SaleInvoiceRequest): Invoice {
        val invoice = Invoice(
            type = InvoiceType.SALE,
            status = InvoiceStatus.PENDING,
            customerId = source.customerId,
            customerName = source.customerName,
            customerEmail = source.customerEmail,
            sourceReference = "Sale-${source.licensePlate}",
            createdAt = Date()
        )

        val vatRate = taxService.getVatRate(LineType.SALE)
        val vat = source.salePrice.multiply(vatRate).setScale(2, RoundingMode.HALF_UP)
        invoice.addLine(saleLine(source, vatRate, vat))

        val subtotal = source.salePrice
        val total = subtotal.add(vat)

        invoice.subtotal = subtotal
        invoice.vatAmount = vat
        invoice.totalAmount = total
        log.info("Sales invoice: {}", invoice)
        return invoice
    }

    internal fun saleLine(
        request: SaleInvoiceRequest,
        vatRate: BigDecimal,
        vatAmount: BigDecimal
    ): InvoiceLine =
        InvoiceLine(
            description = "${request.carBrand} ${request.carModel} ${request.licensePlate}",
            unitPrice = request.salePrice,
            quantity = 1,
            lineTotal = request.salePrice,
            vatRate = vatRate,
            vatAmount = vatAmount,
            lineType = LineType.SALE
        )
}
