package io.demo.companion;

import java.net.URI;

/** HTTPS online; only exact emulator/loopback hosts may use HTTP in debug. */
public final class RelayEndpoint {
    public static String validate(String input, boolean debug) {
        try {
            URI uri = new URI(input.trim());
            String scheme = uri.getScheme(), host = uri.getHost();
            boolean local = "10.0.2.2".equals(host) || "127.0.0.1".equals(host) || "localhost".equals(host);
            if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    || !("https".equals(scheme) || (debug && local && "http".equals(scheme))))
                throw new IllegalArgumentException();
            return input.trim().replaceAll("/+$", "");
        } catch (Exception error) { throw new IllegalArgumentException("HTTPS 서버 또는 Debug 로컬 주소만 사용할 수 있습니다."); }
    }
}
