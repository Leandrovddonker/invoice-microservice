package dealership.invoiceservice.services

import dealership.invoiceservice.dto.InvoiceResponseDto
import dealership.invoiceservice.dto.CustomerResponseDto
import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.factory.RepairInvoiceInput
import dealership.invoiceservice.factory.SaleInvoiceRequest
import dealership.invoiceservice.mapper.InvoiceMapper
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceRepository
import dealership.invoiceservice.rabbit.RepairCompletedEvent
import dealership.invoiceservice.rabbit.UserServiceClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Year

@Service
class InvoiceService(
    private val repairInvoiceFactory: RepairInvoiceFactory,
    private val salesInvoiceFactory: SalesInvoiceFactory,
    private val repository: InvoiceRepository,
    private val invoiceMapper: InvoiceMapper,
    private val userServiceClient: UserServiceClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createRepairInvoice(event: RepairCompletedEvent): Invoice {
        val repairId = event.repairId ?: throw IllegalArgumentException("Repair ID is required")
        val sourceReference = "repair-$repairId"

        if (repository.existsBySourceReference(sourceReference)) {
            return repository.findBySourceReference(sourceReference)
                ?: error("Existing invoice for $sourceReference disappeared during lookup")
        }

        val customer: CustomerResponseDto = userServiceClient.getCustomerById(event.customerId!!)
        val invoiceInput = RepairInvoiceInput(event, customer)
        val invoice = repairInvoiceFactory.createInvoice(invoiceInput)
        return saveWithGeneratedInvoiceNumber(invoice)
    }

    private fun saveWithGeneratedInvoiceNumber(invoice: Invoice): Invoice {
        val savedInvoice = repository.save(invoice)
        savedInvoice.invoiceNumber = String.format(
            "VA-%d-%04d",
            Year.now().value,
            savedInvoice.id
        )
        return repository.save(savedInvoice)
    }

    @Transactional
    fun createSaleInvoice(request: SaleInvoiceRequest): Invoice {
        val invoice = salesInvoiceFactory.createInvoice(request)
        return saveWithGeneratedInvoiceNumber(invoice)
    }

    fun getAllInvoices(): List<InvoiceResponseDto> =
        invoiceMapper.listOfInvoicesToDto(repository.findAll())

    fun getInvoiceById(id: Long): InvoiceResponseDto {
        val invoiceFound = repository.findById(id)
            .orElseThrow { RuntimeException("Invoice not found") }
        val invoice = invoiceMapper.invoiceToDto(invoiceFound)
        log.info("{}", invoice)
        return invoice
    }

    fun getInvoicesByCustomerId(id: Long): List<InvoiceResponseDto> {
        val customerInvoices = repository.findInvoiceByCustomerId(id)
        if (customerInvoices.isEmpty()) {
            throw RuntimeException("Customer has no invoices")
        }
        return invoiceMapper.listOfInvoicesToDto(customerInvoices)
    }

    @Transactional
    fun completeInvoice(invoiceNumber: String): InvoiceResponseDto {
        val invoice = repository.findByInvoiceNumber(invoiceNumber)
            ?: throw RuntimeException("Invoice not found")

        if (invoice.status == InvoiceStatus.PENDING) {
            invoice.status = InvoiceStatus.COMPLETED
            repository.save(invoice)
        }

        return invoiceMapper.invoiceToDto(invoice)
    }
}
