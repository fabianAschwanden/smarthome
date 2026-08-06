import type { API } from 'homebridge';
import { SmarthomePlatform } from './platform';
import { PLATFORM_NAME, PLUGIN_NAME } from './settings';

/** Einstiegspunkt: Homebridge laedt diese Datei und registriert die Plattform. */
export default (api: API): void => {
  api.registerPlatform(PLUGIN_NAME, PLATFORM_NAME, SmarthomePlatform);
};
