# Dynamic Pricing Engine

[![CI](https://github.com/anantghadifreelance/dynamic-pricing-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/anantghadifreelance/dynamic-pricing-engine/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?logo=springboot)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

A Spring Boot service that works out the **final selling price of a product for a marketplace**
(Amazon, Flipkart or your own store). Each quote runs through a chain of pluggable pricing rules:
cost plus margin, promotional discounts, quantity tiers, freight, marketplace commission and closing
fee, a minimum-selling-price floor, and GST. You get back every rule's adjustment as well as the final price.

It's modelled on a minimum-selling-price (MSP) pricing engine I built for marketplace sellers, which
handled discounts, freight and marketplace commissions, with multithreaded batch repricing and Redis caching.

## Features

- **Rule pipeline (strategy + chain of responsibility)**: every rule is a `PricingRule` Spring bean with an `@Order`. Adding a rule means adding a bean; the engine itself doesn't change.
- **Exact money maths**: all amounts are `BigDecimal`, rounded `HALF_UP` to 2 decimal places in one place (`Money`).
- **Discounts with validity windows**, stored in H2 through JPA and managed with CRUD endpoints. A discount can target a SKU, a marketplace, or everything.
- **Quantity tiers**: the best matching volume break applies (tiers don't stack).
- **Freight by weight slab and zone**, with a per-kg charge above the top slab.
- **Marketplace commission % plus a fixed closing fee**, set per marketplace in `application.yml` through `@ConfigurationProperties`. The price is grossed up, so the seller still receives the pre-fee amount.
- **GST** at a configurable rate.
- **Minimum price floor**: the price never drops below cost + minimum margin + pass-through charges, however many discounts stack.
- **Batch quoting in parallel** on a bounded `ThreadPoolExecutor` with `CompletableFuture`. It uses back-pressure, a timeout, and keeps results in request order.
- **Spring Cache**: `@Cacheable` quotes keyed by (request, pricing date). Uses an in-memory `ConcurrentMap` by default, or **Redis** under the `redis` profile. The cache is cleared whenever a discount changes.
- **RFC 7807 errors**: every failure comes back as a `ProblemDetail`, with field-level validation messages.
- **Tests that need no external services**: parameterized unit tests for each rule and the floor guard, plus pipeline, concurrency and MockMvc integration tests (H2, no Redis).

## Tech stack

| Area        | Choice                                                            |
|-------------|-------------------------------------------------------------------|
| Language    | Java 17 (records, switch expressions, text blocks)                |
| Framework   | Spring Boot 3.3.5 (Web, Validation, Data JPA, Cache)              |
| Persistence | H2 (in-memory) via Spring Data JPA                                |
| Caching     | Spring Cache: ConcurrentMap by default, Redis (`redis` profile)   |
| Concurrency | `ThreadPoolExecutor` (bounded queue) + `CompletableFuture`        |
| Testing     | JUnit 5 (parameterized), AssertJ, Mockito, MockMvc                |
| Build / CI  | Maven, GitHub Actions                                             |

## Pricing pipeline

```
QuoteRequest
   │
   ├─ 100 BASE_COST_MARGIN  cost + margin% (request value or default)
   ├─ 200 DISCOUNT          active DB discounts for SKU/marketplace/date (percentage first, then flat)
   ├─ 300 QUANTITY_TIER     best volume tier
   ├─ 400 FREIGHT           weight slab × zone multiplier          (pass-through)
   ├─ 500 MARKETPLACE_FEE   gross-up for commission% + closing fee (pass-through)
   ├─ 600 PRICE_FLOOR       max(price, cost + minMargin% + pass-through)
   └─ 700 GST               + gst% of the pre-tax price
   ▼
QuoteResponse { breakdown[], floorApplied, unitPrice, totalPrice }
```

## Project structure

```
dynamic-pricing-engine
├── .github/workflows/ci.yml
├── pom.xml
└── src
    ├── main
    │   ├── java/com/anantghadi/pricing
    │   │   ├── DynamicPricingEngineApplication.java
    │   │   ├── api/                 PriceController + request/response records (dto/)
    │   │   ├── batch/               BatchQuoteService (parallel quoting)
    │   │   ├── config/              PricingProperties, AppConfig (cache, executor, clock), RedisCacheConfig
    │   │   ├── discount/            Discount entity, repository, service, controller, DTOs
    │   │   ├── domain/              Marketplace, Zone, Money
    │   │   ├── engine/              PricingRule, PricingContext, PricingService, RuleOrder
    │   │   │   └── rules/           BaseCostMargin, Discount, QuantityTier, Freight,
    │   │   │                        MarketplaceFee, MinimumPriceFloor, Gst
    │   │   └── error/               GlobalExceptionHandler (ProblemDetail), exceptions
    │   └── resources
    │       ├── application.yml       pricing config: margins, tiers, freight, marketplaces, GST
    │       └── application-redis.yml Redis cache profile
    └── test/java/com/anantghadi/pricing
        ├── api/                     PricingApiIntegrationTest (MockMvc, H2)
        ├── batch/                   BatchQuoteServiceTest
        ├── engine/                  MoneyTest, PricingServiceTest
        │   └── rules/               one parameterized test class per rule
        └── support/                 TestFixtures
```

## Running locally

Prerequisites: JDK 17+ and Maven 3.9+.

```bash
# build and run all tests
mvn verify

# start the service on http://localhost:8080
mvn spring-boot:run
```

To use Redis as the quote cache:

```bash
docker run -d -p 6379:6379 redis:7
mvn spring-boot:run -Dspring-boot.run.profiles=redis   # REDIS_HOST / REDIS_PORT override the defaults
```

The H2 console is at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:pricing`, user `sa`).

## API

| Method | Path                       | Description                                         |
|--------|----------------------------|-----------------------------------------------------|
| POST   | `/api/prices/quote`        | Price one product, with the full breakdown           |
| POST   | `/api/prices/quote/batch`  | Price up to `pricing.batch.max-items` products in parallel |
| GET    | `/api/discounts`           | List discounts                                      |
| GET    | `/api/discounts/{id}`      | Get one discount                                    |
| POST   | `/api/discounts`           | Create a discount (`201` + `Location`)              |
| PUT    | `/api/discounts/{id}`      | Replace a discount                                  |
| DELETE | `/api/discounts/{id}`      | Delete a discount (`204`)                           |

### Quote request fields

| Field           | Required | Notes                                         |
|-----------------|----------|-----------------------------------------------|
| `sku`           | yes      | up to 64 chars                                |
| `baseCost`      | yes      | landed cost per unit, > 0, 2 decimals         |
| `marginPercent` | no       | defaults to `pricing.default-margin-percent`  |
| `quantity`      | yes      | 1 to 100000; drives the quantity tiers        |
| `weightKg`      | yes      | per-unit weight; drives the freight slab      |
| `zone`          | yes      | `LOCAL`, `REGIONAL`, `NATIONAL`               |
| `marketplace`   | yes      | `AMAZON`, `FLIPKART`, `OWN_STORE`             |

## curl examples

**Single quote**

```bash
curl -s -X POST localhost:8080/api/prices/quote \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1","baseCost":100,"quantity":1,"weightKg":0.5,"zone":"LOCAL","marketplace":"AMAZON"}'
```

```json
{
  "sku": "SKU-1",
  "marketplace": "AMAZON",
  "zone": "LOCAL",
  "quantity": 1,
  "currency": "INR",
  "pricingDate": "2026-11-01",
  "breakdown": [
    { "rule": "BASE_COST_MARGIN", "description": "Base cost",            "amount": 100.00, "runningUnitPrice": 100.00 },
    { "rule": "BASE_COST_MARGIN", "description": "Margin 25%",           "amount": 25.00,  "runningUnitPrice": 125.00 },
    { "rule": "FREIGHT",          "description": "Freight 0.5 kg, LOCAL", "amount": 40.00,  "runningUnitPrice": 165.00 },
    { "rule": "MARKETPLACE_FEE",  "description": "AMAZON commission 15%", "amount": 32.65,  "runningUnitPrice": 197.65 },
    { "rule": "MARKETPLACE_FEE",  "description": "AMAZON closing fee",    "amount": 20.00,  "runningUnitPrice": 217.65 },
    { "rule": "GST",              "description": "GST 18%",               "amount": 39.18,  "runningUnitPrice": 256.83 }
  ],
  "floorApplied": false,
  "unitPrice": 256.83,
  "totalPrice": 256.83
}
```

**Create a time-boxed discount** (the quote cache is cleared automatically)

```bash
curl -s -X POST localhost:8080/api/discounts \
  -H 'Content-Type: application/json' \
  -d '{"name":"Diwali Sale","type":"PERCENTAGE","value":10,"sku":"SKU-1","marketplace":"AMAZON",
       "validFrom":"2026-10-25","validTo":"2026-11-10"}'
```

**Batch quote**

```bash
curl -s -X POST localhost:8080/api/prices/quote/batch \
  -H 'Content-Type: application/json' \
  -d '{"items":[
        {"sku":"A","baseCost":100,"quantity":1,"weightKg":0.5,"zone":"LOCAL","marketplace":"AMAZON"},
        {"sku":"B","baseCost":250,"quantity":12,"weightKg":1.5,"zone":"REGIONAL","marketplace":"FLIPKART"},
        {"sku":"C","baseCost":40,"quantity":120,"weightKg":0.2,"zone":"NATIONAL","marketplace":"OWN_STORE"}
      ]}'
