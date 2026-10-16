package dev.ofrostt.frostduels;

import org.bukkit.configuration.ConfigurationSection;

/** Global rules with optional per-kit overrides. Height limits are inclusive block Y coordinates. */
public record Rules(boolean saturation, boolean regeneration, boolean naturalRegeneration, boolean potionRegeneration,
             boolean hunger, boolean terrain, boolean building, boolean breaking, boolean explosions,
             int minBreakY, int maxBreakY, int minBuildY, int maxBuildY, int food, float startingSaturation) {
    static Rules load(ConfigurationSection base, ConfigurationSection overrides) {
        if (base == null) throw new IllegalArgumentException("Missing rules section");
        Rules result = new Rules(bool(base, overrides, "saturation"), bool(base, overrides, "regeneration"),
                bool(base, overrides, "natural-regeneration"), bool(base, overrides, "potion-regeneration"),
                bool(base, overrides, "hunger"), bool(base, overrides, "terrain-damage"),
                bool(base, overrides, "building"), bool(base, overrides, "breaking"), bool(base, overrides, "explosion-terrain-damage"),
                number(base, overrides, "min-break-y"), number(base, overrides, "max-break-y"),
                number(base, overrides, "min-build-y"), number(base, overrides, "max-build-y"),
                number(base, overrides, "starting-food"), decimal(base, overrides, "starting-saturation"));
        if (result.minBreakY > result.maxBreakY || result.minBuildY > result.maxBuildY
                || result.minBreakY < -2032 || result.minBuildY < -2032 || result.maxBreakY > 2031 || result.maxBuildY > 2031
                || result.food < 0 || result.food > 20 || !Float.isFinite(result.startingSaturation)
                || result.startingSaturation < 0 || result.startingSaturation > 20) throw new IllegalArgumentException("Invalid rule limits");
        if (!result.terrain && (result.building || result.breaking || result.explosions)) throw new IllegalArgumentException("Enable terrain-damage before building, breaking, or explosion terrain damage");
        return result;
    }
    public java.util.Map<String, Object> values() {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("saturation", saturation); values.put("regeneration", regeneration);
        values.put("natural-regeneration", naturalRegeneration); values.put("potion-regeneration", potionRegeneration);
        values.put("hunger", hunger); values.put("terrain-damage", terrain);
        values.put("building", building); values.put("breaking", breaking); values.put("explosion-terrain-damage", explosions);
        values.put("min-break-y", minBreakY); values.put("max-break-y", maxBreakY);
        values.put("min-build-y", minBuildY); values.put("max-build-y", maxBuildY);
        values.put("starting-food", food); values.put("starting-saturation", startingSaturation);
        return java.util.Collections.unmodifiableMap(values);
    }
    public static Rules fromValues(java.util.Map<String, Object> values) {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        values.forEach(yaml::set);
        return load(yaml, null);
    }
    boolean canBuild(int y) { return terrain && building && y >= minBuildY && y <= maxBuildY; }
    boolean canBreak(int y) { return terrain && breaking && y >= minBreakY && y <= maxBreakY; }
    boolean canExplode(int y) { return terrain && explosions && y >= minBreakY && y <= maxBreakY; }
    private static ConfigurationSection source(ConfigurationSection base, ConfigurationSection override, String key) {
        return override != null && override.contains(key) ? override : base;
    }
    private static boolean bool(ConfigurationSection base, ConfigurationSection override, String key) {
        var section = source(base, override, key);
        if (!section.isBoolean(key)) throw new IllegalArgumentException("Rule " + key + " must be true or false");
        return section.getBoolean(key);
    }
    private static int number(ConfigurationSection base, ConfigurationSection override, String key) {
        var section = source(base, override, key);
        if (!section.isInt(key)) throw new IllegalArgumentException("Rule " + key + " must be an integer");
        return section.getInt(key);
    }
    private static float decimal(ConfigurationSection base, ConfigurationSection override, String key) {
        var section = source(base, override, key);
        if (!(section.get(key) instanceof Number number)) throw new IllegalArgumentException("Rule " + key + " must be numeric");
        return number.floatValue();
    }
}
