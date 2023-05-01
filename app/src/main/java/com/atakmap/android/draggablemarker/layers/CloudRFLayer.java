
package com.atakmap.android.draggablemarker.layers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.android.draggablemarker.plugin.R;
import com.atakmap.android.maps.MetaShape;
import com.atakmap.android.menu.PluginMenuParser;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.layer.AbstractLayer;

import java.nio.ByteBuffer;
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

    private final Context pluginContext;
    private final MetaShape metaShape;

    public CloudRFLayer(Context plugin, final String name, final String uri, final List<Double> bounds) {
        super(name);

        this.pluginContext = plugin;

        this.upperLeft = GeoPoint.createMutable();
        this.upperRight = GeoPoint.createMutable();
        this.lowerRight = GeoPoint.createMutable();
        this.lowerLeft = GeoPoint.createMutable();
//        this.upperLeft = new GeoPoint(north,west);
//        this.upperRight = new GeoPoint(north,east);
//        this.lowerRight = new GeoPoint(south,east);
//        this.lowerLeft =  new GeoPoint(south,west);

        bitmap = BitmapFactory.decodeFile(uri);

//        north, east, south, west
        if(bounds.size() == 4) {
            upperLeft.set(bounds.get(0), bounds.get(3));
            upperRight.set(bounds.get(0), bounds.get(1));
            lowerRight.set(bounds.get(2), bounds.get(1));
            lowerLeft.set(bounds.get(2), bounds.get(3));
        }

        layerWidth = bitmap.getWidth();
        layerHeight = bitmap.getHeight();
        Log.d(TAG,
                "decode file: " + uri + " " + layerWidth + " " + layerHeight);
//        layerARGB = new int[layerHeight * layerWidth];

//        bitmap.getPixels(layerARGB, 0, layerWidth, 0, 0, layerWidth,
//                layerHeight);

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
        metaShape.setMetaString("callsign", TAG);
        metaShape.setMetaString("shapeName", TAG);
        metaShape.setType(plugin.getString(R.string.soothsayer_layer));
        metaShape.setMetaString("menu", PluginMenuParser.getMenu(
                pluginContext, "menus/layer_menu.xml"));
//        bitmap.recycle();
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
