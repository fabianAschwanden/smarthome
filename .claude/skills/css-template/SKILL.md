---
name: css-template
description: CSS Corporate-Template für PowerPoint — Farben, Schriften, Layouts und Konventionen aus dem firmeneigenen Foliensatz. Verwende dies bei jeder neuen oder zu erweiternden Präsentation für die CSS (Krankenversicherung) bzw. wann immer der Nutzer auf "das Template", "Firmen-Template" oder "CSS-Template" verweist.
---

# CSS PowerPoint Template

Verbindliche Designvorgaben aus dem Corporate-Template der CSS. Anzuwenden bei jeder Folie, die im Unternehmenskontext entsteht.

## When to use

- Nutzer arbeitet an einer Präsentation für die CSS (z. B. interne Reviews, Architektur-Decks, Business Cases)
- Nutzer sagt sinngemäss "im Firmen-Template", "wie unser Template", "CSS-Stil", "Corporate Design"
- Eine bestehende CSS-Präsentation soll erweitert oder restyled werden
- Beim Aufsetzen neuer Decks, bevor Inhalte erstellt werden

Ist die Datei `Template.pptx` in den Uploads vorhanden, **immer** über `insertSlidesFromBase64` mit `sourceSlideIds` arbeiten — niemals den Master einer fremden Präsentation manuell überschreiben.

## Farbpalette (Theme — verbindlich)

Hex-Werte verbatim verwenden, nicht approximieren:

| Rolle | Hex | Verwendung |
|---|---|---|
| dk1 | `5A5A5A` | Primärtext (warmes Anthrazit, **kein Schwarz**) |
| lt1 | `FFFFFF` | Hintergrund Standardfolien |
| dk2 | `DCDCDC` | Helle Trennlinien, dezente Flächen |
| lt2 | `00A4E0` | CSS Hellblau (Sekundär) |
| accent1 | `00327D` | **CSS Dunkelblau** — Hauptakzent, Headlines, Highlights |
| accent2 | `00A4E0` | CSS Hellblau — Sekundärakzent, Hyperlinks, Hover |
| accent3 | `9B50C8` | Violett — Kategorisierung |
| accent4 | `009467` | Grün — positive Werte, "go" |
| accent5 | `323232` | Tiefes Grau — Datentabellen, Achsen |
| accent6 | `FF548C` | Pink/Magenta — sparsam für Highlights |
| hlink | `092768` | Hyperlink (sehr dunkles Blau) |
| folHlink | `00A4E0` | Besuchter Link |

**Faustregel Charts:** primäre Serie = `accent1` (Dunkelblau), zweite Serie = `accent2` (Hellblau), darüber hinaus `accent4` → `accent3` → `accent6` → `accent5`.

## Typografie

- **Schrift global:** Arial (sowohl `majorFont` als auch `minorFont`). Keine Substitution mit Calibri, Helvetica o. ä.
- **Niemals `<a:latin>` per Run setzen** — die Theme-Schrift greift automatisch.
- Mindestgröße Body 14pt, Titel ≥ 24pt (Standardtitel), Headline-/Kapitelfolien grösser entsprechend Layout.

## Layout-Bibliothek

Das Template enthält **47 Layouts in zwei Mastersätzen**. Layout immer nach **Name** auswählen, nie nach Position. Wichtigste Layouts:

### Strukturfolien
- **`Titelfolie`** — Standard-Titelfolie ohne Bild (Autor, Datum, Titel, Klassifizierung)
- **`Titelfolie mit Bild`** — Titelfolie mit Hero-Bild
- **`Agenda`** — Inhaltsübersicht
- **`Kapiteleinstieg`** / **`Kapiteleinstieg 2`** — Sektionstrennung
- **`Kapitel 1`** … **`Kapitel 8`** — vor-eingefärbte Kapitel-Trenner
- **`Schlussfolie`** — Standard-Schlussfolie
- **`1_Schlussfolie Kampagne Adresse`** / **`Claim`** — Marketing-Abschluss
- **`Backup`** — Anhang-Trenner

