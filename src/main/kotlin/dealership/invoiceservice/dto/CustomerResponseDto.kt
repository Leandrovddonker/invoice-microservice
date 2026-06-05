package dealership.invoiceservice.dto

import com.fasterxml.jackson.annotation.JsonAlias

data class CustomerResponseDto(
    var id: Long? = null,
    @field:JsonAlias("firstname")
    var firstName: String = "",
    @field:JsonAlias("lastname")
    var lastName: String = "",
    var email: String = ""
)
