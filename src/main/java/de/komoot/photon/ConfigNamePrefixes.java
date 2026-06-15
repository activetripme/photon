package de.komoot.photon;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Конфиг type-prefixes для {@code NameNormalizer}: JSON-файл вида
 * {@code {"name_prefixes": {"ru": ["озеро","река",...], "en": ["lake","mount",...], "default": [...]}}}.
 *
 * <p>Загружается через CLI-флаг {@code -name-prefixes-file} (см. {@code ImportFilterConfig}).
 * Метод {@link #forLanguages(Set)} возвращает merge prefixes для выбранных языков + bucket
 * {@code default}.
 */
@NullMarked
public class ConfigNamePrefixes {
    private Map<String, Set<String>> prefixesByLang = Map.of();

    public Map<String, Set<String>> getPrefixesByLang() {
        return prefixesByLang;
    }

    @JsonProperty("name_prefixes")
    @SuppressWarnings("unused")
    public void setPrefixesByLang(@Nullable Map<String, Set<String>> prefixes) {
        if (prefixes == null) {
            this.prefixesByLang = Map.of();
            return;
        }
        Map<String, Set<String>> copy = new HashMap<>();
        prefixes.forEach((k, v) -> copy.put(k, v == null ? Set.of() : new HashSet<>(v)));
        this.prefixesByLang = Map.copyOf(copy);
    }

    /** Merge prefixes для выбранных языков + bucket {@code default}. */
    public Set<String> forLanguages(Set<String> languages) {
        Set<String> out = new HashSet<>();
        Set<String> def = prefixesByLang.get("default");
        if (def != null) out.addAll(def);
        for (var lang : languages) {
            Set<String> set = prefixesByLang.get(lang);
            if (set != null) out.addAll(set);
        }
        return out;
    }

    public static ConfigNamePrefixes loadFromFile(@Nullable String path) {
        if (path == null) return new ConfigNamePrefixes();
        try {
            return new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(new File(path), ConfigNamePrefixes.class);
        } catch (IOException e) {
            throw new UsageException("Cannot read name prefixes file '" + path + "': " + e.getMessage());
        }
    }
}
