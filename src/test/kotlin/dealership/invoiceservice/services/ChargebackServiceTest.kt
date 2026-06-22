package dealership.invoiceservice.services

import dealership.invoiceservice.dto.CreateChargebackRequestDto
import dealership.invoiceservice.enums.ChargebackRequestStatus
import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.mapper.InvoiceLineMapper
import dealership.invoiceservice.mapper.InvoiceMapper
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.math.BigDecimal
import java.util.Date

class ChargebackServiceTest {

    @Test
    fun `manager can create chargeback immediately`() {
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

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)
        `when`(invoiceRepository.save(invoice)).thenAnswer { it.arguments[0] }
        `when`(invoiceRepository.save(any(Invoice::class.java)))
            .thenAnswer { invocation ->
                val arg = invocation.arguments[0] as Invoice
                when {
                    arg.id == null && arg.type == InvoiceType.CHARGEBACK -> arg.copyForTest(id = 8L)
                    arg.type == InvoiceType.CHARGEBACK -> {
                        arg.invoiceNumber = "VA-2026-0002"
                        arg
                    }
                    else -> arg
                }
            }

        val response = service.createChargeback(
            invoiceNumber = "VA-2026-0001",
            request = CreateChargebackRequestDto(
                amount = BigDecimal("50.00"),
                reason = "Customer refund"
            )
        )

