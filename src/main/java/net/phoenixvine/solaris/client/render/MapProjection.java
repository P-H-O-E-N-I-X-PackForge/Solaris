package net.phoenixvine.solaris.client.render;

/** World-to-screen mapping of the flat fullscreen map, for {@link MapLayer}s. */
public interface MapProjection {

    double screenX(double worldX);

    double screenY(double worldZ);

    /** Screen pixels per world block. */
    float zoom();
}
