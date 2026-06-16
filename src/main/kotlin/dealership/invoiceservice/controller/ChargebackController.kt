package dealership.invoiceservice.controller

import dealership.invoiceservice.dto.CreateChargebackRequestDto
import dealership.invoiceservice.dto.InvoiceResponseDto
import dealership.invoiceservice.security.GatewayJwtPrincipal
import dealership.invoiceservice.services.ChargebackService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/invoices")
class ChargebackController(
    private val chargebackService: ChargebackService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/{invoiceNumber}/chargebacks")
    fun createChargeback(
        @PathVariable invoiceNumber: String,
        @RequestBody @Valid request: CreateChargebackRequestDto
    ): ResponseEntity<InvoiceResponseDto> {
        log.info("Immediate chargeback endpoint called for invoice {}", invoiceNumber)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(chargebackService.createChargeback(invoiceNumber, request))
    }

    @GetMapping("/chargebacks")
    fun getAllChargebacks(): List<InvoiceResponseDto> = chargebackService.getAllChargebacks()

    @GetMapping("/chargebacks/{invoiceNumber}")
    fun getChargebackByInvoiceNumber(@PathVariable invoiceNumber: String): InvoiceResponseDto =
        chargebackService.getChargebackByInvoiceNumber(invoiceNumber)

    @GetMapping("/chargeback-requests")
    fun getChargebackRequests(): List<InvoiceResponseDto> = chargebackService.getChargebackRequests()

    @GetMapping("/chargeback-requests/pending")
    fun getPendingChargebackRequests(): List<InvoiceResponseDto> = chargebackService.getPendingChargebackRequests()

    @PostMapping("/me/{invoiceNumber}/chargeback-request")
    fun requestMyChargeback(
        @AuthenticationPrincipal principal: GatewayJwtPrincipal,
        @PathVariable invoiceNumber: String,
        @RequestBody @Valid request: CreateChargebackRequestDto
    ): ResponseEntity<InvoiceResponseDto> {
        log.info(
            "Customer chargeback request endpoint called for invoice {} by user {}",
            invoiceNumber,
            principal.userId
        )
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(chargebackService.requestChargebackForCustomer(principal.userId, invoiceNumber, request))
    }

    @PatchMapping("/{invoiceNumber}/chargeback-request/approve")
    fun approveChargebackRequest(@PathVariable invoiceNumber: String): InvoiceResponseDto =
        chargebackService.approveChargebackRequest(invoiceNumber)

    @PatchMapping("/{invoiceNumber}/chargeback-request/decline")
    fun declineChargebackRequest(@PathVariable invoiceNumber: String): InvoiceResponseDto =
        chargebackService.declineChargebackRequest(invoiceNumber)
}
