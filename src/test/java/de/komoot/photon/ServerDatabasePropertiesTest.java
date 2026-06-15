package de.komoot.photon;

import de.komoot.photon.nominatim.model.NameNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ServerDatabasePropertiesTest extends ESBaseTester {

    @Test
    void testSaveAndLoadFromDatabase(@TempDir Path dataDirectory) throws IOException {
        setUpES(dataDirectory);

        final Date now = new Date();

        DatabaseProperties prop = new DatabaseProperties();
        prop.setLanguages(Set.of("en", "de", "fr"));
        prop.setImportDate(now);
        prop.setSupportGeometries(true);

        getServer().saveToDatabase(prop);

        prop = getServer().loadFromDatabase();

        assertThat(prop.getLanguages()).containsExactlyInAnyOrder("en", "de", "fr");
        assertThat(prop.getImportDate()).hasSameTimeAs(now);
        assertThat(prop.getSupportGeometries()).isTrue();
    }

    /**
     * name-prefixes персистятся в DB и восстанавливаются при load — это делает
     * update-путь самодостаточным (см. App#setupNominatimUpdater).
     */
    @Test
    void testNamePrefixesRoundTrip(@TempDir Path dataDirectory) throws IOException {
        setUpES(dataDirectory);

        DatabaseProperties prop = new DatabaseProperties();
        prop.setLanguages(Set.of("en", "ru"));
        prop.setNameNormalizer(new NameNormalizer(Set.of("озеро", "lake")));

        getServer().saveToDatabase(prop);
        DatabaseProperties loaded = getServer().loadFromDatabase();

        assertThat(loaded.getNameNormalizer().isEnabled()).isTrue();
        assertThat(loaded.getNameNormalizer().getPrefixes())
                .containsExactlyInAnyOrder("озеро", "lake");
        // Поведение сохраняется после round-trip
        assertThat(loaded.getNameNormalizer().stripOne("озеро Байкал")).isEqualTo("Байкал");
    }

    /**
     * Без нормализатора round-trip оставляет префиксы пустыми (disabled).
     */
    @Test
    void testNamePrefixesEmptyWhenNotSet(@TempDir Path dataDirectory) throws IOException {
        setUpES(dataDirectory);

        DatabaseProperties prop = new DatabaseProperties();
        prop.setLanguages(Set.of("en"));

        getServer().saveToDatabase(prop);
        DatabaseProperties loaded = getServer().loadFromDatabase();

        assertThat(loaded.getNameNormalizer().isEnabled()).isFalse();
    }
}