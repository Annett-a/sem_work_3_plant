package ru.itis.documents.domain.enums;

import java.util.Locale;

public enum LightLevel {
    BRIGHT("яркий свет"),
    PART_SHADE("полутень"),
    SHADE("тень");

    private final String ruLabel;

    LightLevel(String ruLabel) {
        this.ruLabel = ruLabel;
    }

    public String ruLabel() {
        return ruLabel;
    }

    public static LightLevel from(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;

        // принимаем и enum-имена, и русские значения из формы
        for (LightLevel v : values()) {
            if (v.name().equalsIgnoreCase(raw)) return v;
            if (v.ruLabel.toLowerCase(Locale.ROOT).equals(s)) return v;
        }

        // мягкие синонимы (чтобы не падало из-за вариаций)
        if (s.contains("ярк")) return BRIGHT;
        if (s.contains("полут")) return PART_SHADE;
        if (s.contains("тен")) return SHADE;

        return null;
    }
}