# Backend Developer Workflow

This guide explains the Restaurant SaaS backend architecture, operational rules, and safe development workflow. Read it before changing APIs, persistence, security, or restaurant lifecycle behavior.

## 1. Service overview

The backend is a multi-tenant restaurant operations API implemented as a Spring Modulith modular monolith. It owns:

- tenant onboarding and restaurant configuration;
- staff identity, roles, permissions, access and refresh tokens;
- outlets, areas, tables, table sessions, and QR access;
- menu catalog and food image storage;
- dine-in and takeaway orders, order rounds, and order status transitions;
- kitchen tickets and fulfilment state;
- waiter profiles, availability, assignments, and notifications;
- billing, invoices, payments, and table release;
- inventory, stock movement, reporting, audit, idempotency, and outbox events.

The React frontend is a separate client. The backend remains authoritative for validation, authorization, tenant isolation, money calculations, and workflow transitions.

## 2. Technology stack

| Concern | Technology |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.1 |
| Architecture | Spring Modulith modular monolith |
| HTTP | Spring Web MVC |
| Persistence | Spring Data JPA and PostgreSQL |
| Schema migrations | Flyway |
| Security | Spring Security and Nimbus JOSE JWT |
| Cache and rate limits | Redis |
| API documentation | springdoc OpenAPI |
| Testing | JUnit 5, Testcontainers, ArchUnit |
| Build | Gradle Wrapper 9.1 |

## 3. Repository layout

```text
backend/
├── src/main/java/com/restaurant/
│   ├── billing, catalog, configuration, identity
│   ├── inventory, kitchen, onboarding, order
│   ├── organization, outlet, payment, platform
│   └── reporting, waiter
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
├── src/test/                 # integration and architecture tests
├── scripts/                  # local Docker lifecycle scripts
├── streamlit_app/            # auxiliary Streamlit client
├── build.gradle
├── docker-compose.yml
└── Dockerfile
```

Most business modules follow this structure:

```text
module/
├── api/              # controllers, public facades, request and response boundary
├── application/      # use cases, orchestration, transactions
├── domain/           # domain types and state rules
└── infrastructure/   # JPA entities, repositories, adapters
```

Keep module internals private where practical. Cross-module calls should use public facades or application events rather than another module's repositories.

## 4. First-day reading path

Read these files in order:

1. `build.gradle`
2. `src/main/resources/application.yml`
3. `RestaurantSaasApplication.java`
4. `identity/infrastructure/SecurityConfig.java`
5. `identity/infrastructure/JwtAuthFilter.java`
6. `platform/api/TenantContext.java`
7. `platform/infrastructure/RlsDataSourceConfig.java`
8. one controller, its application service, entities, and repositories
9. the migration that introduced that feature
10. the corresponding integration test

For the main restaurant workflow, trace outlet and floor -> order -> kitchen and waiter -> billing -> payment.

## 5. Local setup

Prerequisites are Java 25, Docker, PostgreSQL, and Redis. Use the checked-in Gradle wrapper.

Start the Docker stack on Windows:

```powershell
.\scripts\dev-up.ps1
```

On macOS or Linux:

```bash
./scripts/dev-up.sh
```

Run the API locally with environment-provided database, Redis, Flyway, and JWT settings:

```powershell
.\gradlew.bat bootRun
```

Useful endpoints:

- API: `http://localhost:8080`
- health: `http://localhost:8080/actuator/health`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## 6. Configuration and secrets

The Gradle wrapper uses a 60-second network timeout so clean Docker builds can download the distribution reliably on slower development networks.

If Docker cannot reach Gradle or Maven repositories, run `gradlew.bat bootJar -x test` on the host and build `Dockerfile.prebuilt`. This fallback packages the locally validated JAR into the same Java 25 runtime image; never use a stale artifact.

`application.yml` defines defaults and environment-variable bindings. Runtime environments must provide database credentials, Flyway credentials, Redis settings, JWT secret, and public base URL.

- Never commit real passwords, signing secrets, API keys, or production connection strings.
- Never log tokens, passwords, password-reset values, customer data, or payment data.
- Keep the application database user separate from the privileged Flyway owner where row-level security requires it.
- Prefer environment variables or an ignored local profile for developer-specific values.
- Treat a credential found in version control as exposed: rotate it and remove it from future configuration.

JPA uses `ddl-auto: validate`. Hibernate must not create or mutate the production schema.

