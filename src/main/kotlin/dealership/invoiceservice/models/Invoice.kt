package dealership.invoiceservice.models

import dealership.invoiceservice.enums.InvoiceType
import dealership.invoiceservice.enums.InvoiceStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import java.math.BigDecimal
import java.util.Date

@Entity
class Invoice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(unique = true)
    var invoiceNumber: String? = null,

    @Enumerated(EnumType.STRING)
    var type: InvoiceType? = null,

    @Enumerated(EnumType.STRING)
    var status: InvoiceStatus? = null,

    var customerId: Long? = null,

    var customerName: String? = null,

    var customerEmail: String? = null,

    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], orphanRemoval = true)
    var lines: MutableList<InvoiceLine> = mutableListOf(),

    var subtotal: BigDecimal? = null,

    var vatAmount: BigDecimal? = null,

    var totalAmount: BigDecimal? = null,

    var sourceReference: String? = null,

    var reason: String? = null,

    var createdAt: Date? = null
) {
    fun addLine(line: InvoiceLine) {
        lines.add(line)
        line.invoice = this
    }
}
