package net.alkahest.research;

/** Camera arithmetic in screen pixels; zoom is anchored under the mouse. */
public final class ResearchMapView {
    public static final double MIN_ZOOM = .45, MAX_ZOOM = 1.65;
    private double zoom = 1, x, y;
    public double zoom() { return zoom; }
    public double x(double point) { return x + point * zoom; }
    public double y(double point) { return y + point * zoom; }
    public double radius() { return 16 * zoom; }
    public void pan(double dx, double dy) { x += dx; y += dy; }
    public void zoomAt(double factor, double anchorX, double anchorY) {
        double next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        x = anchorX - (anchorX - x) * next / zoom;
        y = anchorY - (anchorY - y) * next / zoom;
        zoom = next;
    }
    public void clamp(double width, double height, double minX, double maxX, double minY, double maxY) {
        double left = width - 30 - maxX * zoom, right = 30 - minX * zoom;
        double top = height / 2 - 25 - maxY * zoom, bottom = -height / 2 + 25 - minY * zoom;
        x = left <= right ? Math.max(left, Math.min(right, x)) : (left + right) / 2;
        y = top <= bottom ? Math.max(top, Math.min(bottom, y)) : (top + bottom) / 2;
    }
}
