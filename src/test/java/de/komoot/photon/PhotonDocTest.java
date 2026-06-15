package de.komoot.photon;

import de.komoot.photon.nominatim.model.AddressType;
import de.komoot.photon.nominatim.model.NameMap;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class PhotonDocTest {

    private PhotonDoc simplePhotonDoc() {
        return new PhotonDoc("1", "W", 2, "highway", "residential").houseNumber("4");
    }

    @Test
    void testCompleteAddressOverwritesStreet() {
        PhotonDoc doc = simplePhotonDoc();
        
        doc.setAddressPartIfNew(AddressType.STREET, Map.of("name", "parent place street"));
        doc.addAddresses(Map.of("street", "test street"), Set.of("de"));

        assertThat(doc.getAddressParts().get(AddressType.STREET))
                .containsEntry("default", "test street");
    }

    @Test
    void testCompleteAddressCreatesStreetIfNonExistantBefore() {
        PhotonDoc doc = simplePhotonDoc();

        doc.addAddresses(Map.of("street", "test street"), Set.of("de"));

        assertThat(doc.getAddressParts().get(AddressType.STREET))
                .containsEntry("default", "test street");

    }

    @Test
    void testAddCountryCode() {
        PhotonDoc doc = new PhotonDoc("1", "W", 2, "highway", "residential").countryCode("de");

        assertThat(doc.getCountryCode())
                .isEqualTo("DE");
    }

    @Test
    void testCopyConstructorCopiesDisplayName() {
        var display = new NameMap();
        display.put("default", "озеро Байкал");

        PhotonDoc doc = new PhotonDoc("1", "W", 2, "water", "lake");
        doc.displayName(display);

        PhotonDoc copy = new PhotonDoc(doc);
        assertThat(copy.getDisplayName()).isEqualTo(display);
        assertThat(copy.getDisplayName().get("default")).isEqualTo("озеро Байкал");
    }

    @Test
    void testDisplayNameSetterGetter() {
        PhotonDoc doc = new PhotonDoc();
        var display = NameMap.makeForPlace(Map.of("name", "озеро Байкал"), Set.of());
        doc.displayName(display);
        assertThat(doc.getDisplayName()).isSameAs(display);
    }

}
