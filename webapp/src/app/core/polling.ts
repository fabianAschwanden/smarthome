import { Observable, fromEvent, merge, timer } from 'rxjs';
import { filter, throttleTime } from 'rxjs/operators';

/**
 * Poll-Auslöser für die Daten-Services: emittiert sofort, dann alle {@code intervalMs} –
 * UND zusätzlich, sobald die App „zurückkommt": Tab wieder sichtbar
 * (`visibilitychange`), Fenster-Fokus (`focus`) oder Wiederherstellung aus dem
 * Back-Forward-Cache (`pageshow`).
 *
 * <p>Hintergrund: Browser drosseln/pausieren `timer` in inaktiven Tabs und frieren die
 * Seite im BFCache ein. Ohne den Resume-Trigger zeigt die App nach längerer Inaktivität
 * veraltete Daten, bis man manuell neu lädt. Ein `switchMap` auf diesen Trigger holt bei
 * jeder Rückkehr sofort frische Daten.</p>
 *
 * <p>Gleichzeitige Resume-Events (visibilitychange + focus) werden per `throttleTime`
 * zu einem Refetch zusammengefasst.</p>
 */
export function pollingTimer(intervalMs: number): Observable<unknown> {
  const resume$ = merge(
    fromEvent(document, 'visibilitychange').pipe(
      filter(() => document.visibilityState === 'visible'),
    ),
    fromEvent(window, 'focus'),
    fromEvent(window, 'pageshow'),
  ).pipe(throttleTime(1000));

  return merge(timer(0, intervalMs), resume$);
}
