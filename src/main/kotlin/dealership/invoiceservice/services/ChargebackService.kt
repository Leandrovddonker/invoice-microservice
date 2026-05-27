package dealership.invoiceservice.services

import dealership.invoiceservice.dto.ChargebackResponseDto
import dealership.invoiceservice.mapper.ChargebackMapper
import dealership.invoiceservice.models.Chargeback
import dealership.invoiceservice.models.ChargebackRepository
import dealership.invoiceservice.models.Invoice
import dealership.invoiceservice.models.InvoiceRepository
import dealership.invoiceservice.rabbit.ChargebackRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class ChargebackService(
    private val invoiceRepository: InvoiceRepository,
    private val repository: ChargebackRepository,
    private val chargebackMapper: ChargebackMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createChargeback(request: ChargebackRequestedEvent): Chargeback {
        val invoice = invoiceRepository.findBySourceReference(request.sourceReference!!)
            ?: throw IllegalArgumentException("Invoice not found")

        if (isChargebackValid(request, invoice)) {
            val chargeback = Chargeback(
                amount = request.amount,
                reason = request.reason,
                createdAt = LocalDateTime.now(),
                invoice = invoice
            )
            invoice.chargeback = chargeback
            invoiceRepository.save(invoice)
            return chargeback
        }
        throw RuntimeException("Chargeback could not be created")
    }

    fun getAllChargebacks(): List<ChargebackResponseDto> =
        chargebackMapper.listOfChargebacksToDto(repository.findAll())

    fun getChargebackByInvoiceId(invoiceId: Long): ChargebackResponseDto =
        repository.findChargebackByInvoiceId(invoiceId)
            ?.let(chargebackMapper::chargebackToDto)
            ?: throw RuntimeException("Chargeback not found")

    private fun isChargebackValid(request: ChargebackRequestedEvent, invoice: Invoice): Boolean {
        if (invoice.chargeback != null) {
            log.error("Invoice already has a chargeback")
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
}
