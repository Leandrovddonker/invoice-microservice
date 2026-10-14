# Invoice Service

A Kotlin/Spring Boot microservice developed as part of a university team project focused on building a microservices-based automotive management platform.

## Project Overview

The project involved developing an automotive management platform consisting of multiple independently developed microservices responsible for different business domains, including vehicle sales, repairs, customer management, and invoicing.

Each team member was responsible for developing and integrating their own microservice. The services communicate through REST APIs and asynchronous messaging using RabbitMQ, with Eureka providing service discovery.

My responsibility was designing and implementing the **Invoice Service**, which handles invoice creation, management, PDF generation, and chargeback workflows. The service integrates with other microservices to automatically generate invoices based on business events.

This repository focuses on my individual contribution to the team project. Other microservices, such as the User Service, were developed separately and are not included.

## Tech Stack

- **Language:** Kotlin 2.2
- **Framework:** Spring Boot 4
- **Security:** Spring Security
- **Persistence:** Spring Data JPA, PostgreSQL / H2
- **Messaging:** RabbitMQ
- **Service Discovery:** Eureka Client
- **PDF Generation:** Thymeleaf + OpenHTMLToPDF
- **Build & Deployment:** Gradle, Docker, GitLab CI/CD
- **Code Quality:** SonarQube

## Main Features & Flows

### Sales Invoices

- `POST /api/invoices/sales` creates a sales invoice.
- Newly created invoices are published to RabbitMQ as `invoice.created`.

### Repair Invoices

- The service listens for `repair.completed` events.
- When a repair is completed, an invoice is automatically generated.
- Newly created repair invoices are published as `invoice.created`.
- Customer information is retrieved through the User Service.

### Invoice Completion

- `PATCH /api/invoices/{invoiceNumber}/complete` completes a pending invoice.
- Completed invoices are published to RabbitMQ as `invoice.completed`.

### Chargeback Management

- The service supports chargeback invoices and customer chargeback requests.
- Customers can request chargebacks through dedicated endpoints.
- Managers can approve or decline chargeback requests.
- Approved requests result in the creation of a chargeback invoice.
- Managers can also create chargebacks directly through the staff API.
- The service supports integration with `chargeback.requested` events.

### PDF Generation

- Generates downloadable PDF invoices using Thymeleaf templates and OpenHTMLToPDF.

## Architecture & Integrations

The Invoice Service operates as part of a distributed microservices architecture.

It communicates with other services through two mechanisms:

**Synchronous Communication (REST)**

- Retrieves customer information from the User Service.
- Exposes REST endpoints for invoice management and customer-facing operations.

**Asynchronous Communication (RabbitMQ)**

- Consumes business events to trigger invoice creation.
- Publishes invoice lifecycle events for other services to process.

### RabbitMQ Events

**Consumed events**

- `repair.completed`
- `chargeback.requested`

**Published events**

- `invoice.created`
- `invoice.completed`

Queue and exchange definitions are maintained in [RabbitConstants.kt](src/main/kotlin/dealership/invoiceservice/config/RabbitConstants.kt).

### Service Discovery

The service integrates with Eureka for registration and discovery within the microservices environment.

## Security

The Invoice Service uses Spring Security for role-based access control (RBAC) and integrates with a centralized API Gateway responsible for JWT authentication.

### Authentication

Authentication is handled by the API Gateway, which validates incoming JWT signatures using an RSA public key before forwarding requests to downstream services.

The Invoice Service receives the forwarded JWT through the `Authorization` header.

A custom `GatewayJwtAuthenticationFilter` and `GatewayJwtTokenParser` extract user identity and role information from the token payload and create a Spring Security authentication principal.

The parser supports multiple user ID claim names and role formats, including:

- `MANAGER`
- `ADMIN`
- `ROLE_MANAGER`
- `ROLE_ADMIN`

### Authorization

Spring Security restricts access based on the authenticated user's role.

| Endpoint | Required Access |
|---|---|
| `/api/invoices/me` | Authenticated user |
| `/api/invoices/me/**` | Authenticated user |
| `/api/invoices/**` | `MANAGER`, `ADMIN` |

Customer-facing endpoints use the authenticated user's identity to retrieve their own invoices and submit chargeback requests.

### Security Architecture Considerations

The Invoice Service relies on the API Gateway for JWT signature validation and does not independently verify token signatures.

The application was originally deployed on a university-hosted server accessible through an authenticated VPN.

Although VPN access restricted network exposure, the service's published port allowed potential direct access for users with sufficient network access, bypassing gateway validation.

In a production environment, this would require additional protection, such as restricting direct service access or implementing independent JWT validation.

## CI/CD

The project uses GitLab CI/CD to automate building, testing, code quality analysis, packaging, and deployment.

The pipeline consists of the following stages:

| Stage | Description |
|---|---|
| Build | Compiles the Kotlin application using Gradle |
| Test | Executes automated tests and generates test reports |
| SonarQube | Performs static code quality analysis |
| Package | Builds a Docker image and pushes it to the GitLab Container Registry |
| Deploy | Deploys the containerized application to the university-hosted server |

The pipeline runs on configured feature, development, and main branches, with production deployment restricted to the main branch.

## HTTP API

