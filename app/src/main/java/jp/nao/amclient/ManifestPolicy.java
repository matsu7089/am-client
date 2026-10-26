package jp.nao.amclient;

/** Asset substitution may select a master requiring an Unlimited entitlement. */
public final class ManifestPolicy {
    private ManifestPolicy() {}
    public static boolean allowsAsinSubstitution(String subscriptionTier) {
        return "UNLIMITED".equals(subscriptionTier) || "UNLIMITED_HD".equals(subscriptionTier);
    }
}
