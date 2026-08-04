package fabianaschwanden.smarthome.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fabianaschwanden.smarthome.domain.model.forecast.Confidence;
import fabianaschwanden.smarthome.domain.model.forecast.PlantProfile;
import fabianaschwanden.smarthome.domain.port.out.forecast.PlantProfileRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Driven Adapter — übersetzt zwischen dem Domänen-Record {@code PlantProfile} und der
 * JPA-Entity. Es gibt genau eine Zeile; {@link #save(PlantProfile)} legt sie an oder
 * überschreibt sie.
 */
@ApplicationScoped
public class PanachePlantProfileRepository
        implements PlantProfileRepository, PanacheRepository<PlantProfileEntity> {

    private static final Logger LOG = Logger.getLogger(PanachePlantProfileRepository.class);

    private static final String KEY_FACTORS = "factorPerHour";
    private static final String KEY_MAX_WATT = "maxObservedPvWatt";
    private static final String KEY_CONFIDENCE = "confidence";

    private final ObjectMapper mapper;

    public PanachePlantProfileRepository(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<PlantProfile> load() {
        PlantProfileEntity entity = findById(PlantProfileEntity.SINGLETON_ID);
        if (entity == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> snapshot =
                    mapper.readValue(entity.profile, new TypeReference<Map<String, Object>>() {});
            List<Double> factors = mapper.convertValue(
                    snapshot.get(KEY_FACTORS), new TypeReference<List<Double>>() {});
            double maxWatt = ((Number) snapshot.get(KEY_MAX_WATT)).doubleValue();
            Confidence confidence = Confidence.valueOf((String) snapshot.get(KEY_CONFIDENCE));
            return Optional.of(new PlantProfile(factors, maxWatt, entity.learnedAt, confidence));
        } catch (Exception e) {
            // Lieber "kein Profil" als ein halb gelesenes: der Aufrufer faellt dann auf
            // den Cold Start zurueck, statt mit unsinnigen Faktoren zu rechnen.
            LOG.warnf("Anlagenprofil nicht lesbar, wird ignoriert: %s", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void save(PlantProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile darf nicht null sein");
        }
        String json = toJson(profile);
        PlantProfileEntity entity = findById(PlantProfileEntity.SINGLETON_ID);
        if (entity == null) {
            entity = new PlantProfileEntity();
            entity.id = PlantProfileEntity.SINGLETON_ID;
            entity.profile = json;
            entity.learnedAt = profile.learnedAt();
            persist(entity);
        } else {
            // Verwaltete Entity: die Aenderung schreibt Hibernate beim Flush selbst.
            entity.profile = json;
            entity.learnedAt = profile.learnedAt();
        }
    }

    private String toJson(PlantProfile profile) {
        try {
            return mapper.writeValueAsString(Map.of(
                    KEY_FACTORS, profile.factorPerHour(),
                    KEY_MAX_WATT, profile.maxObservedPvWatt(),
                    KEY_CONFIDENCE, profile.confidence().name()));
        } catch (Exception e) {
            throw new IllegalStateException("Anlagenprofil nicht serialisierbar", e);
        }
    }
}
