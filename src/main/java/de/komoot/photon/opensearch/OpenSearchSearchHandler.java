package de.komoot.photon.opensearch;

import de.komoot.photon.nominatim.model.NameNormalizer;
import de.komoot.photon.query.SimpleSearchRequest;
import de.komoot.photon.searcher.PhotonResult;
import de.komoot.photon.searcher.QueryReranker;
import de.komoot.photon.searcher.SearchHandler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.SearchType;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@NullMarked
public class OpenSearchSearchHandler implements SearchHandler<SimpleSearchRequest> {
    private static final float IMPORTANCE_FACTOR = 30f;
    private static final double NEG_DECAY_FACTOR = Math.log(0.5);
    private final OpenSearchClient client;
    private final String queryTimeout;
    @Nullable
    private final NameNormalizer nameNormalizer;

    public OpenSearchSearchHandler(OpenSearchClient client, int queryTimeout) {
        this(client, queryTimeout, null);
    }

    public OpenSearchSearchHandler(OpenSearchClient client, int queryTimeout,
                                   @Nullable NameNormalizer nameNormalizer) {
        this.client = client;
        this.queryTimeout = queryTimeout + "s";
        this.nameNormalizer = nameNormalizer;
    }

    @Override
    public Stream<PhotonResult> search(SimpleSearchRequest request) {
        // Return more result candidates than results requested,
        // will be reranked and filtered later.
        final int extLimit = (int) Math.round(Math.max(6, request.getLimit()) * 1.5);

        // Both query variants always run and their results are merged, strict
        // hits first and duplicates of the same object dropped. The lenient
        // variant used to run only when the strict query returned NOTHING, so
        // any single-token match satisfied the strict query and masked
        // documents only the lenient query retrieves: «скала шаманка» matched
        // an unrelated doc named «Скала» (one token) and hid the viewpoint
        // «скала Шаманка» (both tokens). The reranker then orders the merged
        // candidates, exact-name matches still win.
        var strict = sendQuery(buildQuery(request, false), extLimit);
        SearchResponse<OpenSearchResult> lenient = null;
        if (!request.getSuggestAddresses()) {
            // Address suggestion is a special autocomplete mode, keep its
            // original retry-only-when-empty behaviour there.
            lenient = sendQuery(buildQuery(request, true), extLimit);
        } else {
            var total = strict.hits().total();
            if (total == null || total.value() == 0) {
                lenient = sendQuery(buildQuery(request, true), extLimit);
            }
        }

        Set<String> merged = new HashSet<>();
        var results = Stream.concat(
                        ResultScorer.hitsToResultStream(strict),
                        lenient == null
                                ? Stream.<OpenSearchResult>empty()
                                : ResultScorer.hitsToResultStream(lenient))
                .filter(r -> merged.add(objectKey(r)));

        var stream = results
                .peek(r -> r.adjustScoreByImportance(IMPORTANCE_FACTOR * request.getImportanceWeight()));

        if (request.hasLocationBias()) {
            double decay = NEG_DECAY_FACTOR / request.getDecayRadiusForBias();
            stream = stream.peek(r -> {
                assert request.getLocationForBias() != null;
                r.adjustScoreByLocationBias(
                        request.getLocationForBias(),
                        IMPORTANCE_FACTOR,
                        1 - request.getImportanceWeight(),
                        request.getRadiusForBias(),
                        decay
                );
            });
        }

        if (request.getQuery() != null) {
            stream = stream.peek(new QueryReranker(request.getQuery(), request.getLanguage(), request.getDefaultLanguage()));
        }

        return ResultScorer.adjustByNormalizedOpenSearchScore(stream)
                .sorted(Comparator.comparingDouble(PhotonResult::getScore).reversed());
    }

    @Override
    public String dumpQuery(SimpleSearchRequest simpleSearchRequest) {
        return "{}";
    }

    private Query buildQuery(SimpleSearchRequest request, boolean lenient) {
        // Mirror the import-time type-prefix stripping: indexed search names have
        // the prefix removed («скала Шаманка» is searchable as «Шаманка»), so the
        // query must be stripped the same way or the extra token breaks the
        // multi-token AND clauses.
        var requestQuery = request.getQuery();
        final var effectiveQuery = (requestQuery == null || nameNormalizer == null)
                ? requestQuery : nameNormalizer.stripOne(requestQuery);
        final var query = new SearchQueryBuilder(effectiveQuery, lenient, request.getSuggestAddresses());
        query.addCountryCodeFilter(request.getCountryCodes());
        query.addOsmTagFilter(request.getOsmTagFilters());
        query.addLayerFilter(request.getLayerFilters());
        query.addImportance(IMPORTANCE_FACTOR * request.getImportanceWeight());

        if (request.hasLocationBias()) {
            assert request.getLocationForBias() != null;
            query.addLocationBias(
                    request.getLocationForBias(),
                    IMPORTANCE_FACTOR * (1.0f - request.getImportanceWeight()),
                    request.getRadiusForBias(),
                    request.getDecayRadiusForBias());

        }

        query.includeCategories(request.getIncludeCategories());
        query.excludeCategories(request.getExcludeCategories());
        query.addBoundingBox(request.getBbox());

        return query.build();
    }

    private String objectKey(PhotonResult result) {
        // osm_id is stored as a number, osm_type/object_type as strings.
        return String.valueOf(result.get("osm_type")) + "/"
                + String.valueOf(result.get("osm_id")) + "/"
                + String.valueOf(result.get("object_type"));
    }

    private SearchResponse<OpenSearchResult> sendQuery(Query query, int limit) {
        try {
            return client.search(s -> s
                    .index(PhotonIndex.NAME)
                    .searchType(SearchType.QueryThenFetch)
                    .query(query)
                    .size(limit)
                    .timeout(queryTimeout), OpenSearchResult.class);
        } catch (IOException e) {
            throw new RuntimeException("IO error during search", e);
        }
    }
}
