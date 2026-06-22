package dealership.invoiceservice.services

import dealership.invoiceservice.config.TaxProperties
import dealership.invoiceservice.enums.LineType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TaxServiceTest {

    @Test
    fun `returns configured vat rate for each line type`() {
        val service = TaxService(
            TaxProperties().apply {
                labor = BigDecimal("0.09")
                part = BigDecimal("0.21")
                sale = BigDecimal("0.18")
            }
        )

        assertEquals(BigDecimal("0.21"), service.getVatRate(LineType.PART))
        assertEquals(BigDecimal("0.09"), service.getVatRate(LineType.LABOR))
        assertEquals(BigDecimal("0.18"), service.getVatRate(LineType.SALE))
    }

    @Test
    fun `throws when vat rate is missing`() {
        val service = TaxService(
            TaxProperties().apply {
                labor = BigDecimal("0.09")
                part = null
                sale = BigDecimal("0.18")
            }
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            service.getVatRate(LineType.PART)
        }

        assertEquals("VAT rate for PART is not configured", exception.message)
    }
}
