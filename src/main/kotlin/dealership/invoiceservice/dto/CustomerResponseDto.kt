package dealership.invoiceservice.dto

data class CustomerResponseDto(
    var id: Long? = null,
    var firstname: String = "",
    var lastname: String = "",
    var email: String = ""
)
