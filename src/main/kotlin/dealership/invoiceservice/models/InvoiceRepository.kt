package dealership.invoiceservice.models

import dealership.invoiceservice.enums.InvoiceType
import org.springframework.data.jpa.repository.JpaRepository

interface InvoiceRepository : JpaRepository<Invoice, Long> {
    fun existsBySourceReference(sourceReference: String): Boolean
    fun findBySourceReference(sourceReference: String): Invoice?
    fun findByInvoiceNumber(invoiceNumber: String): Invoice?
    fun findBySourceReferenceAndType(sourceReference: String, type: InvoiceType): Invoice?
    fun findAllByType(type: InvoiceType): List<Invoice>
    fun findInvoiceByCustomerId(customerId: Long): List<Invoice>
}
