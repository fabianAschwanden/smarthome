import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { ItemImage } from '../models/item-image';

/**
 * Mitgelieferte Standardbilder je Item (in webapp/public/devices). Werden gezeigt,
 * solange der Nutzer kein eigenes Bild hochgeladen hat.
 */
const DEFAULT_DEVICE_IMAGES: Record<string, string> = {
  klima: '/devices/klima.svg',
  stehlampe: '/devices/stehlampe.svg',
  palmenbeleuchtung: '/devices/palmenbeleuchtung.svg',
  carport: '/devices/carport.svg',
  foehn: '/devices/foehn.svg',
  homecinema: '/devices/homecinema.svg',
  'store-links': '/devices/store.svg',
  'store-mitte': '/devices/store.svg',
  whirlpool: '/devices/whirlpool.svg',
  pool: '/devices/pool.svg',
  innen: '/devices/sensor.svg',
  rauchmelder: '/devices/rauchmelder.svg',
  energy: '/devices/pv-energy.svg',
  pv: '/devices/pv-energy.svg',
  battery: '/devices/battery.svg',
};

/**
 * Verwaltet die Bilder der Items (Schalter, Storen, Klima, Anlagen) über das eigene
 * Backend. Bilder werden je Item-ID als Data-URL gehalten und nach dem ersten Zugriff
 * im Speicher gecacht; ein Signal pro Item liefert die UI reaktiv. RxJS nur an der
 * REST-Grenze.
 */
@Injectable({ providedIn: 'root' })
export class ItemImageService {
  private readonly http = inject(HttpClient);

  /** Cache: itemId -> Data-URL (null = geladen, kein Bild). undefined = noch nicht geladen. */
  private readonly cache = signal<Record<string, string | null>>({});
  private readonly requested = new Set<string>();

  /** Reaktive Data-URL eines Items (null wenn keins). Lädt bei Erstzugriff nach. */
  imageOf(itemId: string): Signal<string | null> {
    this.ensureLoaded(itemId);
    return computed(() => this.cache()[itemId] ?? DEFAULT_DEVICE_IMAGES[itemId] ?? null);
  }

  private ensureLoaded(itemId: string): void {
    if (this.requested.has(itemId)) {
      return;
    }
    this.requested.add(itemId);
    this.http
      .get<ItemImage>(`/api/items/${itemId}/image`)
      .pipe(catchError(() => of(null)))
      .subscribe((img) => this.set(itemId, img?.dataUrl ?? null));
  }

  upload(itemId: string, dataUrl: string): void {
    this.http
      .put<ItemImage>(`/api/items/${itemId}/image`, { dataUrl })
      .subscribe((img) => this.set(itemId, img.dataUrl));
  }

  remove(itemId: string): void {
    this.http.delete(`/api/items/${itemId}/image`).subscribe(() => this.set(itemId, null));
  }

  private set(itemId: string, dataUrl: string | null): void {
    this.cache.update((c) => ({ ...c, [itemId]: dataUrl }));
  }
}
