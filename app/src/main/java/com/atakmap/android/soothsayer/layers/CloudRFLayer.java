
package com.atakmap.android.soothsayer.layers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.menu.PluginMenuParser;
import com.atakmap.android.soothsayer.CustomPolygonTool;
import com.atakmap.android.soothsayer.GeoImageMasker;
import com.atakmap.android.soothsayer.interfaces.CloudRFLayerListener;
import com.atakmap.android.soothsayer.plugin.R;
import com.atakmap.android.maps.MetaShape;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.layer.AbstractLayer;

import java.util.List;
import java.util.UUID;

public class CloudRFLayer extends AbstractLayer {

    public static final String TAG = "CloudRFLayer";

//    final int[] layerARGB;
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

    public CloudRFLayer(Context plugin, final String name, final String description, final String uri, final List<Double> bounds, final CloudRFLayerListener listener) {
        super(name);

        this.description = description;
        this.fileUri = uri;
        this.cloudRFLayerListener = listener;

        this.upperLeft = GeoPoint.createMutable();
        this.upperRight = GeoPoint.createMutable();
        this.lowerRight = GeoPoint.createMutable();
        this.lowerLeft = GeoPoint.createMutable();

        DrawingShape polygon = CustomPolygonTool.getMaskingPolygon();

        if(polygon != null) {
            GeoImageMasker.Bounds newbounds = GeoImageMasker.getBounds(polygon.getPoints());
            bitmap = GeoImageMasker.cropImage(BitmapFactory.decodeFile(uri),newbounds,polygon);
        }else{
            bitmap = BitmapFactory.decodeFile(uri);
        }

                
//        north, east, south, west
        if(bounds.size() == 4) {
            upperLeft.set(bounds.get(0), bounds.get(3));  // north, west
            upperRight.set(bounds.get(0), bounds.get(1)); // north,east
            lowerRight.set(bounds.get(2), bounds.get(1));  // south,east
            lowerLeft.set(bounds.get(2), bounds.get(3)); // south,west
        }

        layerWidth = bitmap.getWidth();
        layerHeight = bitmap.getHeight();
        Log.d(TAG,
                "decode file: " + uri + " " + layerWidth + " " + layerHeight);
        metaShape = new MetaShape(UUID.randomUUID().toString()) {
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
        };
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
}
