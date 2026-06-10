package dealership.invoiceservice.services

import dealership.invoiceservice.config.TaxProperties
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.enums.LineType
import dealership.invoiceservice.factory.SaleInvoiceRequest
import org.junit.jupiter.api.Assertions.assertEquals
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
        assertEquals(BigDecimal("2100.00"), invoice.vatAmount)
        assertEquals(BigDecimal("12100.00"), invoice.totalAmount)
    }
}
