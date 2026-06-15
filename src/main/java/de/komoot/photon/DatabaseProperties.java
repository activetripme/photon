package de.komoot.photon;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.komoot.photon.nominatim.model.NameNormalizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static de.komoot.photon.Server.DATABASE_VERSION;

/**
 * Class collecting database global properties.
 * <p>
 * This class is marshalled and unmarshalled into OS table properties using Jackson.
 */
@NullMarked
public class DatabaseProperties {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Set<String> DEFAULT_LANGUAGES = Set.of("en", "de", "fr", "it");

    private Set<String> languages = DEFAULT_LANGUAGES;
    @Nullable private Date importDate;
    private boolean supportStructuredQueries = true;
    private boolean supportGeometries = false;
    private boolean synonymsInstalled = false;
    private ConfigExtraTags extraTags = new ConfigExtraTags();
    private boolean reverseOnly = false;

    // Сериализуемые префиксы имён (см. NameNormalizer, ConfigNamePrefixes).
    // Пусто/null — нормализатор отключён. Сохраняются при импорте и восстанавливаются
    // при последующих запусках (в т.ч. update), так что update-путь самодостаточен
    // и не требует повторной передачи -name-prefixes-file.
    @Nullable private List<String> namePrefixes = null;

    // In-memory кеш нормализатора, построенный из namePrefixes. Не сериализуется.
    @JsonIgnore
    @Nullable private NameNormalizer nameNormalizer = null;

    @SuppressWarnings("unused")
    public void setDatabaseVersion(String version) {
        if (!DATABASE_VERSION.equals(version)) {
            LOGGER.error("Database has incompatible version '{}'. Expected: {}",
                    version, DATABASE_VERSION);
            throw new UsageException("Incompatible database.");
        }
    }

    @SuppressWarnings("unused")
    public String getDatabaseVersion() {
        return DATABASE_VERSION;
    }

    /**
     * Return the list of languages for which the database is configured.
     * If no list was set, then the default is returned.
     *
     * @return Set of supported languages.
     */
    public Set<String> getLanguages() {
        return languages;
    }

    /**
     * Replace the language list with the given list.
     *
     * @param languages Array of two-letter language codes.
     *
     * @return This object for function chaining.
     */
    public DatabaseProperties setLanguages(Set<String> languages) {
        this.languages = languages;
        return this;
    }

    @Nullable
    public Date getImportDate() {
        return this.importDate;
    }

    public void setImportDate(@Nullable Date importDate) {
        this.importDate = importDate;
    }

    public boolean getSupportGeometries() {
        return supportGeometries;
    }

    public void setSupportGeometries(boolean supportGeometries) {
        this.supportGeometries = supportGeometries;
    }

    // needed for backwards compatibility
    @SuppressWarnings("unused")
    public void setSupportStructuredQueries(boolean supportStructuredQueries) {
        this.supportStructuredQueries = supportStructuredQueries;
    }

    public void setExtraTags(List<String> extraTags) {
        this.extraTags = new ConfigExtraTags(extraTags);
    }

    @SuppressWarnings("unused")
    public List<String> getExtraTags() {
        return extraTags.asConfigParam();
    }

    public void setSynonymsInstalled(boolean synonymsInstalled) {
        this.synonymsInstalled = synonymsInstalled;
    }

    public boolean getSynonymsInstalled() {
        return synonymsInstalled;
    }

    public ConfigExtraTags configExtraTags() {
        return extraTags;
    }

    public void putConfigExtraTags(ConfigExtraTags extraTags) {
        this.extraTags = extraTags;
    }

    @Override
    public String toString() {
        return "DatabaseProperties{" +
                "languages=" + Arrays.toString(languages.toArray()) +
                ", importDate=" + importDate +
                ", supportStructuredQueries=" + supportStructuredQueries +
                ", supportGeometries=" + supportGeometries +
                ", synonymsInstalled=" + synonymsInstalled +
                ", extraTags=" + extraTags +
                '}';
    }

    public void setReverseOnly(boolean reverseOnly) {
        this.reverseOnly = reverseOnly;
    }

    public boolean getReverseOnly() {
        return reverseOnly;
    }

    /**
     * Префиксы имён для срезания type-prefix при поиске (см. {@link NameNormalizer}).
     * Сериализуются в OS table properties. {@code null}/пусто — нормализатор отключён.
     */
    @Nullable
    @SuppressWarnings("unused")
    public List<String> getNamePrefixes() {
        return namePrefixes;
    }

    @SuppressWarnings("unused")
    public void setNamePrefixes(@Nullable List<String> prefixes) {
        this.namePrefixes = (prefixes == null || prefixes.isEmpty()) ? null : List.copyOf(prefixes);
        this.nameNormalizer = null; // сброс кеша
    }

    /**
     * Возвращает нормализатор имён, лениво построенный из {@link #namePrefixes}.
     * Никогда не возвращает {@code null}.
     */
    public NameNormalizer getNameNormalizer() {
        NameNormalizer result = nameNormalizer;
        if (result == null) {
            result = (namePrefixes == null || namePrefixes.isEmpty())
                    ? NameNormalizer.empty()
                    : new NameNormalizer(Set.copyOf(namePrefixes));
            nameNormalizer = result;
        }
        return result;
    }

    public DatabaseProperties setNameNormalizer(NameNormalizer nameNormalizer) {
        this.nameNormalizer = nameNormalizer;
        this.namePrefixes = nameNormalizer.isEnabled() ? nameNormalizer.getPrefixes() : null;
        return this;
    }
}
