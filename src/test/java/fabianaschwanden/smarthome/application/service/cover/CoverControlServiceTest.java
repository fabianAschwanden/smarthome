package fabianaschwanden.smarthome.application.service.cover;

import fabianaschwanden.smarthome.domain.model.cover.Cover;
import fabianaschwanden.smarthome.domain.model.cover.CoverCommand;
import fabianaschwanden.smarthome.domain.port.in.cover.CoverNotFound;
import fabianaschwanden.smarthome.domain.port.out.cover.CoverDevice;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverControlServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-19T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void listetAlleStoren() {
        CoverControlService service = new CoverControlService(
                List.of(new FakeCover("a", "A"), new FakeCover("b", "B")), clock);
        assertEquals(2, service.list().size());
    }

    @Test
    void befehlSchliessenSetztPositionAuf0() {
        FakeCover a = new FakeCover("a", "A");
        CoverControlService service = new CoverControlService(List.of(a), clock);

        Cover result = service.command("a", CoverCommand.CLOSE);

        assertEquals(0, result.position());
        assertTrue(result.online());
        assertEquals(CoverCommand.CLOSE, a.lastCommand);
    }

    @Test
    void setPositionWirdDurchgereicht() {
        FakeCover a = new FakeCover("a", "A");
        CoverControlService service = new CoverControlService(List.of(a), clock);

        Cover result = service.setPosition("a", 60);

        assertEquals(60, result.position());
        assertEquals(60, a.position);
    }

    @Test
    void ungueltigePositionWirdAbgelehnt() {
        CoverControlService service = new CoverControlService(List.of(new FakeCover("a", "A")), clock);
        assertThrows(IllegalArgumentException.class, () -> service.setPosition("a", 150));
    }

    @Test
    void unbekannteIdWirftNotFound() {
        CoverControlService service = new CoverControlService(List.of(new FakeCover("a", "A")), clock);
        assertThrows(CoverNotFound.class, () -> service.command("x", CoverCommand.OPEN));
    }

    @Test
    void offlineMeldetLetztePosition() {
        FakeCover a = new FakeCover("a", "A");
        CoverControlService service = new CoverControlService(List.of(a), clock);
        service.setPosition("a", 40);

        a.reachable = false;
        Cover status = service.list().get(0);

        assertFalse(status.online());
        assertEquals(40, status.position());
    }

    private static final class FakeCover implements CoverDevice {
        private final String id;
        private final String name;
        private int position = 100;
        private boolean reachable = true;
        private CoverCommand lastCommand;

        FakeCover(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override public String id() { return id; }
        @Override public String name() { return name; }
        @Override public String room() { return ""; }
        @Override public void apply(CoverCommand command) {
            lastCommand = command;
            if (command == CoverCommand.OPEN) position = 100;
            if (command == CoverCommand.CLOSE) position = 0;
        }
        @Override public void setPosition(int position) { this.position = position; }
        @Override public OptionalInt readPosition() {
            return reachable ? OptionalInt.of(position) : OptionalInt.empty();
        }
    }
}
