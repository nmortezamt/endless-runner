package com.example.endlessfun;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Stores selected bird, background, and unlocked birds (by record). */
public final class GamePrefs {

    private static final String PREFS_NAME = "endlessfun_prefs";
    private static final String KEY_SELECTED_BIRD = "selected_bird";
    private static final String KEY_SELECTED_BACKGROUND = "selected_background";
    private static final String KEY_UNLOCKED_BIRDS = "unlocked_birds"; // comma-separated indices

    private final SharedPreferences prefs;

    public GamePrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getSelectedBird() {
        return prefs.getInt(KEY_SELECTED_BIRD, 0);
    }

    public void setSelectedBird(int index) {
        prefs.edit().putInt(KEY_SELECTED_BIRD, index).apply();
    }

    public int getSelectedBackground() {
        return prefs.getInt(KEY_SELECTED_BACKGROUND, 0);
    }

    public void setSelectedBackground(int index) {
        prefs.edit().putInt(KEY_SELECTED_BACKGROUND, index).apply();
    }

    /** Bird 0 is always unlocked. Others unlock at score thresholds. */
    public Set<Integer> getUnlockedBirds() {
        Set<Integer> out = new HashSet<>();
        out.add(0); // default always
        String s = prefs.getString(KEY_UNLOCKED_BIRDS, "");
        if (s.isEmpty()) return out;
        for (String part : s.split(",")) {
            try {
                out.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    public void setUnlockedBirds(Set<Integer> indices) {
        StringBuilder sb = new StringBuilder();
        for (Integer i : indices) {
            if (sb.length() > 0) sb.append(",");
            sb.append(i);
        }
        prefs.edit().putString(KEY_UNLOCKED_BIRDS, sb.toString()).apply();
    }

    /** Call when high score is updated: unlocks birds whose threshold is <= highScore. */
    public void updateUnlocksForHighScore(int highScore) {
        Set<Integer> unlocked = getUnlockedBirds();
        int[] thresholds = BirdUnlock.THRESHOLDS;
        for (int i = 0; i < thresholds.length; i++) {
            if (highScore >= thresholds[i]) unlocked.add(i + 1); // bird 0 is default, 1..N unlock by score
        }
        setUnlockedBirds(unlocked);
    }

    public boolean isBirdUnlocked(int birdIndex) {
        return getUnlockedBirds().contains(birdIndex);
    }
}