Base path: `/api/invoices`

| Method | Path | Purpose | Access |
|---|---|---|---|
| `POST` | `/sales` | Create sales invoice | MANAGER, ADMIN |
| `GET` | `/` | Get all invoices | MANAGER, ADMIN |
| `GET` | `/{id}` | Get invoice by ID | MANAGER, ADMIN |
| `GET` | `/customer/{id}` | Get invoices for a customer | MANAGER, ADMIN |
| `PATCH` | `/{invoiceNumber}/complete` | Complete pending invoice | MANAGER, ADMIN |
| `GET` | `/{invoiceNumber}/pdf` | Generate invoice PDF | MANAGER, ADMIN |
| `GET` | `/chargebacks` | Get chargebacks | MANAGER, ADMIN |
| `GET` | `/chargebacks/{invoiceNumber}` | Get specific chargeback | MANAGER, ADMIN |
| `POST` | `/{invoiceNumber}/chargebacks` | Create chargeback | MANAGER, ADMIN |
| `GET` | `/chargeback-requests` | Get pending requests | MANAGER, ADMIN |
| `PATCH` | `/{invoiceNumber}/chargeback-request/approve` | Approve chargeback request | MANAGER, ADMIN |
| `PATCH` | `/{invoiceNumber}/chargeback-request/decline` | Decline chargeback request | MANAGER, ADMIN |
| `GET` | `/me` | Get current customer's invoices | Authenticated user |
| `GET` | `/me/{invoiceNumber}/pdf` | Download own invoice PDF | Authenticated user |
| `POST` | `/me/{invoiceNumber}/chargeback-request` | Request chargeback | Authenticated user |

## Configuration

Important environment variables:

| Property | Purpose | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8091` locally |
| `SPRING_DATASOURCE_URL` | Database connection | Local PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Database username | `postgres` locally |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `postgres` locally |
| `SPRING_RABBITMQ_HOST` | RabbitMQ host | `localhost` locally, `rabbitmq` in Docker |
| `SPRING_RABBITMQ_PORT` | RabbitMQ port | `5672` |
| `SPRING_RABBITMQ_USERNAME` | RabbitMQ username | `guest` locally |
| `SPRING_RABBITMQ_PASSWORD` | RabbitMQ password | `guest` locally |
| `RABBITMQ_AMQP_PORT` | RabbitMQ AMQP port in Docker | `5672` |
| `RABBITMQ_MANAGEMENT_PORT` | RabbitMQ management UI port | `15672` |
| `SERVICES_USER_BASE_URL` | User Service URL | `http://localhost:8081` |
| `EUREKA_CLIENT_ENABLED` | Eureka registration toggle | `true` |
| `EUREKA_DEFAULT_ZONE` | Eureka server URL | `http://localhost:8761/eureka/` |
| `EUREKA_INSTANCE_HOSTNAME` | Eureka instance hostname | `localhost` locally |
| `TAX_RATES_LABOR` | Labor VAT rate | `0.19` |
| `TAX_RATES_PART` | Part VAT rate | `0.21` |
| `TAX_RATES_SALE` | Sales VAT rate | `0.21` |

Local defaults are configured in:

- [application-local.yml](src/main/resources/application-local.yml)
- [application.properties](src/main/resources/application.properties)

## Running Locally

### Requirements

- Java 21
- PostgreSQL
- RabbitMQ
- User Service for customer lookup
- Eureka Server (when service discovery is enabled)

### Start the Service

```bash
./gradlew bootRun
```

To run with the local profile:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

The service requires its database and messaging dependencies to be available. Functionality that depends on other microservices may require those services to be running as well.

## Tests

Run all tests:

```bash
./gradlew test
```

Run a targeted test suite:

```bash
./gradlew test --tests dealership.invoiceservice.services.InvoiceServiceTest
```

## Deployment

The application was originally deployed through GitLab CI/CD to a university-hosted server.

The pipeline builds the application, creates a Docker image, pushes it to the GitLab Container Registry, and deploys it using Docker Compose.

The provided [docker-compose.yml](docker-compose.yml) expects a previously built Docker image.

Deployment-specific environment variables include:

- `DOCKER_IMAGE`
- `DEPLOY_PORT`
- `DEPLOY_CONTAINER_NAME`
- `SPRING_PROFILES_ACTIVE`
- `SERVER_PORT`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_RABBITMQ_PORT`
- `SPRING_RABBITMQ_USERNAME`
- `SPRING_RABBITMQ_PASSWORD`
- `RABBITMQ_AMQP_PORT`
- `RABBITMQ_MANAGEMENT_PORT`
- `SERVICES_USER_BASE_URL`
- `EUREKA_CLIENT_ENABLED`
- `EUREKA_DEFAULT_ZONE`

The deployment configuration is specific to the original university infrastructure and may require adjustments for other environments.

## Additional Notes

- `GET /api/invoices` is a staff endpoint; customer-facing applications should use `GET /api/invoices/me`.
- Invoice responses include chargeback request state and metadata.
- Customer chargeback requests require management approval before a chargeback invoice is created.
- CORS is configured globally through Spring Security.
- Repair invoice creation depends on the User Service for customer lookup.
- This repository contains the Invoice Service, not the complete automotive management platform.
