package dealership.invoiceservice.services

import dealership.invoiceservice.dto.CustomerResponseDto
import dealership.invoiceservice.dto.RepairPartDto
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.enums.LineType
import dealership.invoiceservice.factory.InvoiceFactory
import dealership.invoiceservice.factory.RepairInvoiceInput
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceLine
import dealership.invoiceservice.rabbit.RepairCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date
import java.util.UUID

@Component
class RepairInvoiceFactory(
    private val taxService: TaxService
) : InvoiceFactory<RepairInvoiceInput> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun createInvoice(source: RepairInvoiceInput): Invoice {
        val event = source.event
        val customer = source.customer
        val invoice = buildBaseInvoice(event, customer)

        buildInvoiceLines(event).forEach(invoice::addLine)
        calculateAmounts(invoice)

        log.info("Repair invoice: {}", invoice)
        return invoice
    }

    private fun buildBaseInvoice(event: RepairCompletedEvent, customer: CustomerResponseDto): Invoice =
        Invoice(
            customerId = event.customerId,
            type = InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerName = "${customer.firstName} ${customer.lastName}",
            customerEmail = customer.email,
            sourceReference = "Repair-${event.repairId}",
            createdAt = Date()
        )

    private fun buildInvoiceLines(event: RepairCompletedEvent): List<InvoiceLine> {
        val lines = mutableListOf<InvoiceLine>()
        lines += laborLine(event)
        event.parts.forEach { part -> lines += partLine(part) }
        return lines
    }

    private fun calculateAmounts(invoice: Invoice) {
        var subtotal = BigDecimal.ZERO
        var totalVat = BigDecimal.ZERO

        invoice.lines.forEach { line ->
            val lineTotal = line.lineTotal ?: BigDecimal.ZERO
            val lineType = line.lineType ?: throw IllegalStateException("Repair invoice line type is required")
            val vatRate = taxService.getVatRate(lineType)
            val lineVat = lineTotal.multiply(vatRate).setScale(2, RoundingMode.HALF_UP)

            subtotal = subtotal.add(lineTotal)
            totalVat = totalVat.add(lineVat)

            line.vatAmount = lineVat
            line.vatRate = vatRate
        }

        invoice.subtotal = subtotal
        invoice.vatAmount = totalVat
        invoice.totalAmount = subtotal.add(totalVat)
    }

    private fun laborLine(event: RepairCompletedEvent): InvoiceLine {
        val laborPrice = event.laborPrice ?: throw IllegalArgumentException("Labor price is required")
        return InvoiceLine(
            description = "Labor hours for reparation",
            quantity = event.laborHours,
            unitPrice = laborPrice,
            lineTotal = laborPrice.multiply(BigDecimal.valueOf(event.laborHours.toLong())),
            lineType = LineType.LABOR
        )
    }

    private fun partLine(part: RepairPartDto): InvoiceLine {
        val price = part.price ?: throw IllegalArgumentException("Part price is required")
        val quantity = part.quantity ?: throw IllegalArgumentException("Part quantity is required")
        val line = InvoiceLine(
            description = part.name,
            quantity = quantity,
            unitPrice = price,
            lineTotal = price.multiply(BigDecimal.valueOf(quantity.toLong())),
            lineType = LineType.PART
        )
        log.info("Added parts: {} to invoice", part)
        return line
    }
}
