import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { CameraService } from '../../core/services/camera.service';
import { Camera } from '../../core/models/camera';

/** Port des go2rtc-Gateways auf dem Heim-Server (network_mode: host). */
const GO2RTC_PORT = 1984;

/**
 * Use Case 11: Kameras (siehe docs/camera/SPEC.md). Live-Bild über das go2rtc-Gateway.
 * Der eingebettete go2rtc-Player (WebRTC, Fallback MSE/MJPEG) wird per iframe geladen.
 * Die RTSP-URL/IP bleibt im Gateway (gitignored config); das Frontend kennt nur den
 * Stream-Namen.
 */
@Component({
  selector: 'app-camera-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="space-y-5">
      <h2 class="text-2xl font-semibold">Kameras</h2>

      @if (cameras(); as list) {
        @if (list.length === 0) {
          <p class="text-[color:var(--ink-soft)]">Keine Kameras konfiguriert.</p>
        }
        <div class="grid gap-4 lg:grid-cols-2">
          @for (cam of list; track cam.id) {
            <article class="glass-card overflow-hidden">
              <div class="aspect-video w-full bg-black">
                <iframe
                  class="size-full border-0"
                  [src]="playerUrl(cam)"
                  [title]="cam.name"
                  allow="autoplay; fullscreen"
                ></iframe>
              </div>
              <div class="flex items-baseline justify-between gap-3 p-4">
                <h3 class="text-lg font-semibold">{{ cam.name }}</h3>
                <p class="text-sm text-[color:var(--ink-soft)]">{{ cam.room || 'Kamera' }}</p>
              </div>
            </article>
          }
        </div>
      } @else {
        <p class="text-[color:var(--ink-soft)]">Lade Kameras …</p>
      }
    </section>
  `,
})
export class CameraPage {
  private readonly api = inject(CameraService);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly cameras = this.api.cameras;

  /** go2rtc läuft auf demselben Host wie die App, nur auf einem anderen Port. */
  private readonly base = computed(
    () => `${window.location.protocol}//${window.location.hostname}:${GO2RTC_PORT}`,
  );

  protected playerUrl(cam: Camera): SafeResourceUrl {
    const url = `${this.base()}/stream.html?src=${encodeURIComponent(cam.stream)}&mode=webrtc`;
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }
}
