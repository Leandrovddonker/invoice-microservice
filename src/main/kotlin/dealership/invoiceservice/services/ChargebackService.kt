package dealership.invoiceservice.services

import dealership.invoiceservice.dto.CreateChargebackRequestDto
import dealership.invoiceservice.dto.InvoiceResponseDto
import dealership.invoiceservice.enums.InvoiceStatus
import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.mapper.InvoiceMapper
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Year
import java.util.Date

@Service
class ChargebackService(
    private val invoiceRepository: InvoiceRepository,
    private val invoiceMapper: InvoiceMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createChargeback(request: CreateChargebackRequestDto): InvoiceResponseDto {
        val invoiceNumber = request.invoiceNumber.ifBlank {
            throw IllegalArgumentException("Invoice number is required")
        }
        val invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
            ?: throw IllegalArgumentException("Invoice not found")

        if (isChargebackValid(request, invoice)) {
            invoice.status = InvoiceStatus.REFUNDED

            val chargebackInvoice = Invoice(
                type = InvoiceType.CHARGEBACK,
                status = InvoiceStatus.COMPLETED,
                customerId = invoice.customerId,
                customerName = invoice.customerName,
                customerEmail = invoice.customerEmail,
                subtotal = request.amount?.negate(),
                vatAmount = BigDecimal.ZERO,
                totalAmount = request.amount?.negate(),
                sourceReference = invoice.invoiceNumber,
                reason = request.reason,
                createdAt = Date()
            )
            invoiceRepository.save(invoice)
            val savedChargebackInvoice = saveWithGeneratedInvoiceNumber(chargebackInvoice)
            return invoiceMapper.invoiceToDto(savedChargebackInvoice)
        }
        throw RuntimeException("Chargeback could not be created")
    }

    fun getAllChargebacks(): List<InvoiceResponseDto> =
        invoiceMapper.listOfInvoicesToDto(invoiceRepository.findAllByType(InvoiceType.CHARGEBACK))

    fun getChargebackByInvoiceNumber(invoiceNumber: String): InvoiceResponseDto =
        invoiceRepository.findBySourceReferenceAndType(invoiceNumber, InvoiceType.CHARGEBACK)
            ?.let(invoiceMapper::invoiceToDto)
            ?: throw RuntimeException("Chargeback not found")

    private fun isChargebackValid(request: CreateChargebackRequestDto, invoice: Invoice): Boolean {
        if (invoice.status == InvoiceStatus.REFUNDED) {
            log.error("Invoice already has a chargeback")
            return false
        }

        if (invoice.type == InvoiceType.CHARGEBACK) {
            log.error("Chargeback cannot be created for a chargeback invoice")
            return false
        }

        if (invoice.status == InvoiceStatus.PENDING) {
            log.error("Chargeback cannot be created for an uncompleted invoice")
            return false
        }

        val amount = request.amount
        if (amount == null || amount <= BigDecimal.ZERO) {
            log.error("Chargeback amount must be greater than zero")
            return false
        }

        val invoiceTotal = invoice.totalAmount ?: BigDecimal.ZERO
        if (amount > invoiceTotal) {
            log.error("Chargeback amount cannot exceed invoice total")
            return false
        }

        return true
    }

    private fun saveWithGeneratedInvoiceNumber(invoice: Invoice): Invoice {
        val savedInvoice = invoiceRepository.save(invoice)
        savedInvoice.invoiceNumber = String.format(
            "VA-%d-%04d",
            Year.now().value,
            savedInvoice.id
        )
        return invoiceRepository.save(savedInvoice)
    }
}
