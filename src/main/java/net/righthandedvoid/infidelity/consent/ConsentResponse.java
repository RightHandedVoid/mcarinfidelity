package net.righthandedvoid.infidelity.consent;

/**
 * How a player answers a request. Protection can only be added by the responder, never removed:
 * if either person wants it, it's used.
 */
public enum ConsentResponse {
    DECLINE,
    ACCEPT,
    ACCEPT_WITH_PROTECTION;

    /** Unknown values from the network count as a decline. */
    public static ConsentResponse fromId(int id) {
        ConsentResponse[] values = values();
        return id >= 0 && id < values.length ? values[id] : DECLINE;
    }
}
