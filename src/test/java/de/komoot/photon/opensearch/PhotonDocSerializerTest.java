package de.komoot.photon.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import de.komoot.photon.DatabaseProperties;
import de.komoot.photon.PhotonDoc;
import de.komoot.photon.nominatim.model.NameMap;
import de.komoot.photon.nominatim.model.NameNormalizer;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты контракта {@link PhotonDocSerializer} для срезания type-prefix:
 * {@code _source.name} хранит полное имя для отдачи в API (properties.name),
 * а {@code collector.name} — очищенный токен для поиска.
 */
class PhotonDocSerializerTest {

    private JsonNode serialize(PhotonDoc doc) throws Exception {
        var mapper = new ObjectMapper();
        var module = new SimpleModule();
        module.addSerializer(PhotonDoc.class, new PhotonDocSerializer(new DatabaseProperties()));
        mapper.registerModule(module);

        StringWriter sw = new StringWriter();
        mapper.writeValue(sw, doc);
        return mapper.readTree(sw.toString());
    }

    @Test
    void nameFieldHoldsFullNameForDisplay() throws Exception {
        PhotonDoc doc = new PhotonDoc("1", "W", 2, "water", "lake");
        doc.names(NameMap.makeForPlace(Map.of("name", "озеро Байкал"), Set.of()));
        new NameNormalizer(Set.of("озеро")).applyTo(doc);

        JsonNode root = serialize(doc);

        // properties.name = полное имя (для выдачи)
        assertThat(root.path("name").path("default").asText()).isEqualTo("озеро Байкал");
        // collector.name — очищенный токен (для поиска): «Байкал», без «озеро»
        String collectorName = root.path("collector").path("name").asText();
        assertThat(collectorName).contains("Байкал");
        assertThat(collectorName).doesNotContain("озеро");
        // collector.field.name — массив из чистых значений
        assertThat(root.path("collector").path("field").path("name").toString()).contains("Байкал");
        assertThat(root.path("collector").path("field").path("name").toString()).doesNotContain("озеро");
    }

    @Test
    void displayNameFallsBackToNameWhenNormalizerDisabled() throws Exception {
        // Регрессия: без нормализатора _source.name = name, ничего не режется.
        PhotonDoc doc = new PhotonDoc("1", "W", 2, "water", "lake");
        doc.names(NameMap.makeForPlace(Map.of("name", "озеро Байкал"), Set.of()));

        JsonNode root = serialize(doc);

        assertThat(root.path("name").path("default").asText()).isEqualTo("озеро Байкал");
        assertThat(root.path("collector").path("name").asText()).contains("озеро");
    }
}
