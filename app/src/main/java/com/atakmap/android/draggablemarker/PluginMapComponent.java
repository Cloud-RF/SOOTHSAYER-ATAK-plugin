
package com.atakmap.android.draggablemarker;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.draggablemarker.layers.PluginMapOverlay;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDownMapComponent;

import com.atakmap.coremap.log.Log;
import com.atakmap.android.draggablemarker.plugin.R;

public class PluginMapComponent extends DropDownMapComponent {

    private static final String TAG = "PluginTemplateMapComponent";

    private Context pluginContext;

    private PluginDropDownReceiver ddr;
    private PluginMapOverlay mapOverlay;

    public void onCreate(final Context context, Intent intent,
            final MapView view) {

        context.setTheme(R.style.ATAKPluginTheme);
        super.onCreate(context, intent, view);
        pluginContext = context;
        //Plugin MapOverlay added to Overlay Manager.
        this.mapOverlay = new PluginMapOverlay(view, pluginContext);
        view.getMapOverlayManager().addOverlay(this.mapOverlay);
        ddr = new PluginDropDownReceiver(
                view, context, mapOverlay);

        Log.d(TAG, "registering the plugin filter");
        DocumentedIntentFilter ddFilter = new DocumentedIntentFilter();
        ddFilter.addAction(PluginDropDownReceiver.SHOW_PLUGIN);
        ddFilter.addAction(PluginDropDownReceiver.LAYER_VISIBILITY,
                "Toggle visibility of kmz layer");
        ddFilter.addAction(PluginDropDownReceiver.LAYER_DELETE,
                "Delete kmz layer");
        registerDropDownReceiver(ddr, ddFilter);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);
        view.getMapOverlayManager().removeOverlay(mapOverlay);
    }

}
