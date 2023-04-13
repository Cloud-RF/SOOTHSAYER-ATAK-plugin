
package com.atakmap.android.draggablemarker.plugin;


import com.atak.plugins.impl.AbstractPluginLifecycle;
import com.atakmap.android.draggablemarker.PluginMapComponent;
import android.content.Context;


/**
 * Please note:
 *     Support for versions prior to 4.5.1 can make use of a copy of AbstractPluginLifeCycle shipped with
 *     the plugin.
 */
public class PluginLifecycle extends AbstractPluginLifecycle {

    private final static String TAG = "PluginTemplateLifecycle";

    public PluginLifecycle(Context ctx) {
        super(ctx, new PluginMapComponent());
        PluginNativeLoader.init(ctx);
    }

}
