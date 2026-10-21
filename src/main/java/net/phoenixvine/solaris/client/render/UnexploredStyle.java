package net.phoenixvine.solaris.client.render;

public enum UnexploredStyle {

    FOG,
    STARFIELD,
    PHOENIX,
    CLOUD,
    IMAGE,
    /** Unexplored area is transparent so a backdrop registered with {@link UnexploredBackdrops} shows through. */
    BACKDROP;

    public UnexploredStyle next() {
        UnexploredStyle[] values = values();
        UnexploredStyle next = values[(ordinal() + 1) % values.length];
        // BACKDROP only makes sense when another mod supplies one, so the settings cycle skips it.
        return next == BACKDROP ? values[(next.ordinal() + 1) % values.length] : next;
    }

    /** The next style in the cycle, including BACKDROP: for dimensions that have a backdrop to switch back to. */
    public UnexploredStyle nextWithBackdrop() {
        UnexploredStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String label() {
        return switch (this) {
            case FOG -> "Fog";
            case STARFIELD -> "Starfield";
            case PHOENIX -> "Phoenix";
            case CLOUD -> "Cloud";
            case IMAGE -> "Image";
            case BACKDROP -> "Dimension Backdrop";
        };
    }
}
