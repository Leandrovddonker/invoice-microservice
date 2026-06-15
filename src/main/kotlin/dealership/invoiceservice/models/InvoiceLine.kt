package dealership.invoiceservice.models

import dealership.invoiceservice.enums.LineType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.math.BigDecimal

@Entity
class InvoiceLine(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var description: String? = null,

    var quantity: Int = 0,

    var unitPrice: BigDecimal? = null,

    var vatAmount: BigDecimal? = null,

    var vatRate: BigDecimal? = null,

    var lineTotal: BigDecimal? = null,

    @Enumerated(EnumType.STRING)
    var lineType: LineType? = null,

    @ManyToOne
    @JoinColumn(name = "invoice_id")
    var invoice: Invoice? = null
)
