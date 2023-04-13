(Draggable Marker plugin)


_________________________________________________________________
PURPOSE AND CAPABILITIES

This Plugin is used to add markers on the map and user can drag those markers to different places.


_________________________________________________________________
STATUS

(In Progress)

_________________________________________________________________
POINT OF CONTACTS

(Who is developing this)

_________________________________________________________________
PORTS REQUIRED

(This is important for ATO, networking, and other security concerns)

_________________________________________________________________
EQUIPMENT REQUIRED

_________________________________________________________________
EQUIPMENT SUPPORTED

_________________________________________________________________
COMPILATION

_________________________________________________________________
DEVELOPER NOTES

Use below code to generate debug keystore and add it's path in local.properties :
keytool -genkeypair -alias androiddebugkey -keypass android -keystore debug.keystore -storepass android -dname "CN=Android Debug,O=Android,C=US" -validity 9999

Add below in local properties:
_________________________________________________________________
takDebugKeyFile = /Users/poojajoshi/Projects/Plugin/atak-civ/plugin-examples/DraggablePluginGithub/debug.keystore
takDebugKeyFilePassword = android
takDebugKeyAlias = androiddebugkey
takDebugKeyPassword = android
_________________________________________________________________

The assets file describes both a Lifecycle and a ToolDescriptor.   For convention,
these are in the same location used in the AndroidManifest.xml file.    For
readability I have broken out the plugin to be in a directory off of the main
package structure.

When constructing the plugin, it is important to recognize that there are two
different android.content.Context in play.

The plugin context is used to resolve resources from the plugin APK
The mapView context is used for graphic access (AlertDialogs, Toasts, etc).

Note:
The plugin context will cause a runtime error to occur if used to construct an
AlertDialog.
