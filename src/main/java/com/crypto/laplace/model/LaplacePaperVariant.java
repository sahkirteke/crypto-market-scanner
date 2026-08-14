package com.crypto.laplace.model;

/** Independently persisted paper systems sharing one raw Laplace signal stream. */
public enum LaplacePaperVariant {
    INVERTED_TRUE(true, "inverted-true"),
    INVERTED_FALSE(false, "inverted-false");

    private final boolean signalInverted;
    private final String path;

    LaplacePaperVariant(boolean signalInverted, String path) {
        this.signalInverted = signalInverted;
        this.path = path;
    }

    public boolean signalInverted() { return signalInverted; }
    public String path() { return path; }
}
