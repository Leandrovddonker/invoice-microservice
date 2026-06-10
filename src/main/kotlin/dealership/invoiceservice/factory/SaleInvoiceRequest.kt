package dealership.invoiceservice.factory

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

class SaleInvoiceRequest {
    var customerId: Long? = null

    @field:NotBlank
    var customerName: String = ""

    @field:Email
    @field:NotBlank
    var customerEmail: String = ""

    var carBrand: String? = null
    var carModel: String? = null
    var licensePlate: String? = null

    @field:NotNull
    @field:Positive
    var salePrice: BigDecimal = BigDecimal.ZERO
}
