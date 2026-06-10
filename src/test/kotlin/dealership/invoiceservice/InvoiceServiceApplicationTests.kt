package dealership.invoiceservice

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "eureka.client.enabled=false",
        "services.user.base-url=http://localhost:8083",
        "spring.datasource.url=jdbc:h2:mem:invoice-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
    ]
)
class InvoiceServiceApplicationTests {
    @Test
    fun contextLoads() {
    }
}
