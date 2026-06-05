package dealership.invoiceservice.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class CreateChargebackRequestDto(
    @field:NotBlank
    var invoiceNumber: String = "",

    @field:NotNull
    @field:Positive
    var amount: BigDecimal? = null,

    @field:NotBlank
    var reason: String = ""
)
