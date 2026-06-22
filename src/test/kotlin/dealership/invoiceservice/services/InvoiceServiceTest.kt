package dealership.invoiceservice.services

import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.dto.CustomerResponseDto
import dealership.invoiceservice.dto.RepairPartDto
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
import org.junit.jupiter.api.Assertions.assertThrows
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
        verify(invoiceRepository, never()).save(invoice)
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
    fun `create repair invoice fails when existing invoice disappears during lookup`() {
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

        `when`(invoiceRepository.existsBySourceReference("Repair-7")).thenReturn(true)
        `when`(invoiceRepository.findBySourceReference("Repair-7")).thenReturn(null)

        val exception = assertThrows(IllegalStateException::class.java) {
            service.createRepairInvoice(event)
        }

        assertEquals("Existing invoice for Repair-7 disappeared during lookup", exception.message)
        verifyNoInteractions(userServiceClient, repairInvoiceFactory, invoiceEventPublisher)
    }

    @Test
    fun `create repair invoice builds and publishes invoice when it does not exist yet`() {
        val repairInvoiceFactory = mock(RepairInvoiceFactory::class.java)
        val salesInvoiceFactory = mock(SalesInvoiceFactory::class.java)
        val invoiceRepository = mock(InvoiceRepository::class.java)
        val userServiceClient = mock(UserServiceClient::class.java)
        val invoiceEventPublisher = mock(InvoiceEventPublisher::class.java)
        val invoiceMapper = InvoiceMapper(InvoiceLineMapper())
        val service = InvoiceService(
            repairInvoiceFactory = repairInvoiceFactory,
            salesInvoiceFactory = salesInvoiceFactory,
            repository = invoiceRepository,
            invoiceMapper = invoiceMapper,
            userServiceClient = userServiceClient,
            invoiceEventPublisher = invoiceEventPublisher
        )

        val event = RepairCompletedEvent(
            repairId = 7L,
            customerId = 12L,
            laborHours = 2,
            laborPrice = BigDecimal("100.00"),
            parts = listOf(RepairPartDto(name = "Filter", price = BigDecimal("20.00"), quantity = 1))
        )
        val customer = CustomerResponseDto(
            id = 12L,
            firstName = "Grace",
            lastName = "Hopper",
            email = "grace@example.com"
        )
        val unsavedInvoice = Invoice(
            type = InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerId = 12L,
            customerName = "Grace Hopper",
            customerEmail = "grace@example.com",
            subtotal = BigDecimal("220.00"),
            vatAmount = BigDecimal("41.80"),
            totalAmount = BigDecimal("261.80"),
            sourceReference = "Repair-7",
            createdAt = Date()
        )
        val savedWithoutNumber = Invoice(
            id = 11L,
            type = unsavedInvoice.type,
            status = unsavedInvoice.status,
            customerId = unsavedInvoice.customerId,
            customerName = unsavedInvoice.customerName,
            customerEmail = unsavedInvoice.customerEmail,
            subtotal = unsavedInvoice.subtotal,
            vatAmount = unsavedInvoice.vatAmount,
            totalAmount = unsavedInvoice.totalAmount,
            sourceReference = unsavedInvoice.sourceReference,
            createdAt = unsavedInvoice.createdAt
        )

        `when`(invoiceRepository.existsBySourceReference("Repair-7")).thenReturn(false)
        `when`(userServiceClient.getCustomerById(12L)).thenReturn(customer)
        `when`(repairInvoiceFactory.createInvoice(dealership.invoiceservice.factory.RepairInvoiceInput(event, customer)))
            .thenReturn(unsavedInvoice)
        `when`(invoiceRepository.save(any(Invoice::class.java)))
            .thenReturn(savedWithoutNumber)
            .thenAnswer { it.arguments[0] as Invoice }

        val result = service.createRepairInvoice(event)

        assertEquals("VA-${java.time.Year.now().value}-0011", result.invoiceNumber)
        assertEquals(InvoiceType.REPAIR, result.type)
        verify(invoiceEventPublisher).publishInvoiceCreated(invoiceMapper.invoiceToDto(result))
    }

    @Test
    fun `create repair invoice rejects missing repair id`() {
        val service = InvoiceService(
            repairInvoiceFactory = mock(RepairInvoiceFactory::class.java),
            salesInvoiceFactory = mock(SalesInvoiceFactory::class.java),
            repository = mock(InvoiceRepository::class.java),
            invoiceMapper = InvoiceMapper(InvoiceLineMapper()),
            userServiceClient = mock(UserServiceClient::class.java),
            invoiceEventPublisher = mock(InvoiceEventPublisher::class.java)
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.createRepairInvoice(
                RepairCompletedEvent(
                    repairId = null,
                    customerId = 12L,
                    laborHours = 2,
                    laborPrice = BigDecimal("100.00")
                )
            )
        }

        assertEquals("Repair ID is required", exception.message)
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

    @Test
    fun `get invoices by customer id returns mapped invoices in repository order`() {
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
        val newest = Invoice(
            id = 2L,
            invoiceNumber = "VA-2026-0002",
            type = InvoiceType.SALE,
            status = InvoiceStatus.COMPLETED,
            customerId = 13L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "sale-2",
            createdAt = Date(2_000L)
        )
        val older = Invoice(
            id = 1L,
            invoiceNumber = "VA-2026-0001",
            type = InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerId = 13L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("200.00"),
            vatAmount = BigDecimal("42.00"),
            totalAmount = BigDecimal("242.00"),
            sourceReference = "Repair-1",
            createdAt = Date(1_000L)
        )

        `when`(invoiceRepository.findAllByCustomerIdOrderByCreatedAtDesc(13L)).thenReturn(listOf(newest, older))

        val invoices = service.getInvoicesByCustomerId(13L)

        assertEquals(listOf("VA-2026-0002", "VA-2026-0001"), invoices.map { it.invoiceNumber })
    }

    @Test
    fun `get invoice by id returns mapped invoice`() {
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
            id = 2L,
            invoiceNumber = "VA-2026-0002",
            type = InvoiceType.SALE,
            status = InvoiceStatus.COMPLETED,
            customerId = 13L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "sale-2",
            createdAt = Date(2_000L)
        )

        `when`(invoiceRepository.findById(2L)).thenReturn(java.util.Optional.of(invoice))

        val response = service.getInvoiceById(2L)

        assertEquals("VA-2026-0002", response.invoiceNumber)
        assertEquals(InvoiceStatus.COMPLETED, response.status)
    }

    @Test
    fun `get invoice by id throws when invoice does not exist`() {
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

        `when`(invoiceRepository.findById(99L)).thenReturn(java.util.Optional.empty())

        val exception = assertThrows(RuntimeException::class.java) {
            service.getInvoiceById(99L)
        }

        assertEquals("Invoice not found", exception.message)
    }

    @Test
    fun `get all invoices returns newest invoices first`() {
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

        val newest = Invoice(
            id = 2L,
            invoiceNumber = "VA-2026-0002",
            type = dealership.invoiceservice.enums.InvoiceType.SALE,
            status = InvoiceStatus.COMPLETED,
            customerId = 13L,
            customerName = "Ada Lovelace",
            customerEmail = "ada@example.com",
            subtotal = BigDecimal("100.00"),
            vatAmount = BigDecimal("21.00"),
            totalAmount = BigDecimal("121.00"),
            sourceReference = "sale-2",
            createdAt = Date(2_000L)
        )
        val oldest = Invoice(
            id = 1L,
            invoiceNumber = "VA-2026-0001",
            type = dealership.invoiceservice.enums.InvoiceType.REPAIR,
            status = InvoiceStatus.COMPLETED,
            customerId = 12L,
            customerName = "Grace Hopper",
            customerEmail = "grace@example.com",
            subtotal = BigDecimal("200.00"),
            vatAmount = BigDecimal("42.00"),
            totalAmount = BigDecimal("242.00"),
            sourceReference = "repair-1",
            createdAt = Date(1_000L)
        )

        `when`(invoiceRepository.findAllByOrderByCreatedAtDesc()).thenReturn(listOf(newest, oldest))

        val invoices = service.getAllInvoices()

        assertEquals(listOf("VA-2026-0002", "VA-2026-0001"), invoices.map { it.invoiceNumber })
    }

    @Test
    fun `complete invoice throws when invoice number does not exist`() {
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

        `when`(invoiceRepository.findByInvoiceNumber("VA-2026-9999")).thenReturn(null)

        val exception = assertThrows(RuntimeException::class.java) {
            service.completeInvoice("VA-2026-9999")
        }

        assertEquals("Invoice not found", exception.message)
    }
}
