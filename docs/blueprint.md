# Blueprint — Technologie-Stack & Architektur-Prinzipien

**Verbindliche Vorgabe für alle Apps aus diesem Template.** Beschreibt *wie* gebaut wird (Stack, Schichten, Regeln, Konventionen), nicht *was* fachlich gebaut wird. Versionen sind ein Snapshot (Stand 2026-06); beim Aufsetzen auf die jeweils aktuelle LTS/stabile Version heben und hier dokumentieren.

## 1. Deployment-Form: ein Backend-for-Frontend (BFF), ein Deployable

Eine einzelne Maven-Modul-Einheit enthält Backend und Frontend. Das Quarkus-Backend serviert die SPA und dient ihr als Backend-for-Frontend: die SPA spricht ausschliesslich mit dem eigenen Backend, nie direkt mit Drittsystemen oder dem Identity-Provider.

- Ein Build-Artefakt (`quarkus-run.jar` bzw. Container-Image / GraalVM-Native).
- Frontend-Integration über **Quinoa**: Dev-Modus proxyt Quarkus (:8080) auf den Angular-Dev-Server (:4200); Production-Build packt das kompilierte Frontend als statische Ressource mit.
- Vorteile: keine separate Frontend-Pipeline, kein CORS, Session-Cookies statt Tokens im Browser (§8).

## 2. Technologie-Stack

### Backend

| Bereich | Wahl | Version |
|---|---|---|
| Sprache | Java | 25 (`maven.compiler.release`) |
| Framework | Quarkus (`quarkus-bom`) | 3.35.x |
| REST | `quarkus-rest` + `quarkus-rest-jackson` (JAX-RS, reaktiv) | — |
| Persistenz-ORM | Hibernate ORM mit Panache | — |
| Datenbank | PostgreSQL (`quarkus-jdbc-postgresql`) | — |
| Schema-Migration | Liquibase (`quarkus-liquibase`) | — |
| Validierung | Hibernate Validator | — |
| Messaging | Kafka über SmallRye Reactive Messaging | — |
| Auth | OIDC (`quarkus-oidc`) gegen Keycloak | — |
| Object-Storage | S3-kompatibel (`quarkus-amazon-s3`), Dev über LocalStack | — |
| Scheduling | `quarkus-scheduler` | — |
| API-Doku | `quarkus-smallrye-openapi` (+ Swagger-UI) | — |
| Health | `quarkus-smallrye-health` | — |
| DI | CDI / ArC (`quarkus-arc`) | — |

### Frontend

| Bereich | Wahl | Version |
|---|---|---|
| Framework | Angular (standalone, Signals) | 22.x |
| Sprache | TypeScript (strict) | 6.0.x |
| Styling | TailwindCSS (+ `@tailwindcss/postcss`) | 4.x |
| Reaktivität | RxJS (nur an REST-/Stream-Grenzen) | 7.8.x |
| Build | `@angular/build` (esbuild-basiert) | 22.x |
| Linting | ESLint (`angular-eslint` + `typescript-eslint`) — erzwingt die Frontend-Konventionen (§5) | — |
| Formatierung | Prettier (print width 100, single quotes) + EditorConfig | — |

Plain SPA, keine PWA.

### Test & Qualität

| Ebene | Werkzeug |
|---|---|
| Backend Unit | JUnit 5 + Mockito |
| Backend REST/Integration | `@QuarkusTest` + REST-assured, gegen Dev Services |
| Backend BDD | Cucumber (`quarkus-cucumber`) — Java-Runner |
| Architektur-Invarianten | ArchUnit |
| Frontend Unit/Component | Vitest |
| Frontend E2E / BDD | Playwright (+ Cucumber.js) |
| Coverage-Gate | JaCoCo (`jacoco:check`) |
| Auth-Tests | `quarkus-test-security` (Identitäten stubben), `quarkus-test-keycloak-server` (Real-OIDC-Smoke-Test) |

### Build, Lieferung, Betrieb

| Bereich | Wahl |
|---|---|
| Build | Maven (mvnw) + npm (über Quinoa) |
| Container | Multi-Stage `Dockerfile.jvm` (`eclipse-temurin:25`); `quarkus-container-image-docker` |
| CI | GitHub Actions (ubuntu-latest) |
| Release | Tag (`v*`) → GitHub Actions baut App- + Sidecar-Image und pusht nach ghcr.io |
| Deployment | Heim-Server (Linux-Host im LAN) via docker-compose, zieht die ghcr-Images; Remote-Zugang über Fly-Login-Proxy + WireGuard |
| Dependency-Updates | Dependabot |

## 3. Architektur: Hexagonal (Ports & Adapters) + DDD Tactical Design

Hexagonal definiert, *wo* Code lebt; DDD Tactical Design definiert, *welche Form* der Code im Inneren hat. KISS: Pakete/Bausteine erst anlegen, wenn ein echter Use Case sie braucht.

### 3.1 Backend-Paketstruktur

