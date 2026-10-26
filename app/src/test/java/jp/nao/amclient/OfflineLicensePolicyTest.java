package jp.nao.amclient;
import org.junit.Test;
import static org.junit.Assert.*;
public class OfflineLicensePolicyTest {
    @Test public void acceptsUsablePersistentHandle() { OfflineLicensePolicy.requireUsable(new byte[]{1,2},3600,1800); }
    @Test public void rejectsStreamingLicenseWithoutHandle() {
        for(byte[] handle:new byte[][]{null,new byte[0]}) {
            try {OfflineLicensePolicy.requireUsable(handle,3600,3600);fail("Streaming license must not enable offline saving");}
            catch(IllegalStateException expected){}
        }
    }
    @Test public void rejectsExpiredOrMissingDuration() {
        for(long[] duration:new long[][]{{0,3600},{3600,0},{-1,3600},{3600,-1}}) {
            try {OfflineLicensePolicy.requireUsable(new byte[]{1},duration[0],duration[1]);fail("Expired license accepted");}
            catch(IllegalStateException expected){}
        }
    }
}
