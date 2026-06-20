package fabianaschwanden.smarthome.domain.port.in.cover;

import fabianaschwanden.smarthome.domain.model.cover.Cover;
import fabianaschwanden.smarthome.domain.model.cover.CoverCommand;

import java.util.List;

/**
 * Treiber-Port (Use Case): mehrere Storen verwalten – auflisten, Grundbefehl
 * (Auf/Ab/Stopp) geben und auf eine Position fahren.
 */
public interface ControlCovers {

    List<Cover> list();

    /** Grundbefehl auf eine Store anwenden. */
    Cover command(String id, CoverCommand command);

    /** Store auf eine Position fahren (0 = zu, 100 = offen). */
    Cover setPosition(String id, int position);
}
