package fabianaschwanden.smarthome.adapter.in.rest.backup;

import fabianaschwanden.smarthome.adapter.in.rest.dto.backup.BackupFileDto;
import fabianaschwanden.smarthome.adapter.in.rest.dto.backup.RestoreSummaryDto;
import fabianaschwanden.smarthome.domain.port.in.backup.ManageBackup;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Clock;

/**
 * Driving Adapter — übersetzt HTTP auf den Use-Case-Port, keine Geschäftslogik.
 * GET liefert das Backup als Datei-Download (Content-Disposition), POST stellt ein
 * hochgeladenes Backup wieder her (ersetzt den Bestand je Kategorie).
 */
@Path("/api/backup")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BackupResource {

    private final ManageBackup backup;
    private final Clock clock = Clock.systemUTC();

    public BackupResource(ManageBackup backup) {
        this.backup = backup;
    }

    @GET
    public Response exportBackup() {
        BackupFileDto file = BackupFileDto.from(backup.exportData(), clock.instant());
        return Response.ok(file)
                .header("Content-Disposition", "attachment; filename=\"smarthome-backup.json\"")
                .build();
    }

    @POST
    public Response restore(BackupFileDto file) {
        try {
            return Response.ok(RestoreSummaryDto.from(backup.restore(file.toSnapshot()))).build();
        } catch (IllegalArgumentException e) {
            // Fremde schemaVersion oder unlesbare Werte (UUID/Zeit/Enum) -> 400 statt 500.
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorDto(e.getMessage()))
                    .build();
        }
    }

    /** Fehlermeldung als JSON-Body. */
    public record ErrorDto(String message) {
    }
}
