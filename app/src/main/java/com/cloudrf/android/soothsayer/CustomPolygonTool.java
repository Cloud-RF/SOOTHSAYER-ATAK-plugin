package com.cloudrf.android.soothsayer;

import android.graphics.Color;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.routes.routearound.ShapeToolUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.cloudrf.android.soothsayer.interfaces.CustomPolygonInterface;

import java.util.UUID;

public class CustomPolygonTool {
    public static final String FLAG = "RF box";

    private static final String DRAWING_OBJECTS = "Drawing Objects";

    /** Approximate conversion for metres to lat. */
    private static final double METRES_PER_DEGREE_LAT = 111320.0;

    /**
     * Success handler - enriching the created polygon with metadata + setting any custom attributes.
     */
    private static ShapeToolUtils.Callback<? extends Shape, Object> shapeHandler(CustomPolygonInterface listener) {
        return new ShapeToolUtils.Callback<Shape, Object>() {
            @Override
            public Object apply(Shape polygon) {
                // Set your polygon details here
                polygon.setColor(Color.BLACK);
                polygon.setFillColor(Color.YELLOW);
                polygon.setFillAlpha(0);
                polygon.setTitle(FLAG);

                // Only this is mandatory
                polygon.setMetaString(FLAG, "1");
                listener.onPolygonDrawn(polygon);
                return polygon;
            }
        };
    }

    /**
     * Error handler.
     */
    private static <A> ShapeToolUtils.Callback<Error, A> errorHandler() {
        return x -> {
            throw x;
        };
    }

    /**
     * Retrieves the masking polygon from the map view.
     *
     * @return the {@link DrawingShape} representing the masking polygon, or null if not found.
     */
    public static DrawingShape getMaskingPolygon() {
        return (DrawingShape) MapView.getMapView().getRootGroup().deepFindItem(FLAG, "1");
    }

    public static void setDrawnPolygonSuppressed(boolean suppressed) {
        String current = suppressed ? "1" : "0";
        DrawingShape shape =
                (DrawingShape) MapView.getMapView().getRootGroup().deepFindItem(FLAG, current);
        if (shape != null) {
            shape.setMetaString(FLAG, suppressed ? "0" : "1");
        }
    }

    /**
     * Initiates the polygon creation process on the map using the defined callbacks.
     */
    public static void createPolygon(CustomPolygonInterface listener) {
        ShapeToolUtils shapeUtil = new ShapeToolUtils(MapView.getMapView());
        shapeUtil.runPolygonCreationTool(
                (ShapeToolUtils.Callback<Shape, Object>) shapeHandler(listener),
                errorHandler());
    }

    /**
     * Creates, or repositions, the automatic square RF box.
     *
     * @return the box, or null if there is no valid centre to place it on.
     */
    public static DrawingShape setAutoBox(GeoPoint centre, double halfWidthMetres) {
        MapView mapView = MapView.getMapView();
        if (mapView == null || centre == null || !centre.isValid() || halfWidthMetres <= 0) {
            return null;
        }

        DrawingShape box = getMaskingPolygon();
        if (box == null) {
            box = new DrawingShape(mapView, UUID.randomUUID().toString());
            box.setColor(Color.BLACK);
            box.setFillColor(Color.YELLOW);
            box.setFillAlpha(0);
            box.setTitle(FLAG);
            box.setMetaString(FLAG, "1");
            box.setClosed(true);
            // Follows self marker, dragging by hand is cancelled.
            box.setMetaBoolean("movable", false);
            targetGroup(mapView).addItem(box);
        }
        box.setPoints(corners(centre, halfWidthMetres));
        return box;
    }

    /**
     * Removes the automatic box from the map. Hand-drawn boxes are left alone.
     */
    public static void removeAutoBox() {
        DrawingShape box = getMaskingPolygon();
        if (box == null) {
            return;
        }
        MapGroup group = box.getGroup();
        if (group != null) {
            group.removeItem(box);
        }
    }

    /**
     * Corners of a square, clockwise from the north-west.
     */
    private static GeoPoint[] corners(GeoPoint centre, double halfWidthMetres) {
        double lat = centre.getLatitude();
        double lon = centre.getLongitude();

        double dLat = halfWidthMetres / METRES_PER_DEGREE_LAT;

        double cosLat = Math.max(Math.cos(Math.toRadians(lat)), 0.01);
        double dLon = halfWidthMetres / (METRES_PER_DEGREE_LAT * cosLat);

        double north = lat + dLat;
        double south = lat - dLat;
        double east = lon + dLon;
        double west = lon - dLon;

        return new GeoPoint[]{
                new GeoPoint(north, west),
                new GeoPoint(north, east),
                new GeoPoint(south, east),
                new GeoPoint(south, west)
        };
    }

    private static MapGroup targetGroup(MapView mapView) {
        MapGroup group = mapView.getRootGroup().findMapGroup(DRAWING_OBJECTS);
        return group != null ? group : mapView.getRootGroup();
    }
}
