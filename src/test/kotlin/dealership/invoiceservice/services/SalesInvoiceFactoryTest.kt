package dealership.invoiceservice.services

import dealership.invoiceservice.config.TaxProperties
import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.enums.LineType
import dealership.invoiceservice.factory.SaleInvoiceRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SalesInvoiceFactoryTest {

    @Test
    fun `sale invoice line gets vat fields and type`() {
        val taxProperties = TaxProperties().apply {
            labor = BigDecimal("0.19")
            part = BigDecimal("0.21")
            sale = BigDecimal("0.21")
        }
        val factory = SalesInvoiceFactory(TaxService(taxProperties))
        val request = SaleInvoiceRequest().apply {
            customerId = 42L
            customerName = "Ada Lovelace"
            customerEmail = "ada@example.com"
            carBrand = "Volvo"
            carModel = "XC40"
            licensePlate = "AB-123-CD"
            salePrice = BigDecimal("10000.00")
        }

        val invoice = factory.createInvoice(request)
        val line = invoice.lines.single()

        assertEquals(InvoiceType.SALE, invoice.type)
        assertEquals(LineType.SALE, line.lineType)
        assertEquals(BigDecimal("0.21"), line.vatRate)
        assertEquals(BigDecimal("2100.00"), line.vatAmount)
        assertEquals(BigDecimal("10000.00"), invoice.subtotal)
        assertEquals(BigDecimal("2100.00"), invoice.vatAmount)
        assertEquals(BigDecimal("12100.00"), invoice.totalAmount)
    }

    @Test
    fun `sale invoice is created as pending with one linked sale line`() {
        val factory = SalesInvoiceFactory(taxService())
        val request = SaleInvoiceRequest().apply {
            customerId = 42L
            customerName = "Ada Lovelace"
            customerEmail = "ada@example.com"
            carBrand = "Volvo"
            carModel = "XC40"
            licensePlate = "AB-123-CD"
            salePrice = BigDecimal("10000.00")
        }

        val invoice = factory.createInvoice(request)
        val line = invoice.lines.single()

        assertEquals(InvoiceType.SALE, invoice.type)
        assertEquals(InvoiceStatus.PENDING, invoice.status)
        assertEquals(42L, invoice.customerId)
        assertEquals("Ada Lovelace", invoice.customerName)
        assertEquals("ada@example.com", invoice.customerEmail)
        assertEquals("Sale-AB-123-CD", invoice.sourceReference)
        assertNotNull(invoice.createdAt)
        assertEquals(1, invoice.lines.size)
        assertEquals("Volvo XC40 AB-123-CD", line.description)
        assertEquals(1, line.quantity)
        assertEquals(BigDecimal("10000.00"), line.unitPrice)
        assertEquals(BigDecimal("10000.00"), line.lineTotal)
        assertEquals(invoice, line.invoice)
    }

    @Test
    fun `sale invoice rounds vat and total amounts to two decimals`() {
        val taxProperties = TaxProperties().apply {
            labor = BigDecimal("0.19")
            part = BigDecimal("0.21")
            sale = BigDecimal("0.215")
        }
        val factory = SalesInvoiceFactory(TaxService(taxProperties))
        val request = SaleInvoiceRequest().apply {
            customerId = 42L
            customerName = "Ada Lovelace"
            customerEmail = "ada@example.com"
            carBrand = "Volvo"
            carModel = "XC40"
            licensePlate = "AB-123-CD"
            salePrice = BigDecimal("9999.99")
        }

        val invoice = factory.createInvoice(request)

        assertEquals(BigDecimal("2150.00"), invoice.vatAmount)
        assertEquals(BigDecimal("12149.99"), invoice.totalAmount)
    }

    @Test
    fun `sale invoice creation fails when sale vat is not configured`() {
        val taxProperties = TaxProperties().apply {
            labor = BigDecimal("0.19")
            part = BigDecimal("0.21")
            sale = null
        }
        val factory = SalesInvoiceFactory(TaxService(taxProperties))
        val request = SaleInvoiceRequest().apply {
            customerId = 42L
            customerName = "Ada Lovelace"
            customerEmail = "ada@example.com"
            carBrand = "Volvo"
            carModel = "XC40"
            licensePlate = "AB-123-CD"
            salePrice = BigDecimal("10000.00")
        }

        val exception = assertThrows(IllegalStateException::class.java) {
            factory.createInvoice(request)
        }

        assertEquals("VAT rate for SALE is not configured", exception.message)
    }

    private fun taxService(): TaxService {
        val taxProperties = TaxProperties().apply {
            labor = BigDecimal("0.19")
            part = BigDecimal("0.21")
            sale = BigDecimal("0.21")
        }
        return TaxService(taxProperties)
    }
}
