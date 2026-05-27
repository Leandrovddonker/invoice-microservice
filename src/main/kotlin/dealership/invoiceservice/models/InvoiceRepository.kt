package dealership.invoiceservice.models

import org.springframework.data.jpa.repository.JpaRepository

interface InvoiceRepository : JpaRepository<Invoice, Long> {
    fun existsBySourceReference(sourceReference: String): Boolean
    fun findBySourceReference(sourceReference: String): Invoice?
    fun findInvoiceByCustomerId(customerId: Long): List<Invoice>
}
