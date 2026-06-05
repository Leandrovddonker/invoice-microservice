package dealership.invoiceservice.controller

import dealership.invoiceservice.dto.InvoiceResponseDto
import dealership.invoiceservice.factory.SaleInvoiceRequest
import dealership.invoiceservice.mapper.InvoiceMapper
import dealership.invoiceservice.services.InvoiceService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin(origins = ["http://localhost:4200"])
@RequestMapping("/api/invoices")
class InvoiceController(
    private val invoiceService: InvoiceService,
    private val invoiceMapper: InvoiceMapper
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

    @GetMapping("/customer/{id}")
    fun getInvoiceByCustomerId(@PathVariable id: Long): List<InvoiceResponseDto> =
        invoiceService.getInvoicesByCustomerId(id)

    @PatchMapping("/{invoiceNumber}/complete")
    fun completeInvoice(@PathVariable invoiceNumber: String): InvoiceResponseDto =
        invoiceService.completeInvoice(invoiceNumber)
}
