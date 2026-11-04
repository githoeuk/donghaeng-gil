package io.demo.companion;

/** Monotonic timestamps keep freshness checks independent of device clock changes. */
public final class JourneyState {
    public static final long FRESH_MS = 15000;
    public static final double MISMATCH_METERS = 300;
    public enum EtaStatus { CONNECTING, UNAVAILABLE, NO_DESTINATION, ARRIVED, STALE, WAITING, CHECK_LOCATION, READY }
    public boolean connected;
    public String connectionMessage = "Pleos 연결 중";
    public boolean locationPermission, precisePermission, locationEnabled = true, providerAvailable;
    public double gpsLatitude, gpsLongitude, gpsAccuracy;
    public long gpsAt = -1;
    public double naviLatitude, naviLongitude;
    public long naviAt = -1;
    public String naviAddress = "", destinationAddress = "";
    public String destinationName = "";
    public double destinationLatitude, destinationLongitude;
    public int remainingSeconds, remainingMeters;
    public long destinationAt = -1, destinationWallTime;
    public boolean arrived, destinationKnown, routeEnded;

    public static boolean validCoordinates(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isInfinite(lat) && !Double.isNaN(lon)
                && !Double.isInfinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }
    public static boolean fresh(long sampleAt, long now) {
        return sampleAt >= 0 && now >= sampleAt && now - sampleAt <= FRESH_MS;
    }
    public void gps(double lat, double lon, float accuracy, long sampleAt) {
        if (!validCoordinates(lat, lon) || sampleAt < gpsAt) return;
        gpsLatitude = lat; gpsLongitude = lon; gpsAccuracy = accuracy; gpsAt = sampleAt;
    }
    public void navi(double lat, double lon, long now) { navi(lat, lon, "", now); }
    public void navi(double lat, double lon, String address, long now) {
        if (!validCoordinates(lat, lon)) return;
        naviLatitude = lat; naviLongitude = lon; naviAt = now; naviAddress = cleanAddress(address);
    }
    public void destination(String name, double lat, double lon, int seconds, int meters, long now, long wallTime) {
        destination(name, "", lat, lon, seconds, meters, now, wallTime);
    }
    public void destination(String name, String address, double lat, double lon, int seconds, int meters, long now, long wallTime) {
        if (routeEnded) return;
        if (name == null || name.trim().isEmpty() || !validCoordinates(lat, lon)) {
            clearDestination(false); return;
        }
        destinationKnown = true; destinationName = name; destinationAddress = cleanAddress(address); destinationLatitude = lat; destinationLongitude = lon;
        remainingSeconds = seconds; remainingMeters = meters; destinationAt = now;
        destinationWallTime = wallTime; arrived = false;
    }
    public void clearDestination(boolean didArrive) {
        destinationKnown = true; destinationName = ""; destinationAddress = ""; remainingSeconds = 0; remainingMeters = 0;
        destinationAt = -1; arrived = didArrive;
    }
    public void endRoute(boolean didArrive) { clearDestination(didArrive); routeEnded = true; }
    public void beginRoute() { routeEnded = false; arrived = false; }
    public boolean hasDestination() { return !destinationName.isEmpty(); }
    public boolean mismatch(long now) {
        return locationPermission && locationEnabled && fresh(gpsAt, now) && fresh(naviAt, now)
                && distanceMeters(gpsLatitude, gpsLongitude, naviLatitude, naviLongitude)
                > Math.max(MISMATCH_METERS, gpsAccuracy * 2);
    }
    public EtaStatus etaStatus(long now) {
        if (!connected) return "Pleos 연결 중".equals(connectionMessage) ? EtaStatus.CONNECTING : EtaStatus.UNAVAILABLE;
        if (arrived) return EtaStatus.ARRIVED;
        if (!destinationKnown) return EtaStatus.WAITING;
        if (!hasDestination()) return EtaStatus.NO_DESTINATION;
        if (!fresh(destinationAt, now)) return EtaStatus.STALE;
        if (remainingSeconds <= 0 || remainingMeters < 0) return EtaStatus.WAITING;
        if (!locationPermission || !locationEnabled || !fresh(gpsAt, now) || !fresh(naviAt, now) || gpsAccuracy > 100 || mismatch(now)) {
            return EtaStatus.CHECK_LOCATION;
        }
        return EtaStatus.READY;
    }
    private static String cleanAddress(String address) { return address == null ? "" : address.trim(); }
    public String currentAddress(long now) {
        if (!locationPermission || !locationEnabled || !fresh(gpsAt, now) || !fresh(naviAt, now)
                || distanceMeters(gpsLatitude, gpsLongitude, naviLatitude, naviLongitude) > 50) return "";
        return naviAddress;
    }
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double lat = Math.toRadians(lat2 - lat1), lon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lon / 2) * Math.sin(lon / 2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(Math.min(1, a)), Math.sqrt(Math.max(0, 1 - a)));
    }
}
