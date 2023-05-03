
package com.atakmap.android.soothsayer.layers;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.atakmap.android.soothsayer.plugin.R;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapView.RenderStack;
import com.atakmap.android.maps.MetaShape;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.menu.MenuLayoutWidget;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.Layer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Example map overlay that is displayed within Overlay Manager
 */
public class PluginMapOverlay extends AbstractMapOverlay2 {

    private static final String TAG = "PluginMapOverlay";

    private final MapView _mapView;
    private final Context _plugin;
    private final PluginDeepMapItemQuery _query;
    private final DefaultMapGroup _group;

    private PluginListModel _listModel;

    public PluginMapOverlay(MapView mapView, Context plugin) {
        _mapView = mapView;
        _plugin = plugin;
        _query = new PluginDeepMapItemQuery();
        _group = new DefaultMapGroup("Plugin Map Group");
        _group.setMetaBoolean("addToObjList", false);
    }

    @Override
    public String getIdentifier() {
        return TAG;
    }

    @Override
    public String getName() {
        return _plugin.getString(R.string.app_name);
    }

    @Override
    public MapGroup getRootGroup() {
        return _group;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return _query;
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter adapter,
                                          long capabilities, HierarchyListFilter prefFilter) {
        if (_listModel == null)
            _listModel = new PluginListModel();
        _listModel.refresh(adapter, prefFilter);
        return _listModel;
    }

    private List<CloudRFLayer> getLayers() {
        List<CloudRFLayer> ret = new ArrayList<>();
        List<Layer> layers = _mapView.getLayers(
                RenderStack.MAP_SURFACE_OVERLAYS);
        for (Layer l : layers) {
            if (l instanceof CloudRFLayer) {
                CloudRFLayer el = (CloudRFLayer) l;
                MetaShape shape = el.getMetaShape();
                if (shape.getGroup() == null)
                    _group.addItem(shape);
                ret.add(el);
            }
        }
        return ret;
    }

    public CloudRFLayer findLayer(String uid) {
        for (CloudRFLayer l : getLayers()) {
            if (l.getMetaShape().getUID().equals(uid))
                return l;
        }
        return null;
    }

//    private List<ExampleMultiLayer> getMultiLayers() {
//        List<ExampleMultiLayer> ret = new ArrayList<>();
//        List<Layer> layers = _mapView.getLayers(
//                RenderStack.MAP_SURFACE_OVERLAYS);
//        for (Layer l : layers) {
//            if (l instanceof ExampleMultiLayer) {
//                ExampleMultiLayer el = (ExampleMultiLayer) l;
//                MetaShape shape = el.getMetaShape();
//                if (shape.getGroup() == null)
//                    _group.addItem(shape);
//                ret.add(el);
//            }
//        }
//        return ret;
//    }

//    public ExampleMultiLayer findMultiLayer(String uid) {
//        for (ExampleMultiLayer l : getMultiLayers()) {
//            if (l.getMetaShape().getUID().equals(uid))
//                return l;
//        }
//        return null;
//    }

