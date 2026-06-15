package de.komoot.photon.nominatim.model;

import de.komoot.photon.PhotonDoc;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Срезает type-prefix из имени места для поиска («озеро Байкал» → «Байкал»).
 *
 * <p>Применяется при импорте к {@link PhotonDoc#getName()} (для поиска); оригинал
 * сохраняется в {@link PhotonDoc#getDisplayName()} для отдачи в API. См. план
 * photon-server/docs/plans/gentle-shimmying-pillow.md.
 *
 * <p>Алгоритм {@link #stripOne(String)}: снять внешние кавычки, затем case-insensitive
 * матч prefix из списка с обязательной границей после (пробел или кавычка), срезать
 * prefix + разделитель + одну закрывающую кавычку. Guard: пустой результат → оригинал.
 */
@NullMarked
public class NameNormalizer {
    private static final char[] QUOTES = {'«', '»', '“', '”', '„', '‘', '’'};

    private final List<String> prefixes; // lowercased, trimmed, отсортированы по убыванию длины
    private final boolean enabled;

    public NameNormalizer(Set<String> rawPrefixes) {
        List<String> list = new ArrayList<>();
        for (var p : rawPrefixes) {
            if (p == null) continue;
            var s = p.toLowerCase().strip();
            if (!s.isEmpty()) list.add(s);
        }
        list.sort(Comparator.comparingInt(String::length).reversed());
        this.prefixes = list;
        this.enabled = !list.isEmpty();
    }

    public static NameNormalizer empty() {
        return new NameNormalizer(Set.of());
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Возвращает нормализованные префиксы (lowercased, trimmed), по которым построен
     * нормализатор. Используется для сериализации/десериализации конфигурации в
     * {@link de.komoot.photon.DatabaseProperties} (поле {@code name_prefixes}).
     */
    public List<String> getPrefixes() {
        return List.copyOf(prefixes);
    }

    /**
     * Срезает type-prefix из значения имени. Возвращает оригинал, если результат пуст
     * или ничего не сматчилось.
     */
    public String stripOne(@Nullable String value) {
        if (value == null) return null;
        String current = value.strip();
        if (current.isEmpty() || !enabled) return value;
        // До 2 итераций — на случай вложенных кавычек («Природный парк «Ергаки»»).
        for (int iter = 0; iter < 2; iter++) {
            String next = stripOnce(current);
            if (next.equals(current)) break;
            current = next;
        }
        return current;
    }

    private String stripOnce(String s) {
        String unquoted = stripOuterQuotes(s);
        for (var p : prefixes) {
            if (unquoted.length() <= p.length()) continue;
            if (unquoted.regionMatches(true, 0, p, 0, p.length())) {
                char after = unquoted.charAt(p.length());
                if (after == ' ' || isQuote(after)) {
                    String rest = unquoted.substring(p.length() + 1); // prefix + разделитель
                    rest = stripOuterQuotes(rest).strip();
                    if (!rest.isEmpty()) return rest;
                }
            }
        }
        return unquoted;
    }

    private static String stripOuterQuotes(String s) {
        String cur = s;
        while (cur.length() >= 2 && isQuote(cur.charAt(0)) && isQuote(cur.charAt(cur.length() - 1))) {
            cur = cur.substring(1, cur.length() - 1).strip();
            if (cur.length() < 2) break;
        }
        return cur;
    }

    private static boolean isQuote(char c) {
        for (var q : QUOTES) if (q == c) return true;
        return false;
    }

    /**
     * Возвращает НОВЫЙ {@link NameMap} со срезанными значениями. Вход не мутируется.
     * Если нормализатор отключён или вход пуст — возвращает вход как есть.
     */
    public NameMap normalize(NameMap input) {
        if (!enabled || input == null || input.isEmpty()) return input;
        NameMap out = new NameMap();
        for (var e : input.entrySet()) {
            out.put(e.getKey(), stripOne(e.getValue()));
        }
        return out;
    }

    /**
     * Сохраняет текущее name в {@link PhotonDoc#getDisplayName()}, затем заменяет
     * {@link PhotonDoc#getName()} на нормализованное. No-op, если нормализатор отключён.
     */
    public void applyTo(@Nullable PhotonDoc doc) {
        if (!enabled || doc == null) return;
        var full = new NameMap();
        full.putAll(doc.getName());
        doc.displayName(full);
        doc.names(normalize(doc.getName()));
    }
}
