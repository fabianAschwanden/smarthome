import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/dashboard/dashboard-page').then((m) => m.DashboardPage),
  },
  {
    path: 'energy',
    loadComponent: () => import('./features/energy/energy-page').then((m) => m.EnergyPage),
  },
  {
    path: 'battery',
    loadComponent: () => import('./features/battery/battery-page').then((m) => m.BatteryPage),
  },
  {
    path: 'switch',
    loadComponent: () => import('./features/tuya/switch-page').then((m) => m.SwitchPage),
  },
  {
    path: 'switch/:id/schedule',
    loadComponent: () => import('./features/tuya/schedule-page').then((m) => m.SchedulePage),
  },
  {
    path: 'covers',
    loadComponent: () => import('./features/cover/cover-page').then((m) => m.CoverPage),
  },
  {
    path: 'wellness',
    loadComponent: () => import('./features/appliance/appliance-page').then((m) => m.AppliancePage),
  },
  {
    path: 'climate',
    loadComponent: () => import('./features/climate/climate-page').then((m) => m.ClimatePage),
  },
  {
    path: 'cameras',
    loadComponent: () => import('./features/camera/camera-page').then((m) => m.CameraPage),
  },
  {
    path: 'native',
    loadComponent: () => import('./features/nativeview/native-page').then((m) => m.NativePage),
  },
  {
    path: 'settings',
    loadComponent: () => import('./features/settings/settings-page').then((m) => m.SettingsPage),
  },
];