```
<base-package>/
├── domain/                    # Inneres Hexagon — reine Geschäftslogik, KEINE Framework-Imports
│   ├── model/                 # Aggregates, Entities, Value Objects
│   ├── event/                 # Domain Events            (anlegen wenn gebraucht)
│   ├── service/               # Domain Services          (anlegen wenn gebraucht)
│   ├── factory/               # Factories                (anlegen wenn gebraucht)
│   └── port/
│       ├── in/                # Driving Ports  — Use-Case-Interfaces
│       └── out/               # Driven Ports   — Repository-/Publisher-Interfaces
├── application/
│   └── service/               # Application Services — orchestrieren Use Cases, Transaktionsgrenze
└── adapter/
    ├── in/
    │   └── rest/              # Driving Adapter (JAX-RS Resources)
    │       └── dto/           # Transport-Objekte der REST-Schicht
    └── out/
        └── persistence/       # Driven Adapter (JPA-Entities, Panache-Repositories)
```

### 3.2 Abhängigkeitsregel (unverhandelbar)

`adapter → application → domain`; Adapter implementieren `domain/port/out`. `domain/` hat null Framework-Abhängigkeiten (kein Quarkus, kein JPA, kein Jackson); Domänen-Modelle sind reine Java-`records`. Use-Case-Interfaces in `port/in/`, Repository-Interfaces in `port/out/`. Adapter hängen an der Domäne, nie umgekehrt.

### 3.3 DDD-Bausteine

| Baustein | Lebt in | Regel |
|---|---|---|
| Aggregate Root | `domain/model/` | Konsistenz-/Transaktionsgrenze; einziger Einstiegspunkt ins Aggregat |
| Entity | `domain/model/` | Stabile Identität; Gleichheit per ID |
| Value Object | `domain/model/` | Immutable `record`; Invarianten im Compact-Constructor (fail fast) |
| Domain Event | `domain/event/` | Immutable `record`, Vergangenheitsform; publiziert über Driven Port |
| Domain Service | `domain/service/` | Zustandslose Domänenlogik über Aggregate hinweg; pur |
| Repository | Port `domain/port/out/`, Impl `adapter/out/persistence/` | Pro Aggregate Root; nimmt/liefert Domänen-Modelle, nie JPA-Entities |
| Factory | `domain/factory/` | Komplexe Aggregat-Erzeugung |
| Application Service | `application/service/` | Orchestriert Use Case, hält Transaktionsgrenze, keine Geschäftsregeln |

Regeln: Aggregate per ID referenzieren, nie per Objektreferenz · ganze Aggregate laden/speichern · Invarianten im Aggregat erzwingen · Application ≠ Domain Service strikt trennen · Domänen-Modelle immutable («Mutation» liefert neue Instanz).

### 3.4 Architektur-Invarianten erzwingen (ArchUnit)

Ein ArchUnit-Test bricht den Build bei Schichtverletzungen (Framework-Import in `domain/`, `application/` greift auf Adapter zu, Persistence-Adapter referenziert anderen Adapter, REST-DTOs ausserhalb `adapter/in/rest/dto/`). Architektur, die nicht getestet wird, erodiert.

## 4. Backend-Konventionen (Java)

`records` für Domänen-Modelle, Events und DTOs · Value-Object-Invarianten im Compact-Constructor · JPA-Entities nur in `adapter/out/persistence/` (öffentliche Felder OK) · Panache-Repository-Pattern hinter `port/out/` · `@ApplicationScoped` · Konstruktor-Injection · kein `null` als Rückgabe (`Optional`) · früh am Systemrand validieren, internem Code vertrauen.

## 5. Frontend-Konventionen (Angular)

Frontend spiegelt die REST-DTOs (publizierte Sprache des Backends), nicht das Domänen-Modell — keine Invarianten, keine Aggregat-Regeln.

```
webapp/src/app/
├── core/
│   ├── models/      # Interfaces, spiegeln die REST-DTOs
│   └── services/    # Use-Case-Logik, REST-Zugriff
├── features/        # Driving Adapter — UI-Komponenten je Route
└── shared/          # Wiederverwendbare UI-Bausteine (anlegen wenn gebraucht)
```

Standalone Components (nie NgModules; `standalone: true` nicht setzen) · Signals (`signal()`, `computed()`, `effect()`) · `inject()` statt Konstruktor-Injection · `input()`/`output()` statt Decorators · `OnPush` überall · Native Control Flow `@if`/`@for`/`@switch` · `providedIn: 'root'` · strict TS, kein `any` · feature-basierte Ordner, `app-*`-Präfix.

## 6. Persistenz-Prinzipien

- **Liquibase besitzt das Schema** (`migrate-at-start=true`); Hibernate läuft im `validate`-Modus.
- Migrationen append-only & unveränderlich; jede Änderung = neue Migration + Change-Log-Eintrag.
- **Dev Services** starten PostgreSQL, Kafka, Keycloak, LocalStack automatisch (Container-Runtime nötig).
- **JSONB-Snapshots** für eingebettete, versionierte Wertobjekte; Persistenz-Modell = Domänen-record. Feldänderungen erfordern schema-bewusste Daten-Migration.
- DB-Spalten: `snake_case`.

