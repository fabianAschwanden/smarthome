import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { SearchService } from '../core/search/search.service';
import { SearchHit, SearchKind } from '../core/search/search';

const KIND_BADGE: Record<SearchKind, string> = {
  switch: 'Schalter',
  cover: 'Store',
  climate: 'Klima',
  battery: 'Batterie',
  sensor: 'Sensor',
  smoke: 'Rauchmelder',
  page: 'Seite',
};

/**
 * Globale Suche der Topbar: Geräte und Seiten fuzzy finden, Befehle direkt ausführen
 * ("stehlampe ein", "storen zu", "klima boost"). Pfeiltasten wählen, Enter führt aus,
 * Esc schliesst. Nach einem Befehl erscheint kurz eine Bestätigung.
 */
@Component({
  selector: 'app-search-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative">
      <label class="glass flex items-center gap-3 rounded-full px-5 py-3">
        <svg
          class="size-5 shrink-0 opacity-60"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
        >
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3-3" stroke-linecap="round" />
        </svg>
        <input
          type="text"
          role="combobox"
          aria-label="Geräte suchen oder Befehl eingeben"
          [attr.aria-expanded]="open()"
          aria-controls="search-results"
          placeholder="Suchen oder Befehl – z. B. »Stehlampe ein«"
          class="w-full bg-transparent text-sm outline-none placeholder:text-[color:var(--ink-faint)]"
          [value]="query()"
          (input)="onInput($event)"
          (keydown)="onKeydown($event)"
          (focus)="open.set(true)"
          (blur)="onBlur()"
        />
        @if (feedback()) {
          <span class="shrink-0 text-xs text-emerald-300">✓ {{ feedback() }}</span>
        }
      </label>

      @if (open() && query().trim().length > 0) {
        <div
          id="search-results"
          role="listbox"
          class="glass-card absolute left-0 right-0 top-full z-30 mt-2 overflow-hidden py-1.5"
        >
          @for (
            hit of hits();
            track hit.entry.kind + hit.entry.id + hit.entry.route;
            let i = $index
          ) {
            <button
              type="button"
              role="option"
              [attr.aria-selected]="i === active()"
              class="flex w-full items-center justify-between gap-3 px-4 py-2.5 text-left text-sm hover:bg-white/10"
              [class]="i === active() ? 'bg-white/10' : ''"
              (mousedown)="$event.preventDefault()"
              (click)="run(hit)"
            >
              <span class="min-w-0 truncate">
                {{ hit.label }}
                @if (hit.entry.room) {
                  <span class="text-[color:var(--ink-faint)]"> · {{ hit.entry.room }}</span>
                }
              </span>
              <span
                class="shrink-0 rounded-full bg-white/10 px-2 py-0.5 text-[10px] uppercase tracking-wide text-[color:var(--ink-soft)]"
              >
                {{ hit.action ? 'Befehl' : badge(hit.entry.kind) }}
              </span>
            </button>
          } @empty {
            <p class="px-4 py-2.5 text-sm text-[color:var(--ink-soft)]">
              Keine Treffer – Gerätename, Raum oder Seite versuchen.
            </p>
          }
        </div>
      }
    </div>
  `,
})
export class SearchBar {
  private readonly searchSvc = inject(SearchService);

  protected readonly query = signal('');
  protected readonly open = signal(false);
  protected readonly active = signal(0);
  protected readonly feedback = signal('');

  protected readonly hits = computed(() => this.searchSvc.search(this.query()));

  protected onInput(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
    this.open.set(true);
    this.active.set(0);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const hits = this.hits();
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.active.set(Math.min(this.active() + 1, hits.length - 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.active.set(Math.max(this.active() - 1, 0));
        break;
      case 'Enter': {
        const hit = hits[this.active()];
        if (hit) {
          this.run(hit);
        }
        break;
      }
      case 'Escape':
        this.open.set(false);
        break;
    }
  }

  protected onBlur(): void {
    // Verzögert schliessen, damit ein Klick auf einen Treffer noch ankommt.
    setTimeout(() => this.open.set(false), 150);
  }

  protected run(hit: SearchHit): void {
    const message = this.searchSvc.execute(hit);
    this.open.set(false);
    this.query.set('');
    this.active.set(0);
    if (message) {
      this.feedback.set(message);
      setTimeout(() => this.feedback.set(''), 3000);
    }
  }

  protected badge(kind: SearchKind): string {
    return KIND_BADGE[kind];
  }
}
