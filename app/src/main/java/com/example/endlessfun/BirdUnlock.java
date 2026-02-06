package com.example.endlessfun;

/** Unlock thresholds: bird index 1 unlocks at THRESHOLDS[0], bird 2 at THRESHOLDS[1], etc. Bird 0 is default (always). */
public final class BirdUnlock {
    public static final int[] THRESHOLDS = { 5, 15, 30, 50 };
    public static final int BIRD_COUNT = 1 + THRESHOLDS.length; // default + 4 unlockable

    public static int getUnlockScoreForBird(int birdIndex) {
        if (birdIndex <= 0 || birdIndex > THRESHOLDS.length) return 0;
        return THRESHOLDS[birdIndex - 1];
    }
}
