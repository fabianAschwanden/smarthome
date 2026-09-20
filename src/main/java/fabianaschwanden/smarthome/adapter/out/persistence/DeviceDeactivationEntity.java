package fabianaschwanden.smarthome.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA-Entity eines stillgelegten Geräts – eine Zeile je stillgelegtem Gerät, keine für
 * aktive. Schlüssel ist (Art, ID), damit sich «pool» als Anlage und ein gleichnamiges
 * Gerät anderer Art nie in die Quere kommen. Lebt ausschliesslich im Persistence-Adapter.
 */
@Entity
@Table(name = "device_deactivation")
@IdClass(DeviceDeactivationEntity.Key.class)
public class DeviceDeactivationEntity {

    @Id
    @Column(name = "kind", length = 32)
    public String kind;

    @Id
    @Column(name = "device_id", length = 64)
    public String deviceId;

    @Column(name = "deactivated_at", nullable = false)
    public Instant deactivatedAt;

    /** Zusammengesetzter Schlüssel (Art, ID). */
    public static class Key implements Serializable {
        public String kind;
        public String deviceId;

        public Key() {
        }

        public Key(String kind, String deviceId) {
            this.kind = kind;
            this.deviceId = deviceId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(kind, k.kind) && Objects.equals(deviceId, k.deviceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, deviceId);
        }
    }
}
