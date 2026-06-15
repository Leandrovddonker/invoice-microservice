package dealership.invoiceservice.services

import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.factory.SaleInvoiceRequest
import dealership.invoiceservice.mapper.InvoiceLineMapper
import dealership.invoiceservice.mapper.InvoiceMapper
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceRepository
import dealership.invoiceservice.rabbit.InvoiceEventPublisher
import dealership.invoiceservice.rabbit.RepairCompletedEvent
import dealership.invoiceservice.rabbit.UserServiceClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.math.BigDecimal
import java.util.Date

class InvoiceServiceTest {

    @Test
    fun `create sale invoice generates invoice number from persisted id and publishes created event`() {
        val repairInvoiceFactory = mock(RepairInvoiceFactory::class.java)
        val salesInvoiceFactory = mock(SalesInvoiceFactory::class.java)
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val userServiceClient = mock(UserServiceClient::class.java)
        val invoiceEventPublisher = mock(InvoiceEventPublisher::class.java)
        val service = InvoiceService(
            repairInvoiceFactory = repairInvoiceFactory,
            salesInvoiceFactory = salesInvoiceFactory,
            repository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper()),
            userServiceClient = userServiceClient,
            invoiceEventPublisher = invoiceEventPublisher
        )

        val request = SaleInvoiceRequest().apply {
            customerId = 1L
            customerName = "Ada Lovelace"
            customerEmail = "ada@example.com"
            salePrice = BigDecimal("1000.00")
        }

        val unsavedInvoice = Invoice(
            type = dealership.invoiceservice.enums.InvoiceType.SALE,
            customerId = 1L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("1000.00"),
            vatAmount = BigDecimal("210.00"),
            totalAmount = BigDecimal("1210.00"),
            sourceReference = "sale-null",
            createdAt = Date()
        )

        val savedWithoutNumber = Invoice(
            id = 42L,
            type = unsavedInvoice.type,
            customerId = unsavedInvoice.customerId,
            customerName = unsavedInvoice.customerName,
            customerEmail = unsavedInvoice.customerEmail,
            subtotal = unsavedInvoice.subtotal,
            vatAmount = unsavedInvoice.vatAmount,
            totalAmount = unsavedInvoice.totalAmount,
            sourceReference = unsavedInvoice.sourceReference,
            createdAt = unsavedInvoice.createdAt
        )

        `when`(salesInvoiceFactory.createInvoice(request)).thenReturn(unsavedInvoice)
        `when`(invoiceRepository.save(any(Invoice::class.java)))
            .thenReturn(savedWithoutNumber)
            .thenAnswer { it.arguments[0] as Invoice }

        val result = service.createSaleInvoice(request)

        assertEquals("VA-${java.time.Year.now().value}-0042", result.invoiceNumber)
        assertNotNull(result.id)
        verify(invoiceRepository, times(2)).save(any(Invoice::class.java))