        assertEquals(InvoiceType.SALE, invoice.type)
        assertEquals(InvoiceStatus.REFUNDED, invoice.status)
        assertEquals("VA-2026-0001", response.invoiceNumber)
        assertEquals(InvoiceType.SALE, response.type)
        assertEquals(InvoiceStatus.REFUNDED, response.status)
        assertEquals(ChargebackRequestStatus.APPROVED, response.chargebackRequestStatus)
        assertEquals(BigDecimal("50.00"), response.chargebackRequestedAmount)
        assertEquals("Customer refund", response.chargebackRequestReason)
    }

    @Test
    fun `customer chargeback request is stored on the original invoice`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )

        val invoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerId = 42L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "Repair-7",
            createdAt = Date()
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)
        `when`(invoiceRepository.save(invoice)).thenAnswer { it.arguments[0] }

        val response = service.requestChargebackForCustomer(
            customerId = 42L,
            invoiceNumber = "VA-2026-0001",
            request = CreateChargebackRequestDto(
                amount = BigDecimal("25.00"),
                reason = "Wrong amount charged"
            )
        )

        assertEquals(InvoiceStatus.COMPLETED, invoice.status)
        assertEquals(ChargebackRequestStatus.REQUESTED, invoice.chargebackRequestStatus)
        assertEquals("VA-2026-0001", response.invoiceNumber)
        assertEquals(ChargebackRequestStatus.REQUESTED, response.chargebackRequestStatus)
        assertEquals(BigDecimal("25.00"), response.chargebackRequestedAmount)
    }

    @Test
    fun `approving a requested chargeback creates chargeback invoice and marks original invoice refunded`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )

        val invoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerId = 42L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "Repair-7",
            createdAt = Date(),
            chargebackRequestStatus = ChargebackRequestStatus.REQUESTED,
            chargebackRequestedAmount = BigDecimal("25.00"),
            chargebackRequestReason = "Wrong amount charged",
            chargebackRequestedAt = Date()
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)
        `when`(invoiceRepository.save(invoice)).thenAnswer { it.arguments[0] }
        `when`(invoiceRepository.save(any(Invoice::class.java)))
            .thenAnswer { invocation ->
                val arg = invocation.arguments[0] as Invoice
                when {
                    arg.id == null && arg.type == InvoiceType.CHARGEBACK -> arg.copyForTest(id = 8L)
                    arg.type == InvoiceType.CHARGEBACK -> {
                        arg.invoiceNumber = "VA-2026-0002"
                        arg
                    }
                    else -> arg
                }
            }

        val response = service.approveChargebackRequest("VA-2026-0001")

        assertEquals(InvoiceStatus.REFUNDED, invoice.status)
        assertEquals(ChargebackRequestStatus.APPROVED, invoice.chargebackRequestStatus)
        assertEquals("VA-2026-0001", response.invoiceNumber)
        assertEquals(true, response.hasChargeback)
    }

    @Test
    fun `declining a requested chargeback updates request status without creating chargeback invoice`() {
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
            customerId = 42L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "sale-AB-123-CD",
            createdAt = Date(),
            chargebackRequestStatus = ChargebackRequestStatus.REQUESTED,
            chargebackRequestedAmount = BigDecimal("25.00"),
            chargebackRequestReason = "Wrong amount charged",
            chargebackRequestedAt = Date()
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)
        `when`(invoiceRepository.save(invoice)).thenAnswer { it.arguments[0] }

        val response = service.declineChargebackRequest("VA-2026-0001")

        assertEquals(InvoiceStatus.COMPLETED, invoice.status)
        assertEquals(ChargebackRequestStatus.DECLINED, invoice.chargebackRequestStatus)
        assertEquals(ChargebackRequestStatus.DECLINED, response.chargebackRequestStatus)
    }

    @Test
    fun `chargeback requests list contains pending and resolved requests`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )

        val requestedInvoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.SALE,
            status = InvoiceStatus.COMPLETED,
            customerId = 42L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "sale-AB-123-CD",
            createdAt = Date(),
            chargebackRequestStatus = ChargebackRequestStatus.REQUESTED,
            chargebackRequestedAmount = BigDecimal("25.00"),
            chargebackRequestReason = "Wrong amount charged",
            chargebackRequestedAt = Date()
        )
        val declinedInvoice = Invoice(
            id = 8L,
            invoiceNumber = "VA-2026-0002",
            type = InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerId = 43L,
            customerName = "Grace Hopper",
            customerEmail = "grace@example.com",
            subtotal = BigDecimal("150.00"),
            vatAmount = BigDecimal("31.50"),
            totalAmount = BigDecimal("181.50"),
            sourceReference = "repair-12",
            createdAt = Date(),
            chargebackRequestStatus = ChargebackRequestStatus.DECLINED,
            chargebackRequestedAmount = BigDecimal("50.00"),
            chargebackRequestReason = "Wrong labor charge",
            chargebackRequestedAt = Date(),
            chargebackReviewedAt = Date()
        )

        `when`(
            invoiceRepository.findAllByChargebackRequestStatusNotOrderByChargebackRequestedAtDesc(
                ChargebackRequestStatus.NONE
            )
        ).thenReturn(listOf(requestedInvoice, declinedInvoice))

        val response = service.getChargebackRequests()

        assertEquals(2, response.size)
        assertEquals(
            listOf(ChargebackRequestStatus.REQUESTED, ChargebackRequestStatus.DECLINED),
            response.map { it.chargebackRequestStatus }
        )
    }

    @Test
    fun `pending chargeback requests list only contains requested requests`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )

        val requestedInvoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.SALE,
            status = InvoiceStatus.COMPLETED,
            customerId = 42L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "sale-AB-123-CD",
            createdAt = Date(),
            chargebackRequestStatus = ChargebackRequestStatus.REQUESTED,
            chargebackRequestedAmount = BigDecimal("25.00"),
            chargebackRequestReason = "Wrong amount charged",
            chargebackRequestedAt = Date()
        )

        `when`(
            invoiceRepository.findAllByChargebackRequestStatusOrderByChargebackRequestedAtDesc(
                ChargebackRequestStatus.REQUESTED
            )
        ).thenReturn(listOf(requestedInvoice))

        val response = service.getPendingChargebackRequests()

        assertEquals(1, response.size)
        assertEquals(ChargebackRequestStatus.REQUESTED, response.first().chargebackRequestStatus)
    }

    @Test
    fun `customer cannot request chargeback for another customers invoice`() {
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
            customerId = 99L,
            customerName = "Grace Hopper",
            customerEmail = "grace@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "sale-AB-123-CD",
            createdAt = Date()
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)

        assertThrows(IllegalArgumentException::class.java) {
            service.requestChargebackForCustomer(
                customerId = 42L,
                invoiceNumber = "VA-2026-0001",
                request = CreateChargebackRequestDto(
                    amount = BigDecimal("25.00"),
                    reason = "Not my invoice"
                )
            )
        }

        verify(invoiceRepository, never()).save(any(Invoice::class.java))
    }

    @Test
    fun `get all chargebacks returns only chargeback invoices`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )
        val chargebackInvoice = Invoice(
            id = 8L,
            invoiceNumber = "VA-2026-0002",
            type = InvoiceType.CHARGEBACK,
            status = InvoiceStatus.COMPLETED,
            customerId = 43L,
            customerName = "Grace Hopper",
            customerEmail = "grace@example.com",
            subtotal = BigDecimal("-50.00"),
            vatAmount = BigDecimal.ZERO,
            totalAmount = BigDecimal("-50.00"),
            sourceReference = "VA-2026-0001",
            createdAt = Date()
        )

        `when`(invoiceRepository.findAllByType(InvoiceType.CHARGEBACK)).thenReturn(listOf(chargebackInvoice))

        val response = service.getAllChargebacks()

        assertEquals(1, response.size)
        assertEquals(InvoiceType.CHARGEBACK, response.single().type)
        assertEquals("VA-2026-0001", response.single().sourceReference)
    }

    @Test
    fun `get chargeback by invoice number returns chargeback dto`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )
        val chargebackInvoice = Invoice(
            id = 8L,
            invoiceNumber = "VA-2026-0002",
            type = InvoiceType.CHARGEBACK,
            status = InvoiceStatus.COMPLETED,
            customerId = 43L,
            customerName = "Grace Hopper",
            customerEmail = "grace@example.com",
            subtotal = BigDecimal("-50.00"),
            vatAmount = BigDecimal.ZERO,
            totalAmount = BigDecimal("-50.00"),
            sourceReference = "VA-2026-0001",
            createdAt = Date()
        )

        `when`(invoiceRepository.findBySourceReferenceAndType("VA-2026-0001", InvoiceType.CHARGEBACK))
            .thenReturn(chargebackInvoice)

        val response = service.getChargebackByInvoiceNumber("VA-2026-0001")

        assertEquals("VA-2026-0002", response.invoiceNumber)
        assertEquals(InvoiceType.CHARGEBACK, response.type)
    }

    @Test
    fun `get chargeback by invoice number throws when chargeback does not exist`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )

        `when`(invoiceRepository.findBySourceReferenceAndType("VA-2026-0001", InvoiceType.CHARGEBACK))
            .thenReturn(null)

        val exception = assertThrows(RuntimeException::class.java) {
            service.getChargebackByInvoiceNumber("VA-2026-0001")
        }

        assertEquals("Chargeback not found", exception.message)
    }

    @Test
    fun `cannot create immediate chargeback with blank invoice number`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.createChargeback(
                invoiceNumber = "",
                request = CreateChargebackRequestDto(
                    amount = BigDecimal("25.00"),
                    reason = "Refund requested"
                )
            )
        }

        assertEquals("Invoice number is required", exception.message)
    }

    @Test
    fun `cannot create immediate chargeback when invoice does not exist`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-9999")).thenReturn(null)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.createChargeback(
                invoiceNumber = "VA-2026-9999",
                request = CreateChargebackRequestDto(
                    amount = BigDecimal("25.00"),
                    reason = "Refund requested"
                )
            )
        }

        assertEquals("Invoice not found", exception.message)
    }

    @Test
    fun `cannot create immediate chargeback when amount exceeds invoice total`() {
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

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)

        val exception = assertThrows(RuntimeException::class.java) {
            service.createChargeback(
                invoiceNumber = "VA-2026-0001",
                request = CreateChargebackRequestDto(
                    amount = BigDecimal("150.00"),
                    reason = "Refund requested"
                )
            )
        }

        assertEquals("Chargeback could not be created", exception.message)
    }

    @Test
    fun `cannot create immediate chargeback for pending invoice`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )
        val invoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.SALE,
            status = InvoiceStatus.PENDING,
            customerId = 1L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "sale-AB-123-CD",
            createdAt = Date()
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)

        val exception = assertThrows(RuntimeException::class.java) {
            service.createChargeback(
                invoiceNumber = "VA-2026-0001",
                request = CreateChargebackRequestDto(
                    amount = BigDecimal("25.00"),
                    reason = "Refund requested"
                )
            )
        }

        assertEquals("Chargeback could not be created", exception.message)
    }

    @Test
    fun `cannot create immediate chargeback for already refunded invoice`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )
        val invoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.SALE,
            status = InvoiceStatus.REFUNDED,
            customerId = 1L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "sale-AB-123-CD",
            createdAt = Date(),
            chargebackRequestStatus = ChargebackRequestStatus.APPROVED
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)

        val exception = assertThrows(RuntimeException::class.java) {
            service.createChargeback(
                invoiceNumber = "VA-2026-0001",
                request = CreateChargebackRequestDto(
                    amount = BigDecimal("25.00"),
                    reason = "Refund requested"
                )
            )
        }

        assertEquals("Chargeback could not be created", exception.message)
    }

    @Test
    fun `cannot request chargeback twice for the same invoice`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )
        val invoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerId = 42L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "Repair-7",
            createdAt = Date(),
            chargebackRequestStatus = ChargebackRequestStatus.REQUESTED,
            chargebackRequestedAmount = BigDecimal("25.00"),
            chargebackRequestReason = "Wrong amount charged",
            chargebackRequestedAt = Date()
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)

        val exception = assertThrows(RuntimeException::class.java) {
            service.requestChargebackForCustomer(
                customerId = 42L,
                invoiceNumber = "VA-2026-0001",
                request = CreateChargebackRequestDto(
                    amount = BigDecimal("25.00"),
                    reason = "Wrong amount charged"
                )
            )
        }

        assertEquals("Chargeback request could not be created", exception.message)
    }

    @Test
    fun `customer chargeback request fails when invoice does not exist`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-9999")).thenReturn(null)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.requestChargebackForCustomer(
                customerId = 42L,
                invoiceNumber = "VA-2026-9999",
                request = CreateChargebackRequestDto(
                    amount = BigDecimal("25.00"),
                    reason = "Wrong amount charged"
                )
            )
        }

        assertEquals("Invoice not found", exception.message)
    }

    @Test
    fun `customer chargeback request fails when request was already approved`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )
        val invoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerId = 42L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "Repair-7",
            createdAt = Date(),
            chargebackRequestStatus = ChargebackRequestStatus.APPROVED,
            chargebackRequestedAmount = BigDecimal("25.00"),
            chargebackRequestReason = "Wrong amount charged",
            chargebackRequestedAt = Date(),
            chargebackReviewedAt = Date()
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)

        val exception = assertThrows(RuntimeException::class.java) {
            service.requestChargebackForCustomer(
                customerId = 42L,
                invoiceNumber = "VA-2026-0001",
                request = CreateChargebackRequestDto(
                    amount = BigDecimal("25.00"),
                    reason = "Wrong amount charged"
                )
            )
        }

        assertEquals("Chargeback request could not be created", exception.message)
    }

    @Test
    fun `approve chargeback request rejects missing invoice`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-9999")).thenReturn(null)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.approveChargebackRequest("VA-2026-9999")
        }

        assertEquals("Invoice not found", exception.message)
    }

    @Test
    fun `approve chargeback request fails when requested amount is missing`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )
        val invoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerId = 42L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "Repair-7",
            createdAt = Date(),
            chargebackRequestStatus = ChargebackRequestStatus.REQUESTED,
            chargebackRequestedAmount = null,
            chargebackRequestReason = "Wrong amount charged",
            chargebackRequestedAt = Date()
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.approveChargebackRequest("VA-2026-0001")
        }

        assertEquals("Chargeback amount is missing", exception.message)
    }

    @Test
    fun `approve chargeback request rejects invoices without pending request`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )
        val invoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerId = 42L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "Repair-7",
            createdAt = Date(),
            chargebackRequestStatus = ChargebackRequestStatus.NONE
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.approveChargebackRequest("VA-2026-0001")
        }

        assertEquals("Chargeback request is not pending", exception.message)
    }

    @Test
    fun `decline chargeback request rejects missing invoice`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-9999")).thenReturn(null)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.declineChargebackRequest("VA-2026-9999")
        }

        assertEquals("Invoice not found", exception.message)
    }

    @Test
    fun `decline chargeback request rejects invoices without pending request`() {
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val service = ChargebackService(
            invoiceRepository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        )
        val invoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerId = 42L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "Repair-7",
            createdAt = Date(),
            chargebackRequestStatus = ChargebackRequestStatus.NONE
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0001")).thenReturn(invoice)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.declineChargebackRequest("VA-2026-0001")
        }

        assertEquals("Chargeback request is not pending", exception.message)
    }

    private fun Invoice.copyForTest(id: Long): Invoice =
        Invoice(
            id = id,
            invoiceNumber = invoiceNumber,
            type = type,
            status = status,
            customerId = customerId,
            customerName = customerName,
            customerEmail = customerEmail,
            subtotal = subtotal,
            vatAmount = vatAmount,
            totalAmount = totalAmount,
            sourceReference = sourceReference,
            reason = reason,
            createdAt = createdAt,
            chargebackRequestStatus = chargebackRequestStatus,
            chargebackRequestedAmount = chargebackRequestedAmount,
            chargebackRequestReason = chargebackRequestReason,
            chargebackRequestedAt = chargebackRequestedAt,
            chargebackReviewedAt = chargebackReviewedAt
        )
}
