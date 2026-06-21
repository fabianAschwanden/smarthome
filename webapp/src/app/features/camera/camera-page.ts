import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CameraService } from '../../core/services/camera.service';
import { Camera } from '../../core/models/camera';

/**
 * Use Case 11: Kameras (siehe docs/camera/SPEC.md). Live-Bild über das go2rtc-Gateway.
 *
 * Der Stream kommt als fragmentiertes MP4 (H.264) über den relativen Pfad
 * {@code /go2rtc/...}, den das Backend an go2rtc weiterreicht. Reines HTTP – kein
 * WebSocket/UDP – darum funktioniert es identisch im LAN und remote (durch den
 * Fly-Login-Proxy + WireGuard, wo nur Port 8080 erreichbar ist). Die RTSP-URL/IP
 * bleibt im Gateway; das Frontend kennt nur den Stream-Namen.
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
                <!-- Fragmentiertes MP4 als progressive Quelle: kein iframe/WS nötig. -->
                <video
                  class="size-full object-contain"
                  [src]="streamUrl(cam)"
                  [title]="cam.name"
                  autoplay
                  muted
                  playsinline
                ></video>
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

  protected readonly cameras = this.api.cameras;

  /** MP4-Stream über den Backend-Proxy (gleiche Origin) – H.264 für Browser-Wiedergabe. */
  protected streamUrl(cam: Camera): string {
    return `/go2rtc/api/stream.mp4?src=${encodeURIComponent(cam.stream)}&video=h264`;
  }
}
