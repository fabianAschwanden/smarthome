---
name: testing-strategy
description: Design test strategies and test plans. Trigger with "how should we test", "test strategy for", "write tests for", "test plan", "what tests do we need", or when the user needs help with testing approaches, coverage, or test architecture.
---

# Testing Strategy

Design effective testing strategies balancing coverage, speed, and maintenance.

## Testing Pyramid

```
        /  E2E  \         Few, slow, high confidence
       / Integration \     Some, medium speed
      /    Unit Tests  \   Many, fast, focused
```

## Strategy by Component Type

- **API endpoints**: Unit tests for business logic, integration tests for HTTP layer, contract tests for consumers
- **Data pipelines**: Input validation, transformation correctness, idempotency tests
- **Frontend**: Component tests, interaction tests, visual regression, accessibility
- **Infrastructure**: Smoke tests, chaos engineering, load tests

## What to Cover

Focus on: business-critical paths, error handling, edge cases, security boundaries, data integrity.

Skip: trivial getters/setters, framework code, one-off scripts.

## Testdaten-Konventionen

Testdaten müssen synthetisch und eindeutig als solche erkennbar sein — damit PII-Scanner (z.B. gitleaks, eigener sourcecode-scanner) keine false positives erzeugen und keine echten Personendaten im Repo landen:

- **E-Mail**: immer `@example.com`, `@example.org` oder `@test.invalid` (RFC 2606) — nie `@gmail.com` o.ä.
- **Namen**: generisch (`user1`, `player-a`) statt echter Vornamen
- **IDs / Kundennummern**: Präfix `TEST-` oder `DUMMY-` wenn das Format produktionsähnlich ist
- **Passwörter / Tokens in Fixtures**: eindeutig fake (`not-a-real-token`, `scrypt:test:hash`)
- **Systeminterne Adressen**: `.internal`-TLD oder klar synthetisches Muster

## Output

Produce a test plan with: what to test, test type for each area, coverage targets, and example test cases. Identify gaps in existing coverage.
