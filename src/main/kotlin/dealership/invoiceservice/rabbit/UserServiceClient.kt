package dealership.invoiceservice.rabbit

import dealership.invoiceservice.dto.CustomerResponseDto
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class UserServiceClient(
    private val restTemplate: RestTemplate,
    @param:Value("\${services.user.base-url}") private val userServiceBaseUrl: String
) {
    fun getCustomerById(customerId: Long): CustomerResponseDto? =
        restTemplate.getForObject(
            "$userServiceBaseUrl/api/users/internal/$customerId",
            CustomerResponseDto::class.java
        )
}