```

**Validation error** (`400`, `application/problem+json`)

```json
{
  "type": "about:blank",
  "title": "Invalid request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/prices/quote",
  "errors": { "baseCost": "must be greater than or equal to 0.01", "marketplace": "must not be null" }
}
```

## Configuration

Everything commercial lives in `application.yml` under `pricing.*` and is validated at startup:

```yaml
pricing:
  default-margin-percent: 25
  min-margin-percent: 8          # floor = cost + 8% + freight + marketplace fees
  gst-percent: 18
  tiers:   [{min-quantity: 10, discount-percent: 2}, {min-quantity: 50, discount-percent: 5}, ...]
  freight:
    slabs: [{max-weight-kg: 0.5, charge: 40}, {max-weight-kg: 1, charge: 65}, ...]
    extra-per-kg: 25
    zone-multipliers: {LOCAL: 1.0, REGIONAL: 1.3, NATIONAL: 1.6}
  marketplaces:
    AMAZON:    {commission-percent: 15, closing-fee: 20}
    FLIPKART:  {commission-percent: 12, closing-fee: 25}
    OWN_STORE: {commission-percent: 2,  closing-fee: 0}
  batch: {threads: 8, queue-capacity: 200, max-items: 100, timeout: 10s}
```

## Design notes

- **Open/closed pipeline.** `PricingService` takes `List<PricingRule>`, which Spring injects sorted by `@Order`. The rule positions in `RuleOrder` are spaced 100 apart, so a new rule (say, a competitor-match cap) can go between two existing ones without renumbering.
- **One context per quote.** `PricingContext` is mutable but never shared. Each quote (and each batch item) gets its own, so the parallel batch path needs no locks.
- **Rounding at every step.** Each adjustment is rounded to 2 dp before it's applied, so the breakdown always adds up exactly to the final price, which is what a seller checking an invoice expects.
- **Commission is grossed up, not added.** Marketplaces take their commission from the *selling* price, so the engine uses `selling = (net + closingFee) / (1 - commission%)`. A test checks that the seller still nets the pre-fee amount to within 1 paisa.
- **Floor guard, not discount validation.** Discounts are free to stack. The `PRICE_FLOOR` rule then guarantees the pre-tax price covers cost, the minimum margin and every pass-through charge (freight and fees), and marks the quote `floorApplied` so a pricing team can see when a promotion went too deep.
- **The cache key includes the pricing date.** `@Cacheable` keys on `(request, LocalDate)`, so a discount window that opens or closes at midnight is never hidden behind a stale entry. Any discount write clears the cache with `@CacheEvict(allEntries = true)`.
- **Bounded concurrency.** The batch path uses a fixed pool with an `ArrayBlockingQueue` and `CallerRunsPolicy`, which gives back-pressure rather than unbounded memory growth or rejected requests. It also has an overall timeout and a maximum batch size. Batch items go through the caching proxy, so repeated SKUs are cheap.
- **Redis is opt-in.** Redis auto-configuration is excluded by default and switched back on by the `redis` profile, which also stores quotes as JSON with a TTL. Tests and local runs never try to reach a Redis server.
- **Testable time.** A `Clock` bean drives "today", and the engine takes the pricing date as a parameter, so the rules are deterministic in tests.

## Author

**Anant Ghadi**, Senior Java Full Stack Developer (Java, Spring Boot, microservices, Angular/React)

- LinkedIn: [linkedin.com/in/anantghadi](https://www.linkedin.com/in/anantghadi/)
- Topmate (1:1 mentoring & consulting): [topmate.io/anantghadi](https://topmate.io/anantghadi)
- Dev.to: [dev.to/anantghadi](https://dev.to/anantghadi)

> 💼 Need a pricing engine, a Spring Boot backend or a full-stack product built? I'm available for freelance work, so get in touch on LinkedIn or Topmate.

## License

[MIT](LICENSE) © 2026 Anant Ghadi