## 7. Messaging

Kafka als Event-Backbone (SmallRye Reactive Messaging). Domain Events nach dem Persistieren über Driven Port publizieren (Application Service = Transaktionsgrenze). `%test`-Profil: Outgoing-Channels auf In-Memory-Connector. Transaktionale Observer (`@Observes(AFTER_SUCCESS)`) mit `@Transactional(REQUIRES_NEW)`, ggf. Worker-Thread.

## 8. Authentifizierung & Autorisierung (OIDC BFF-Pattern)

- **BFF-Session-Cookie statt Token im Browser**: `application-type=web-app`, PKCE, SameSite, Session-Verlängerung.
- **Rollen-Mapping am OIDC-Boundary**: `SecurityIdentityAugmentor` mappt IdP-Rollennamen auf interne, stabile Rollen; `@RolesAllowed`, Frontend und Tests nutzen nur interne Namen.
- IdP-Rollen als Client-Rollen mit App-Präfix (`<app>-<rolle>`), aus `resource_access/<client>/roles`.
- Auth in Dev/Test aus (Ersatz-Identität), in Prod an — über Profile (§9).
- **Row-Level-Security serverseitig** aus der authentifizierten Identität, nie aus dem Request-Body.
- Security-Header in Prod (CSP, Referrer-Policy, Permissions-Policy).

## 9. Konfiguration & Profile

| Profil | Zweck |
|---|---|
| `%dev` | Frontend-Dev-Server, Dev Services, Auth aus |
| `%test` | zufälliger HTTP-Port, kein Frontend-Dev-Server, Auth aus, Messaging In-Memory |
| `%prod` | Auth an, explizite DB-URL, Security-Header |
| `%<idp>` (optional) | OIDC gegen echten IdP, additiv kombinierbar (`-Dquarkus.profile=dev,<idp>`) |

## 10. Test-Strategie

Unit (Domäne + Application Services, Ports gemockt, kein Container) · `@QuarkusTest` gegen Dev Services · BDD mit Tag-Routing (eine `.feature`, Java-Cucumber vs. Playwright/Cucumber.js; `@Pending` statt still überspringen) · Verhalten testen, nicht Implementierung · ArchUnit- und Konsistenz-Tests in der Suite · geteilte Test-DB: keine positions-/zählungsabhängigen Assertions auf Fremddaten, dedizierte Fixtures, Zeitbezüge relativ · Real-OIDC-Smoke-Test gegen echten Keycloak.

## 11. CI/CD & Betrieb

CI (build + verify) auf jeden PR, verify als Merge-Gate · Release: Tag `v*` → GitHub Actions (`release.yml`) baut App- + Sidecar-Image und pusht versioniert nach ghcr.io · Deployment: Heim-Server zieht die Images per docker-compose (`docker-compose.release.yml` / `scripts/server-update.sh`), Datenbank lokal als Postgres-Container · Remote-Zugang über Fly-Login-Proxy (`deploy/fly-remote/`, manuell deployt) + WireGuard · Dependabot hebt Maven/npm/Actions-Versionen; Framework-Majors (Angular, TypeScript) werden manuell via `ng update` gehoben · CI-Diagnose: erst klären ob Failure PR-eigen oder flaky, statt blind re-runnen.

## 12. Clean Code & Naming

Single Responsibility · Dependency Inversion · KISS/YAGNI · DRY erst ab 3+ Vorkommen · Fail Fast an Systemgrenzen · Klassen = was sie sind, Methoden = was sie tun · keine technischen Suffixe (`*Aggregate`, `*VO`) · nur öffentliche APIs dokumentieren (Warum, nicht Was) · kein toter Code, keine TODOs ohne Issue, keine Magic Numbers, frühe Returns.

## 13. Checkliste neue App

1. Quarkus-Projekt (`quarkus-bom`), Java-Release, Maven-Wrapper
2. Paketskelett `domain`/`application`/`adapter` — Domäne framework-frei
3. ArchUnit-Test **vor** Fachcode
4. PostgreSQL + Liquibase + Hibernate `validate`; Dev Services
5. REST-Adapter + DTO-Paket; OpenAPI/Health
6. Angular-SPA via Quinoa; Frontend-Konventionen als Lint-/Review-Baseline
7. OIDC BFF + Rollen-Mapping-Augmentor; Auth Dev/Test aus, Prod an
8. Profile `%dev`/`%test`/`%prod` (+ IdP)
9. Test-Schichten: Unit, `@QuarkusTest`, BDD Tag-Routing, Vitest, Coverage-Gate
10. CI, Release-Pipeline (release.yml → ghcr.io-Images), Heim-Server-Deployment (docker-compose), Fly-Login-Proxy für Remote, Dependabot
