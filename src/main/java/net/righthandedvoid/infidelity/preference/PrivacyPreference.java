package net.righthandedvoid.infidelity.preference;

/** Where a villager is comfortable being intimate. Rolled once, then kept for life. */
public enum PrivacyPreference {
    UNDECIDED,
    /** Only at her own home, with nobody watching. Falls back to PRIVATE if she has no home. */
    HOME,
    /** Somewhere indoors or sheltered, with nobody watching. */
    PRIVATE,
    /** Doesn't mind where, or who's around. */
    ANYWHERE;

    public static PrivacyPreference parse(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return UNDECIDED;
        }
    }
}
