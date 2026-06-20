# smarthome

Template-Gerüst ohne Fachlichkeit. Verbindliche Konventionen: docs/blueprint.md — bei jeder
Änderung dagegen prüfen. Kurzfassung:

- Hexagonal + DDD: `domain/` ist framework-frei (reine records, Invarianten im Compact-Constructor);
  Abhängigkeitsrichtung `adapter → application → domain`. ArchUnit bricht den Build bei Verstössen.
- Use-Case-Interfaces in `domain/port/in/`, Repository-/Publisher-Interfaces in `domain/port/out/`.
- JPA-Entities nur in `adapter/out/persistence/`; Repositories nehmen/liefern Domänen-Modelle.
- Liquibase besitzt das Schema (append-only, neue Datei je Änderung); Hibernate validiert nur.
- Angular: standalone, Signals, `inject()`, OnPush, native Control Flow, strict TS, kein `any`.
  Frontend spiegelt REST-DTOs, nie das Domänenmodell.
- Kein `null` als Rückgabe (`Optional`), Konstruktor-Injection, keine technischen Suffixe.

Befehle: `./mvnw quarkus:dev` (Entwicklung) · `./mvnw verify` (Gate) · `cd webapp && npm test`
