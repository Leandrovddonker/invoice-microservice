package dealership.invoiceservice.dto

import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomerResponseDtoTest {

    @Test
    fun `deserializes user service field names`() {
        val objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build()
        val payload = """
            {
              "id": 42,
              "email": "ada@example.com",
              "firstName": "Ada",
              "lastName": "Lovelace"
            }
        """.trimIndent()

        val customer = objectMapper.readValue(payload, CustomerResponseDto::class.java)

        assertEquals(42L, customer.id)
        assertEquals("Ada", customer.firstName)
        assertEquals("Lovelace", customer.lastName)
        assertEquals("ada@example.com", customer.email)
    }
}