    public class PluginListModel extends AbstractHierarchyListItem2
            implements Search, Visibility2, View.OnClickListener, CompoundButton.OnCheckedChangeListener {

//        private final static String TAG = "PluginListModel";

        private View _footer;
        private ToggleButton _vizBtn, _outlineBtn;

        public PluginListModel() {
            this.asyncRefresh = true;
        }

        @Override
        public String getTitle() {
            return PluginMapOverlay.this.getName();
        }

        @Override
        public String getIconUri() {
            return "android.resource://" + _plugin.getPackageName()
                    + "/" + R.drawable.ic_launcher;
        }

        public int getPreferredListIndex() {
            return 5;
        }

        @Override
        public int getDescendantCount() {
            return 0;
        }

        @Override
        public Object getUserObject() {
            return this;
        }

        @Override
        public View getExtraView() {
            return null;
        }

        @Override
        public View getHeaderView() {
//            if (_header == null) {
//                _header = LayoutInflater.from(_plugin).inflate(
//                        R.layout.overlay_header, _mapView, false);
//                _header.findViewById(R.id.header_button)
//                        .setOnClickListener(this);
//            }
//            return _header;
            return super.getHeaderView();
        }

        @Override
        public View getFooterView() {
//            if (_footer == null) {
//                _footer = LayoutInflater.from(_plugin).inflate(
//                        R.layout.overlay_footer, _mapView, false);
//                _footer.findViewById(R.id.footer_button)
//                        .setOnClickListener(this);
//            }
//            return _footer;
//            return super.getFooterView();

            if (_footer == null) {
                _footer = LayoutInflater.from(_plugin).inflate(
                        R.layout.overlay_footer, _mapView, false);
                _vizBtn = _footer.findViewById(R.id.grg_visible_toggle);
                _outlineBtn = _footer.findViewById(R.id.layer_outline_toggle);
            }

            _outlineBtn.setOnCheckedChangeListener(null);
//            _outlineBtn.setChecked(_prefs.get("grgs.outlines-visible", true));
            _outlineBtn.setOnCheckedChangeListener(this);

            int viz = getVisibility();
            _vizBtn.setOnCheckedChangeListener(null);
            if (viz != Visibility2.INVISIBLE) {
                String text = _plugin.getString(viz == Visibility2.VISIBLE
                        ? R.string.visible_on
                        : R.string.visible_partial);
                _vizBtn.setTextOn(text);
            }
            _vizBtn.setChecked(viz != Visibility2.INVISIBLE);
            _vizBtn.setOnCheckedChangeListener(this);

            return _footer;
        }

        @Override
        public View getCustomLayout() {
            return super.getCustomLayout();
        }

        @Override
        public void refreshImpl() {
            List<HierarchyListItem> filtered = new ArrayList<>();
            List<CloudRFLayer> layers = getLayers();
            for (CloudRFLayer l : layers) {
                LayerHierarchyListItem item = new LayerHierarchyListItem(l,l.description);
                if (this.filter.accept(item))
                    filtered.add(item);
            }
//            List<ExampleMultiLayer> multilayers = getMultiLayers();
//            for (ExampleMultiLayer ml : multilayers) {
//                LayerHierarchyListItem item = new LayerHierarchyListItem(ml);
//                if (this.filter.accept(item))
//                    filtered.add(item);
//            }

            // Sort
            sortItems(filtered);

            // Update
            updateChildren(filtered);
        }

        @Override
        public void dispose() {
            disposeChildren();
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        @Override
        public boolean isMultiSelectSupported() {
            return false;
        }

        @Override
        public boolean setVisible(boolean visible) {
            List<Visibility> actions = getChildActions(Visibility.class);
            boolean ret = !actions.isEmpty();
            for (Visibility del : actions)
                ret &= del.setVisible(visible);
            return ret;
        }

        @Override
        public Set<HierarchyListItem> find(String searchTerms) {
            searchTerms = searchTerms.toLowerCase();
            Set<HierarchyListItem> results = new HashSet<>();
            List<HierarchyListItem> items = getChildren();
            for (HierarchyListItem item : items) {
                if (item.getTitle().toLowerCase().contains(searchTerms))
                    results.add(item);
            }
            return results;
        }

        @Override
        public void onClick(View v) {
            if (v instanceof Button)
                Toast.makeText(_mapView.getContext(),
                        ((Button) v).getText(),
                        Toast.LENGTH_LONG).show();
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            int id = buttonView.getId();
            if (id == R.id.layer_outline_toggle) {
//                List<CloudRFLayer> layers = getLayers();
//                if(!layers.isEmpty()) {
//                    layers.get(0).cloudRFLayerListener.toggleVisibility(isChecked, layers);
//                }
            } else if (id == R.id.grg_visible_toggle) {
                setVisible(isChecked);
            }
            getFooterView();
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    HierarchyListReceiver.REFRESH_HIERARCHY));
        }
    }

    private class LayerHierarchyListItem extends AbstractHierarchyListItem2
            implements Visibility, GoTo, MapItemUser, View.OnClickListener {

        private final CloudRFLayer _layer;
        private final String _description;
//        private final ExampleMultiLayer _multilayer;

        LayerHierarchyListItem(CloudRFLayer layer, String description) {
            _layer = layer;
            _description = description;
//            _multilayer = null;
        }

//        LayerHierarchyListItem(ExampleMultiLayer multilayer) {
//            _multilayer = multilayer;
//            _layer = null;
//        }

        @Override
        public View getExtraView(View convertView, ViewGroup parent) {
            ExtraHolder holder = convertView != null && convertView.getTag() instanceof ExtraHolder
                    ? (ExtraHolder) convertView.getTag()
                    : null;
            if (holder == null) {
                holder = new ExtraHolder();
                convertView = LayoutInflater.from(_plugin).inflate(
                        R.layout.overlay_list_item_extra, parent, false);
                holder.delete = convertView.findViewById(R.id.grg_delete);
                convertView.setTag(holder);
            }
            if (this.listener instanceof HierarchyListAdapter
                    && ((HierarchyListAdapter) this.listener)
                    .getSelectHandler() != null) {
                // Hide delete buttons when selecting
                convertView.setVisibility(View.GONE);
                holder.delete.setOnClickListener(null);
            } else {
                convertView.setVisibility(View.VISIBLE);
                holder.delete.setOnClickListener(this);
            }
            return convertView;
        }

        @Override
        public String getTitle() {
            if (_layer != null) {
                return _layer.getName();
            }
//            if (_multilayer != null) {
//                return _multilayer.getName();
//            }
            return "";
        }

        @Override
        public String getDescription() {
//            return _plugin.getString(R.string.example_layer_description);
            return _description;
        }

        @Override
        public String getIconUri() {
            return "android.resource://" + _mapView.getContext()
                    .getPackageName() + "/"
                    + com.atakmap.app.R.drawable.ic_overlay_gridlines;
        }

        @Override
        public Object getUserObject() {
            return _layer;
//            if (_multilayer != null) {
//                return _multilayer;
//            }
        }

        @Override
        public boolean isChildSupported() {
            return false;
        }

        @Override
        public int getDescendantCount() {
            return 0;
        }

        @Override
        public void refreshImpl() {
        }

        @Override
        public boolean hideIfEmpty() {
            return false;
        }

        @Override
        public boolean setVisible(boolean visible) {
            if (_layer != null) {
                if (visible != _layer.isVisible()) {
                    _layer.setVisible(visible);
                    return true;
                }
            }
//            if (_multilayer != null) {
//                if (visible != _multilayer.isVisible()) {
//                    _multilayer.setVisible(visible);
//                    return true;
//                }
//            }
            return false;
        }

        @Override
        public boolean isVisible() {
            if (_layer != null) {
                return _layer.isVisible();
            }
//            if (_multilayer != null) {
//                return _multilayer.isVisible();
//            }
            return false;
        }

        @Override
        public MapItem getMapItem() {
            if (_layer != null) {
                return _layer.getMetaShape();
            }
//            if (_multilayer != null) {
//                return _multilayer.getMetaShape();
//            }
            return null;
        }

        @Override
        public boolean goTo(boolean select) {
            if (_layer != null) {
                ATAKUtilities.scaleToFit(_mapView, _layer.getPoints(),
                        _mapView.getWidth(), _mapView.getHeight());
                if (select) {
                    MenuLayoutWidget mw = MapMenuReceiver.getMenuWidget();
                    if (mw != null) {
                        mw.openMenuOnItem(_layer.getMetaShape());
                        return true;
                    }
                }
            }
//            if (_multilayer != null) {
//                ATAKUtilities.scaleToFit(_mapView, _multilayer.getPoints(),
//                        _mapView.getWidth(), _mapView.getHeight());
//                if (select) {
//                    MenuLayoutWidget mw = MapMenuReceiver.getMenuWidget();
//                    if (mw != null) {
//                        mw.openMenuOnItem(_multilayer.getMetaShape());
//                        return true;
//                    }
//                }
//            }
            return false;
        }

        @Override
        public void onClick(View v) {
            int i = v.getId();
            if (i == R.id.grg_delete) {
                _layer.cloudRFLayerListener.delete(_layer);
            }
        }
    }



    private class PluginDeepMapItemQuery implements DeepMapItemQuery {

        @Override
        public MapItem deepFindItem(Map<String, String> metadata) {
            return null;
        }

        @Override
        public List<MapItem> deepFindItems(Map<String, String> metadata) {
            return null;
        }

        @Override
        public MapItem deepFindClosestItem(GeoPoint location, double threshold,
                                           Map<String, String> metadata) {
            return null;
        }

        @Override
        public Collection<MapItem> deepFindItems(GeoPoint location,
                                                 double radius, Map<String, String> metadata) {
            return null;
        }

        @Override
        public MapItem deepHitTest(int xpos, int ypos, GeoPoint point,
                                   MapView view) {
            for (CloudRFLayer l : getLayers()) {
                if (l.isVisible() && l.getBounds().contains(point))
                    return l.getMetaShape();
            }
//            for (ExampleMultiLayer l : getMultiLayers()) {
//                if (l.isVisible() && l.getBounds().contains(point))
//                    return l.getMetaShape();
//            }
            return null;
        }

        @Override
        public SortedSet<MapItem> deepHitTestItems(int xpos, int ypos,
                                                   GeoPoint point, MapView view) {
            SortedSet<MapItem> ret = new TreeSet<>(
                    MapItem.ZORDER_HITTEST_COMPARATOR);
            for (CloudRFLayer l : getLayers()) {
                if (l.isVisible() && l.getBounds().contains(point))
                    ret.add(l.getMetaShape());
            }
//            for (ExampleMultiLayer l : getMultiLayers()) {
//                if (l.isVisible() && l.getBounds().contains(point))
//                    ret.add(l.getMetaShape());
//            }
            return ret;
        }

        @Override
        public Collection<MapItem> deepFindItems(GeoBounds bounds,
                                                 Map<String, String> metadata) {
            SortedSet<MapItem> ret = new TreeSet<>(
                    MapItem.ZORDER_HITTEST_COMPARATOR);
            for (CloudRFLayer l : getLayers()) {
                if (l.isVisible() && l.getBounds().intersects(bounds))
                    ret.add(l.getMetaShape());
            }
//            for (ExampleMultiLayer l : getMultiLayers()) {
//                if (l.isVisible() && l.getBounds().intersects(bounds))
//                    ret.add(l.getMetaShape());
//            }
            return ret;
        }
    }

    private static class ExtraHolder {
        View delete, send;
    }
}
