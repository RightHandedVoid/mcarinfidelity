package net.righthandedvoid.infidelity.preference;

/** A villager's lasting stance on having children. Rolled once, then kept for life. */
public enum ChildPreference {
    UNDECIDED,      // not rolled yet; resolved the first time anything asks
    WANTS,
    DOES_NOT_WANT;

    public static ChildPreference parse(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return UNDECIDED;   // unknown or corrupted data: re-roll rather than crash
        }
    }
}
