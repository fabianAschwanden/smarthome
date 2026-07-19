/**
 * Reine Such-Logik der globalen Suche (Topbar): Normalisierung, Befehls-Erkennung
 * und Relevanz-Bewertung – bewusst framework-frei, damit sie isoliert testbar ist.
 */

/** Ausführbare Aktion eines Treffers (Teilmenge je Gerätetyp). */
export type SearchAction = 'ON' | 'OFF' | 'OPEN' | 'CLOSE' | 'STOP' | 'BOOST';

/** Art des Eintrags – bestimmt Icon/Badge und welche Aktionen möglich sind. */
export type SearchKind = 'switch' | 'cover' | 'climate' | 'battery' | 'sensor' | 'smoke' | 'page';

/** Ein durchsuchbarer Eintrag (Gerät oder Seite). */
export interface SearchEntry {
  kind: SearchKind;
  /** Geräte-Id (leer bei Seiten). */
  id: string;
  name: string;
  room: string;
  /** Zielroute bei Auswahl ohne Aktion. */
  route: string;
  /** Synonyme/Schlagworte, die zusätzlich zum Namen matchen. */
  keywords: string[];
  /** Aktionen, die dieser Eintrag ausführen kann. */
  actions: SearchAction[];
}

/** Ein bewerteter Treffer; {@code action} gesetzt, wenn die Eingabe einen Befehl enthielt. */
export interface SearchHit {
  entry: SearchEntry;
  score: number;
  action?: SearchAction;
  /** Anzeigetext, z. B. "Stehlampe einschalten" bei Befehlen. */
  label: string;
}

/** Befehlswörter → Aktion (Eingabe wird normalisiert verglichen). */
const VERBS: Record<string, SearchAction> = {
  ein: 'ON',
  an: 'ON',
  einschalten: 'ON',
  on: 'ON',
  aus: 'OFF',
  ausschalten: 'OFF',
  off: 'OFF',
  auf: 'OPEN',
  hoch: 'OPEN',
  oeffnen: 'OPEN',
  open: 'OPEN',
  zu: 'CLOSE',
  runter: 'CLOSE',
  schliessen: 'CLOSE',
  close: 'CLOSE',
  stopp: 'STOP',
  stop: 'STOP',
  halt: 'STOP',
  boost: 'BOOST',
  turbo: 'BOOST',
};

const ACTION_LABEL: Record<SearchAction, string> = {
  ON: 'einschalten',
  OFF: 'ausschalten',
  OPEN: 'öffnen',
  CLOSE: 'schliessen',
  STOP: 'stoppen',
  BOOST: 'Boost umschalten',
};

/** Kleinschreibung + Umlaute/ß auffalten, damit "stören"/"storen" gleich matchen. */
export function normalize(text: string): string {
  return text
    .toLowerCase()
    .replaceAll('ä', 'ae')
    .replaceAll('ö', 'oe')
    .replaceAll('ü', 'ue')
    .replaceAll('ß', 'ss');
}

/** Zerlegte Sucheingabe: Suchbegriffe + optional erkannter Befehl. */
export interface ParsedQuery {
  terms: string[];
  action?: SearchAction;
}

/**
 * Zerlegt die Eingabe in Suchbegriffe und erkennt Befehlswörter ("stehlampe ein").
 * Nur das ERSTE Befehlswort wird zur Aktion; besteht die Eingabe ausschliesslich aus
 * dem Befehlswort, bleibt es zugleich Suchbegriff (sonst fände "aus" nichts).
 */
export function parseQuery(text: string): ParsedQuery {
  const words = normalize(text).split(/\s+/).filter(Boolean);
  let action: SearchAction | undefined;
  const terms: string[] = [];
  for (const word of words) {
    const verb = VERBS[word];
    if (verb && action === undefined && words.length > 1) {
      action = verb;
    } else {
      terms.push(word);
    }
  }
  return { terms, action };
}

/** Bewertet einen Eintrag gegen die Suchbegriffe; 0 = kein Treffer (UND-Verknüpfung). */
export function score(entry: SearchEntry, terms: string[]): number {
  if (terms.length === 0) {
    return 0;
  }
  const name = normalize(entry.name);
  const nameWords = name.split(/\s+|-/);
  const room = normalize(entry.room);
  const keywords = entry.keywords.map(normalize);

  let total = 0;
  for (const term of terms) {
    let best = 0;
    if (name === term) {
      best = 100;
    } else if (name.startsWith(term)) {
      best = 80;
    } else if (nameWords.some((w) => w.startsWith(term))) {
      best = 60;
    } else if (name.includes(term)) {
      best = 40;
    } else if (keywords.some((k) => k === term || k.startsWith(term))) {
      best = 30;
    } else if (keywords.some((k) => k.includes(term))) {
      best = 25;
    } else if (room === term || room.startsWith(term)) {
      best = 20;
    }
    if (best === 0) {
      return 0; // jeder Begriff muss treffen
    }
    total += best;
  }
  return total;
}

/**
 * Sucht in den Einträgen: bei erkanntem Befehl nur Einträge, die die Aktion können
 * (Treffer-Label wird zum Befehlstext); sonst reine Relevanz-Reihenfolge.
 */
export function search(entries: SearchEntry[], text: string, limit = 8): SearchHit[] {
  const { terms, action } = parseQuery(text);
  if (terms.length === 0) {
    return [];
  }
  const hits: SearchHit[] = [];
  for (const entry of entries) {
    if (action !== undefined && !entry.actions.includes(action)) {
      continue;
    }
    const s = score(entry, terms);
    if (s > 0) {
      hits.push({
        entry,
        score: s,
        action,
        label: action !== undefined ? `${entry.name} ${ACTION_LABEL[action]}` : entry.name,
      });
    }
  }
  return hits
    .sort((a, b) => b.score - a.score || a.entry.name.localeCompare(b.entry.name, 'de'))
    .slice(0, limit);
}
