package fabianaschwanden.smarthome.domain.model.alert;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AlertSettingsTest {

    @Test
    void gueltigeInstanzBautKorrekt() {
        AlertSettings s = new AlertSettings(true, "mein-topic");
        assertTrue(s.enabled());
        assertEquals("mein-topic", s.ntfyTopic());
    }

    @Test
    void nullTopicWirdLeererString() {
        AlertSettings s = new AlertSettings(false, null);
        assertEquals("", s.ntfyTopic());
    }

    @Test
    void topicWirdGetrimmt() {
        AlertSettings s = new AlertSettings(true, "  topic  ");
        assertEquals("topic", s.ntfyTopic());
    }

    @Test
    void disabledIstAusOhneTopic() {
        AlertSettings s = AlertSettings.disabled();
        assertFalse(s.enabled());
        assertEquals("", s.ntfyTopic());
        assertFalse(s.canPush());
    }

    @Test
    void canPushNurWennAktiviertUndTopic() {
        assertTrue(new AlertSettings(true, "topic").canPush());
    }

    @Test
    void canPushFalseWennDeaktiviert() {
        assertFalse(new AlertSettings(false, "topic").canPush());
    }

    @Test
    void canPushFalseWennTopicLeer() {
        assertFalse(new AlertSettings(true, "").canPush());
        assertFalse(new AlertSettings(true, "   ").canPush());
    }
}
