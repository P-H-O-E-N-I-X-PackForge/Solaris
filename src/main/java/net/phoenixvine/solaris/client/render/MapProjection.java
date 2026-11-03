package net.phoenixvine.solaris.client.render;

public interface MapProjection {

    double screenX(double worldX);

    double screenY(double worldZ);

    float zoom();

    /** Whether a screen point is inside the visible map area; the minimap is not always square. */
    default boolean contains(double screenX, double screenY) {
        return true;
    }
}
