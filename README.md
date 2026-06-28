# endpoint-service
endpoint-service
# Endpoint Services

Single Spring Boot application covering authentication, core (orders), master
data, and order search, running as **one deployable service** on **one port
(8080)**, sharing **one database connection pool**.

## Architecture at a glance

```
endpoint-services (single Spring Boot app, port 8080)
│
├── com.authen   → Authentication module
├── com.core     → Core / Orders module
├── com.master   → Master Data module
├── com.search   → Order Search module
└── com.common   → Shared cross-module code (ApiResponse, SecurityFilter, exceptions)
```

Each module has its own API path prefix:

| Module | Base URL |
|---|---|
| Authentication | `localhost:8080/endpoint-authen-service` |
| Core (Orders) | `localhost:8080/endpoint-core-service` |
| Master Data | `localhost:8080/endpoint-master-service` |
| Order Search | `localhost:8080/endpoint-search-service` |

Inter-module calls (e.g. core to authen for token validation, core/search to
master for address lookups) are plain in-process Spring bean calls -- all 4
modules run in the same JVM/app, so there's no HTTP round trip between them.

## Project layout

```
src/main/java/com/
├── endpoint/
│   └── EndpointServicesApplication.java   ← single @SpringBootApplication entry point
│
├── common/                                ← Shared code used by all 4 modules
│   ├── dto/          ApiResponse (standard response wrapper, all modules)
│   ├── exception/    InvalidTokenException, TokenExpiredException, InvalidRequestBodyException
│   ├── config/        CacheConfig (shared CacheManager: permissionCache + masterDataCache)
│   └── filter/        SecurityFilter (single filter covering all 3 path-prefixed
│                       modules; branches internally for core's permission check
│                       and search's JWT "sub" extraction)
│
├── authen/                                ← Authentication module
│   ├── controller/   AuthenticationController, HealthController
│   ├── service/      AuthenticationService
│   ├── entity/       User, Role, Permission, Provider, UserSession
│   ├── repository/   UserRepository, RoleRepository, PermissionRepository, UserSessionRepository
│   ├── config/       SecurityConfig (Spring Security), OpenApiConfig
│   ├── util/         JwtUtil
│   ├── dto/          LoginRequest, LoginResponse, ...
│   └── exception/    GlobalExceptionHandler
│
├── core/                                  ← Core (Orders) module
│   ├── controller/       OrderController
│   ├── service/           OrderService, security/SecurityService, clients/MasterDataService
│   │                       (calls master module's repositories/AddressService directly,
│   │                        in-process — no HTTP)
│   ├── entity/            Order, Customer, OrderAddress, DeliveryJob, DeliveryRouting
│   ├── repository/        OrderRepository, CustomerRepository, ...
│   ├── dto/                OrderRequest/Response, master/* (DTOs mirroring master module's data)
│   └── exception/          GlobalExceptionHandler
│
├── master/                                ← Master Data module
│   ├── controller/        ProvinceController, DistrictController, SubdistrictController, AddressController, ConfigurationController
│   ├── service/            ProvinceService, DistrictService, SubdistrictService, AddressService, ConfigurationService
│   ├── entity/              Province, District, Subdistrict, Configuration
│   ├── repository/          ProvinceRepository, DistrictRepository, SubdistrictRepository, ConfigurationRepository
│   └── dto/, exception/       request/response DTOs, GlobalExceptionHandler
│
└── search/                                ← Order Search module
    ├── controller/        OrderSearchController
    ├── service/            OrderSearchService (dynamic Specification-based search), clients/MasterDataService
    │                        (calls master module's AddressService directly, in-process — no HTTP)
    ├── entity/              Order, Customer, OrderAddress, DeliveryJob, DeliveryRouting
    │                        (mapped with @Entity(name="Search...") to avoid clashing
    │                         with core's entities of the same table)
    ├── repository/          Search*Repository (renamed from core's plain names to avoid collisions)
    └── dto/, exception/       request/response DTOs, GlobalExceptionHandler
```

`src/main/resources/`
- `application.yml` — local/dev profile (single datasource, single port, merged config of all 4 modules)
- `application-prod.yml` — production profile (same structure, values from environment variables)

## API surface

