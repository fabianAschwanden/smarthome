# Spec – Item-Bilder

Status: v1.0 (umgesetzt) · Datum: 2026-06-20 · Plattform: Java 25 / Quarkus (Hexagonal + DDD)

## 1. Zweck & Scope

Pro Item (Schalter, Store, Klima, Wellness-Anlage …) ein **Bild** hinterlegen, das
auf den Detailseiten und als Thumbnail auf dem Dashboard erscheint. Serverseitig
gespeichert → auf allen Clients sichtbar. Die `itemId` ist die fachliche Geräte-ID
(z. B. `stehlampe`), typ-übergreifend.

## 2. Modell & Speicherung

Bild als **Data-URL** (`data:image/…;base64,…`). Domänen-Record `ItemImage`
validiert im Compact-Constructor: nicht leer, `data:image/`-Präfix, Längenlimit
(`MAX_DATA_URL_LENGTH ≈ 3 MB`). Persistiert in PostgreSQL (Liquibase
`0003-create-item-image.xml`, Tabelle `item_image`, PK = `item_id`, `data_url` als
`text`). Das Frontend verkleinert Bilder vor dem Upload clientseitig auf max. 512 px
(JPEG), damit die Data-URL klein bleibt.

## 3. API (REST)

| Methode | Pfad                       | Body / Antwort                          |
|---------|----------------------------|-----------------------------------------|
| GET     | `/api/items/{id}/image`    | `{ itemId, dataUrl, updatedAt }` · 404 wenn keins |
| PUT     | `/api/items/{id}/image`    | `{ "dataUrl": "data:image/…" }` → gespeichertes Bild · 400 ungültig |
| DELETE  | `/api/items/{id}/image`    | 204                                     |

Validierungsfehler (kein Bild-Data-URL, zu gross) → 400 über den vorhandenen
`IllegalArgumentException`-Mapper.

## 4. Frontend

`ItemImageService` cacht je Item-ID als Signal; geteiltes Widget `app-item-image`
(Variante `card` mit Upload/Entfernen auf Detailseiten, `avatar` als Dashboard-
Thumbnail). Solange kein eigenes Bild hochgeladen ist, zeigt das Widget ein
mitgeliefertes Standard-Icon (`webapp/public/devices/*.svg`).

## 5. Architektur-Einordnung (Hexagonal)

Slice `itemimage`: Domäne `ItemImage`; Port `ManageItemImages` (in) +
`ItemImageNotFound`, `ItemImageRepository` (out); `ItemImageService` (application);
Adapter `adapter/in/rest/itemimage` + `adapter/out/persistence`
(`PanacheItemImageRepository`, `ItemImageEntity`).

## 6. Offene Punkte / TODO

- [ ] Optional: serverseitige Bildverkleinerung/-validierung (derzeit clientseitig).
