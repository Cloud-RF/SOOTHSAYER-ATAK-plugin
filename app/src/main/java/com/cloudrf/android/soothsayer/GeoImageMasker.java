package com.cloudrf.android.soothsayer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;

public class GeoImageMasker {

    private static final String TAG = "GeoImageMasker";


    /**
     * A helper class that encapsulates the geographic bounds of a set of points.
     */
    public static class Bounds {
        public double north;
        public double south;
        public double east;
        public double west;

        /**
         * Constructs a new Bounds object.
         *
         * @param north the maximum latitude (northern bound)
         * @param south the minimum latitude (southern bound)
         * @param east  the maximum longitude (eastern bound)
         * @param west  the minimum longitude (western bound)
         */
        public Bounds(double north, double south, double east, double west) {
            this.north = north;
            this.south = south;
            this.east = east;
            this.west = west;
        }

        /**
         * Returns the northern bound.
         *
         * @return the maximum latitude
         */
        public double getNorth() {
            return north;
        }

        /**
         * Returns the southern bound.
         *
         * @return the minimum latitude
         */
        public double getSouth() {
            return south;
        }

        /**
         * Returns the eastern bound.
         *
         * @return the maximum longitude
         */
        public double getEast() {
            return east;
        }

        /**
         * Returns the western bound.
         *
         * @return the minimum longitude
         */
        public double getWest() {
            return west;
        }

        /**
         * Returns a string representation of the bounds.
         *
         * @return a string in the format "Bounds [north=..., south=..., east=..., west=...]"
         */
        @Override
        public String toString() {
            return "["+north+","+east+","+south+","+west+"]";
        }

        public ArrayList<Double> toArray() {
            ArrayList<Double> bounds = new ArrayList<Double>();
            bounds.add(north);
            bounds.add(east);
            bounds.add(south);
            bounds.add(west);
            return bounds;
        }
    }

    /**
     * Extracts the north, south, east, and west bounds from an array of GeoPoint objects.
     *
     * <p>The north bound is the maximum latitude, the south bound is the minimum latitude,
     * the east bound is the maximum longitude, and the west bound is the minimum longitude.</p>
     *
     * @param points an array of GeoPoint objects representing the polygon
     * @return a Bounds object containing the north, south, east, and west bounds
     * @throws IllegalArgumentException if the points array is null or empty
     */
    public static Bounds getBounds(GeoPoint[] points) {
        if (points == null || points.length == 0) {
            throw new IllegalArgumentException("Points array is null or empty");
        }

        double north = -Double.MAX_VALUE;
        double south = Double.MAX_VALUE;
        double east = -Double.MAX_VALUE;
        double west = Double.MAX_VALUE;

        for (GeoPoint p : points) {
            double lat = p.getLatitude();
            double lon = p.getLongitude();

            if (lat > north) {
                north = lat;
            }
            if (lat < south) {
                south = lat;
            }
            if (lon > east) {
                east = lon;
            }
            if (lon < west) {
                west = lon;
            }
        }

        return new Bounds(north, south, east, west);
    }

    public static Bitmap cropImage(Bitmap bm, Bounds imageBounds, DrawingShape ds, Boolean bsa) {

        if (ds == null || !ds.isClosed()) {
            throw new IllegalArgumentException("Invalid input: DrawingShape needs to be a valid closed polygon");
        }

        GeoPoint[] points = ds.getPoints();

        return cropImage(bm, imageBounds, points, bsa);
    }

