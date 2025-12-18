package com.cloudrf.android.soothsayer;

import android.graphics.Color;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.routes.routearound.ShapeToolUtils;

public class CustomPolygonTool {
    public static final String FLAG = "RF box";

    /**
     * Success handler - enriching the created polygon with metadata + setting any custom attributes.
     */
    private static ShapeToolUtils.Callback<? extends Shape, Object> shapeHandler() {
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

    /**
     * Initiates the polygon creation process on the map using the defined callbacks.
     */
    public static void createPolygon() {
        ShapeToolUtils shapeUtil = new ShapeToolUtils(MapView.getMapView());
        shapeUtil.runPolygonCreationTool(
                (ShapeToolUtils.Callback<Shape, Object>) shapeHandler(),
                errorHandler());
    }
}