## 7. Module boundaries and transactions

Controllers validate transport input, read route/header information, call an application service, and return stable contracts. They should not implement persistence or workflow rules.

Application services:

- enforce invariants and resource-level authorization;
- define transaction boundaries with `@Transactional`;
- coordinate repositories and public module APIs;
- publish events after valid state changes;
- remain independent of frontend presentation.

Repositories and entities belong in the owning module's infrastructure package. Avoid returning mutable JPA entities from APIs.

Use locking or atomic database operations where concurrent requests could create duplicate active orders, over-assign a waiter, oversell stock, or record payment twice.

## 8. Multi-tenancy and data isolation

`JwtAuthFilter` creates a `TenantPrincipal` and places it into Spring Security and `TenantContext`. The data-source wrapper applies tenant information to PostgreSQL connections for row-level security.

Every tenant-owned row must carry the correct tenant ID. New code must:

1. obtain the principal through `TenantContext.require()`;
2. verify outlet and resource ownership for supplied identifiers;
3. set tenant ID on new entities;
4. use tenant-safe repository methods;
5. preserve PostgreSQL RLS policies in migrations;
6. test cross-tenant access denial.

Never trust a tenant ID supplied only in a request body. Derive authority from the authenticated principal.

## 9. Authentication and authorization

The access-token API is stateless.

- Staff JWTs contain tenant, user, outlet, and role claims.
- Guest tokens contain tenant, outlet, table, session, and QR-token claims.
- Refresh tokens are random opaque values stored as hashes.
- Refresh rotates the token and revokes the previous value.
- Reuse, revocation, expiry, invalid signatures, and inactive users fail closed.
- Login attempts are rate-limited through Redis.

Platform administrators are tenantless identities stored separately from restaurant users. The first administrator is provisioned only when the database has none and both `APP_PLATFORM_ADMIN_EMAIL` and `APP_PLATFORM_ADMIN_PASSWORD` are supplied at startup; the password must be at least 14 characters and the variables must be removed after provisioning. Platform login, refresh, and logout live under `/api/v1/platform/auth/**`. Their JWTs use `typ=platform` and the `SUPER_ADMIN` role; a tenant role named `SUPER_ADMIN` must never grant platform access.

Migration V15 grants the runtime database role only the select, insert, and update operations required for platform authentication. It does not grant schema changes, deletion, or tenant-table bypass privileges.

Super Admin management is available only to authenticated platform tokens under `/api/v1/platform/administrators`. Invitations store only a hash of a random, single-use setup token and expire after 48 hours. The token is returned once to the inviting administrator for delivery; `/api/v1/platform/auth/setup-password` activates the account after a password of at least 14 characters is set. Administrators cannot disable their own account, disabling revokes every refresh token, and management actions are recorded in `platform_administrator_audit`. Migration V16 adds these invitation and audit structures and grants the runtime role only their required DML permissions.

Migration V17 adds narrowly scoped `SECURITY DEFINER` read functions for platform dashboard, plan, restaurant list/detail, and audit projections. Public execution is revoked and only `restaurant_app` may call them. This supplies cross-tenant control-plane reads without placing the request connection in bootstrap mode or weakening normal RLS policies. Current tenant status and tenant plan codes are authoritative; subscription risk counters and dates remain empty until a dedicated subscription lifecycle table exists.

Route rules live in `SecurityConfig`; resource permissions also belong in controllers/services. Authentication alone does not authorize every outlet operation.

When adding an endpoint:

1. classify it as public, guest, staff, or role-limited;
2. add the narrowest route or method rule;
3. validate outlet membership and ownership;
4. test allowed and denied roles;
5. do not reveal whether another tenant's resource exists.

## 10. API conventions

- Version business endpoints under `/api/v1`.
- Prefer typed request and response DTOs for new contracts.
- Validate UUIDs, ranges, lengths, enums, and required values.
- Return stable error codes through `ApiException` and `GlobalExceptionHandler`.
- Preserve request IDs for troubleshooting.
- Keep OpenAPI contracts accurate.
- Make destructive and financial mutations idempotent when retries are possible.

| Status | Meaning |
| --- | --- |
| 400 | invalid request or validation failure |
| 401 | missing, invalid, or expired authentication |
| 403 | authenticated but not permitted |
| 404 | resource not visible or not found |
| 409 | business conflict or illegal transition |
| 500 | unexpected failure; details remain in logs |

