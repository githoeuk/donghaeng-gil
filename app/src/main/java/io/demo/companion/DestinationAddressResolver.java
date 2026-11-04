package io.demo.companion;

import java.util.List;

/** Matches only the same Pleos POI; never looks up or uploads coordinates. */
public final class DestinationAddressResolver {
    public static final class Place {
        final String id, subId, provider, name, address;
        final double lat, lon;
        public Place(String id, String subId, String provider, String name, double lat, double lon, String address) {
            this.id = clean(id); this.subId = clean(subId); this.provider = clean(provider);
            this.name = clean(name); this.lat = lat; this.lon = lon; this.address = clean(address);
        }
    }
    public static String resolve(Place destination, List<Place> recent) {
        String address = destination.address;
        if (destination.id.isEmpty()) return address;
        for (Place place : recent) {
            if (!destination.id.equals(place.id) || !destination.subId.equals(place.subId)
                    || !destination.provider.equals(place.provider) || !destination.name.equals(place.name)) continue;
            if (!JourneyState.validCoordinates(destination.lat, destination.lon)
                    || !JourneyState.validCoordinates(place.lat, place.lon)
                    || JourneyState.distanceMeters(destination.lat, destination.lon, place.lat, place.lon) > 100) continue;
            if (place.address.length() > address.length() && place.address.matches(".*[0-9].*")) address = place.address;
        }
        return address;
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
