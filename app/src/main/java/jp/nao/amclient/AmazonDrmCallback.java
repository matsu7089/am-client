package jp.nao.amclient;

import android.net.Uri;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.drm.*;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import java.util.*;

/** License challenge/response remains opaque; Android's CDM owns all content keys. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class AmazonDrmCallback implements MediaDrmCallback {
    private final AmazonApi api;
    private final boolean online;
    public AmazonDrmCallback(AmazonApi api) { this(api,true); }
    public AmazonDrmCallback(AmazonApi api,boolean online) { this.api=api;this.online=online; }
    private void requireNetworkPermission() throws MediaDrmCallbackException {
        if(!online)throw new MediaDrmCallbackException(new DataSpec(Uri.EMPTY),Uri.EMPTY,Collections.emptyMap(),0,new java.io.IOException("オフラインライセンスが失効しました。オンラインで保存し直してください"));
    }
    @Override public byte[] executeProvisionRequest(UUID uuid, ExoMediaDrm.ProvisionRequest request) throws MediaDrmCallbackException {
        requireNetworkPermission();
        return new HttpMediaDrmCallback("",new DefaultHttpDataSource.Factory().setConnectTimeoutMs(15000).setReadTimeoutMs(25000))
            .executeProvisionRequest(uuid,request);
    }
    @Override public byte[] executeKeyRequest(UUID uuid, ExoMediaDrm.KeyRequest request) throws MediaDrmCallbackException {
        requireNetworkPermission();
        android.util.Log.i("AMPlayback","Requesting DRM license; requestType="+request.getRequestType());
        try { byte[] response=api.license(request.getData());android.util.Log.i("AMPlayback","DRM license received");return response; }
        catch(Exception e) {android.util.Log.e("AMPlayback","DRM license request failed",e);throw new MediaDrmCallbackException(new DataSpec(Uri.parse("https://music.amazon.co.jp/FE/api/dmls/")),Uri.EMPTY,Collections.emptyMap(),0,e); }
    }
    public static DefaultDrmSessionManager manager(AmazonApi api) { return manager(api,true); }
    public static DefaultDrmSessionManager manager(AmazonApi api,boolean online) {
        return new DefaultDrmSessionManager.Builder().setUuidAndExoMediaDrmProvider(androidx.media3.common.C.WIDEVINE_UUID,FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setPlayClearSamplesWithoutKeys(false)
            .build(new AmazonDrmCallback(api,online));
    }
    public static DefaultDrmSessionManager offlineManager(AmazonApi api) {
        return new DefaultDrmSessionManager.Builder().setUuidAndExoMediaDrmProvider(androidx.media3.common.C.WIDEVINE_UUID,FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setPlayClearSamplesWithoutKeys(false)
            .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(){
                @Override public long getRetryDelayMsFor(androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo info){
                    return OfflineFailure.licenseRejected(info.exception)?androidx.media3.common.C.TIME_UNSET:super.getRetryDelayMsFor(info);
                }
            }).build(new AmazonDrmCallback(api));
    }
}
