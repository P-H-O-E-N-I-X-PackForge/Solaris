package net.phoenixvine.solaris.client.render;

import net.phoenixvine.solaris.client.SolarisText;

public enum UnexploredStyle {

    FOG,
    STARFIELD,
    PHOENIX,
    CLOUD,
    IMAGE,

    BACKDROP;

    public UnexploredStyle next() {
        UnexploredStyle[] values = values();
        UnexploredStyle next = values[(ordinal() + 1) % values.length];

        return next == BACKDROP ? values[(next.ordinal() + 1) % values.length] : next;
    }

    /** The next style in the cycle, including BACKDROP: for dimensions that have a backdrop to switch back to. */
    public UnexploredStyle nextWithBackdrop() {
        UnexploredStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String label() {
        return switch (this) {
            case FOG -> SolarisText.str("solaris.unexplored_style.fog");
            case STARFIELD -> SolarisText.str("solaris.unexplored_style.starfield");
            case PHOENIX -> SolarisText.str("solaris.unexplored_style.phoenix");
            case CLOUD -> SolarisText.str("solaris.unexplored_style.cloud");
            case IMAGE -> SolarisText.str("solaris.unexplored_style.image");
            case BACKDROP -> SolarisText.str("solaris.unexplored_style.dimension_backdrop");
        };
    }
}
