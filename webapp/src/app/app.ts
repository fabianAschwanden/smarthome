import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NotificationBell } from './shared/notification-bell';
import { SearchBar } from './shared/search-bar';
import { RoomService } from './core/services/room.service';

/** App-Shell: Glass-Layout mit Icon-Rail (Navigation), Topbar und Raum-Tabs. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificationBell, SearchBar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly roomService = inject(RoomService);

  /** Räume automatisch aus den konfigurierten Geräten. */
  protected readonly rooms = this.roomService.rooms;
  /** Aktiver Raumfilter (null = "Alle"). */
  protected readonly activeRoom = this.roomService.activeRoom;

  protected selectRoom(room: string | null): void {
    this.roomService.select(room);
  }
}
