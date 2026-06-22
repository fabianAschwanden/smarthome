package fabianaschwanden.smarthome.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Erzwingt die Architektur-Invarianten des Blueprints (§3.4) als Build-Gate.
 * Architektur, die nicht getestet wird, erodiert.
 *
 * <p>Bewusst reguläre JUnit-Jupiter-Tests statt {@code @ArchTest}: die ArchUnit-JUnit-Engine
 * wird unter JUnit Platform 6 (Quarkus ≥ 3.36) nicht ausgeführt — die Regeln liefen still nie.
 */
class HexagonalArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("fabianaschwanden.smarthome");

    @Test
    void domain_ist_framework_frei() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta..", "io.quarkus..", "org.hibernate..",
                        "com.fasterxml.jackson..", "io.smallrye..", "org.jboss..")
                .because("das innere Hexagon hat null Framework-Abhängigkeiten (Blueprint §3.2)")
                .check(CLASSES);
    }

    @Test
    void schichten_regel() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("domain").definedBy("..domain..")
                .layer("application").definedBy("..application..")
                .layer("adapter").definedBy("..adapter..")
                // 'support' = geteilte, framework-nahe Helfer (z. B. Tuya-Protokoll). Nur Adapter
                // dürfen sie nutzen – so bleibt das innere Hexagon (domain/application) frei davon.
                .layer("support").definedBy("..support..")
                .whereLayer("adapter").mayNotBeAccessedByAnyLayer()
                .whereLayer("application").mayOnlyBeAccessedByLayers("adapter")
                // support darf die Domäne nutzen (wie ein Adapter), domain/application aber nicht support.
                .whereLayer("domain").mayOnlyBeAccessedByLayers("application", "adapter", "support")
                .whereLayer("support").mayOnlyBeAccessedByLayers("adapter")
                .because("adapter -> application -> domain ist unverhandelbar (Blueprint §3.2)")
                .check(CLASSES);
    }

    @Test
    void adapter_referenzieren_einander_nicht() {
        slices()
                .matching("..adapter.(*).(*)..")
                .should().notDependOnEachOther()
                .because("Adapter hängen an der Domäne, nie aneinander (Blueprint §3.4)")
                .check(CLASSES);
    }

    @Test
    void rest_dtos_bleiben_im_rest_adapter() {
        classes()
                .that().resideInAPackage("..adapter.in.rest.dto..")
                .should().onlyBeAccessed().byClassesThat().resideInAPackage("..adapter.in.rest..")
                .because("REST-DTOs sind Transport-Objekte der REST-Schicht (Blueprint §3.4)")
                .check(CLASSES);
    }

    @Test
    void jpa_entities_nur_im_persistence_adapter() {
        noClasses()
                .that().resideOutsideOfPackage("..adapter.out.persistence..")
                .should().dependOnClassesThat().areAnnotatedWith(jakarta.persistence.Entity.class)
                .because("JPA-Entities leben ausschliesslich im Persistence-Adapter (Blueprint §4)")
                .check(CLASSES);
    }

    @Test
    void domaenenmodelle_sind_records_oder_enums() {
        classes()
                .that().resideInAPackage("..domain.model..")
                .should().beRecords()
                .orShould().beEnums()
                .because("Domänen-Modelle sind reine records (Werte) bzw. enums (Blueprint §3.2/§4)")
                .check(CLASSES);
    }

    @Test
    void ports_sind_interfaces() {
        classes()
                .that().resideInAPackage("..domain.port..")
                .and().areTopLevelClasses()                       // verschachtelte Wert-records (Reading/State) sind ok
                .and().areNotAssignableTo(RuntimeException.class) // Fachfehler (NotFound/Unavailable) sind Exceptions
                .should().beInterfaces()
                .because("Ports sind Interfaces; nur Fach-Exceptions dürfen Klassen sein (Blueprint §3.3)")
                .check(CLASSES);
    }

    @Test
    void keine_field_injection() {
        noFields()
                .should().beAnnotatedWith(jakarta.inject.Inject.class)
                .because("ausschliesslich Konstruktor-Injection (Blueprint §4)")
                .check(CLASSES);
    }

    @Test
    void application_services_sind_application_scoped() {
        classes()
                .that().resideInAPackage("..application.service..")
                .and().areNotNestedClasses()
                .should().beAnnotatedWith(jakarta.enterprise.context.ApplicationScoped.class)
                .because("Application Services sind CDI-Beans mit @ApplicationScoped (Blueprint §4)")
                .check(CLASSES);
    }
}
