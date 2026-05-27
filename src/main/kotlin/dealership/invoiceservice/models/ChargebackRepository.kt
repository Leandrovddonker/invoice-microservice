package dealership.invoiceservice.models

import org.springframework.data.jpa.repository.JpaRepository

interface ChargebackRepository : JpaRepository<Chargeback, Long> {
    fun findChargebackByInvoiceId(invoiceId: Long): Chargeback?
}
