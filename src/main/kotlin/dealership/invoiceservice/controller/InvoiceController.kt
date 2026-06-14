package dealership.invoiceservice.controller

import dealership.invoiceservice.dto.InvoiceResponseDto
import dealership.invoiceservice.factory.SaleInvoiceRequest
import dealership.invoiceservice.mapper.InvoiceMapper
import dealership.invoiceservice.security.GatewayJwtPrincipal
import dealership.invoiceservice.services.InvoicePdfService
import dealership.invoiceservice.services.InvoiceService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.security.core.annotation.AuthenticationPrincipal

@RestController
@CrossOrigin(origins = ["http://localhost:4200", "http://student.inf.st.hsleiden.nl:8091/"])
@RequestMapping("/api/invoices")
class InvoiceController(
    private val invoiceService: InvoiceService,
    private val invoiceMapper: InvoiceMapper,
    private val invoicePdfService: InvoicePdfService
) {
    @PostMapping("/sales")
    fun createSaleInvoice(@RequestBody @Valid request: SaleInvoiceRequest): ResponseEntity<InvoiceResponseDto> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(invoiceMapper.invoiceToDto(invoiceService.createSaleInvoice(request)))

    @GetMapping
    fun getAllInvoices(): List<InvoiceResponseDto> = invoiceService.getAllInvoices()

    @GetMapping("/{id}")
    fun getInvoiceById(@PathVariable id: Long): InvoiceResponseDto = invoiceService.getInvoiceById(id)

    @GetMapping("/me")
    fun getMyInvoices(@AuthenticationPrincipal principal: GatewayJwtPrincipal): List<InvoiceResponseDto> =
        invoiceService.getInvoicesByCustomerId(principal.userId)

    @GetMapping("/me/{invoiceNumber}/pdf")
    fun getMyInvoicePdf(
        @AuthenticationPrincipal principal: GatewayJwtPrincipal,
        @PathVariable invoiceNumber: String
    ): ResponseEntity<ByteArray> {
        val pdfBytes = invoicePdfService.generateCustomerInvoicePdf(invoiceNumber, principal.userId)

        return pdfResponse(invoiceNumber, pdfBytes)
    }

    @GetMapping("/customer/{id}")
    fun getInvoiceByCustomerId(@PathVariable id: Long): List<InvoiceResponseDto> =
        invoiceService.getInvoicesByCustomerId(id)

    @GetMapping("/{invoiceNumber}/pdf")
    fun getInvoicePdf(@PathVariable invoiceNumber: String): ResponseEntity<ByteArray> {
        val pdfBytes = invoicePdfService.generateInvoicePdf(invoiceNumber)

        return pdfResponse(invoiceNumber, pdfBytes)
    }

    private fun pdfResponse(invoiceNumber: String, pdfBytes: ByteArray): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            // Change `inline` to `attachment` here if you want to force downloads instead of browser preview.
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                org.springframework.http.ContentDisposition.inline()
                    .filename("invoice-$invoiceNumber.pdf")
                    .build()
                    .toString()
            )
            .body(pdfBytes)

    @PatchMapping("/{invoiceNumber}/complete")
    fun completeInvoice(@PathVariable invoiceNumber: String): InvoiceResponseDto =
        invoiceService.completeInvoice(invoiceNumber)
}
