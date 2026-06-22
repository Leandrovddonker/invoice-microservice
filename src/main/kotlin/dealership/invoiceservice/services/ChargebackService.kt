package dealership.invoiceservice.services

import dealership.invoiceservice.dto.CreateChargebackRequestDto
import dealership.invoiceservice.dto.InvoiceResponseDto
import dealership.invoiceservice.enums.ChargebackRequestStatus
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
    private val invoiceMapper: InvoiceMapper,
    private val invoiceNotFound: String = "Invoice not found"
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createChargeback(invoiceNumber: String, request: CreateChargebackRequestDto): InvoiceResponseDto {
        val normalizedInvoiceNumber = invoiceNumber.ifBlank {
            throw IllegalArgumentException("Invoice number is required")
        }
        val invoice = invoiceRepository.findByInvoiceNumber(normalizedInvoiceNumber)
            ?: throw IllegalArgumentException(invoiceNotFound)

        return createChargebackNow(invoice, request.amount, request.reason)
    }

    @Transactional
    fun requestChargebackForCustomer(
        customerId: Long,
        invoiceNumber: String,
        request: CreateChargebackRequestDto
    ): InvoiceResponseDto {
        val invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
            ?: throw IllegalArgumentException(invoiceNotFound)

        if (invoice.customerId != customerId) {
            log.error("Customer {} attempted chargeback for invoice {}", customerId, invoiceNumber)
            throw IllegalArgumentException(invoiceNotFound)
        }

        return storeChargebackRequest(invoice, request.amount, request.reason)
    }

    @Transactional
    fun approveChargebackRequest(invoiceNumber: String): InvoiceResponseDto {
        val invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
            ?: throw IllegalArgumentException(invoiceNotFound)

        if (invoice.chargebackRequestStatus != ChargebackRequestStatus.REQUESTED) {
            throw IllegalArgumentException("Chargeback request is not pending")
        }

        val chargebackInvoice = createChargebackInvoice(
            invoice = invoice,
            amount = invoice.chargebackRequestedAmount,
            reason = invoice.chargebackRequestReason.orEmpty()
        )

        invoice.status = InvoiceStatus.REFUNDED
        invoice.chargebackRequestStatus = ChargebackRequestStatus.APPROVED
        invoice.chargebackReviewedAt = Date()
        invoiceRepository.save(invoice)
        saveWithGeneratedInvoiceNumber(chargebackInvoice)

        return invoiceMapper.invoiceToDto(invoice)
    }

    @Transactional
    fun declineChargebackRequest(invoiceNumber: String): InvoiceResponseDto {
        val invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
            ?: throw IllegalArgumentException(invoiceNotFound)

        require (invoice.chargebackRequestStatus == ChargebackRequestStatus.REQUESTED) {
            throw IllegalArgumentException("Chargeback request is not pending")
        }

        invoice.chargebackRequestStatus = ChargebackRequestStatus.DECLINED
        invoice.chargebackReviewedAt = Date()
        invoiceRepository.save(invoice)

        return invoiceMapper.invoiceToDto(invoice)
    }

    fun getAllChargebacks(): List<InvoiceResponseDto> =
        invoiceMapper.listOfInvoicesToDto(invoiceRepository.findAllByType(InvoiceType.CHARGEBACK))

    fun getChargebackRequests(): List<InvoiceResponseDto> =
        invoiceMapper.listOfInvoicesToDto(
            invoiceRepository.findAllByChargebackRequestStatusNotOrderByChargebackRequestedAtDesc(
                ChargebackRequestStatus.NONE
            )
        )

    fun getPendingChargebackRequests(): List<InvoiceResponseDto> =
        invoiceMapper.listOfInvoicesToDto(
            invoiceRepository.findAllByChargebackRequestStatusOrderByChargebackRequestedAtDesc(
                ChargebackRequestStatus.REQUESTED
            )
        )

    fun getChargebackByInvoiceNumber(invoiceNumber: String): InvoiceResponseDto =
        invoiceRepository.findBySourceReferenceAndType(invoiceNumber, InvoiceType.CHARGEBACK)
            ?.let(invoiceMapper::invoiceToDto)
            ?: throw RuntimeException("Chargeback not found")

    private fun createChargebackNow(invoice: Invoice, amount: BigDecimal?, reason: String)
    : InvoiceResponseDto {
        if (isImmediateChargebackValid(amount, invoice)) {
            invoice.status = InvoiceStatus.REFUNDED
            invoice.chargebackRequestStatus = ChargebackRequestStatus.APPROVED
            invoice.chargebackRequestedAmount = amount
            invoice.chargebackRequestReason = reason
            if (invoice.chargebackRequestedAt == null) {
                invoice.chargebackRequestedAt = Date()
            }
            invoice.chargebackReviewedAt = Date()

            val chargebackInvoice = createChargebackInvoice(invoice, amount, reason)
            invoiceRepository.save(invoice)
            saveWithGeneratedInvoiceNumber(chargebackInvoice)

            return invoiceMapper.invoiceToDto(invoice)
        }

        throw RuntimeException("Chargeback could not be created")
    }

    private fun storeChargebackRequest(invoice: Invoice, amount: BigDecimal?, reason: String)
    : InvoiceResponseDto {
        if (isChargebackRequestValid(amount, invoice)) {
            invoice.chargebackRequestStatus = ChargebackRequestStatus.REQUESTED
            invoice.chargebackRequestedAmount = amount
            invoice.chargebackRequestReason = reason
            invoice.chargebackRequestedAt = Date()
            invoice.chargebackReviewedAt = null
            return invoiceMapper.invoiceToDto(invoiceRepository.save(invoice))
        }

        throw RuntimeException("Chargeback request could not be created")
    }

    private fun createChargebackInvoice(invoice: Invoice, amount: BigDecimal?, reason: String)
    : Invoice {
        val normalizedAmount = amount ?: throw IllegalArgumentException("Chargeback amount is missing")

        return Invoice(
            type = InvoiceType.CHARGEBACK,
            status = InvoiceStatus.COMPLETED,
            customerId = invoice.customerId,
            customerName = invoice.customerName,
            customerEmail = invoice.customerEmail,
            subtotal = normalizedAmount.negate(),
            vatAmount = BigDecimal.ZERO,
            totalAmount = normalizedAmount.negate(),
            sourceReference = invoice.invoiceNumber,
            reason = reason,
            createdAt = Date()
        )
    }

    private fun isImmediateChargebackValid(amount: BigDecimal?, invoice: Invoice): Boolean {
        if (!isBaseChargebackValid(amount, invoice)) {
            return false
        }

        if (invoice.chargebackRequestStatus == ChargebackRequestStatus.APPROVED) {
            log.error("Chargeback request has already been approved")
            return false
        }

        return true
    }

    private fun isChargebackRequestValid(amount: BigDecimal?, invoice: Invoice): Boolean {
        if (!isBaseChargebackValid(amount, invoice)) {
            return false
        }

        if (invoice.chargebackRequestStatus == ChargebackRequestStatus.REQUESTED) {
            log.error("Chargeback request is already pending")
            return false
        }

        if (invoice.chargebackRequestStatus == ChargebackRequestStatus.APPROVED) {
            log.error("Chargeback request has already been approved")
            return false
        }

        return true
    }

    private fun isBaseChargebackValid(amount: BigDecimal?, invoice: Invoice): Boolean {
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
