package net.phoenixvine.solaris.client.render;

public interface MapProjection {

    double screenX(double worldX);

    double screenY(double worldZ);

    float zoom();

    default boolean contains(double screenX, double screenY) {
        return true;
    }
}
