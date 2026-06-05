package dealership.invoiceservice.controller

import dealership.invoiceservice.dto.CreateChargebackRequestDto
import dealership.invoiceservice.dto.InvoiceResponseDto
import dealership.invoiceservice.services.ChargebackService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin(origins = ["http://localhost:4200"])
@RequestMapping("/api/chargebacks")
class ChargebackController(
    private val chargebackService: ChargebackService
) {
    @PostMapping
    fun createChargeback(@RequestBody @Valid request: CreateChargebackRequestDto): ResponseEntity<InvoiceResponseDto> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(chargebackService.createChargeback(request))

    @GetMapping
    fun getAllChargebacks(): List<InvoiceResponseDto> = chargebackService.getAllChargebacks()

    @GetMapping("/{invoiceNumber}")
    fun getChargebackByInvoiceNumber(@PathVariable invoiceNumber: String): InvoiceResponseDto =
        chargebackService.getChargebackByInvoiceNumber(invoiceNumber)
}
