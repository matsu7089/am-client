package jp.nao.amclient;
import org.junit.Test;
import static org.junit.Assert.*;
public class NavigationPolicyTest {
    @Test public void acceptsMusicAndLogin() {
        assertTrue(NavigationPolicy.isMusic("https://music.amazon.co.jp/search/%E9%9F%B3"));
        assertTrue(NavigationPolicy.isInternal("https://www.amazon.co.jp/ap/signin?openid.return_to=example"));
        assertFalse(NavigationPolicy.isMusic("https://www.amazon.co.jp/ap/signin"));
    }
    @Test public void rejectsSpoofedAndUnsafeUrls() {
        for (String url : new String[]{null, "", "http://music.amazon.co.jp/", "https://music.amazon.co.jp.evil.test/",
            "https://music.amazon.co.jp@evil.test/", "https://evil.test@music.amazon.co.jp/", "https://music.amazon.co.jp:8443/",
            "javascript:alert(1)", "file:///etc/passwd", "intent://music.amazon.co.jp/", "https://[", "//music.amazon.co.jp/"})
            assertFalse(String.valueOf(url), NavigationPolicy.isInternal(url));
    }
    @Test public void externalLinksRequireHttps() {
        assertTrue(NavigationPolicy.isExternalWeb("https://example.org/help"));
        assertFalse(NavigationPolicy.isExternalWeb("intent://example.org/"));
        assertFalse(NavigationPolicy.isExternalWeb(null));
    }
}
