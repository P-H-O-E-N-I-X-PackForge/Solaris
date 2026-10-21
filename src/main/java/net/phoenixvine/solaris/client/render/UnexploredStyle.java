package net.phoenixvine.solaris.client.render;

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
