import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NotificationBell } from './shared/notification-bell';
import { RoomService } from './core/services/room.service';

/** App-Shell: Glass-Layout mit Icon-Rail (Navigation), Topbar und Raum-Tabs. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificationBell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly roomService = inject(RoomService);

  /** Räume automatisch aus den konfigurierten Geräten. */
  protected readonly rooms = this.roomService.rooms;

  private readonly selected = signal<string | null>(null);
  /** Aktiver Raum: gewählter, sonst der erste verfügbare. */
  protected readonly activeRoom = computed(() => this.selected() ?? this.rooms()[0] ?? '');

  protected selectRoom(room: string): void {
    this.selected.set(room);
  }
}
