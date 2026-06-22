package fabianaschwanden.smarthome.adapter.in.rest;

import fabianaschwanden.smarthome.adapter.in.rest.appliance.ApplianceNotFoundMapper;
import fabianaschwanden.smarthome.adapter.in.rest.appliance.ApplianceUnavailableMapper;
import fabianaschwanden.smarthome.adapter.in.rest.appliance.FunctionNotSupportedMapper;
import fabianaschwanden.smarthome.adapter.in.rest.appliance.TemperatureNotSupportedMapper;
import fabianaschwanden.smarthome.adapter.in.rest.battery.ManualSwitchNotAllowedMapper;
import fabianaschwanden.smarthome.adapter.in.rest.climate.ClimateNotFoundMapper;
import fabianaschwanden.smarthome.adapter.in.rest.climate.ClimateUnavailableMapper;
import fabianaschwanden.smarthome.adapter.in.rest.cover.CoverNotFoundMapper;
import fabianaschwanden.smarthome.adapter.in.rest.cover.CoverUnavailableMapper;
import fabianaschwanden.smarthome.adapter.in.rest.itemimage.ItemImageNotFoundMapper;
import fabianaschwanden.smarthome.adapter.in.rest.schedule.InvalidScheduleMapper;
import fabianaschwanden.smarthome.adapter.in.rest.schedule.ScheduleNotFoundMapper;
import fabianaschwanden.smarthome.adapter.in.rest.tuya.CriticalConfirmationRequiredMapper;
import fabianaschwanden.smarthome.adapter.in.rest.tuya.SwitchNotFoundMapper;
import fabianaschwanden.smarthome.adapter.in.rest.tuya.SwitchUnavailableMapper;
import fabianaschwanden.smarthome.domain.model.appliance.ApplianceFunction;
import fabianaschwanden.smarthome.domain.port.in.appliance.ApplianceNotFound;
import fabianaschwanden.smarthome.domain.port.in.appliance.FunctionNotSupported;
import fabianaschwanden.smarthome.domain.port.in.appliance.TemperatureNotSupported;
import fabianaschwanden.smarthome.domain.port.in.battery.ManualSwitchNotAllowed;
import fabianaschwanden.smarthome.domain.port.in.climate.ClimateNotFound;
import fabianaschwanden.smarthome.domain.port.in.cover.CoverNotFound;
import fabianaschwanden.smarthome.domain.port.in.itemimage.ItemImageNotFound;
import fabianaschwanden.smarthome.domain.port.in.schedule.ScheduleNotFound;
import fabianaschwanden.smarthome.domain.port.in.tuya.CriticalConfirmationRequired;
import fabianaschwanden.smarthome.domain.port.in.tuya.SwitchNotFound;
import fabianaschwanden.smarthome.domain.port.out.appliance.ApplianceUnavailable;
import fabianaschwanden.smarthome.domain.port.out.climate.ClimateUnavailable;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverUnavailable;
import fabianaschwanden.smarthome.domain.port.out.tuya.SwitchUnavailable;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JAX-RS {@link jakarta.ws.rs.ext.ExceptionMapper}s direkt aufgerufen (ohne HTTP-Stack):
 * jede Domänen-Ausnahme wird auf den erwarteten HTTP-Status abgebildet, die Meldung
 * landet im Body.
 *
 * <p>{@code @QuarkusTest}, damit die Coverage ins Quarkus-JaCoCo zählt.
 */
@QuarkusTest
class ExceptionMappersTest {

    private static void assertStatus(Response response, Response.Status expected) {
        assertEquals(expected.getStatusCode(), response.getStatus());
    }

    private static void assertBody(Response response, String expected) {
        assertEquals(expected, response.getEntity());
    }

    @Test
    void applianceUnavailableWird503() {
        Response r = new ApplianceUnavailableMapper().toResponse(new ApplianceUnavailable("offline"));
        assertStatus(r, Response.Status.SERVICE_UNAVAILABLE);
        assertBody(r, "offline");
    }

    @Test
    void applianceNotFoundWird404() {
        Response r = new ApplianceNotFoundMapper().toResponse(new ApplianceNotFound("x"));
        assertStatus(r, Response.Status.NOT_FOUND);
    }

    @Test
    void functionNotSupportedWird400() {
        Response r = new FunctionNotSupportedMapper()
                .toResponse(new FunctionNotSupported("x", ApplianceFunction.PUMP));
        assertStatus(r, Response.Status.BAD_REQUEST);
    }

    @Test
    void temperatureNotSupportedWird400() {
        Response r = new TemperatureNotSupportedMapper().toResponse(new TemperatureNotSupported("x"));
        assertStatus(r, Response.Status.BAD_REQUEST);
    }

    @Test
    void climateUnavailableWird503() {
        Response r = new ClimateUnavailableMapper().toResponse(new ClimateUnavailable("offline"));
        assertStatus(r, Response.Status.SERVICE_UNAVAILABLE);
        assertBody(r, "offline");
    }

    @Test
    void climateNotFoundWird404() {
        Response r = new ClimateNotFoundMapper().toResponse(new ClimateNotFound("x"));
        assertStatus(r, Response.Status.NOT_FOUND);
    }

    @Test
    void coverUnavailableWird503() {
        Response r = new CoverUnavailableMapper().toResponse(new CoverUnavailable("offline"));
        assertStatus(r, Response.Status.SERVICE_UNAVAILABLE);
    }

    @Test
    void coverNotFoundWird404() {
        Response r = new CoverNotFoundMapper().toResponse(new CoverNotFound("x"));
        assertStatus(r, Response.Status.NOT_FOUND);
    }

    @Test
    void switchUnavailableWird503() {
        Response r = new SwitchUnavailableMapper().toResponse(new SwitchUnavailable("offline"));
        assertStatus(r, Response.Status.SERVICE_UNAVAILABLE);
    }

    @Test
    void switchNotFoundWird404() {
        Response r = new SwitchNotFoundMapper().toResponse(new SwitchNotFound("x"));
        assertStatus(r, Response.Status.NOT_FOUND);
    }

    @Test
    void criticalConfirmationRequiredWird409() {
        Response r = new CriticalConfirmationRequiredMapper().toResponse(new CriticalConfirmationRequired("x"));
        assertStatus(r, Response.Status.CONFLICT);
    }

    @Test
    void manualSwitchNotAllowedWird409() {
        Response r = new ManualSwitchNotAllowedMapper().toResponse(new ManualSwitchNotAllowed());
        assertStatus(r, Response.Status.CONFLICT);
    }

    @Test
    void itemImageNotFoundWird404() {
        Response r = new ItemImageNotFoundMapper().toResponse(new ItemImageNotFound("x"));
        assertStatus(r, Response.Status.NOT_FOUND);
    }

    @Test
    void scheduleNotFoundWird404() {
        Response r = new ScheduleNotFoundMapper().toResponse(new ScheduleNotFound(UUID.randomUUID()));
        assertStatus(r, Response.Status.NOT_FOUND);
    }

    @Test
    void invalidScheduleWird400() {
        Response r = new InvalidScheduleMapper().toResponse(new IllegalArgumentException("bad"));
        assertStatus(r, Response.Status.BAD_REQUEST);
        assertTrue(((String) r.getEntity()).contains("bad"));
    }
}
