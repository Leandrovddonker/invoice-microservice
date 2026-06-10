package dealership.invoiceservice.config

import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.math.BigDecimal

@Validated
@ConfigurationProperties(prefix = "tax.rates")
class TaxProperties {
    @field:NotNull
    var labor: BigDecimal? = null

    @field:NotNull
    var part: BigDecimal? = null

    @field:NotNull
    var sale: BigDecimal? = null
}
