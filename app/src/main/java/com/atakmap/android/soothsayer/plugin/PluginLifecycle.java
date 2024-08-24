
package com.atakmap.android.soothsayer.plugin;


import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.soothsayer.PluginMapComponent;
import gov.tak.api.plugin.IServiceController;


/**
 * Please note:
 *     Support for versions prior to 4.5.1 can make use of a copy of AbstractPluginLifeCycle shipped with
 *     the plugin.
 */

public class PluginLifecycle  extends AbstractPlugin {

    public PluginLifecycle(IServiceController serviceController) {
        super(serviceController, new PluginToolDescriptor(serviceController.getService(PluginContextProvider.class).getPluginContext()), new PluginMapComponent());
    }
}