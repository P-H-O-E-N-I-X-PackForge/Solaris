package net.phoenixvine.solaris.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SolarisPresets {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Paths.get("config", "solaris", "presets.json");

    private static Map<String, Preset> presets;

    private SolarisPresets() {}

    private static final class Preset {

        Double saturation;
        Double contrast;
        Double brightness;
        Double foliageBrightness;
        Double tintRed;
        Double tintGreen;
        Double tintBlue;
        Boolean blackAndWhite;
        Boolean vignette;
        Double vignetteStrength;
        Double nightModeStrength;
        Double waterOpacity;
        Boolean waterDeepOnly;
        Integer waterDeepYThreshold;
        Integer waterBlendRadius;
        Double waypointIconScale;
        Boolean hillshading;
        Double hillshadingStrength;
    }

    private static Preset solarisDefault() {
        Preset p = new Preset();
        p.saturation = 0.85;
        p.contrast = 1.3;
        p.brightness = 1.2;
        p.foliageBrightness = 0.45;
        p.tintRed = 1.0;
        p.tintGreen = 1.0;
        p.tintBlue = 1.0;
        p.blackAndWhite = false;
        p.vignette = false;
        p.vignetteStrength = 0.5;
        p.nightModeStrength = 0.35;
        p.waterOpacity = 0.0;
        p.waterDeepOnly = false;
        p.waterDeepYThreshold = 50;
        p.waterBlendRadius = 8;
        p.waypointIconScale = 1.0;
        p.hillshading = false;
        p.hillshadingStrength = 0.7;
        return p;
    }

    private static Preset unadjustedColors() {
        Preset p = new Preset();
        p.saturation = 1.0;
        p.contrast = 1.0;
        p.brightness = 1.0;
        p.foliageBrightness = 1.0;
        p.tintRed = 1.0;
        p.tintGreen = 1.0;
        p.tintBlue = 1.0;
        p.blackAndWhite = false;
        return p;
    }

    private static Preset look(double saturation, double contrast, double brightness, double foliage) {
        Preset p = new Preset();
        p.saturation = saturation;
        p.contrast = contrast;
        p.brightness = brightness;
        p.foliageBrightness = foliage;
        p.tintRed = 1.0;
        p.tintGreen = 1.0;
        p.tintBlue = 1.0;
        p.blackAndWhite = false;
        return p;
    }

    private static Map<String, Preset> builtIns() {
        Map<String, Preset> builtIns = new LinkedHashMap<>();
        builtIns.put("Solaris Default", solarisDefault());
        builtIns.put("Unadjusted Colors", unadjustedColors());

        builtIns.put("Vivid", look(1.3, 1.2, 1.1, 0.55));
        builtIns.put("Muted", look(0.55, 1.15, 1.15, 0.5));
        builtIns.put("High Contrast", look(0.9, 1.7, 1.15, 0.45));

        Preset cartographic = look(0.7, 1.2, 1.1, 0.5);
        cartographic.hillshading = true;
        cartographic.hillshadingStrength = 0.9;
        cartographic.waterOpacity = 0.4;
        builtIns.put("Cartographic", cartographic);

        Preset nightFriendly = look(0.85, 1.3, 1.2, 0.45);
        nightFriendly.nightModeStrength = 0.1;
        builtIns.put("Night Friendly", nightFriendly);

        Preset sepia = look(0.6, 1.2, 1.15, 0.5);
        sepia.tintRed = 1.15;
        sepia.tintGreen = 1.0;
        sepia.tintBlue = 0.72;
        builtIns.put("Sepia", sepia);

        Preset blackAndWhite = look(0.85, 1.3, 1.2, 0.45);
        blackAndWhite.blackAndWhite = true;
        builtIns.put("Black & White", blackAndWhite);

        return builtIns;
    }

    private static Preset builtIn(String name) {
        return builtIns().get(name);
    }

    public static boolean isBuiltIn(String name) {
        return builtIn(name) != null;
    }

    public static boolean isDeletable(String name) {
        ensureLoaded();
        return presets.containsKey(name);
    }

    public static List<String> names() {
        ensureLoaded();
        List<String> names = new ArrayList<>(builtIns().keySet());
        for (String name : presets.keySet()) {
            if (!names.contains(name)) names.add(name);
        }
        return names;
    }

    public static boolean exists(String name) {
        ensureLoaded();
        return presets.containsKey(name);
    }

    public static void save(String name) {
        ensureLoaded();
        Preset p = new Preset();
        p.saturation = SolarisConfig.SATURATION.get();
        p.contrast = SolarisConfig.CONTRAST.get();
        p.brightness = SolarisConfig.BRIGHTNESS.get();
        p.foliageBrightness = SolarisConfig.FOLIAGE_BRIGHTNESS.get();
        p.tintRed = SolarisConfig.TINT_RED.get();
        p.tintGreen = SolarisConfig.TINT_GREEN.get();
        p.tintBlue = SolarisConfig.TINT_BLUE.get();
        p.blackAndWhite = SolarisConfig.BLACK_AND_WHITE.get();
        p.vignette = SolarisConfig.VIGNETTE.get();
        p.vignetteStrength = SolarisConfig.VIGNETTE_STRENGTH.get();
        p.nightModeStrength = SolarisConfig.NIGHT_MODE_STRENGTH.get();
        p.waterOpacity = SolarisConfig.WATER_OPACITY.get();
        p.waterDeepOnly = SolarisConfig.WATER_DEEP_ONLY.get();
        p.waterDeepYThreshold = SolarisConfig.WATER_DEEP_Y_THRESHOLD.get();
        p.waterBlendRadius = SolarisConfig.WATER_BLEND_RADIUS.get();
        p.waypointIconScale = SolarisConfig.WAYPOINT_ICON_SCALE.get();
        p.hillshading = SolarisConfig.HILLSHADING.get();
        p.hillshadingStrength = SolarisConfig.HILLSHADING_STRENGTH.get();
        presets.put(name, p);
        persist();
    }

    public static void load(String name) {
        ensureLoaded();
        Preset p = presets.get(name);
        if (p == null) p = builtIn(name);
        if (p == null) return;

        if (p.saturation != null) {
            SolarisConfig.SATURATION.set(p.saturation);
            SolarisConfig.SATURATION.save();
        }
        if (p.contrast != null) {
            SolarisConfig.CONTRAST.set(p.contrast);
            SolarisConfig.CONTRAST.save();
        }
        if (p.brightness != null) {
            SolarisConfig.BRIGHTNESS.set(p.brightness);
            SolarisConfig.BRIGHTNESS.save();
        }
        if (p.foliageBrightness != null) {
            SolarisConfig.FOLIAGE_BRIGHTNESS.set(p.foliageBrightness);
            SolarisConfig.FOLIAGE_BRIGHTNESS.save();
        }
        if (p.tintRed != null) {
            SolarisConfig.TINT_RED.set(p.tintRed);
            SolarisConfig.TINT_RED.save();
        }
        if (p.tintGreen != null) {
            SolarisConfig.TINT_GREEN.set(p.tintGreen);
            SolarisConfig.TINT_GREEN.save();
        }
        if (p.tintBlue != null) {
            SolarisConfig.TINT_BLUE.set(p.tintBlue);
            SolarisConfig.TINT_BLUE.save();
        }
        if (p.blackAndWhite != null) {
            SolarisConfig.BLACK_AND_WHITE.set(p.blackAndWhite);
            SolarisConfig.BLACK_AND_WHITE.save();
        }
        if (p.vignette != null) {
            SolarisConfig.VIGNETTE.set(p.vignette);
            SolarisConfig.VIGNETTE.save();
        }
        if (p.vignetteStrength != null) {
            SolarisConfig.VIGNETTE_STRENGTH.set(p.vignetteStrength);
            SolarisConfig.VIGNETTE_STRENGTH.save();
        }
        if (p.nightModeStrength != null) {
            SolarisConfig.NIGHT_MODE_STRENGTH.set(p.nightModeStrength);
            SolarisConfig.NIGHT_MODE_STRENGTH.save();
        }
        if (p.waterOpacity != null) {
            SolarisConfig.WATER_OPACITY.set(p.waterOpacity);
            SolarisConfig.WATER_OPACITY.save();
        }
        if (p.waterDeepOnly != null) {
            SolarisConfig.WATER_DEEP_ONLY.set(p.waterDeepOnly);
            SolarisConfig.WATER_DEEP_ONLY.save();
        }
        if (p.waterDeepYThreshold != null) {
            SolarisConfig.WATER_DEEP_Y_THRESHOLD.set(p.waterDeepYThreshold);
            SolarisConfig.WATER_DEEP_Y_THRESHOLD.save();
        }
        if (p.waterBlendRadius != null) {
            SolarisConfig.WATER_BLEND_RADIUS.set(p.waterBlendRadius);
            SolarisConfig.WATER_BLEND_RADIUS.save();
        }
        if (p.waypointIconScale != null) {
            SolarisConfig.WAYPOINT_ICON_SCALE.set(p.waypointIconScale);
            SolarisConfig.WAYPOINT_ICON_SCALE.save();
        }
        if (p.hillshading != null) {
            SolarisConfig.HILLSHADING.set(p.hillshading);
            SolarisConfig.HILLSHADING.save();
        }
        if (p.hillshadingStrength != null) {
            SolarisConfig.HILLSHADING_STRENGTH.set(p.hillshadingStrength);
            SolarisConfig.HILLSHADING_STRENGTH.save();
        }
    }

    public static void delete(String name) {
        ensureLoaded();
        presets.remove(name);
        persist();
    }

    private static void ensureLoaded() {
        if (presets != null) return;
        presets = new LinkedHashMap<>();
        try {
            if (Files.exists(FILE)) {
                String json = Files.readString(FILE);
                Type type = new TypeToken<LinkedHashMap<String, Preset>>() {}.getType();
                Map<String, Preset> loaded = GSON.fromJson(json, type);
                if (loaded != null) presets.putAll(loaded);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void persist() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(presets));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
