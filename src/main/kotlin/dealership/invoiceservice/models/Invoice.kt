package dealership.invoiceservice.models

import dealership.invoiceservice.enums.InvoiceType
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import java.math.BigDecimal
import java.util.Date

@Entity
class Invoice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var invoiceNumber: String? = null,

    @Enumerated(EnumType.STRING)
    var type: InvoiceType? = null,

    var customerId: Long? = null,

    var customerName: String? = null,

    var customerEmail: String? = null,

    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], orphanRemoval = true)
    var lines: MutableList<InvoiceLine> = mutableListOf(),

    var subtotal: BigDecimal? = null,

    var vatAmount: BigDecimal? = null,

    var totalAmount: BigDecimal? = null,

    var sourceReference: String? = null,

    var createdAt: Date? = null,

    @OneToOne(mappedBy = "invoice", cascade = [CascadeType.ALL], orphanRemoval = true)
    var chargeback: Chargeback? = null
) {
    fun addLine(line: InvoiceLine) {
        lines.add(line)
        line.invoice = this
    }
}
