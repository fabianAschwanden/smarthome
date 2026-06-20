package fabianaschwanden.smarthome.domain.model.energy;

/** Zustand einer Messung beim Abruf einer Quelle. */
public enum ReadingStatus {
    /** Werte aktuell und gültig. */
    OK,
    /** Quelle nicht erreichbar oder Antwort nicht interpretierbar. */
    ERROR
}
