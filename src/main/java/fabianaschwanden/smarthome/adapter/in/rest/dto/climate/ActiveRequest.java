package fabianaschwanden.smarthome.adapter.in.rest.dto.climate;

/** Anlage stilllegen ({@code false}) oder wieder in Betrieb nehmen ({@code true}). */
public record ActiveRequest(boolean active) {}
