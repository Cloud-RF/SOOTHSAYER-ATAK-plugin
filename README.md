# SOOTHSAYER ATAK plugin


_________________________________________________________________
PURPOSE AND CAPABILITIES

This plugin is a client to the CloudRF / SOOTHSAYER radio planning API. 

Presently it can task the `Area` and `Multisite` APIs to make heatmaps for RF propagation based upon system templates loaded from the SD card.

![SOOTHSAYER ATAK plugin](help/soothsayer_atak.jpg "SOOTHSAYER ATAK plugin")
_________________________________________________________________
STATUS

Under active development

_________________________________________________________________
POINTS OF CONTACT

Public issue board: https://github.com/Cloud-RF/SOOTHSAYER-ATAK-plugin

Developer Email: support@cloudrf.com

_________________________________________________________________
PORTS REQUIRED

Outgoing: TCP 443

_________________________________________________________________
EQUIPMENT REQUIRED

You will need a JSON template describing a radio. You can find examples in this repo and these must be placed on the SD card in the atak/SOOTHSAYER folder.

For operation on a LAN you will require a SOOTHSAYER server on your network. https://cloudrf.com/soothsayer
_________________________________________________________________
EQUIPMENT SUPPORTED

ATAK 4.3+
_________________________________________________________________
COMPILATION

1. Open Plugin folder in Android studio
2. Open a terminal and issue ./gradlew assembleCivDebug --info 

_________________________________________________________________
DEVELOPER NOTES

1. Install a legacy Java release like 8 or 11 but not 17. JDK 17 does not play well with old Java and doing this early on heads off build issues later. Set the JDK version from the File > Project Structure menu
2. Download Android studio https://developer.android.com/studio
3. Unzip it, open a terminal in the folder then run bin/studio.sh to start
4. Choose a standard setup and accept the terms for both packages, let it install APIs
5. Fetch an ATAK CIV SDK release from Github https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV/releases
6. Unzip the ATAK CIV release and rename the folder to include the version eg. mv atak-civ atak-civ-x.x
7. Choose open project, navigate to atak-civ-x.x/plugin-examples/helloworld
8. Edit app/build.gradle near line ~200 and move "getIsDefault().set(true)" into the civ {} block to default to ATAK CIV
9. Make your signing key and move the .jks key file into your app folder:

```
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-alias
```

Set these strings within local.properties 

```
takDebugKeyFile=my-release-key.jks
takDebugKeyFilePassword=android
takDebugKeyAlias=my-alias
takDebugKeyPassword=android

takReleaseKeyFile=my-release-key.jks
takReleaseKeyFilePassword=android
takReleaseKeyAlias=my-alias
takReleaseKeyPassword=android
```

10. Open Tools, Device manager and create a Pixel 5 with Android Image version 28 (or whatever matches your build.gradle) and a VM heap of 512MB. Boot it up.
11. Find atak.apk from the SDK folder and drag it onto the emulator to start installation. Complete the ATAK install wizard and approve all permissions.
12. With ATAK running, run the plugin. Expect an ATAK prompt.