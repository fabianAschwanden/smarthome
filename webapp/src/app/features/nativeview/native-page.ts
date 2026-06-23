import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { NativeViewService } from '../../core/services/native-view.service';
import { NativeView } from '../../core/models/native-view';

/**
 * Use Case 13: Native Geräte-Weboberflächen. Kacheln je Gerät; die gewählte wird im
 * iframe über den Backend-Reverse-Proxy ({@code /native/<id>/}) geladen – gleiche Origin,
 * daher auch remote (Fly-Tunnel) erreichbar. Die Geräte-IP bleibt serverseitig.
 */
@Component({
  selector: 'app-native-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="space-y-5">
      <h2 class="text-2xl font-semibold">Native</h2>

      @if (views(); as list) {
        @if (list.length === 0) {
          <p class="text-[color:var(--ink-soft)]">Keine nativen Oberflächen konfiguriert.</p>
        } @else {
          <!-- Kachel-Auswahl -->
          <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            @for (v of list; track v.id) {
              <button
                type="button"
                class="tile-toggle"
                [class.tile-toggle-active]="v.id === active()?.id"
                (click)="select(v)"
              >
                <svg
                  class="size-6"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.7"
                >
                  @switch (v.icon) {
                    @case ('bolt') {
                      <path d="M13 2 3 14h7l-1 8 10-12h-7z" stroke-linejoin="round" />
                    }
                    @default {
                      <rect x="3" y="4" width="18" height="14" rx="2" />
                      <path d="M3 9h18" stroke-linecap="round" />
                    }
                  }
                </svg>
                <span class="text-xs">{{ v.name }}</span>
              </button>
            }
          </div>

          <!-- Viewer: die gewählte Oberfläche im iframe -->
          @if (active(); as v) {
            <article class="glass-card overflow-hidden">
              <header class="flex items-center justify-between gap-3 px-4 py-2.5">
                <h3 class="text-sm font-medium">{{ v.name }}</h3>
                <a
                  [href]="v.path"
                  target="_blank"
                  rel="noopener"
                  class="text-xs text-[color:var(--ink-soft)] hover:text-[color:var(--ink)]"
                  >Im neuen Tab öffnen ↗</a
                >
              </header>
              <div class="h-[70vh] w-full bg-white">
                <iframe class="size-full border-0" [src]="frameUrl(v)" [title]="v.name"></iframe>
              </div>
            </article>
          }
        }
      } @else {
        <p class="text-[color:var(--ink-soft)]">Lade …</p>
      }
    </section>
  `,
})
export class NativePage {
  private readonly api = inject(NativeViewService);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly views = this.api.views;

  private readonly selected = signal<NativeView | null>(null);
  /** Gewählte View; per Default die erste. */
  protected readonly active = computed(() => this.selected() ?? this.views()?.[0] ?? null);

  protected select(v: NativeView): void {
    this.selected.set(v);
  }

  protected frameUrl(v: NativeView): SafeResourceUrl {
    return this.sanitizer.bypassSecurityTrustResourceUrl(v.path);
  }
}
