package dealership.invoiceservice.models

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
class Chargeback(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var amount: BigDecimal? = null,

    var reason: String? = null,

    var createdAt: LocalDateTime? = null,

    @OneToOne
    @JoinColumn(name = "invoice_id", nullable = false, unique = true)
    var invoice: Invoice? = null
)
