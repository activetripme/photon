package de.komoot.photon.opensearch;

import java.util.HashMap;
import java.util.Map;

/**
 * Approximate transliteration of Cyrillic and Greek characters to Latin.
 *
 * Used at query time so that a name written in one script can be matched while
 * searching in another: «Пилион», «Πήλιο» and «Pilion» all map to the same
 * Latin form. The mapping is deliberately approximate — the same function is
 * applied to the query and to the document names, so only consistency matters,
 * not linguistic accuracy.
 */
public final class Translit {

    private Translit() {
    }

    private static final Map<Character, String> CYRILLIC = new HashMap<>();
    private static final Map<Character, String> GREEK = new HashMap<>();

    static {
        String cyr = "абвгдеёжзийклмнопрстуфхцчшщъыьэюяііїєґ";
        String[] cyrLat = {"a", "b", "v", "g", "d", "e", "e", "zh", "z", "i", "y",
                "k", "l", "m", "n", "o", "p", "r", "s", "t", "u", "f", "kh", "ts",
                "ch", "sh", "shch", "", "y", "", "e", "yu", "ya", "i", "i", "yi", "ie", "g"};
        for (var i = 0; i < cyr.length(); i++) {
            CYRILLIC.put(cyr.charAt(i), cyrLat[i]);
        }

        String gre = "αβγδεζηθικλμνξοπρσςτυφχψωάέήίόύώϊϋΐΰ";
        String[] greLat = {"a", "v", "g", "d", "e", "z", "i", "th", "i", "k", "l",
                "m", "n", "x", "o", "p", "r", "s", "s", "t", "y", "f", "ch", "ps",
                "o", "a", "e", "i", "i", "o", "y", "o", "i", "y", "i", "y"};
        for (var i = 0; i < gre.length(); i++) {
            GREEK.put(gre.charAt(i), greLat[i]);
        }
    }

    /**
     * Maps all Cyrillic and Greek characters to their Latin counterparts,
     * Latin characters and everything else is passed through unchanged.
     */
    public static String transliterate(String in) {
        var needsWork = false;
        for (var i = 0; i < in.length() && !needsWork; i++) {
            var c = in.charAt(i);
            needsWork = CYRILLIC.containsKey(c) || GREEK.containsKey(c);
        }
        if (!needsWork) {
            return in;
        }

        var out = new StringBuilder(in.length());
        for (var i = 0; i < in.length(); i++) {
            var original = in.charAt(i);
            var lower = Character.toLowerCase(original);
            var replacement = CYRILLIC.containsKey(lower) ? CYRILLIC.get(lower)
                    : GREEK.containsKey(lower) ? GREEK.get(lower) : null;
            if (replacement != null) {
                out.append(replacement);
            } else {
                out.append(original);
            }
        }
        return out.toString();
    }
}
