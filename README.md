# Invoice Service

Spring Boot/Kotlin microservice for creating, storing, retrieving, completing, and rendering invoices.

It supports:

- sales invoices
- repair invoices created from RabbitMQ events
- chargeback invoices
- PDF generation for invoices
- JWT-based authorization for customer and staff endpoints
- RabbitMQ event publishing when invoices are created or completed

## Stack

- Kotlin 2.2
- Spring Boot 4
- Spring Security
- Spring Data JPA
- PostgreSQL / H2
- RabbitMQ
- Eureka client
- Thymeleaf + OpenHTMLToPDF

## Main flows

### Sales invoices

- `POST /api/invoices/sales` creates a sales invoice
- newly created invoices are published to RabbitMQ as `invoice.created`

### Repair invoices

- the service listens for `repair.completed`
- when a repair-completed event arrives, the service creates a repair invoice
- newly created repair invoices are also published as `invoice.created`

### Invoice completion

- `PATCH /api/invoices/{invoiceNumber}/complete` completes a pending invoice
- completed invoices are published as `invoice.completed`

### Chargebacks

- the service listens for `chargeback.requested`
- managers can still create chargebacks immediately through the staff API
- customers can request chargebacks through the HTTP API
- management must approve or decline a request before a chargeback invoice is created

## HTTP API

Base path: `/api/invoices`

| Method | Path | Purpose | Access |
|---|---|---|---|
| `POST` | `/sales` | Create sales invoice | `MANAGER`, `ADMIN` |
| `GET` | `/` | Get all invoices | `MANAGER`, `ADMIN` |
| `GET` | `/{id}` | Get invoice by id | `MANAGER`, `ADMIN` |
| `GET` | `/customer/{id}` | Get invoices for a customer id | `MANAGER`, `ADMIN` |
| `PATCH` | `/{invoiceNumber}/complete` | Complete invoice | `MANAGER`, `ADMIN` |
| `GET` | `/{invoiceNumber}/pdf` | Render invoice PDF | `MANAGER`, `ADMIN` |
| `POST` | `/{invoiceNumber}/chargebacks` | Create chargeback immediately | `MANAGER`, `ADMIN` |
| `GET` | `/chargeback-requests` | Get pending chargeback requests | `MANAGER`, `ADMIN` |
| `PATCH` | `/{invoiceNumber}/chargeback-request/approve` | Approve requested chargeback | `MANAGER`, `ADMIN` |
| `PATCH` | `/{invoiceNumber}/chargeback-request/decline` | Decline requested chargeback | `MANAGER`, `ADMIN` |
| `GET` | `/me` | Get current customer invoices | authenticated user |
| `GET` | `/me/{invoiceNumber}/pdf` | Render current customer invoice PDF | authenticated user |
| `POST` | `/me/{invoiceNumber}/chargeback-request` | Request chargeback for current customer invoice | authenticated user |

Chargebacks:

- `GET /api/invoices/chargebacks`
- `GET /api/invoices/chargebacks/{invoiceNumber}`
- `POST /api/invoices/{invoiceNumber}/chargebacks`
- `GET /api/invoices/chargeback-requests`
- `PATCH /api/invoices/{invoiceNumber}/chargeback-request/approve`
- `PATCH /api/invoices/{invoiceNumber}/chargeback-request/decline`

## Security

The service expects a bearer token in the `Authorization` header.

Role handling:

- `/api/invoices/me` and `/api/invoices/me/**` require any authenticated user
- `/api/invoices/**` requires `MANAGER` or `ADMIN`

The JWT parser accepts role claims in these forms:

- `MANAGER`
- `ADMIN`
- `ROLE_MANAGER`
- `ROLE_ADMIN`

## RabbitMQ integration

### Consumed events

- `repair.completed`

### Published events

- `invoice.created`
- `invoice.completed`

Current queue/exchange names are defined in [src/main/kotlin/dealership/invoiceservice/config/RabbitConstants.kt](src/main/kotlin/dealership/invoiceservice/config/RabbitConstants.kt).

## Configuration

Important properties:

| Property | Purpose | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8091` locally |
| `SPRING_DATASOURCE_URL` | database connection | local Postgres |
| `SPRING_DATASOURCE_USERNAME` | database username | `postgres` locally |
| `SPRING_DATASOURCE_PASSWORD` | database password | `postgres` locally |
| `SPRING_RABBITMQ_HOST` | RabbitMQ host | `localhost` locally |
| `SPRING_RABBITMQ_PORT` | RabbitMQ port | `5672` |
| `SPRING_RABBITMQ_USERNAME` | RabbitMQ username | `guest` locally |
| `SPRING_RABBITMQ_PASSWORD` | RabbitMQ password | `guest` locally |
| `SERVICES_USER_BASE_URL` | user service base URL | `http://localhost:8081` locally |
| `EUREKA_CLIENT_ENABLED` | Eureka registration toggle | `true` |
| `EUREKA_DEFAULT_ZONE` | Eureka server URL | `http://localhost:8761/eureka/` locally |
| `EUREKA_INSTANCE_HOSTNAME` | hostname advertised to Eureka | `localhost` locally, `invoice-service` in Docker |
| `TAX_RATES_LABOR` | labor VAT rate | `0.19` |
| `TAX_RATES_PART` | part VAT rate | `0.21` |
| `TAX_RATES_SALE` | sales VAT rate | `0.21` |

Local defaults live in [src/main/resources/application-local.yml](src/main/resources/application-local.yml) and [src/main/resources/application.properties](src/main/resources/application.properties).

## Running locally

### Requirements

- Java 21
- RabbitMQ
- PostgreSQL

### Start the service

```bash
./gradlew bootRun
```

To run with the local profile:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

## Tests

Run all tests:

```bash
./gradlew test
```

Run a targeted suite:

```bash
./gradlew test --tests dealership.invoiceservice.services.InvoiceServiceTest
```

## Deployment

The provided [docker-compose.yml](docker-compose.yml) expects the image to already be built and pushed by CI.

Environment variables used there include:

- `DOCKER_IMAGE`
- `DEPLOY_PORT`
- `DEPLOY_CONTAINER_NAME`
- `SPRING_PROFILES_ACTIVE`
- `SERVER_PORT`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_RABBITMQ_HOST`
- `SPRING_RABBITMQ_PORT`
- `SPRING_RABBITMQ_USERNAME`
- `SPRING_RABBITMQ_PASSWORD`
- `SERVICES_USER_BASE_URL`
- `EUREKA_CLIENT_ENABLED`
- `EUREKA_DEFAULT_ZONE`

## Notes

- `GET /api/invoices` is a staff endpoint. Customer-facing panels should call `GET /api/invoices/me`.
- Staff can still create an immediate chargeback through `POST /api/invoices/{invoiceNumber}/chargebacks`.
- Invoice responses now include chargeback request state and request metadata.
- Customer-facing profile tabs should request chargebacks through `POST /api/invoices/me/{invoiceNumber}/chargeback-request`.
- Management should review pending requests through `GET /api/invoices/chargeback-requests` and approve or decline them with the dedicated `PATCH` endpoints.
- CORS is configured globally in the security configuration.
- Repair invoice creation depends on the user service for customer lookup.
