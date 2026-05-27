 package dealership.invoiceservice

import org.springframework.amqp.rabbit.annotation.EnableRabbit
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@EnableRabbit
@SpringBootApplication
@ConfigurationPropertiesScan
class InvoiceServiceApplication

fun main(args: Array<String>) {
    runApplication<InvoiceServiceApplication>(*args)
}