Never leak stack traces, SQL, credentials, or entity internals in API responses.

### Read-path performance

Operational screens poll aggregate endpoints, so query count must remain constant as the number of visible tables or orders grows.

- Do not call a repository from inside a stream or loop over a result set.
- Batch related entities with `IN` queries or use one tenant-scoped aggregate query.
- Filter terminal/history rows in SQL instead of loading them and filtering in Java.
- Reuse already-loaded rows when building detail responses.
- Add tenant-prefixed indexes for new joins and polling predicates.
- Keep frontend polling intervals visibility-aware and avoid one HTTP request per card.
- Measure query count and endpoint latency with representative active-order volume before handoff.

## 11. Money and quantities

- Money is stored as `long` paise. Never use floating point for currency.
- Use the shared `Money` value type for arithmetic.
- Quantities use `BigDecimal` and `NUMERIC(19,4)` with shared validation.
- Percentage charges use basis points where defined.
- Order lines snapshot names and prices so menu edits do not rewrite sales history.
- Server totals are authoritative; frontend totals are previews.
- Guard overflow, negative values, excessive quantities, and invalid discounts.

Financial changes require tests for zero totals, partial payment, over-tender and change, rounding, discounts, tax modes, and duplicate submission.

## 12. Restaurant lifecycle rules

### Floor

Areas and tables are persisted under an outlet. Names are not identifiers. Enforce uniqueness and ownership at service and database levels.

Table state is coupled to active order state. A table with an active order cannot be made available manually. The normal flow is:

```text
FREE -> OCCUPIED -> PAYMENT_PENDING -> CLEANING -> FREE
```

API labels may be friendlier, but services must use the canonical values defined by the owning module.

### Orders

Only one active dine-in order should exist for a table unless the business model explicitly changes. `OrderStatus` governs transitions; do not assign arbitrary states.

Within the transaction, validate menu availability, outlet ownership, quantities, modifiers, notes, table availability, waiter assignment, and maximum open amount.

Every newly created order has an explicit `OrderType` and `OrderEntryMode`. Supported contexts are:

- `DINE_IN + DIRECT_POS`;
- `DINE_IN + WAITER_PAPER_COUNTER`;
- `TAKEAWAY + DIRECT_POS`.

The context changes how an order starts, not how items, KOTs, invoices, or payments are implemented. Keep those lifecycle operations in the common `OrderService`. A dine-in order must have a table; a takeaway must not occupy or release one. Additional rounds must append to the supplied order ID—never look up a tableless order by table ID.

Order entry is exposed through:

- `POST /api/v1/tables/{tableId}/orders` for idempotent dine-in start/reopen;
- `POST /api/v1/orders/takeaway` for a tableless order and daily pickup token;
- `GET /api/v1/orders/takeaway/active?outletId=...` for the active counter queue;
- the existing `POST /api/v1/orders/{orderId}/rounds`, request-bill, and payment APIs for both types.

`OrderConfigurationService` merges the `order_configuration` entry from tenant scope with an optional outlet override. The effective settings control enabled order types, the default dine-in entry mode, additional KOT permission, automatic completion after full payment, and stock policy. Updates require numeric `If-Match`; tenant updates require OWNER and outlet updates require OWNER or MANAGER.

Migration `V13__order_types_and_takeaway_tokens.sql` is intentionally expand-only. Historical rows remain unchanged and are interpreted with legacy fallbacks at read time. New writes populate order context, business date, creator, order number, and token. Do not add an irreversible historical classification update without a separately reviewed migration plan.

### Kitchen and waiters

Kitchen ticket and line fulfilment states decide whether service is complete. Waiter assignment, availability, history, and notifications are server-owned. Notification clear or acknowledge operations must be explicit and tenant-safe.

### Billing and payment

Invoice generation freezes the relevant order snapshot. Payment must calculate the remaining balance atomically and prevent duplicate financial effects.

After full payment:

1. mark the invoice paid;
2. publish payment event and outbox records;
3. move the order from billed to paid;
4. automatically complete a takeaway when outlet configuration allows it;
5. complete a dine-in order only when kitchen and service work are terminal;
6. move a completed dine-in table to cleaning;
7. allow an authorized waiter or manager to release the cleaned table.

Do not release a billed or paid table while active service work remains.

## 13. Flyway migrations

Migrations live in `src/main/resources/db/migration` and execute in version order during application startup.

