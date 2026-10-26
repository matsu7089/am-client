package jp.nao.amclient;

import org.junit.Test;
import static org.junit.Assert.*;

public class ManifestPolicyTest {
    @Test public void primeDoesNotReceiveAnUnlimitedSubstitute() {
        assertFalse(ManifestPolicy.allowsAsinSubstitution("PRIME"));
    }
    @Test public void unlimitedMayUseSubstitution() {
        assertTrue(ManifestPolicy.allowsAsinSubstitution("UNLIMITED"));
        assertTrue(ManifestPolicy.allowsAsinSubstitution("UNLIMITED_HD"));
    }
    @Test public void unknownOrFreeTiersDoNotGainSubstitution() {
        for(String tier:new String[]{null,"","NONE","AMF","FREE","UNKNOWN"}) {
            assertFalse(ManifestPolicy.allowsAsinSubstitution(tier));
        }
    }
}
