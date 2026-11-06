package net.righthandedvoid.infidelity;

public enum EligibilityResult {
    OK,
    NOT_ADULT,
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
