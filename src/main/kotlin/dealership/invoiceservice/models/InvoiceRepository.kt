package dealership.invoiceservice.models

import dealership.invoiceservice.enums.ChargebackRequestStatus
import dealership.invoiceservice.enums.InvoiceType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface InvoiceRepository : JpaRepository<Invoice, Long> {
    fun existsBySourceReference(sourceReference: String): Boolean
    fun findBySourceReference(sourceReference: String): Invoice?
    fun findByInvoiceNumber(invoiceNumber: String): Invoice?
    @EntityGraph(attributePaths = ["lines"])
    fun findDetailedByInvoiceNumber(invoiceNumber: String): Invoice?
    @EntityGraph(attributePaths = ["lines"])
    fun findDetailedByInvoiceNumberAndCustomerId(invoiceNumber: String, customerId: Long): Invoice?
    fun findBySourceReferenceAndType(sourceReference: String, type: InvoiceType): Invoice?
    fun findAllByType(type: InvoiceType): List<Invoice>
    fun findAllByOrderByCreatedAtDesc(): List<Invoice>
    @EntityGraph(attributePaths = ["lines"])
    fun findAllByCustomerIdOrderByCreatedAtDesc(customerId: Long): List<Invoice>
    @EntityGraph(attributePaths = ["lines"])
    fun findAllByChargebackRequestStatusOrderByChargebackRequestedAtDesc(
        chargebackRequestStatus: ChargebackRequestStatus
    ): List<Invoice>

    @EntityGraph(attributePaths = ["lines"])
    fun findAllByChargebackRequestStatusNotOrderByChargebackRequestedAtDesc(
        chargebackRequestStatus: ChargebackRequestStatus
    ): List<Invoice>
}
