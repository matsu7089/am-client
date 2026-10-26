package jp.nao.amclient;

import java.net.URI;

/** Exact host matching: never trust a hostname suffix or an intent supplied by a page. */
public final class NavigationPolicy {
    public static final String HOME = "https://music.amazon.co.jp/";
    private NavigationPolicy() {}
    public static boolean isInternal(String url) {
        if (url == null) return false;
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            return "music.amazon.co.jp".equalsIgnoreCase(uri.getHost())
                    || "www.amazon.co.jp".equalsIgnoreCase(uri.getHost())
                    || "amazon.co.jp".equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException e) { return false; }
    }
    public static boolean isMusic(String url) {
        return isInternal(url) && "music.amazon.co.jp".equalsIgnoreCase(URI.create(url).getHost());
    }
    public static boolean isExternalWeb(String url) {
        if (url == null) return false;
        try { URI uri = URI.create(url); return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null; }
        catch (IllegalArgumentException e) { return false; }
    }
}
