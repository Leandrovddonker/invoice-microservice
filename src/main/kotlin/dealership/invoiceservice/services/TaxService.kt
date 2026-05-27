package dealership.invoiceservice.services

import dealership.invoiceservice.config.TaxProperties
import dealership.invoiceservice.enums.LineType
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class TaxService(
    private val taxProperties: TaxProperties
) {
    fun getVatRate(type: LineType): BigDecimal =
        when (type) {
            LineType.PART -> taxProperties.part
            LineType.LABOR -> taxProperties.labor
        } ?: throw IllegalStateException("VAT rate for $type is not configured")
}
