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
| Framework | Quarkus (`quarkus-bom`) | 3.36.x |
| REST | `quarkus-rest` + `quarkus-rest-jackson` (JAX-RS, reaktiv) | — |
| Persistenz-ORM | Hibernate ORM mit Panache | — |
| Datenbank | PostgreSQL (`quarkus-jdbc-postgresql`) | — |
| Schema-Migration | Liquibase (`quarkus-liquibase`) | — |
| Validierung | Hibernate Validator | — |
| Messaging | Kafka über SmallRye Reactive Messaging | *Template-Option, in dieser App nicht genutzt* |
| Auth | OIDC (`quarkus-oidc`) gegen einen OIDC-IdP | — |
| Object-Storage | S3-kompatibel (`quarkus-amazon-s3`), Dev über LocalStack | *Template-Option, in dieser App nicht genutzt* |
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
| Backend Unit | JUnit 5 (alle Backend-Tests als `@QuarkusTest`, siehe Hinweis); `quarkus-junit5-mockito` verfügbar, aktuell ungenutzt |
| Backend REST/Integration | `@QuarkusTest` + REST-assured, gegen Dev Services |
| Backend BDD | Cucumber (`quarkus-cucumber`) — *Template-Option, in dieser App nicht eingerichtet* |
| Architektur-Invarianten | ArchUnit (9 Regeln, siehe §3.4) |
| Frontend Unit/Component | Vitest |
| Frontend E2E | Playwright (`webapp/e2e/`); Cucumber.js *Template-Option, nicht eingerichtet* |
| Coverage-Gate | JaCoCo (`jacoco:check`), min. **0.70** Line-Coverage |
| Auth-Tests | `quarkus-test-security` (Identitäten stubben); `quarkus-test-keycloak-server` *Template-Option, nicht eingerichtet* |

> **Coverage zählt nur aus `@QuarkusTest`-Läufen.** Das Gate misst `jacoco-quarkus.exec`
> (quarkus-jacoco) – reine JUnit-Tests OHNE `@QuarkusTest` werden NICHT erfasst, selbst
> wenn sie Logik gründlich testen. Darum tragen ALLE Backend-Tests `@QuarkusTest`
> (Ausnahme: `HexagonalArchitectureTest`, ArchUnit braucht keinen Container).
> Geräte-Adapter mit echtem I/O werden direkt instanziiert und gegen einen lokalen
> Fake-Server getestet (`com.sun.net.httpserver.HttpServer` für HTTP, kein echtes Gerät) –
> Muster: `FroniusEnergySourceTest`. So zählt ihr Parsing/Mapping zur Coverage, ohne die
> `@IfBuildProperty(real-devices)`-Aktivierung (Build-Zeit, per Test-Profil nicht umschaltbar)
> zu benötigen.

### Build, Lieferung, Betrieb

| Bereich | Wahl |
|---|---|
| Build | Maven (mvnw) + npm (über Quinoa) |
| Container | Multi-Stage `deploy/Dockerfile` (`eclipse-temurin:25`, baut Backend + Frontend); Sidecar `tools/tuya-sidecar/Dockerfile` |
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
    │   ├── rest/              # Driving Adapter (JAX-RS Resources)
    │   │   └── dto/           # Transport-Objekte der REST-Schicht
    │   ├── gateway/           # weitere Driving Adapter (z. B. Vert.x-Reverse-Proxy)
    │   └── security/          # OIDC-Rollen-Mapping am Auth-Boundary
    └── out/
        ├── persistence/       # Driven Adapter (JPA-Entities, Panache-Repositories)
        └── <device>/          # Geräte-Adapter: mock / local / pending je Slice

support/                       # geteilte, framework-nahe Helfer (z. B. Tuya-LAN-Protokoll);
                               # nur von Adaptern genutzt, nie von domain/application
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

Ein ArchUnit-Test (`HexagonalArchitectureTest`, 9 Regeln) bricht den Build bei
Architektur-Verletzungen. Geprüft werden: Framework-Import in `domain/`; die Schichtenregel
`adapter → application → domain` (inkl. `support` nur durch Adapter nutzbar); Adapter
referenzieren einander nicht; REST-DTOs nur in `adapter/in/rest/dto/`; JPA-Entities nur im
Persistence-Adapter; `domain/model` enthält nur `records`/`enums`; Ports sind Interfaces (ausser
Fach-Exceptions); ausschliesslich Konstruktor-Injection (kein Feld-`@Inject`); Application
Services sind `@ApplicationScoped`. Architektur, die nicht getestet wird, erodiert.

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
- **Dev Services** starten PostgreSQL automatisch (Container-Runtime nötig). Kafka/Keycloak/LocalStack
  sind Template-Optionen und in dieser App nicht aktiv (Keycloak-DevServices explizit aus).
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

> **Stand in dieser App:** `IdpRoleMappingAugmentor` ist vorhanden; die Autorisierung ist
> path-basiert (`%prod`/`%fly`: alles unter `/*` erfordert Authentifizierung, Health offen).
> Rollenbasierte `@RolesAllowed`-Endpunkte und Row-Level-Security sind als Template-Muster
> beschrieben, aber (Single-User-Heim-App) noch nicht ausprogrammiert. Der Remote-Zugang
> erzwingt den Login zusätzlich am Fly-Proxy (oauth2-proxy, siehe `docs/remote/SETUP.md`).

## 9. Konfiguration & Profile

| Profil | Zweck |
|---|---|
| `%dev` | Frontend-Dev-Server, Dev Services, Auth aus, Mock-Geräte (Default) |
| `%test` | zufälliger HTTP-Port, kein Frontend-Dev-Server, Auth aus, Mock-Geräte |
| `%live` | additiv zu `%dev`: echte Geräte im LAN (`-Dquarkus.profile=dev,live`), Auth aus |
| `%lan` | Heimbetrieb auf dem Linux-Server (docker-compose): echte Geräte, Auth aus, DB per Env |
| `%prod` / `%fly` | Cloud/Produktion: Auth an (OIDC), explizite DB-URL, Security-Header |

Geräte real vs. Mock steuert die Build-Property `smarthome.real-devices` (siehe §10 zu
`@IfBuildProperty`). App-spezifisch; das generische Template kennt nur `%dev/%test/%prod/%<idp>`.

## 10. Test-Strategie

Domäne + Application Services mit handgeschriebenen Fake-Ports (kein Mockito nötig) · REST über `@QuarkusTest` + REST-assured gegen Dev Services · **alle Backend-Tests tragen `@QuarkusTest`**, da nur diese ins Coverage-Gate zählen (siehe Hinweis in §2 Test & Qualität) · Geräte-Adapter mit echtem I/O werden direkt instanziiert und gegen lokale Fake-Server getestet (kein echtes Gerät) · Verhalten testen, nicht Implementierung · ArchUnit-Regeln in der Suite · Zeitbezüge über `Clock` injizierbar/relativ · BDD (Cucumber) und Real-OIDC-Smoke-Test sind Template-Optionen, hier nicht eingerichtet.

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
