package com.crypto.laplace.model;

public enum LaplacePaperVariant {
    INVERTED_TRUE(true),
    INVERTED_FALSE(false);

    private final boolean signalInverted;

    LaplacePaperVariant(boolean signalInverted) { this.signalInverted = signalInverted; }
    public boolean signalInverted() { return signalInverted; }
}
