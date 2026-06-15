package de.komoot.photon.config;

import com.beust.jcommander.Parameter;
import de.komoot.photon.ConfigExtraTags;
import de.komoot.photon.ConfigNamePrefixes;
import de.komoot.photon.DatabaseProperties;
import de.komoot.photon.nominatim.model.NameNormalizer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NullMarked
public class ImportFilterConfig {
    public static final String GROUP = "Data filtering options";

    @Parameter(names = "-languages", category = GROUP, placeholder = "LANG,...", description = """
            Comma-separated list of languages for which names will be extracted from the source
            """)
    private List<String> languages = List.of("en", "de", "fr", "it");

    @Parameter(names = "-country-codes", category = GROUP, placeholder = "CC,...", description = """
            Restrict data from which country to use; comma-separated list of two-letter country codes of countries to use
            """)
    private List<String> countryCodes = new ArrayList<>();

    @Parameter(names = "-extra-tags", category = GROUP, placeholder = "[ALL|tag,...]", description = """
            Additional information to extract for each place; when unset only necessary address information
            will be used; the special term 'ALL' means to use all available information; a comma-separated list of
            tag keys restricts the usage to the given tags
            """)
    @Nullable private List<String> extraTags = null;

    @Parameter(names = {"-full-geometries", "-import-geometry-column"}, category = GROUP, description = """
            Add the full geometry for each place if available instead of just recording the centroid;
            WARNING: this will increase the Photon database size quite a bit
            """)
    private boolean importGeometryColumn = false;

    @Parameter(names = "-reverse-only", category = GROUP, description = """
            Set up database for reverse geocoding only""")
    private boolean reverseOnly = false;

    @Parameter(names = "-name-prefixes-file", category = GROUP, placeholder = "FILE", description = """
            JSON file with type prefixes to strip from place names during import
            (e.g. "озеро Байкал" -> "Байкал" for search; full name kept for display).
            Format: {"name_prefixes": {"ru": [...], "en": [...], "default": [...]}}
            """)
    @Nullable private String namePrefixesFile = null;

    // Кеш нормализатора: getNameNormalizer() иначе перечитывает и парсит JSON-файл
    // на каждый вызов (а вызывается на нескольких путях импорта).
    @Nullable private NameNormalizer cachedNameNormalizer = null;

    public Set<String> getLanguages() {
        return new HashSet<>(languages);
    }

    public String[] getCountryCodes() {
        return this.countryCodes.toArray(String[]::new);
    }

    public ConfigExtraTags getExtraTags() {
        return new ConfigExtraTags(extraTags == null? List.of() : extraTags);
    }

    public boolean isExtraTagsSet() { return this.extraTags == null; }

    public boolean getImportGeometryColumn() {
        return importGeometryColumn;
    }

    @Nullable
    public String getNamePrefixesFile() {
        return namePrefixesFile;
    }

    /** Резолвит NameNormalizer из -name-prefixes-file + выбранных языков (с кешем). */
    public NameNormalizer getNameNormalizer() {
        if (cachedNameNormalizer == null) {
            var cfg = ConfigNamePrefixes.loadFromFile(namePrefixesFile);
            cachedNameNormalizer = new NameNormalizer(cfg.forLanguages(getLanguages()));
        }
        return cachedNameNormalizer;
    }

    public DatabaseProperties getDatabaseProperties() {
        final var dbProps = new DatabaseProperties();
        if (!languages.isEmpty()) {
            dbProps.setLanguages(getLanguages());
        }
        dbProps.setSupportGeometries(importGeometryColumn);
        dbProps.setReverseOnly(reverseOnly);
        dbProps.setNameNormalizer(getNameNormalizer());

        if (extraTags != null) {
            dbProps.setExtraTags(extraTags);
        }

        return dbProps;
    }
}
