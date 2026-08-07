package fabianaschwanden.smarthome.adapter.in.rest.forecast;

import fabianaschwanden.smarthome.adapter.in.rest.dto.batteryschedule.BatteryScheduleDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.coverschedule.CoverScheduleDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.forecast.AccuracyDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.forecast.HeatProtectionDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.forecast.PvForecastDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.forecast.SurplusDto;
import fabianaschwanden.smarthome.domain.port.in.forecast.ApplyRecommendation;
import fabianaschwanden.smarthome.domain.port.in.forecast.ApplyHeatProtection;
import fabianaschwanden.smarthome.domain.port.in.forecast.ForecastAccuracyQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.HeatProtectionQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.PvForecastQuery;
import fabianaschwanden.smarthome.domain.port.in.forecast.SurplusQuery;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Driving Adapter — PV-Prognose, Überschussfenster und das Übernehmen der Ladeempfehlung.
 * Keine Geschäftslogik: übersetzt nur HTTP auf die Use-Case-Ports.
 */
@Path("/api/forecast")
@Produces(MediaType.APPLICATION_JSON)
public class ForecastResource {

    /** Zeigt zwei Wochen - genug, um einen Trend zu sehen, wenig genug fuer eine Kachel. */
    private static final int DEFAULT_DAYS = 14;

    private final PvForecastQuery forecast;
    private final SurplusQuery surplus;
    private final ApplyRecommendation applyRecommendation;
    private final ForecastAccuracyQuery accuracy;
    private final HeatProtectionQuery heatProtection;
    private final ApplyHeatProtection applyHeatProtection;
    private final int shadedPosition;

    public ForecastResource(
            PvForecastQuery forecast,
            SurplusQuery surplus,
            ApplyRecommendation applyRecommendation,
            ForecastAccuracyQuery accuracy,
            HeatProtectionQuery heatProtection,
            ApplyHeatProtection applyHeatProtection,
            @ConfigProperty(name = "forecast.heat-protection.position") int shadedPosition) {
        this.forecast = forecast;
        this.surplus = surplus;
        this.applyRecommendation = applyRecommendation;
        this.accuracy = accuracy;
        this.heatProtection = heatProtection;
        this.applyHeatProtection = applyHeatProtection;
        this.shadedPosition = shadedPosition;
    }

    @GET
    @Path("heat-protection")
    @Operation(
            summary = "Lohnt sich heute Beschatten gegen die Hitze?",
            description = "Zeitfenster starker Einstrahlung bei ohnehin warmen Raeumen. "
                    + "204, wenn eine der beiden Bedingungen fehlt - an einem kuehlen oder "
                    + "trueben Tag der Normalfall.")
    @APIResponse(responseCode = "200", description = "Fenster vorhanden")
    @APIResponse(responseCode = "204", description = "Kein Fenster")
    public Response heatProtection() {
        return heatProtection.heatProtection()
                .map(window -> Response.ok(HeatProtectionDto.from(window, shadedPosition)).build())
                .orElseGet(() -> Response.noContent().build());
    }

    @POST
    @Path("heat-protection/apply")
    @Operation(
            summary = "Beschattungsfenster als Storen-Zeitsteuerung uebernehmen",
            description = "Legt je Store einen Countdown fuers Zufahren und einen fuers "
                    + "Oeffnen an. 409, wenn gerade kein Fenster vorliegt.")
    public java.util.List<CoverScheduleDto> applyHeatProtection() {
        return applyHeatProtection.applyHeatProtection().stream().map(CoverScheduleDto::from).toList();
    }

    @GET
    @Path("accuracy")
    @Operation(
            summary = "Wie gut lag die Prognose?",
            description = "Prognose gegen Ist je Tag samt mittlerem relativem Fehler (MAPE). "
                    + "mapePercent ist null, solange kein Tag bewertbar ist - offene Tage und "
                    + "solche ganz ohne Ertrag zaehlen nicht mit.")
    public AccuracyDto accuracy(@QueryParam("days") Integer days) {
        return AccuracyDto.from(accuracy.accuracy(days == null ? DEFAULT_DAYS : days));
    }

    @GET
    @Path("pv")
    @Operation(
            summary = "PV-Ertragsprognose für heute und morgen",
            description = "Stundenwerte samt Tagessummen. 204, solange noch nie gerechnet wurde "
                    + "(etwa direkt nach dem Start). Eine veraltete Prognose wird ausgeliefert und "
                    + "trägt ihr Alter in computedAt.")
    @APIResponse(responseCode = "200", description = "Prognose vorhanden")
    @APIResponse(responseCode = "204", description = "Noch keine Prognose gerechnet")
    public Response pv() {
        return forecast.currentForecast()
                .map(f -> Response.ok(PvForecastDto.from(f)).build())
                .orElseGet(() -> Response.noContent().build());
    }

    @GET
    @Path("surplus")
    @Operation(
            summary = "Erwartete Überschussfenster samt Ladeempfehlung",
            description = "Enthält die Verbrauchs-Baseline als Vergleichskurve. "
                    + "recommendation ist null, wenn kein Fenster die Schwellen erreicht.")
    public SurplusDto surplus() {
        return SurplusDto.from(surplus.baseline(), surplus.windows(), surplus.recommendation());
    }

    @POST
    @Path("recommendation/apply")
    @Operation(
            summary = "Ladeempfehlung als Batterie-Zeitplan übernehmen",
            description = "Legt einen einmaligen Zeitplan auf den Fensterbeginn an (Use Case 14). "
                    + "Geschaltet wird nicht hier – das bleibt bei der Zeitsteuerung.")
    @APIResponse(responseCode = "200", description = "Zeitplan angelegt")
    @APIResponse(responseCode = "409", description = "Derzeit liegt keine Empfehlung vor")
    public BatteryScheduleDto apply() {
        return BatteryScheduleDto.from(applyRecommendation.apply());
    }
}
