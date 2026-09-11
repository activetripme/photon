package de.komoot.photon.opensearch;

import de.komoot.photon.ESBaseTester;
import de.komoot.photon.Importer;
import de.komoot.photon.PhotonDoc;
import de.komoot.photon.nominatim.model.AddressType;
import de.komoot.photon.nominatim.model.NameNormalizer;
import de.komoot.photon.query.SimpleSearchRequest;
import de.komoot.photon.searcher.PhotonResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Query-side type-prefix stripping must mirror the import-side stripping:
 * a viewpoint named «скала Шаманка» is indexed with the search name «Шаманка»,
 * so the full query «скала Шаманка» has to be stripped the same way before
 * matching, otherwise the extra type token breaks the multi-token AND clauses.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NamePrefixStripTest extends ESBaseTester {

    private static final long ROCK_OSM_ID = 5971168485L;

    @BeforeAll
    void setUp(@TempDir Path dataDirectory) throws Exception {
        getProperties().setLanguages(Set.of("ru", "en"));
        getProperties().setNameNormalizer(new NameNormalizer(Set.of("скала", "озеро")));
        setUpES(dataDirectory);

        Importer importer = makeImporter();
        importer.add(List.of(new PhotonDoc()
                .placeId("5971168485").osmType("N").osmId(5971168485L)
                .tagKey("tourism").tagValue("viewpoint")
                .centroid(makePoint(107.3416275, 53.2039693))
                .names(makeDocNames("name", "скала Шаманка", "name:en", "Shaman Rock"))
                .importance(0.5)
                .addressType(AddressType.OTHER)));
        importer.finish();
        refresh();
    }

    private List<Long> searchIds(String query) {
        var request = new SimpleSearchRequest();
        request.setQuery(query);
        request.setLanguage("ru");
        var handler = getServer().createSearchHandler(1, new NameNormalizer(Set.of("скала", "озеро")));
        return handler.search(request).toList().stream()
                .map(r -> ((Number) r.get("osm_id")).longValue())
                .toList();
    }

    @Test
    void fullQueryWithPrefixFindsViewpoint() {
        var ids = searchIds("скала Шаманка");
        assertTrue(ids.contains(ROCK_OSM_ID),
                "stripped query «скала Шаманка» must find the viewpoint, got " + ids);
    }

    @Test
    void bareQueryFindsViewpoint() {
        var ids = searchIds("шаманка");
        assertTrue(ids.contains(ROCK_OSM_ID),
                "bare query «шаманка» must find the viewpoint, got " + ids);
    }

    @Test
    void prefixOnlyQueryDoesNotShadowRealResults() {
        // «озеро» + нормализатор: стриппинг не должен ломать запросы-префиксы других мест
        var ids = searchIds("Шаманка");
        assertTrue(ids.contains(ROCK_OSM_ID), "query «Шаманка» must find the viewpoint, got " + ids);
    }

    @AfterAll
    @Override
    public void tearDown() {
        super.tearDown();
    }
}
