
package com.cloudrf.android.soothsayer.layers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Pair;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.menu.PluginMenuParser;
import com.cloudrf.android.soothsayer.CustomPolygonTool;
import com.cloudrf.android.soothsayer.GeoImageMasker;
import com.cloudrf.android.soothsayer.interfaces.CloudRFLayerListener;
import com.cloudrf.android.soothsayer.plugin.R;
import com.atakmap.android.maps.MetaShape;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.spatial.file.export.KMZFolder;

import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.GroundOverlay;
import com.ekito.simpleKML.model.Icon;
import com.ekito.simpleKML.model.LatLonBox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CloudRFLayer extends AbstractLayer {

    public static final String TAG = "CloudRFLayer";

    final int layerWidth;
    final int layerHeight;

    final GeoPoint upperLeft;
    final GeoPoint upperRight;
    final GeoPoint lowerRight;
    final GeoPoint lowerLeft;

    final Bitmap bitmap;
    public final String description;

    private final MetaShape metaShape;
    public final String fileUri;

    public final CloudRFLayerListener cloudRFLayerListener;

    public CloudRFLayer(Context plugin, final String name, final String description, final String uri, final List<Double> bounds, final CloudRFLayerListener listener, boolean bsa) {
        super(name);
        this.description = description;
        this.fileUri = uri;
        this.cloudRFLayerListener = listener;
        this.upperLeft = GeoPoint.createMutable();
        this.upperRight = GeoPoint.createMutable();
        this.lowerRight = GeoPoint.createMutable();
        this.lowerLeft = GeoPoint.createMutable();

        // Polygon vertices
        DrawingShape polygon = CustomPolygonTool.getMaskingPolygon();

        if(polygon != null) {
            GeoImageMasker.Bounds newbounds = GeoImageMasker.getBounds(polygon.getPoints());

            // Replace polygon bounds with response bounds as BSA dimensions != Area dimensions
            if(bsa) {
                newbounds.north = bounds.get(0);
                newbounds.east = bounds.get(1);
                newbounds.south = bounds.get(2);
                newbounds.west = bounds.get(3);
            }

            bitmap = GeoImageMasker.cropImage(BitmapFactory.decodeFile(uri),newbounds,polygon,bsa);
        }else{
            bitmap = BitmapFactory.decodeFile(uri);
        }

        //   From API response: north, east, south, west
        if(bounds.size() == 4) {
            upperLeft.set(bounds.get(0), bounds.get(3));  // north, west
            upperRight.set(bounds.get(0), bounds.get(1)); // north, east
            lowerRight.set(bounds.get(2), bounds.get(1)); // south, east
            lowerLeft.set(bounds.get(2), bounds.get(3));  // south, west
        }

        layerWidth = bitmap.getWidth();
        layerHeight = bitmap.getHeight();
        Log.d(TAG, "decode file: " + uri + " " + layerWidth + " " + layerHeight);

        metaShape = new ExportableMetaShape(UUID.randomUUID().toString(), plugin, name);
        metaShape.setMetaString("callsign", name);
        metaShape.setMetaString("shapeName", name);
        metaShape.setMetaBoolean("removable", true);
        metaShape.setType(plugin.getString(R.string.soothsayer_layer));
        metaShape.setMetaString("menu", PluginMenuParser.getMenu(plugin, "menus/layer_menu.xml"));
    }

    public GeoBounds getBounds() {
        return GeoBounds.createFromPoints(getPoints());
    }

    public GeoPoint[] getPoints() {
        return new GeoPoint[]{
                upperLeft, upperRight, lowerRight, lowerLeft
        };
    }

    public MetaShape getMetaShape() {
        return metaShape;
    }

    /**
     * A concrete MetaShape subclass that also implements Exportable so ATAK's
     * KML/KMZ export pipeline can serialise the layer as a GroundOverlay with
     * the coverage PNG bundled inside the KMZ archive.
     */
    private class ExportableMetaShape extends MetaShape implements Exportable {

        ExportableMetaShape(String uid, Context plugin, String name) {
            super(uid);
        }

        @Override
        public GeoPointMetaData[] getMetaDataPoints() {
            return GeoPointMetaData.wrap(CloudRFLayer.this.getPoints());
        }

        @Override
        public GeoPoint[] getPoints() {
            return CloudRFLayer.this.getPoints();
        }

        @Override
        public GeoBounds getBounds(MutableGeoBounds bounds) {
            return CloudRFLayer.this.getBounds();
        }

        // ------------------------------------------------------------------ //
        //  Exportable                                                          //
        // ------------------------------------------------------------------ //

        @Override
        public boolean isSupported(Class<?> target) {
            return Folder.class.equals(target) || KMZFolder.class.equals(target);
        }

        @Override
        public Object toObjectOf(Class<?> target, ExportFilters filters)
                throws FormatNotSupportedException {

            if (!isSupported(target)) {
                throw new FormatNotSupportedException(
                        "CloudRFLayer does not support export to " + target.getName());
            }

            // Build the GroundOverlay feature
            GroundOverlay overlay = new GroundOverlay();
            overlay.setName(CloudRFLayer.this.getName());

            LatLonBox box = new LatLonBox();
            box.setNorth(String.valueOf(upperLeft.getLatitude()));   // north
            box.setSouth(String.valueOf(lowerLeft.getLatitude()));   // south
            box.setEast(String.valueOf(upperRight.getLongitude()));  // east
            box.setWest(String.valueOf(upperLeft.getLongitude()));   // west
            overlay.setLatLonBox(box);

            // Build the KMZFolder (extends Folder, satisfies both target types)
            KMZFolder folder = new KMZFolder();

            String pngName = new File(fileUri).getName();
            String hrefInKmz = "files/" + pngName;

            Icon icon = new Icon();
            if (KMZFolder.class.equals(target)) {
                // Bundle the PNG into the KMZ archive
                icon.setHref(hrefInKmz);
                folder.getFiles().add(new Pair<>(fileUri, hrefInKmz));
            } else {
                // KML-only export: reference the PNG by its absolute path
                icon.setHref("file://" + fileUri);
            }
            overlay.setIcon(icon);

            List<Feature> features = new ArrayList<>();
            features.add(overlay);
            folder.setFeatureList(features);

            return folder;
        }
    }
}
