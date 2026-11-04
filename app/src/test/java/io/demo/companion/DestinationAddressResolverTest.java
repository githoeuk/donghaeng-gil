package io.demo.companion;
import java.util.Arrays;
import java.util.Collections;
public final class DestinationAddressResolverTest {
    private static int checks;
    private static DestinationAddressResolver.Place place(String id, String sub, String provider, String name, double lat, String address) {
        return new DestinationAddressResolver.Place(id, sub, provider, name, lat, 127.10195, address);
    }
    private static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    public static void main(String[] args) {
        DestinationAddressResolver.Place destination = place("poi", null, "", "shop", 37.37055, "분당구 궁내동");
        DestinationAddressResolver.Place detail = place("poi", "", null, "shop", 37.37058888, "경기도 성남시 분당구 대왕판교로 302");
        expect(DestinationAddressResolver.resolve(destination, Arrays.asList(detail)).endsWith("302"), "same POI detail accepted despite entrance coordinate offset");
        expect(DestinationAddressResolver.resolve(destination, Collections.emptyList()).equals(destination.address), "no recent data retains SDK address");
        expect(DestinationAddressResolver.resolve(destination, Arrays.asList(place("other", "", "", "shop", 37.37055, detail.address))).equals(destination.address), "other ID rejected");
        expect(DestinationAddressResolver.resolve(destination, Arrays.asList(place("poi", "other", "", "shop", 37.37055, detail.address))).equals(destination.address), "other sub ID rejected");
        expect(DestinationAddressResolver.resolve(destination, Arrays.asList(place("poi", "", "other", "shop", 37.37055, detail.address))).equals(destination.address), "other provider rejected");
        expect(DestinationAddressResolver.resolve(destination, Arrays.asList(place("poi", "", "", "other", 37.37055, detail.address))).equals(destination.address), "other branch name rejected");
        expect(DestinationAddressResolver.resolve(destination, Arrays.asList(place("poi", "", "", "shop", 37.5, detail.address))).equals(destination.address), "far coordinates rejected");
        expect(DestinationAddressResolver.resolve(destination, Arrays.asList(place("poi", "", "", "shop", Double.NaN, detail.address))).equals(destination.address), "invalid coordinates rejected");
        expect(DestinationAddressResolver.resolve(place("", "", "", "shop", 37.37055, "fallback"), Arrays.asList(detail)).equals("fallback"), "missing ID does not guess by name");
        expect(DestinationAddressResolver.resolve(detail, Arrays.asList(destination)).equals(detail.address), "detailed SDK address retained");
        expect(DestinationAddressResolver.resolve(destination, Arrays.asList(place("poi", "", "", "shop", 37.37055, null))).equals(destination.address), "null address retains fallback");
        System.out.println("Passed " + checks + " destination-address checks.");
    }
}