### Inhaltsfolien
- **`Titel und Inhalt`** — Standardlayout, primärer Default für Fließtext/Bullets
- **`Nur Titel`** — Titel + freie Fläche (für Diagramme, Custom-Shapes, Element-Library)
- **`Leer`** — komplett leer (nur Vollbild-Visuals)
- **`Bild mit Textbox`** — Bild + Caption-Block

### Spalten-Layouts (für Vergleiche, Listen, Side-by-Side)
- **`2 Spalten`**, **`3 Spalten`**, **`4 Spalten`** — gleichmäßig
- **`2:1 Spalten`** — links breit, rechts schmal
- **`1:2 Spalten`** — links schmal, rechts breit

### Highlight-Folien
- **`Highlight Dunkelblau`** — Vollflächig `accent1` (Dunkelblau) als Statement-Folie, weisser Text
- **`Highlight Verlauf`** — Farbverlauf für Schlüsselbotschaften

**Auswahlregel:** Pro Inhaltstyp das spezifischste passende Layout nehmen. Niemals "Leer" für Textfolien — dort fehlen die Master-Schriftgrößen.

## Folienkonventionen (aus Beispielfolien extrahiert)

### Footer-Format
Jede Inhaltsfolie hat eine Fußzeile im Schema:
```
TT.MM.YYYY ∙ Autor ∙ Präsentationstitel ∙ intern
```
Trennzeichen ist **Bullet Operator `∙`** (U+2219), nicht `·` oder `•`. Klassifizierung am Ende: meist `intern`, bei Bedarf `vertraulich` oder `öffentlich`.

### Seitenzahl
Rechts unten als nackte Zahl (kein "Seite", kein "/").

### Titelfolie-Inhalte
- Ort und Datum (gleicher Block, z. B. `Luzern, 15.05.2025`)
- Autor
- Titel der Präsentation (Haupt-Headline)
- Klassifizierung (Intern / Vertraulich / Öffentlich)

## Vorgehen beim Aufsetzen einer neuen Präsentation

1. **Template-Folien einfügen, nicht Master kopieren.** Mit `insertSlidesFromBase64` und `sourceSlideIds` exakt die benötigten Layouts importieren — Master, Theme und Layouts kommen automatisch mit.
2. Default-Folien des leeren Decks **nach** dem Import löschen (sonst Konflikte).
3. Footer-Felder (Datum, Autor, Titel) auf jeder Folie aktualisieren — sie sind Platzhalter, kein Master-Header.
4. Inhalte einfügen, **ohne Master-Stile zu überschreiben.** Keine harten Hex-Farben auf Text setzen, die Theme-Farben (`<a:schemeClr val="accent1"/>` etc.) reichen.
5. Eigene Shapes einfärben mit Theme-Farben (siehe Tabelle oben), **nicht** mit anderen Blautönen.

**Done when:** Folien zeigen Arial, weisser Hintergrund, Footer im Format `TT.MM.YYYY ∙ Autor ∙ Titel ∙ intern`, Seitenzahl rechts unten, Akzente im CSS-Blau `00327D` / `00A4E0`.

## Diagramme und Visualisierungen

- Charts über `edit_slide_chart` erstellen, nie aus Shapes nachbauen.
- `<c:style val="2"/>` verwenden, damit Theme-Akzentfarben in Reihenfolge greifen.
- Kategorie-Achse: `majorTickMark="none"`. Wertachse: `majorTickMark="out"`.
- Datenlabels (`<c:dLbls>`) standardmäßig an mit `<c:showVal val="1"/>`.

## Was zu vermeiden ist

- ❌ **Kein reines Schwarz** (`000000`) für Text — immer `dk1` = `5A5A5A`.
- ❌ **Kein Calibri** — Theme ist Arial.
- ❌ **Keine Emojis** als Icons — vector icons via `search_icons` / `insert_icon`, eingefärbt in `accent1`.
- ❌ **Kein eigenes Blau** — wenn blau, dann exakt `00327D` oder `00A4E0`.
- ❌ **Kein neuer Master/Theme** — vorhandenes Template ist verbindlich.
- ❌ Layout `Leer` nicht als Default für Textfolien.
