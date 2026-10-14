# Qaryati – Software Architecture

## 1. Project Overview

Qaryati is an API-centric platform designed to organize and manage structured information about Palestinian villages. The system aims to preserve village identity, geographical information, historical and population records, cultural heritage, agricultural activities, and community facilities.

The backend is developed using Java 21, Spring Boot, and PostgreSQL.

## 2. Architectural Style

Qaryati follows a **Modular Monolith Architecture**, combined with **Layered Architecture** and **Package-by-Feature organization**.

The application is deployed as one backend system, but its responsibilities are divided into separate modules.

Each module owns a specific business area and organizes its code into layers such as Controller, Service, Repository, Entity, DTO, and Mapper.

### Why We Chose This Architecture

- **Separation of concerns:** Each module has a clearly defined responsibility.
- **Maintainability:** Changes in one feature can be managed without unnecessarily affecting other features.
- **Team collaboration:** Team members can work on different modules using separate Git branches.
- **Simpler deployment:** A single application is easier to develop, test, and deploy than multiple independent services.
- **Extensibility:** Well-defined module boundaries can support future architectural changes.

### Trade-offs

A modular monolith still shares one application deployment and database infrastructure. Modules must follow dependency rules to avoid becoming tightly coupled.

Unlike microservices, individual modules cannot be deployed and scaled independently.

## 3. Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Backend programming language |
| Spring Boot 4.1.1 | Backend application framework |
| Spring Web MVC | REST API development |
| Spring Data JPA | ORM-based database access |
| PostgreSQL | Relational database |
| Maven | Dependency management and build automation |
| Spring Security | Authentication and authorization |
| Spring Validation | Input validation |
| Spring Modulith | Support for module organization and architectural verification |
| Git and GitHub | Version control and team collaboration |

Planned development tools include Docker for containerization, k6 for performance testing, and an external geocoding service for geographical data integration.

## 4. Module Organization

The application is organized under the base package `com.qaryati`.

| Module | Responsibility | Assigned Member |
|---|---|---|
| Village | Village identity, geographic data, and village lookup | Member 1 |
| Population | Population records and historical demographic information | Member 2 |
| Heritage | Historical events, landmarks, and cultural heritage | Member 2 |
| Security | Authentication, authorization, and access control | Member 3 |
| Data Quality | Data provenance, source tracking, conflicts, and auditing | Member 3 |
| Agriculture | Crops, agricultural products, and village farming activities | Member 4 |
| Community | Community facilities and local economic activities | Member 4 |
| Integration | External geocoding and mapping service integration | Member 4 |
| Common | Shared technical utilities and application-wide concerns | Shared |

## 5. Internal Layered Structure

Most business modules follow the structure below:

```text
module/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
├── mapper/
└── package-info.java
```

**Controller:** Receives HTTP requests, validates request data, and returns API responses.

**Service:** Implements business logic, coordinates operations, and manages transaction boundaries.

**Repository:** Handles database access through Spring Data JPA or SQL queries.

**Entity:** Represents persistent domain data.

**DTO:** Defines request and response data structures.

**Mapper:** Converts between Entities and DTOs.

Some modules use specialized packages. For example, Security includes `config`, Data Quality includes `audit`, and Integration includes `client`, `adapter`, and `exception`.

The Village module also includes an `api` package for public Java interfaces that may be used by other modules.

## 6. Module Boundaries and Dependency Rules

To maintain modularity, the project follows these design rules:

1. Each business module owns its Entities and Repositories.
2. A module must not directly access another module's Repository or internal Entity implementation.
3. Cross-module operations should use explicitly defined public interfaces.
4. Controllers should not contain business logic.
5. Database access and transaction coordination belong in the appropriate application services and repositories.
6. The Common module should contain only genuinely shared technical concerns.
7. External API calls should be isolated behind integration interfaces or adapters.
8. Circular dependencies between modules should be avoided.

### Example: Population and Village

A population record must be associated with a valid village.

The Population module should use a public Village lookup contract to check whether a village exists rather than accessing `VillageRepository` directly.

The exact interface and return types will be agreed upon by the team before implementation.

### Example: Village and External Geocoding

The Village module may need geographical coordinates from an external service.

The Integration module will isolate the external API's request formats, response formats, and error handling.

This approach reduces the effect of changing external service providers.

## 7. API and Database Design Principles

The system will expose REST APIs for managing village-related information.

The API design should follow these principles:

- Use resource-oriented endpoints and appropriate HTTP methods.
- Validate requests and return consistent error responses.
- Apply authentication and role-based authorization where required.
- Support filtering, pagination, and searching where relevant.
- Keep external API response formats separate from internal API models.
- Preserve traceability for records requiring historical sources or audit information.

PostgreSQL will be used for relational data storage. Spring Data JPA will support ORM-based operations, while selected repository operations will use SQL where required.

Database schema changes should be managed through versioned migrations when the migration strategy is finalized.

## 8. External API Integration

An external geocoding service is planned to help obtain or validate geographical coordinates.

The Integration module will be responsible for:

- HTTP communication with the service.
- Mapping external responses to internal data structures.
- Configuring timeouts and service URLs.
- Handling service failures and invalid responses.
- Supporting future replacement of the service provider.

The exact provider, authentication requirements, rate limits, and fallback behavior will be decided during implementation.

## 9. Security and Data Quality

### Security

The Security module is responsible for authentication, authorization, user roles, and endpoint access restrictions.

### Data Quality

The Data Quality module is responsible for maintaining data provenance, recording sources, tracking changes, and supporting auditability.

These responsibilities are important because Qaryati manages historical and potentially conflicting information from different sources.

## 10. Testing and Quality Assurance Plan

The planned testing strategy includes:

- Unit tests for individual services and business rules.
- Integration tests for persistence and REST API behavior.
- Architecture tests to check module boundaries.
- Security tests for protected operations.
- Performance and load testing using k6.

Spring Modulith will be evaluated for verifying module dependencies and detecting violations of architectural boundaries.

Passing compilation alone does not verify that the architecture is correct.

## 11. Development Workflow

The project is maintained in a shared GitHub repository.

The development workflow is:

1. Keep `main` as the integration branch.
2. Create a separate feature branch for each task or module.
3. Make focused commits with meaningful commit messages.
4. Push changes to GitHub.
5. Open a Pull Request.
6. Review and test changes before merging into `main`.
7. Avoid committing passwords, secrets, generated build files, or local IDE settings.

## 12. Current Project Status

The initial Spring Boot project, PostgreSQL connection configuration, and package structure have been prepared.

Package documentation files have been added to establish the intended organization.

The modules currently represent an architectural skeleton. Their business logic, database entities, REST endpoints, public interfaces, and automated architectural checks still need to be implemented.

The final dependency contracts and domain model will be refined through team discussion.

## 13. Planned Architectural Documentation

The team will maintain Architecture Decision Records (ADRs) describing important design choices, including:

- ADR-001: Modular Monolith versus Microservices.
- ADR-002: PostgreSQL and the database access strategy.
- ADR-003: External API integration and failure handling.

An architecture diagram, database model, API contract documentation, and performance testing results will be added as implementation progresses.