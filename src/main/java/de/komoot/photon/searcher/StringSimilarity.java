package de.komoot.photon.searcher;

import org.jspecify.annotations.NullMarked;

/**
 * String similarity for toponym name matching.
 *
 * Used by the reranker to credit results whose name is a close variant of the
 * query («pelio»/«Pilion», «Байкал»/«Бакал») but neither an exact match nor a
 * prefix, so they do not fall through to the importance-only fallback score.
 * Works on code points, correct for Cyrillic and Greek.
 */
@NullMarked
public final class StringSimilarity {

    /**
     * Jaro-Winkler similarity above which two names are treated as variants of
     * each other. Below it the similarity of short strings is too noisy to be
     * meaningful («Мойка»/«москва» scores ≈ 0.86 after normalization,
     * «Московский»/«москва» ≈ 0.87 — both are different words).
     */
    public static final double VARIANT_THRESHOLD = 0.87;

    private StringSimilarity() {
    }

    /**
     * Whether two (already normalized) names are variants of the same toponym:
     * Damerau-Levenshtein distance within the ES-like AUTO edit budget
     * (&lt;3 chars → 0, 3–5 → 1, ≥6 → 2 edits, budget taken from the
     * <em>longer</em> name) or Jaro-Winkler similarity of at least
     * {@link #VARIANT_THRESHOLD}. Requires at least 4 common-length
     * characters, shorter strings are never variants.
     *
     * <p>The budget is derived from the longer of the two names because
     * transliteration legitimately changes length: «Πήλιο» → "pilio" drops the
     * trailing consonant of "pelion", digraph mappings (th, ch, kh, shch) add
     * characters. Measuring against the shorter name would reject exactly the
     * cross-script pairs this check exists for.
     */
    public static boolean isNameVariant(String a, String b) {
        var ra = a.codePoints().toArray();
        var rb = b.codePoints().toArray();
        var shorter = Math.min(ra.length, rb.length);
        var longer = Math.max(ra.length, rb.length);
        if (shorter < 4) {
            return false;
        }
        if (damerauLevenshtein(ra, rb) <= autoFuzziness(longer)) {
            return true;
        }
        return jaroWinkler(a, b) >= VARIANT_THRESHOLD;
    }

    /**
     * Maximum edit count, mirroring Elasticsearch/OpenSearch
     * {@code fuzziness=AUTO} (LOW=3, HIGH=5) used for retrieval.
     */
    private static int autoFuzziness(int length) {
        if (length < 3) {
            return 0;
        }
        return length <= 5 ? 1 : 2;
    }

    private static int damerauLevenshtein(int[] a, int[] b) {
        var la = a.length;
        var lb = b.length;
        if (la == 0) {
            return lb;
        }
        if (lb == 0) {
            return la;
        }
        var d = new int[la + 1][lb + 1];
        for (var i = 0; i <= la; i++) {
            d[i][0] = i;
        }
        for (var j = 0; j <= lb; j++) {
            d[0][j] = j;
        }
        for (var i = 1; i <= la; i++) {
            for (var j = 1; j <= lb; j++) {
                var cost = a[i - 1] == b[j - 1] ? 0 : 1;
                var v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = Math.min(v, d[i - 2][j - 2] + 1);
                }
                d[i][j] = v;
            }
        }
        return d[la][lb];
    }

    public static double jaroWinkler(String a, String b) {
        var ra = a.codePoints().toArray();
        var rb = b.codePoints().toArray();
        var j = jaro(ra, rb);
        if (j == 0.0) {
            return 0.0;
        }
        var prefix = 0;
        while (prefix < ra.length && prefix < rb.length && prefix < 4
                && ra[prefix] == rb[prefix]) {
            prefix++;
        }
        return j + 0.1 * prefix * (1.0 - j);
    }

    private static double jaro(int[] a, int[] b) {
        var la = a.length;
        var lb = b.length;
        if (la == 0 && lb == 0) {
            return 1.0;
        }
        if (la == 0 || lb == 0) {
            return 0.0;
        }
        var matchDistance = Math.max(la, lb) / 2 - 1;
        if (matchDistance < 0) {
            matchDistance = 0;
        }
        var aMatched = new boolean[la];
        var bMatched = new boolean[lb];
        var matches = 0;
        for (var i = 0; i < la; i++) {
            var lo = Math.max(0, i - matchDistance);
            var hi = Math.min(i + matchDistance + 1, lb);
            for (var j = lo; j < hi; j++) {
                if (!bMatched[j] && a[i] == b[j]) {
                    aMatched[i] = true;
                    bMatched[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) {
            return 0.0;
        }
        var transpositions = 0;
        var k = 0;
        for (var i = 0; i < la; i++) {
            if (!aMatched[i]) {
                continue;
            }
            while (!bMatched[k]) {
                k++;
            }
            if (a[i] != b[k]) {
                transpositions++;
            }
            k++;
        }
        var m = (double) matches;
        var t = transpositions / 2.0;
        return (m / la + m / lb + (m - t) / m) / 3.0;
    }
}
