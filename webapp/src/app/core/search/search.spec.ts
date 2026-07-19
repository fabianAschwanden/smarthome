import { describe, expect, it } from 'vitest';
import { SearchEntry, parseQuery, search } from './search';

function entry(partial: Partial<SearchEntry> & { name: string }): SearchEntry {
  return {
    kind: 'switch',
    id: partial.name.toLowerCase(),
    room: '',
    route: '/switch',
    keywords: [],
    actions: ['ON', 'OFF'],
    ...partial,
  };
}

const ENTRIES: SearchEntry[] = [
  entry({ name: 'Stehlampe', room: 'Wohnzimmer' }),
  entry({ name: 'Palmenbeleuchtung', room: 'Garten' }),
  entry({
    kind: 'cover',
    name: 'Store Küche',
    room: 'Küche',
    route: '/covers',
    keywords: ['rollladen', 'jalousie'],
    actions: ['OPEN', 'CLOSE', 'STOP'],
  }),
  entry({
    kind: 'climate',
    name: 'Wohnzimmer-Klima',
    room: 'Wohnzimmer',
    route: '/climate',
    keywords: ['klimaanlage', 'ac'],
    actions: ['ON', 'OFF', 'BOOST'],
  }),
  entry({
    kind: 'page',
    name: 'Energie',
    route: '/energy',
    keywords: ['strom', 'pv'],
    actions: [],
  }),
];

describe('parseQuery', () => {
  it('erkennt Befehlswörter neben dem Gerätenamen', () => {
    expect(parseQuery('stehlampe ein')).toEqual({ terms: ['stehlampe'], action: 'ON' });
    expect(parseQuery('zu storen')).toEqual({ terms: ['storen'], action: 'CLOSE' });
  });

  it('behandelt ein einzelnes Befehlswort als Suchbegriff', () => {
    expect(parseQuery('aus')).toEqual({ terms: ['aus'], action: undefined });
  });

  it('normalisiert Umlaute', () => {
    expect(parseQuery('Küche öffnen').terms).toEqual(['kueche']);
    expect(parseQuery('Küche öffnen').action).toBe('OPEN');
  });
});

describe('search', () => {
  it('findet Geräte per Namens-Präfix vor Substring-Treffern', () => {
    const hits = search(ENTRIES, 'steh');
    expect(hits[0]?.entry.name).toBe('Stehlampe');
  });

  it('findet über Synonyme (rollladen -> Store)', () => {
    const hits = search(ENTRIES, 'rollladen');
    expect(hits[0]?.entry.name).toBe('Store Küche');
  });

  it('findet über den Raum', () => {
    const hits = search(ENTRIES, 'garten');
    expect(hits[0]?.entry.name).toBe('Palmenbeleuchtung');
  });

  it('filtert bei Befehlen auf passende Aktionen', () => {
    // "zu" kann nur der Store – Lampe und Klima fallen raus.
    const hits = search(ENTRIES, 'kueche zu');
    expect(hits).toHaveLength(1);
    expect(hits[0]?.entry.name).toBe('Store Küche');
    expect(hits[0]?.action).toBe('CLOSE');
    expect(hits[0]?.label).toBe('Store Küche schliessen');
  });

  it('boost trifft nur die Klimaanlage', () => {
    const hits = search(ENTRIES, 'klima boost');
    expect(hits).toHaveLength(1);
    expect(hits[0]?.entry.kind).toBe('climate');
    expect(hits[0]?.action).toBe('BOOST');
  });

  it('verlangt, dass jeder Suchbegriff trifft (UND)', () => {
    expect(search(ENTRIES, 'stehlampe garten')).toHaveLength(0);
  });

  it('leere Eingabe liefert keine Treffer', () => {
    expect(search(ENTRIES, '   ')).toHaveLength(0);
  });
});
