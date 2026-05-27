package dealership.invoiceservice.controller

import dealership.invoiceservice.dto.ChargebackResponseDto
import dealership.invoiceservice.services.ChargebackService
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin(origins = ["http://localhost:4200"])
@RequestMapping("/api/chargebacks")
class ChargebackController(
    private val chargebackService: ChargebackService
) {
    @GetMapping
    fun getAllChargebacks(): List<ChargebackResponseDto> = chargebackService.getAllChargebacks()

    @GetMapping("/{invoiceId}")
    fun getChargebackByInvoiceId(@PathVariable invoiceId: Long): ChargebackResponseDto =
        chargebackService.getChargebackByInvoiceId(invoiceId)
}