`V18__inventory_stock_engine.sql` is the forward-only inventory expansion. It follows the already-applied `V11__menu_item_image_storage.sql` and the V12-V17 platform sequence. Existing databases at V17 apply the inventory expansion as V18; clean databases retain exactly one migration per version. Do not rename this migration back to V11.

`V19__platform_subscription_lifecycle.sql` adds the global subscription-plan catalog, authoritative tenant subscriptions and immutable history, and versioned tenant/outlet lifecycle fields. Restaurant provisioning is restricted to `POST /api/v1/platform/restaurants`; legacy public onboarding returns `403` unless `app.legacy-onboarding-enabled=true`, which is reserved for integration fixtures and controlled migrations. Provisioning creates the tenant, brand, first outlet, owner setup token, subscription, and audit record atomically. Tenant/outlet status and subscription commands require `If-Match`; restaurant owners read their assigned outlets, subscription, and features through `/api/v1/me/*`.

`SubscriptionLifecycleService` evaluates subscriptions hourly by default (`app.subscription.lifecycle-cron`). It records `EXPIRING_SOON`, `GRACE_PERIOD`, and `EXPIRED` transitions in subscription history and audit data. The first seven days after the end date are the grace period. Expiry immediately blocks new orders and additional rounds and marks an active tenant `SUBSCRIPTION_EXPIRED`; billing, payment, fulfilment, cancellation, and closing remain available so existing work can finish. Renewal restores a tenant that was expired automatically.

`V20__billing_payment_integrity.sql` safely enforces valid new invoice/payment writes while tolerating unverified legacy rows through `NOT VALID` checks. Advisory-lock triggers serialize invoice-per-order and KOT-per-round creation without requiring a potentially unsafe unique-index deployment. Payment recording locks the invoice, accepts only `CASH`, `UPI`, or `CARD` with a positive amount, and changes a fully settled invoice to `PAID`. Completing a paid dine-in order releases its table directly to `FREE` under the V1 baseline; the optional cleaning workflow remains available for manual operational use but is not forced after payment.

`V21__order_inventory_consumption.sql` records one inventory-consumption marker per completed order. Recipe stock is no longer deducted when a KOT is confirmed; completion posts every captured recipe version exactly once in the same transaction. Orders cancelled before completion therefore consume nothing. `POST /api/v1/recipes` accepts an `ingredients` array to create one immutable multi-ingredient recipe version, while the legacy single-ingredient request remains compatible.

`V22__kot_print_jobs.sql` persists an initial KOT print job before dispatch and records reprints separately with a reason, attempts, print timestamp, and failure detail. `app.kot.printer-enabled` defaults to `false`, which deliberately marks jobs `FAILED` rather than pretending a printer exists. Kitchen users can request `/kots/{id}/reprint` or `/kots/{id}/retry-print`; the scheduled retry worker retries failed jobs up to five times. A production printer adapter should be enabled only after its delivery path is configured and monitored.

Entities with application-assigned UUIDs and optimistic locking use nullable `Long` versions. A null version tells Spring Data that a UUID-bearing instance is new and must be persisted; exposing version `0` through API views preserves the existing optimistic-concurrency contract. Do not change these fields back to primitive `long`, which causes new records to be merged and can produce a stale-version conflict inside their creation transaction.

Cross-module dependencies must go through a declared named interface. Platform authentication depends on the platform-owned `PlatformTokenService` contract, implemented by identity, so the platform module does not import identity implementation classes. Payment totals used by order and waiter flows go through `PaymentFacade`; consumers must not import payment repositories or entities.

1. Inspect the latest version before choosing the next number.
2. Add `V<number>__descriptive_name.sql`.
3. Never modify a migration applied to a shared database.
4. Make changes safe for existing data.
5. Backfill or clean data before adding restrictive constraints.
6. Index new lookup, foreign-key, polling, and uniqueness patterns.
7. Include tenant IDs and RLS policies on tenant-owned tables.
8. Consider locks, migration duration, forward fixes, and mixed application versions.
9. Start against a representative database and confirm Flyway validation.
10. Add integration coverage for new persistence behavior.

The sequence currently ends at V12. Always inspect the directory because another change may have already claimed the next number. V12 adds tenant-prefixed indexes for waiter/order, invoice, and payment summary reads.

## 14. Events, outbox, audit, and idempotency

Use application events for in-process module coordination and the outbox for durable downstream or reporting effects.

