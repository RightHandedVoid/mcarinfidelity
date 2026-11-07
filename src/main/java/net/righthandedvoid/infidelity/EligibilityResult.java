package net.righthandedvoid.infidelity;

public enum EligibilityResult {
    OK,
    NOT_ADULT,
    NOT_OPTED_IN,          // the asking player hasn't opted in
    PARTNER_NOT_OPTED_IN,  // the other player hasn't (never revealed to the asker as such)
    RELATED,
    INVALID_STATE,
    TOO_FAR,
    BUSY,
    LOW_AFFECTION,
    BAD_MOOD;

    public boolean allowed() {
        return this == OK;
    }
}
