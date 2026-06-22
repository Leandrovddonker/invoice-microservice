package dealership.invoiceservice.services

import dealership.invoiceservice.config.TaxProperties
import dealership.invoiceservice.dto.CustomerResponseDto
import dealership.invoiceservice.dto.RepairPartDto
import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.enums.LineType
import dealership.invoiceservice.factory.RepairInvoiceInput
import dealership.invoiceservice.rabbit.RepairCompletedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RepairInvoiceFactoryTest {

    @Test
    fun `create invoice calculates totals for labor and parts`() {
        val factory = RepairInvoiceFactory(taxService())

        val invoice = factory.createInvoice(
            RepairInvoiceInput(
                event = RepairCompletedEvent(
                    repairId = 44L,
                    customerId = 9L,
                    laborHours = 2,
                    laborPrice = BigDecimal("100.00"),
                    parts = listOf(
                        RepairPartDto(name = "Brake pad", price = BigDecimal("20.00"), quantity = 2),
                        RepairPartDto(name = "Oil filter", price = BigDecimal("15.00"), quantity = 1)
                    )
                ),
                customer = CustomerResponseDto(
                    id = 9L,
                    firstName = "Ada",
                    lastName = "Lovelace",
                    email = "ada@example.com"
                )
            )
        )

        assertEquals(InvoiceType.REPAIR, invoice.type)
        assertEquals(InvoiceStatus.COMPLETED, invoice.status)
        assertEquals("Ada Lovelace", invoice.customerName)
        assertEquals("Repair-44", invoice.sourceReference)
        assertEquals(BigDecimal("255.00"), invoice.subtotal)
        assertEquals(BigDecimal("31.00"), invoice.vatAmount)
        assertEquals(BigDecimal("286.00"), invoice.totalAmount)
        assertEquals(3, invoice.lines.size)
        assertEquals(listOf(LineType.LABOR, LineType.PART, LineType.PART), invoice.lines.map { it.lineType })
        assertEquals(BigDecimal("20.00"), invoice.lines[0].vatAmount)
        assertEquals(BigDecimal("8.00"), invoice.lines[1].vatAmount)
        assertEquals(BigDecimal("3.00"), invoice.lines[2].vatAmount)
    }

    @Test
    fun `create invoice rejects repair without labor price`() {
        val factory = RepairInvoiceFactory(taxService())

        val exception = assertThrows(IllegalArgumentException::class.java) {
            factory.createInvoice(
                RepairInvoiceInput(
                    event = RepairCompletedEvent(
                        repairId = 44L,
                        customerId = 9L,
                        laborHours = 2,
                        laborPrice = null
                    ),
                    customer = CustomerResponseDto(
                        id = 9L,
                        firstName = "Ada",
                        lastName = "Lovelace",
                        email = "ada@example.com"
                    )
                )
            )
        }

        assertEquals("Labor price is required", exception.message)
    }

    @Test
    fun `create invoice with labor only still produces a completed repair invoice`() {
        val factory = RepairInvoiceFactory(taxService())

        val invoice = factory.createInvoice(
            RepairInvoiceInput(
                event = RepairCompletedEvent(
                    repairId = 55L,
                    customerId = 12L,
                    laborHours = 3,
                    laborPrice = BigDecimal("80.00"),
                    parts = emptyList()
                ),
                customer = CustomerResponseDto(
                    id = 12L,
                    firstName = "Grace",
                    lastName = "Hopper",
                    email = "grace@example.com"
                )
            )
        )

        assertEquals(InvoiceType.REPAIR, invoice.type)
        assertEquals(InvoiceStatus.COMPLETED, invoice.status)
        assertEquals("Grace Hopper", invoice.customerName)
        assertEquals("grace@example.com", invoice.customerEmail)
        assertEquals("Repair-55", invoice.sourceReference)
        assertEquals(1, invoice.lines.size)
        assertEquals("Labor hours for reparation", invoice.lines.single().description)
        assertEquals(LineType.LABOR, invoice.lines.single().lineType)
        assertEquals(invoice, invoice.lines.single().invoice)
        assertEquals(BigDecimal("240.00"), invoice.subtotal)
        assertEquals(BigDecimal("24.00"), invoice.vatAmount)
        assertEquals(BigDecimal("264.00"), invoice.totalAmount)
    }

    @Test
    fun `create invoice rejects part without price`() {
        val factory = RepairInvoiceFactory(taxService())

        val exception = assertThrows(IllegalArgumentException::class.java) {
            factory.createInvoice(
                RepairInvoiceInput(
                    event = RepairCompletedEvent(
                        repairId = 44L,
                        customerId = 9L,
                        laborHours = 2,
                        laborPrice = BigDecimal("100.00"),
                        parts = listOf(
                            RepairPartDto(name = "Brake pad", price = null, quantity = 2)
                        )
                    ),
                    customer = CustomerResponseDto(
                        id = 9L,
                        firstName = "Ada",
                        lastName = "Lovelace",
                        email = "ada@example.com"
                    )
                )
            )
        }

        assertEquals("Part price is required", exception.message)
    }

    @Test
    fun `create invoice rejects part without quantity`() {
        val factory = RepairInvoiceFactory(taxService())

        val exception = assertThrows(IllegalArgumentException::class.java) {
            factory.createInvoice(
                RepairInvoiceInput(
                    event = RepairCompletedEvent(
                        repairId = 44L,
                        customerId = 9L,
                        laborHours = 2,
                        laborPrice = BigDecimal("100.00"),
                        parts = listOf(
                            RepairPartDto(name = "Brake pad", price = BigDecimal("20.00"), quantity = null)
                        )
                    ),
                    customer = CustomerResponseDto(
                        id = 9L,
                        firstName = "Ada",
                        lastName = "Lovelace",
                        email = "ada@example.com"
                    )
                )
            )
        }

        assertEquals("Part quantity is required", exception.message)
    }

    private fun taxService(): TaxService {
        val properties = TaxProperties().apply {
            labor = BigDecimal("0.10")
            part = BigDecimal("0.20")
            sale = BigDecimal("0.21")
        }
        return TaxService(properties)
    }
}