| Module | Method & Path | Purpose |
|---|---|---|
| Authentication | `POST /endpoint-authen-service/auth/login` | Login, issue JWT |
| | `POST /endpoint-authen-service/auth/logout` | Logout / invalidate session |
| | `GET  /endpoint-authen-service/auth/validate` | Validate a token |
| | `POST /endpoint-authen-service/auth/check-permission` | Check a permission |
| | `POST /endpoint-authen-service/auth/refresh` | Refresh token |
| | `GET  /endpoint-authen-service/health` | Health check |
| Core (Orders) | `* /endpoint-core-service/api/orders/**` | Create / read / update orders |
| Master Data | `* /endpoint-master-service/api/provinces/**` | Province lookups |
| | `* /endpoint-master-service/api/districts/**` | District lookups |
| | `* /endpoint-master-service/api/subdistricts/**` | Subdistrict lookups |
| | `* /endpoint-master-service/api/address/**` | Combined address lookups |
| | `* /endpoint-master-service/api/configuration/**` | App configuration |
| Order Search | `POST /endpoint-search-service/api/search/orders` | Filtered/paginated order search |

Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI spec: `http://localhost:8080/v3/api-docs`

## How the 4 modules coexist in one app

- **Bean name collisions** (`MasterDataService`, `GlobalExceptionHandler` exist in
  multiple packages) → resolved with `FullyQualifiedAnnotationBeanNameGenerator` in
  `EndpointServicesApplication`, so beans are named by their full class path instead
  of simple name. `SecurityFilter` is defined once, as a shared bean in `com.common`,
  so there's no collision to resolve for that.
- **Duplicate JPA entity names** — `core` and `search` each have their own
  `Order`/`Customer`/`OrderAddress`/`DeliveryJob`/`DeliveryRouting` mapped to the same
  DB tables but with slightly different fields. The `search` side entities are given an
  explicit `@Entity(name = "Search...")` so Hibernate can register both without a name
  clash, and the `search` repositories are named `Search*Repository` for the same
  reason.
- **One shared `SecurityFilter`** (`com.common.filter.SecurityFilter`) — a single
  `OncePerRequestFilter` bean covers all three path-prefixed modules
  (`/endpoint-core-service/**`, `/endpoint-master-service/**`,
  `/endpoint-search-service/**`); `shouldNotFilter()` skips everything else. Each
  module's distinguishing behavior (core's permission check via `SecurityService`,
  search's JWT `sub`-claim extraction) is handled via path-prefix branching inside the
  filter. The authentication module's Spring Security chain is scoped separately via
  `.securityMatcher("/endpoint-authen-service/**")`.
- **One shared `ApiResponse` / exception set** (`com.common.dto`, `com.common.exception`)
  — the standard response wrapper and `InvalidTokenException`/`TokenExpiredException`/
  `InvalidRequestBodyException` are defined once and reused by all modules.
- **No internal HTTP calls between modules** — `core`'s and `search`'s `MasterDataService`
  (in `clients/`) inject `master`'s repositories/`AddressService` directly as Spring
  beans instead of going through `RestTemplate`/`ApiClient`, since all 4 modules run in
  the same JVM. The `service.master.url` / `SERVICE_MASTER_URL` config entries are now
  unused leftovers.
- **Exception handlers** — each `GlobalExceptionHandler` is scoped with
  `@RestControllerAdvice(basePackages = "...")` to its own module's controller
  package, but they all build responses from the shared `com.common.dto.ApiResponse`
  and catch the shared exception types.
- **Two caches, one manager** — `core`'s `permissionCache` (1h TTL) and `search`'s
  `masterDataCache` (10m TTL) are registered together on a single
  `SimpleCacheManager` bean (in `com.common.config.CacheConfig`).
- **One shared database connection pool** — all 4 modules use the same `endpoint_db`
  Postgres database through a single Hikari pool (`EndpointServicesHikariPool`).

## Configuration

All config is merged into two YAML files:

- **`application.yml`** (default/local) — single Postgres datasource (`endpoint_db`),
  JWT secret/expiration, `security.filter.*` properties (read by the shared
  `com.common.filter.SecurityFilter`), inter-module service URLs, search tuning,
  actuator, and Swagger settings. `api.filter.token.prefix` is kept as a separate key
  — it's read independently by `com.core.service.security.SecurityService`, unrelated
  to the main security filter.
- **`application-prod.yml`** — same structure, values sourced from environment
  variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`,
  `JWT_SECRET`, `SERVER_PORT`, `SERVICE_*_URL`, ...).

`spring.jpa.hibernate.ddl-auto` is `none` because two modules (`core`, `search`) map
different entity definitions onto the same physical tables — schema is managed by the
database directly, not by Hibernate.

## Running locally

```bash
# requires a local PostgreSQL with the endpoint_db database and matching credentials
# (see application.yml for default dev credentials)
mvn spring-boot:run
```

The app starts on `http://localhost:8080`. To run against the production profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

## Build

```bash
mvn clean package
java -jar target/endpoint-services-0.0.1-SNAPSHOT.jar
```
