import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiClient } from '../src/api-client';

const log = { debug: () => {}, warn: () => {} };

function respond(body: unknown, ok = true, status = 200): Response {
  return { ok, status, json: async () => body } as Response;
}

afterEach(() => vi.unstubAllGlobals());

describe('ApiClient', () => {
  it('liefert die Geraete aller Endpunkte', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url.endsWith('/api/switches') ? respond([{ id: 'stehlampe' }]) : respond([]),
      ),
    );

    const snapshot = await new ApiClient('http://app', log).snapshot();

    expect(snapshot.switches).toEqual([{ id: 'stehlampe' }]);
    expect(snapshot.covers).toEqual([]);
  });

  it('laesst einen fehlerhaften Endpunkt die uebrigen nicht mitreissen', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.endsWith('/api/covers')) {
          throw new Error('ECONNREFUSED');
        }
        return respond(url.endsWith('/api/switches') ? [{ id: 'stehlampe' }] : []);
      }),
    );

    const snapshot = await new ApiClient('http://app', log).snapshot();

    expect(snapshot.switches).toHaveLength(1);
    expect(snapshot.covers).toEqual([]);
  });

  it('verwirft eine 200er-Antwort, die keine Liste ist', async () => {
    // Der SPA-Rueckfall der App lieferte frueher HTML mit Status 200.
    vi.stubGlobal('fetch', vi.fn(async () => respond('<!doctype html>')));

    const snapshot = await new ApiClient('http://app', log).snapshot();

    expect(snapshot.switches).toEqual([]);
  });

  it('schaltet ohne Bestaetigung, solange sie nicht angefordert wird', async () => {
    const fetchMock = vi.fn(async () => respond(null));
    vi.stubGlobal('fetch', fetchMock);

    await new ApiClient('http://app', log).setSwitch('homecinema', false);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('http://app/api/switches/homecinema');
    expect(JSON.parse(init.body as string)).toEqual({ state: 'OFF', confirm: false });
  });

  it('meldet einen abgelehnten Schaltbefehl als Fehler', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => respond(null, false, 409)));

    await expect(new ApiClient('http://app', log).setSwitch('homecinema', false)).rejects.toThrow(
      /409/,
    );
  });
});
