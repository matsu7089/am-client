package jp.nao.amclient;

/** Saving bytes is permitted only after the platform CDM returns a usable persistent license. */
public final class OfflineLicensePolicy {
    private OfflineLicensePolicy() {}
    public static void requireUsable(byte[] keySet,long licenseSeconds,long playbackSeconds) {
        if(keySet==null||keySet.length==0)throw new IllegalStateException("永続ライセンスが発行されませんでした");
        if(licenseSeconds<=0||playbackSeconds<=0)throw new IllegalStateException("オフラインライセンスが失効しています");
    }
}
