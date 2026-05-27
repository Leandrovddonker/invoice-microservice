package dealership.invoiceservice.mapper

import dealership.invoiceservice.dto.ChargebackResponseDto
import dealership.invoiceservice.models.Chargeback
import org.springframework.stereotype.Component

@Component
class ChargebackMapper(
    private val invoiceMapper: InvoiceMapper
) {
    fun chargebackToDto(chargeback: Chargeback): ChargebackResponseDto =
        ChargebackResponseDto(
            amount = chargeback.amount,
            reason = chargeback.reason,
            createdAt = chargeback.createdAt,
            invoice = chargeback.invoice?.let(invoiceMapper::invoiceToDto)
        )

    fun listOfChargebacksToDto(chargebacks: List<Chargeback>): List<ChargebackResponseDto> =
        chargebacks.map(::chargebackToDto)
}
