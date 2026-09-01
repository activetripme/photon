package de.komoot.photon.searcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class StringSimilarityTest {

    @Test
    void classicJaroWinklerReferencePairs() {
        // Reference values from the literature (prefix boost 0.1, max prefix 4).
        assertEquals(0.9611, StringSimilarity.jaroWinkler("martha", "marhta"), 0.001);
        assertEquals(0.8400, StringSimilarity.jaroWinkler("dwayne", "duane"), 0.001);
        assertEquals(0.8133, StringSimilarity.jaroWinkler("dixon", "dicksonx"), 0.001);
        assertEquals(1.0, StringSimilarity.jaroWinkler("abc", "abc"), 1e-9);
        assertEquals(0.0, StringSimilarity.jaroWinkler("abc", "xyz"), 1e-9);
    }

    @Test
    void toponymVariantsReachTheThreshold() {
        // Same name, different spellings (after normalization+translit):
        // accepted via the edit-distance path or the Jaro-Winkler path.
        assertTrue(StringSimilarity.isNameVariant("pilion", "pelion"));  // DL = 1
        assertTrue(StringSimilarity.isNameVariant("pilio", "pilion"));   // DL = 1
        assertTrue(StringSimilarity.isNameVariant("baykal", "bakal"));   // DL = 1
        assertTrue(StringSimilarity.isNameVariant("petrozavodsk", "petrozavodskaya")); // JW ≈ 0.94
    }

    @Test
    void differentWordsStayBelowTheThreshold() {
        // Different mountains / different words: must not be credited as variants.
        assertFalse(StringSimilarity.isNameVariant("pirin", "pilon"));   // DL = 2 at length 5, JW < threshold
        // Prefix-boost trap on short words: «Мойка»/«москва» ≈ 0.858, «Московский»/«москва» ≈ 0.867.
        assertFalse(StringSimilarity.isNameVariant("moyka", "moskva"));  // DL = 2 at length 5
        assertFalse(StringSimilarity.isNameVariant("moskovskiy", "moskva"));
        assertFalse(StringSimilarity.isNameVariant("чернечья гора", "тарасова"));
        // Short strings are never variants.
        assertFalse(StringSimilarity.isNameVariant("ab", "ba"));
    }
}
