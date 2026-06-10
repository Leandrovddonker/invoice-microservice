package dealership.invoiceservice.services

import dealership.invoiceservice.dto.CreateChargebackRequestDto
import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.mapper.InvoiceLineMapper
import dealership.invoiceservice.mapper.InvoiceMapper
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import java.math.BigDecimal
import java.util.Date

class ChargebackServiceTest {

    @Test
    fun `creates chargeback invoice and marks original invoice refunded`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )

        val invoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.SALE,
            status = InvoiceStatus.COMPLETED,
            customerId = 1L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "sale-AB-123-CD",
            createdAt = Date()
        )

        val savedChargebackInvoice = Invoice(
            id = 8L,
            type = InvoiceType.CHARGEBACK,
            status = InvoiceStatus.COMPLETED,
            customerId = invoice.customerId,
            customerName = invoice.customerName,
            customerEmail = invoice.customerEmail,
            subtotal = BigDecimal("-50.00"),
            vatAmount = BigDecimal.ZERO,
            totalAmount = BigDecimal("-50.00"),
            sourceReference = "VA-2026-0001",
            reason = "Customer refund",
            createdAt = Date()
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)
        `when`(invoiceRepository.save(invoice)).thenAnswer { it.arguments[0] }
        `when`(invoiceRepository.save(any(Invoice::class.java)))
            .thenAnswer { invocation ->
                val arg = invocation.arguments[0] as Invoice
                when {
                    arg.id == null && arg.type == InvoiceType.CHARGEBACK -> savedChargebackInvoice
                    arg.type == InvoiceType.CHARGEBACK -> {
                        arg.invoiceNumber = "VA-2026-0002"
                        arg
                    }
                    else -> arg
                }
            }

        val response = service.createChargeback(
            CreateChargebackRequestDto(
                invoiceNumber = "VA-2026-0001",
                amount = BigDecimal("50.00"),
                reason = "Customer refund"
            )
        )

        assertEquals(InvoiceType.SALE, invoice.type)
        assertEquals(InvoiceStatus.REFUNDED, invoice.status)
        assertEquals("VA-2026-0002", response.invoiceNumber)
        assertEquals(InvoiceType.CHARGEBACK, response.type)
        assertEquals(InvoiceStatus.COMPLETED, response.status)
        assertEquals("Ada Lovelace", response.customerName)
        assertEquals(BigDecimal("-50.00"), response.totalAmount)
        assertEquals("VA-2026-0001", response.sourceReference)
        assertEquals("Customer refund", response.reason)
    }
}
