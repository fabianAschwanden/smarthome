import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { CameraService } from '../../core/services/camera.service';
import { Camera } from '../../core/models/camera';

/**
 * Use Case 11: Kameras (siehe docs/camera/SPEC.md). Live-Bild über das go2rtc-Gateway.
 *
 * Eingebettet wird go2rtcs eigener MSE-Player ({@code stream.html}) per iframe über den
 * relativen Pfad {@code /go2rtc/...}, den das Backend (inkl. WebSocket fürs MSE-Signaling)
 * an go2rtc weiterreicht. MSE ist adaptiv und reconnectet – das ist über instabile
 * Remote-Verbindungen (Fly-Tunnel/Mobilfunk) robuster als ein roher progressiver
 * MP4-Download. Alles über Port 8080/gleiche Origin; die RTSP-URL/IP bleibt im Gateway.
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
                <!-- go2rtc-MSE-Player (adaptiv, reconnect) – tunnel-tauglich. -->
                <iframe
                  class="size-full border-0"
                  [src]="streamUrl(cam)"
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

  /** go2rtc-MSE-Player über den Backend-Proxy (gleiche Origin). */
  protected streamUrl(cam: Camera): SafeResourceUrl {
    const url = `/go2rtc/stream.html?src=${encodeURIComponent(cam.stream)}&mode=mse`;
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }
}