    /**
     * Crops a given Bitmap image so that only the area inside the polygon defined by the DrawingShape is left.
     *
     * <p>The conversion from geographic coordinate to pixel coordinate is done as follows:
     * <ul>
     *     <li>x = (lon - imageWest) / (imageEast - imageWest) * imageWidth</li>
     *     <li>y = (imageNorth - lat) / (imageNorth - imageSouth) * imageHeight</li>
     * </ul>
     * where the image's geographic bounds are derived from the {@code bounds} object.</p>
     *
     * @param bm          the source Bitmap image
     * @param imageBounds a Bounds object representing the geographic bounds of the image
     * @return a new Bitmap containing only the cropped area inside the polygon
     * @throws IllegalArgumentException if any of the inputs are invalid
     */
    public static Bitmap cropImage(Bitmap bm, Bounds imageBounds, GeoPoint[] points, Boolean bsa) {

        if (bm == null || points == null || points.length == 0 || imageBounds == null) {
            throw new IllegalArgumentException("Invalid input: Bitmap, points, or bounds are not valid.");
        }

        double imgNorth = imageBounds.getNorth();
        double imgSouth = imageBounds.getSouth();
        double imgEast = imageBounds.getEast();
        double imgWest = imageBounds.getWest();

        int imgWidth = bm.getWidth();
        int imgHeight = bm.getHeight();

        // Convert GeoPoints to pixel coordinates.
        // x = (lon - imgWest) / (imgEast - imgWest) * imgWidth
        // y = (imgNorth - lat) / (imgNorth - imgSouth) * imgHeight
        Point[] pixelPoints = new Point[points.length];
        for (int i = 0; i < points.length; i++) {
            double lon = points[i].getLongitude();
            double lat = points[i].getLatitude();
            int x = (int) ((lon - imgWest) / (imgEast - imgWest) * imgWidth);
            int y = (int) ((imgNorth - lat) / (imgNorth - imgSouth) * imgHeight);
            pixelPoints[i] = new Point(x, y);
        }

        // Create a mask bitmap the same size as the original image.
        Bitmap mask = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888);
        Canvas maskCanvas = new Canvas(mask);
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setStyle(Paint.Style.FILL);
        // Clear the mask to transparent.
        maskCanvas.drawColor(Color.TRANSPARENT);

        // Build the polygon path using the converted pixel points.
        Path path = new Path();
        path.moveTo(pixelPoints[0].x, pixelPoints[0].y);
        for (int i = 1; i < pixelPoints.length; i++) {
            path.lineTo(pixelPoints[i].x, pixelPoints[i].y);
        }
        path.close();

        // Draw the polygon on the mask with a solid color.
        maskPaint.setColor(Color.WHITE);
        maskCanvas.drawPath(path, maskPaint);

        // Create a result bitmap to apply the mask.
        Bitmap result = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888);
        Canvas resultCanvas = new Canvas(result);
        Paint resultPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Bitmap myBitmap = bm.copy( Bitmap.Config.ARGB_8888 , true);

        // restyle greyscale BSA to ice schema
        if(bsa){
            for (int x=0; x<myBitmap.getWidth(); x++)
                for (int y=0; y<myBitmap.getHeight(); y++) {

                    // sample red channel for intensity where white is best signal, black is worst
                    int px = (myBitmap.getPixel(x, y) >> 16) & 0xff; // Red

                    /*
                    90% = 229
                    75% = 191
                    60% = 153
                    45% = 115
                    30% = 76
                     */

                    // Transparent
                    int Colour = Color.argb(0, 255, 255, 255);

                    if(px > 76) {
                        Colour = Color.argb(150, 194, 26, 255);
                    }
                    if(px > 115) {
                        Colour = Color.argb(160, 208, 83, 191);
                    }
                    if(px > 153) {
                        Colour = Color.argb(170, 223, 141, 128);
                    }
                    if(px > 191) {
                        Colour = Color.argb(180, 237, 198, 64);
                    }
                    if(px > 229) {
                        Colour = Color.argb(180, 251, 255, 0);
                    }
                    myBitmap.setPixel(x, y, Colour);
                }
        }

        resultCanvas.drawBitmap(myBitmap, 0, 0, resultPaint);

        // Apply the mask using DST_IN mode so only the area inside the polygon is retained.
        resultPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        resultCanvas.drawBitmap(mask, 0, 0, resultPaint);
        resultPaint.setXfermode(null);


        return result;
    }
}