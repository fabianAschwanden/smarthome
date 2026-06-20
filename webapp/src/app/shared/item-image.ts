import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ItemImageService } from '../core/services/item-image.service';

/**
 * Einheitliches Bild-Widget für Items (Schalter, Storen, Klima, Anlagen).
 *
 * Zwei Varianten:
 * - 'card' (Standard): grosses 16:9-Bild mit Upload/Entfernen (Detailseiten).
 * - 'avatar': kleines, rundes Thumbnail ohne Aktionen (kompakte Dashboard-Kacheln).
 *
 * Das Bild wird vor dem Upload clientseitig auf max. 512 px verkleinert (JPEG), damit
 * die Data-URL klein bleibt (Backend-Limit beachten).
 *
 *   <app-item-image [itemId]="s.id" [label]="s.name" />
 *   <app-item-image [itemId]="s.id" [label]="s.name" variant="avatar" />
 */
@Component({
  selector: 'app-item-image',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (variant() === 'avatar') {
      <div class="item-avatar" [class.item-avatar-empty]="!url()">
        @if (url(); as src) {
          <img [src]="src" [alt]="label()" class="item-image-img" />
        } @else {
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
            <rect x="3" y="4" width="18" height="16" rx="2" />
            <circle cx="8.5" cy="9.5" r="1.6" />
            <path d="M21 16l-5-5L5 20" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        }
      </div>
    } @else {
    <div class="item-image" [class.item-image-empty]="!url()">
      @if (url(); as src) {
        <img [src]="src" [alt]="label()" class="item-image-img" />
      } @else {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
          <rect x="3" y="4" width="18" height="16" rx="2" />
          <circle cx="8.5" cy="9.5" r="1.6" />
          <path d="M21 16l-5-5L5 20" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      }

      <div class="item-image-actions">
        <label class="item-image-btn" [attr.aria-label]="'Bild für ' + label() + ' wählen'">
          <input type="file" accept="image/*" class="sr-only" (change)="onPick($event)" />
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <path d="M12 5v14M5 12h14" stroke-linecap="round" />
          </svg>
        </label>
        @if (url()) {
          <button
            type="button"
            class="item-image-btn"
            [attr.aria-label]="'Bild für ' + label() + ' entfernen'"
            (click)="onRemove()"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <path d="M5 7h14M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
        }
      </div>
    </div>
    }
  `,
})
export class ItemImage {
  private readonly images = inject(ItemImageService);

  /** Fachliche Item-ID (z. B. 'stehlampe'). */
  readonly itemId = input.required<string>();

  /** Beschriftung für Barrierefreiheit (z. B. der Item-Name). */
  readonly label = input('Item');

  /** Darstellung: grosse Karte mit Upload ('card') oder kompaktes Thumbnail ('avatar'). */
  readonly variant = input<'card' | 'avatar'>('card');

  protected readonly url = computed(() => this.images.imageOf(this.itemId())());

  protected onPick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.downscale(file).then((dataUrl) => this.images.upload(this.itemId(), dataUrl));
    input.value = '';
  }

  protected onRemove(): void {
    this.images.remove(this.itemId());
  }

  /** Skaliert das Bild auf max. 512 px Kantenlänge und kodiert als JPEG-Data-URL. */
  private downscale(file: File, max = 512, quality = 0.82): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = () => reject(reader.error);
      reader.onload = () => {
        const img = new Image();
        img.onerror = () => reject(new Error('Bild nicht lesbar'));
        img.onload = () => {
          const scale = Math.min(1, max / Math.max(img.width, img.height));
          const w = Math.round(img.width * scale);
          const h = Math.round(img.height * scale);
          const canvas = document.createElement('canvas');
          canvas.width = w;
          canvas.height = h;
          const ctx = canvas.getContext('2d');
          if (!ctx) {
            reject(new Error('Canvas nicht verfügbar'));
            return;
          }
          ctx.drawImage(img, 0, 0, w, h);
          resolve(canvas.toDataURL('image/jpeg', quality));
        };
        img.src = reader.result as string;
      };
      reader.readAsDataURL(file);
    });
  }
}
