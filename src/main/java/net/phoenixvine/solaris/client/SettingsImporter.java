package net.phoenixvine.solaris.client;

import java.util.List;

public final class SettingsImporter {

    private SettingsImporter() {}

    public static String importAll() {
        List<String> journeyMap = JourneyMapSettingsImporter.apply();
        List<String> xaero = XaeroSettingsImporter.apply();

        if (journeyMap == null && xaero == null) return "No JourneyMap or Xaero's config found in this game directory.";

        StringBuilder message = new StringBuilder();
        append(message, "JourneyMap", journeyMap);
        append(message, "Xaero's", xaero);
        return message.length() == 0 ? "Config found, but nothing in it could be imported." : message.toString();
    }

    private static void append(StringBuilder message, String source, List<String> applied) {
        if (applied == null || applied.isEmpty()) return;
        if (message.length() > 0) message.append("; ");
        message.append(source).append(" (").append(applied.size()).append("): ").append(String.join(", ", applied));
    }
}
