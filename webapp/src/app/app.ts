import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NotificationBell } from './shared/notification-bell';

/** App-Shell: Glass-Layout mit Icon-Rail (Navigation), Topbar und Raum-Tabs. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificationBell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly rooms = signal(['Wohnzimmer', 'Schlafzimmer', 'Küche', 'Keller']);
  protected readonly activeRoom = signal('Keller');

  protected selectRoom(room: string): void {
    this.activeRoom.set(room);
  }
}