- Publish only after the owning state change is valid.
- Include tenant, outlet, and resource identifiers needed by consumers.
- Make consumers safe to retry.
- Do not use events to hide required synchronous invariants.
- Audit sensitive administrative and lifecycle operations.
- Use `IdempotencyService` when duplicate external requests would be unsafe.

The outbox poller must retain retry information and stop after the configured maximum instead of spinning indefinitely.

## 15. Menu images

Image upload and public retrieval must enforce:

- owner or manager authorization for mutation;
- accepted image MIME types and strict size limits;
- non-empty, valid content where practical;
- tenant, outlet, and item ownership;
- safe response headers and cache behavior;
- no use of the original filename as a storage path;
- bounded replacement and cleanup behavior.

Keep binary payloads out of JSON and logs.

## 16. Testing

Run all tests from the backend repository.

Windows:

```powershell
.\gradlew.bat test
```

macOS or Linux:

```bash
./gradlew test
```

Docker is required for Testcontainers integration tests. Existing tests cover module boundaries, money architecture, authentication and refresh, access management, floor and menu management, waiter operations, and the main restaurant spine.

New behavior should test:

- success and validation failures;
- authentication and every relevant role;
- cross-tenant and wrong-outlet identifiers;
- conflicts and illegal transitions;
- repeated or idempotent calls;
- rollback after downstream failure;
- concurrency where uniqueness, stock, or money is affected;
- database constraints and migration behavior;
- events and outbox effects;
- edge values for money, quantity, text, and uploads.

## 17. Debugging and operations

Start with `GET /actuator/health`, then correlate the HTTP error code, request ID, tenant/user/outlet MDC fields, application logs, database state, Flyway history, Redis connectivity, and outbox attempts.

Do not diagnose production by disabling authentication, RLS, validation, or transition rules.

- `401`: invalid or expired access token, or rejected refresh rotation.
- `403`: role, permission, outlet, assignment, or guest restriction.
- `404`: wrong tenant, outlet, resource ID, or deleted resource.
- `409`: duplicate name, active order, unavailable resource, or illegal transition.
- `500`: inspect logs and schema consistency; keep the client response safe.

## 18. Adding a feature safely

1. Identify the owning module and current public API.
2. Trace controller, service, persistence, migration, and tests.
3. Define invariants, roles, tenant boundary, transaction, and idempotency.
4. Design request, response, validation, and error codes.
5. Add a forward-only migration if persistence changes.
6. Implement orchestration in the application layer.
7. Keep persistence in infrastructure and expose only necessary module APIs.
8. Add audit, event, and outbox effects where required.
9. Test success, denial, conflict, isolation, retry, and concurrency.
10. Update this guide and OpenAPI behavior.
11. Run the complete suite and a focused API smoke test.

## 19. Documentation enforcement

Two safeguards keep this guide synchronized:

- `AGENTS.md` requires coding agents to update it with relevant backend changes.
- `.githooks/pre-commit` blocks commits containing backend code or configuration changes unless this file is staged too.

The Gradle `classes` task installs the hook automatically in a Git working tree. Install it directly on Windows with:

```powershell
.\gradlew.bat installGitHooks
```

or on macOS and Linux with:

```bash
./gradlew installGitHooks
```

If a change genuinely does not affect developer guidance, bypass once with `SKIP_BACKEND_DEVELOPER_DOC_CHECK=1`. Do not add meaningless documentation edits.

## 20. Outlet reporting read models

The `reporting` module exposes owner-facing outlet read models under `/api/v1/outlets/{outletId}`: `dashboard`, `daily-sales`, `reports/sales`, `reports/top-items`, `reports/payment-mix`, and `alerts`. Every endpoint obtains the tenant from `TenantContext` and requires a non-guest principal assigned to the requested outlet.

Daily and range sales use `outlet_daily_sales`, which is populated through the outbox projection. Top items use PAID invoice snapshots and payment mix uses successful payment records. Dashboard attention alerts use current inventory balances and reorder levels. Reporting ranges are inclusive and capped at 93 days to keep read queries bounded. These endpoints are read-only and should remain free of side effects.

`GET /api/v1/audit-log` returns the recent tenant operational history to an `OWNER` only. It is deliberately tenant-wide, since older `audit_log` records are not outlet-labelled; do not expose it to narrower outlet roles until new audit events carry a durable outlet scope.

### Unpaid bill revision

