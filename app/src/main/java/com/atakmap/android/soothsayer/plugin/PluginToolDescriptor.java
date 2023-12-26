
package com.atakmap.android.soothsayer.plugin;

import com.atak.plugins.impl.AbstractPluginTool;
import com.atakmap.android.soothsayer.PluginDropDownReceiver;
import gov.tak.api.util.Disposable;

import android.content.Context;

/**
 * Please note:
 *     Support for versions prior to 4.5.1 can make use of a copy of AbstractPluginTool shipped with
 *     the plugin.
 */
public class PluginToolDescriptor extends AbstractPluginTool implements Disposable {

    public PluginToolDescriptor(Context context) {
        super(context,
                context.getString(R.string.app_name),
                context.getString(R.string.app_name),
                context.getResources().getDrawable(R.drawable.ic_launcher),
                PluginDropDownReceiver.SHOW_PLUGIN);
    }

    @Override
    public void dispose() {
    }
}
