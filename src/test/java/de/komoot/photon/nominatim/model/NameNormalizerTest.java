package de.komoot.photon.nominatim.model;

import de.komoot.photon.PhotonDoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class NameNormalizerTest {

    private static final Set<String> PREFIXES = Set.of(
            "озеро", "река", "заказник", "природный парк", "парк", "lake", "mount");

    static Stream<Arguments> stripCases() {
        return Stream.of(
                arguments("озеро Байкал", "Байкал"),
                arguments("Озеро Байкал", "Байкал"),          // case-insensitive prefix
                arguments("ОЗЕРО Байкал", "Байкал"),
                arguments("заказник «Гряда Вярямянселькя»", "Гряда Вярямянселькя"),
                arguments("Природный парк «Ергаки»", "Ергаки"),  // multi-word prefix
                arguments("Природный парк Ергаки", "Ергаки"),    // без кавычек
                arguments("«Ергаки»", "Ергаки"),                  // только кавычки, без prefix
                arguments("«Природный парк «Ергаки»»", "Ергаки"), // вложенные кавычки
                arguments("Москва", "Москва"),                    // без prefix
                arguments("озеро", "озеро"),                      // prefix-only → guard (не пусто)
                arguments("Озероведение", "Озероведение"),        // prefix без разделителя → без match
                arguments("Lake Baikal", "Baikal"),
                arguments("Mount Everest", "Everest"),
                arguments("  озеро  Байкал  ", "Байкал"),         // толерантность к пробелам
                arguments("парк Горького", "Горького"));
    }

    @ParameterizedTest
    @MethodSource("stripCases")
    void stripOne(String input, String expected) {
        var n = new NameNormalizer(PREFIXES);
        assertThat(n.stripOne(input)).isEqualTo(expected);
    }

    @Test
    void emptyNormalizerKeepsEverything() {
        var n = NameNormalizer.empty();
        assertThat(n.isEnabled()).isFalse();
        assertThat(n.stripOne("озеро Байкал")).isEqualTo("озеро Байкал");
    }

    @Test
    void normalizeReturnsNewMapAndDoesNotMutateInput() {
        var n = new NameNormalizer(Set.of("озеро", "lake"));
        var input = NameMap.makeForPlace(
                Map.of("name", "озеро Байкал", "name:en", "Lake Baikal", "alt_name", "озеро Святое"),
                Set.of("en"));
        var snapshot = Map.of(
                "default", input.get("default"),
                "en", input.get("en"),
                "alt", input.get("alt"));

        var out = n.normalize(input);

        assertThat(out).isNotSameAs(input);
        assertThat(out).contains(
                Map.entry("default", "Байкал"),
                Map.entry("en", "Baikal"),
                Map.entry("alt", "Святое"));
        // input не мутирован
        assertThat(input.get("default")).isEqualTo(snapshot.get("default"));
        assertThat(input.get("en")).isEqualTo(snapshot.get("en"));
        assertThat(input.get("alt")).isEqualTo(snapshot.get("alt"));
    }

    @Test
    void normalizeNoOpWhenDisabled() {
        var n = NameNormalizer.empty();
        var input = NameMap.makeForPlace(Map.of("name", "озеро Байкал"), Set.of());
        var out = n.normalize(input);
        assertThat(out).isSameAs(input);
    }

    @Test
    void applyToSplitsNameAndDisplayName() {
        var n = new NameNormalizer(Set.of("озеро", "lake"));
        var doc = new PhotonDoc();
        doc.names(NameMap.makeForPlace(
                Map.of("name", "озеро Байкал", "name:en", "Lake Baikal"), Set.of("en")));

        n.applyTo(doc);

        // name — чистое (для поиска)
        assertThat(doc.getName().get("default")).isEqualTo("Байкал");
        assertThat(doc.getName().get("en")).isEqualTo("Baikal");
        // displayName — оригинал (для выдачи)
        assertThat(doc.getDisplayName().get("default")).isEqualTo("озеро Байкал");
        assertThat(doc.getDisplayName().get("en")).isEqualTo("Lake Baikal");
    }

    @Test
    void applyToNoOpWhenDisabled() {
        var n = NameNormalizer.empty();
        var doc = new PhotonDoc();
        doc.names(NameMap.makeForPlace(Map.of("name", "озеро Байкал"), Set.of()));

        n.applyTo(doc);

        assertThat(doc.getName().get("default")).isEqualTo("озеро Байкал");
        // displayName остаётся пустым (no-op не заполняет)
        assertThat(doc.getDisplayName()).isEmpty();
    }
}
