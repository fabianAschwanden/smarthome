package fabianaschwanden.smarthome.adapter.in.rest.backup;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/** Integrationstest der Backup-Endpunkte: Export-Download, Restore-Roundtrip, Versions-Check. */
@QuarkusTest
class BackupResourceTest {

    private static final String BACKUP = """
            {
              "schemaVersion": 1,
              "exportedAt": "2026-07-19T10:00:00Z",
              "alertSettings": { "enabled": true, "ntfyTopic": "backup-test" },
              "switchSchedules": [{
                "id": "5f1e2d3c-4b5a-6978-8899-aabbccddeeff",
                "switchId": "lampe", "type": "SCHEDULE", "action": "ON", "enabled": true,
                "time": "06:30", "weekdays": ["MONDAY", "FRIDAY"],
                "fireAt": null, "windowStart": null, "windowEnd": null, "pulseSeconds": null
              }],
              "batterySchedules": [],
              "coverSchedules": [],
              "itemImages": [{
                "itemId": "lampe",
                "dataUrl": "data:image/jpeg;base64,AAAA",
                "updatedAt": "2026-07-19T09:00:00Z"
              }]
            }
            """;

    @Test
    void restore_und_export_roundtrip() {
        given().contentType("application/json").body(BACKUP)
                .when().post("/api/backup")
                .then().log().ifValidationFails().statusCode(200)
                .body("alertSettings", is(true))
                .body("switchSchedules", is(1))
                .body("itemImages", is(1));

        given()
                .when().get("/api/backup")
                .then().statusCode(200)
                .header("Content-Disposition", equalTo("attachment; filename=\"smarthome-backup.json\""))
                .body("schemaVersion", is(1))
                .body("exportedAt", notNullValue())
                .body("switchSchedules.find { it.switchId == 'lampe' }.time", equalTo("06:30"))
                .body("itemImages.find { it.itemId == 'lampe' }.dataUrl",
                        equalTo("data:image/jpeg;base64,AAAA"))
                .body("alertSettings.ntfyTopic", equalTo("backup-test"));
    }

    @Test
    void fremde_schema_version_ist_400() {
        given().contentType("application/json").body("{\"schemaVersion\": 99}")
                .when().post("/api/backup")
                .then().statusCode(400);
    }
}
