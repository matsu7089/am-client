package jp.nao.amclient;

import org.junit.Test;
import java.io.*;
import java.net.*;
import static org.junit.Assert.*;

public class OfflineFailureTest {
    @Test public void recognizesNestedPersistentLicenseRejection(){
        Throwable error=new IOException(new AmazonApi.HttpFailure(400,"com.amazon.digitalmusicdrmlicenseservice#BadRequestException EC_INVALID_PARAMETER"));
        assertTrue(OfflineFailure.licenseRejected(error));
        assertTrue(OfflineFailure.message(error,true).contains("保存用ライセンスの発行を拒否"));
        assertFalse(OfflineFailure.message(error,false).contains("ライセンスの発行を拒否"));
    }
    @Test public void doesNotTreatTransientOrUnrelatedFailuresAsUnsupportedOffline(){
        for(Throwable error:new Throwable[]{new SocketTimeoutException(),new IOException(new UnknownHostException()),new AmazonApi.HttpFailure(503,""),new AmazonApi.HttpFailure(400,"OtherException")})
            assertFalse(OfflineFailure.licenseRejected(error));
        assertTrue(OfflineFailure.message(new IOException(new SocketTimeoutException()),true).contains("接続を確認"));
    }
}