A generated bill may be reopened through the existing order `unlock-add` command only before a successful payment exists. Reopening voids the generated invoice while retaining its immutable snapshot, returns the order to `READY`, and permits another round. Generating the bill again creates a new invoice from all current order lines. Any successful (including partial) payment blocks reopening; paid invoices are never edited. Migration `V23` permits invoice history while enforcing at most one `GENERATED` invoice per order.

Financial-control work begins with `V24`'s append-only `invoice_amendments` ledger. Amendment request, approval, and application must be explicit server-side commands; store only a short-lived token hash, never an approver password or PIN.

`V25` introduces tenant-wide invoice numbering and `V26` safely assigns non-colliding `INV-LEGACY-*` numbers to older rows while protecting the sequence table with tenant RLS. Invoice search is available at `GET /api/v1/outlets/{outletId}/invoices`; `GET /api/v1/invoices/{invoiceId}` returns the immutable line snapshot, payments, amendments, and invoice audit trail. Financial amendments use explicit request, manager/owner approval, and one-time apply commands. Approval credentials are verified server-side and are never persisted.

## 21. Contribution checklist

### Owner-managed role permissions

### Orphaned table reconciliation

Reconciliation updates the table status and optimistic-lock version only. The `tables` schema has no `updated_at` column, so native table-status mutations must not attempt to write one.

Operational statuses `OCCUPIED` and `BILL_REQUESTED` are order-controlled and cannot be assigned or cleared through the generic table update command. Terminal generic order transitions release their table. `POST /api/v1/outlets/{outletId}/tables/reconcile` is an owner/manager, tenant-and-outlet-scoped repair command that resets only occupied/bill-requested tables for which no non-terminal order exists. It increments table versions and audits repairs; it never changes a table that still has an active order.

`PUT /api/v1/users/roles/{roleCode}/permissions` replaces the selected non-owner role's permission set for the authenticated tenant. Only an `OWNER` may call it; the `OWNER` role is immutable and always retains the complete tenant permission catalog. Permission codes are validated against the tenant catalog, the replacement and audit event are transactional, and refresh tokens for users assigned to the changed role are revoked so stale authorization cannot survive a role update. The UI must communicate that affected staff need to sign in again.

Migration `V27` adds `roles.permissions_customized`. Default permission seeding skips customized roles so removed permissions, including an intentionally empty role, are not silently restored when access services seed the tenant catalog.

- [ ] The owning module and existing flow were traced.
- [ ] API, application, domain, and infrastructure responsibilities remain separated.
- [ ] Tenant and outlet ownership are enforced.
- [ ] Roles and permissions are enforced server-side.
- [ ] Inputs and transitions are validated.
- [ ] Money uses paise and quantities use validated decimals.
- [ ] Transactions, concurrency, and duplicate submission were considered.
- [ ] Schema changes use a new forward-only Flyway migration.
- [ ] Tenant schema includes IDs, constraints, indexes, and RLS behavior.
- [ ] Errors are stable and reveal no internals.
- [ ] Audit, events, outbox, and idempotency were considered.
- [ ] Authorization, isolation, conflict, retry, and success tests were added.
- [ ] Secrets and sensitive data are absent from code, docs, and logs.
- [ ] `BACKEND_DEVELOPER_WORKFLOW.md` reflects the change.
- [ ] `gradlew test` passes and the result is reported.
## Owner multi-outlet management

Restaurant owners can list and create their tenant's branches through `/api/v1/me/outlets`. Branch creation is tenant-scoped, audited, serialized against the subscription row, and rejected when the effective subscription outlet limit is reached. Every owner account in the tenant is assigned to a newly created branch through `user_outlets`; clients must refresh authentication after creation so JWT outlet scope includes the branch. `GET /api/v1/users/outlets` is safe for the shared workspace selector: owners receive tenant branches, while other staff receive only branches present in their authenticated outlet scope. Operational APIs continue to enforce the selected outlet against the authenticated `outletIds` claim.
## Operational dashboard

The restaurant dashboard uses tenant- and outlet-scoped read endpoints under `/api/v1/outlets/{outletId}/dashboard`. Summary comparisons use the previous equivalent date range; settled sales come only from successful payments and remain integer paise. Order totals use the persisted restaurant `business_date`, while payment time buckets use the outlet timezone. Overview, order-status, recent-order, and debounced search projections are database aggregations/read models rather than in-memory entity scans. Rating is intentionally unavailable until a real review domain exists.