        val captor = ArgumentCaptor.forClass(Invoice::class.java)
        verify(invoiceRepository, times(2)).save(captor.capture())
        val savedInvoices = captor.allValues
        assertEquals(null, savedInvoices[0].invoiceNumber)
        assertEquals("VA-${java.time.Year.now().value}-0042", savedInvoices[1].invoiceNumber)
        verify(invoiceEventPublisher).publishInvoiceCreated(InvoiceMapper(InvoiceLineMapper()).invoiceToDto(result))
    }

    @Test
    fun `complete invoice changes pending status to completed and publishes completed event`() {
        val repairInvoiceFactory = mock(RepairInvoiceFactory::class.java)
        val salesInvoiceFactory = mock(SalesInvoiceFactory::class.java)
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val userServiceClient = mock(UserServiceClient::class.java)
        val invoiceEventPublisher = mock(InvoiceEventPublisher::class.java)
        val service = InvoiceService(
            repairInvoiceFactory = repairInvoiceFactory,
            salesInvoiceFactory = salesInvoiceFactory,
            repository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper()),
            userServiceClient = userServiceClient,
            invoiceEventPublisher = invoiceEventPublisher
        )

        val invoice = Invoice(
            id = 42L,
            invoiceNumber = "VA-2026-0042",
            type = dealership.invoiceservice.enums.InvoiceType.SALE,
            status = InvoiceStatus.PENDING,
            customerId = 1L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("1000.00"),
            vatAmount = BigDecimal("210.00"),
            totalAmount = BigDecimal("1210.00"),
            sourceReference = "sale-AB-123-CD",
            createdAt = Date()
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0042")).thenReturn(invoice)
        `when`(invoiceRepository.save(invoice)).thenReturn(invoice)

        val response = service.completeInvoice("VA-2026-0042")

        assertEquals(InvoiceStatus.COMPLETED, invoice.status)
        assertEquals(InvoiceStatus.COMPLETED, response.status)
        verify(invoiceRepository).save(invoice)
        verify(invoiceEventPublisher).publishInvoiceCompleted(response)
    }

    @Test
    fun `complete invoice does not republish when already completed`() {
        val repairInvoiceFactory = mock(RepairInvoiceFactory::class.java)
        val salesInvoiceFactory = mock(SalesInvoiceFactory::class.java)
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val userServiceClient = mock(UserServiceClient::class.java)
        val invoiceEventPublisher = mock(InvoiceEventPublisher::class.java)
        val service = InvoiceService(
            repairInvoiceFactory = repairInvoiceFactory,
            salesInvoiceFactory = salesInvoiceFactory,
            repository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper()),
            userServiceClient = userServiceClient,
            invoiceEventPublisher = invoiceEventPublisher
        )

        val invoice = Invoice(
            id = 42L,
            invoiceNumber = "VA-2026-0042",
            type = dealership.invoiceservice.enums.InvoiceType.SALE,
            status = InvoiceStatus.COMPLETED,
            customerId = 1L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("1000.00"),
            vatAmount = BigDecimal("210.00"),
            totalAmount = BigDecimal("1210.00"),
            sourceReference = "sale-AB-123-CD",
            createdAt = Date()
        )

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-0042")).thenReturn(invoice)

        val response = service.completeInvoice("VA-2026-0042")

        assertEquals(InvoiceStatus.COMPLETED, response.status)
        verify(invoiceRepository, never()).save(any(Invoice::class.java))
        verifyNoInteractions(invoiceEventPublisher)
    }

    @Test
    fun `create repair invoice does not recreate or republish when invoice already exists`() {
        val repairInvoiceFactory = mock(RepairInvoiceFactory::class.java)
        val salesInvoiceFactory = mock(SalesInvoiceFactory::class.java)
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val userServiceClient = mock(UserServiceClient::class.java)
        val invoiceEventPublisher = mock(InvoiceEventPublisher::class.java)
        val service = InvoiceService(
            repairInvoiceFactory = repairInvoiceFactory,
            salesInvoiceFactory = salesInvoiceFactory,
            repository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper()),
            userServiceClient = userServiceClient,
            invoiceEventPublisher = invoiceEventPublisher
        )

        val event = RepairCompletedEvent(
            repairId = 7L,
            customerId = 12L,
            laborHours = 2,
            laborPrice = BigDecimal("100.00")
        )
        val existingInvoice = Invoice(
            id = 7L,
            invoiceNumber = "VA-2026-0007",
            type = dealership.invoiceservice.enums.InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerId = 12L,
            customerName = "Grace Hopper",
            customerEmail = "grace@example.com",
            subtotal = BigDecimal("200.00"),
            vatAmount = BigDecimal("38.00"),
            totalAmount = BigDecimal("238.00"),
            sourceReference = "Repair-7",
            createdAt = Date()
        )

        `when`(invoiceRepository.existsBySourceReference("Repair-7")).thenReturn(true)
        `when`(invoiceRepository.findBySourceReference("Repair-7")).thenReturn(existingInvoice)

        val result = service.createRepairInvoice(event)

        assertEquals(existingInvoice, result)
        verifyNoInteractions(userServiceClient, repairInvoiceFactory, invoiceEventPublisher)
    }

    @Test
    fun `get invoices by customer id returns empty list when customer has no invoices`() {
        val repairInvoiceFactory = mock(RepairInvoiceFactory::class.java)
        val salesInvoiceFactory = mock(SalesInvoiceFactory::class.java)
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val userServiceClient = mock(UserServiceClient::class.java)
        val invoiceEventPublisher = mock(InvoiceEventPublisher::class.java)
        val service = InvoiceService(
            repairInvoiceFactory = repairInvoiceFactory,
            salesInvoiceFactory = salesInvoiceFactory,
            repository = invoiceRepository,
            invoiceMapper = InvoiceMapper(InvoiceLineMapper()),
            userServiceClient = userServiceClient,
            invoiceEventPublisher = invoiceEventPublisher
        )

        `when`(invoiceRepository.findAllByCustomerIdOrderByCreatedAtDesc(99L)).thenReturn(emptyList())

        val invoices = service.getInvoicesByCustomerId(99L)

        assertTrue(invoices.isEmpty())
    }
}
