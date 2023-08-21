# SOOTHSAYER ATAK Plugin

## Purpose and Capabilities

This plugin is a client to the CloudRF / SOOTHSAYER radio planning API. 

Presently it can task the `Area` and `Multisite` APIs to make heatmaps for RF propagation based upon system templates loaded from the SD card.

![SOOTHSAYER ATAK plugin](help/soothsayer_atak.jpg "SOOTHSAYER ATAK plugin")

## Status

Under active development.

## Points of Contact

- Public issue board: https://github.com/Cloud-RF/SOOTHSAYER-ATAK-plugin
- Developer email: [support@cloudrf.com](mailto:support@cloudrf.com)

## Ports Required

Outgoing: TCP 443

## Equipment Required

You will need a JSON template describing a radio. You can find examples in this repository and these must be placed on the SD card in the atak/SOOTHSAYER folder.

For operation on a LAN you will require a SOOTHSAYER server on your network. For more information please consult https://cloudrf.com/soothsayer.

## Equipment Supported

ATAK 4.3+

## Compilation

1. Open Plugin folder in Android studio
2. Open a terminal and issue `./gradlew assembleCivDebug --info`

## Developer Notes

You can follow these notes to complete a build of the plugin and have it working in a virtual Android environment with Android Studio. These notes have been written from the viewpoint of an Ubuntu machine. You may need to adjust for other systems if you are not using Ubuntu.

1. Install a legacy Java release like 8 or 11, but not 17. JDK 17 does not play well with old Java and doing this early on heads off build issues later.
2. Download Android studio https://developer.android.com/studio.
3. Unzip the download of Android studio, open a terminal in the folder then run `bin/studio.sh` to start.
4. Choose a standard setup and accept the terms for both packages, let it install APIs.
5. Fetch an ATAK CIV SDK release from https://tak.gov. You can find this by logging in and then navigating to "Products" > "ATAK-CIV". Select your version (this has been tested with "ATAK-CIV 4.8.1"). Scroll down on the page to the "Downloadable Resources" section and click on the "Developer Resources" and then download the ZIP of the SDK for the version you just specified.
6. Unzip the ATAK CIV release and you may wish to rename the folder to include the version eg. `mv atak-civ atak-civ-x.x`, this will allow you to better identify which version you are working with if you have multiple versions on your system.
7. If a `plugin-examples` directory doesn't exist in the ATAK CIV release then create one.
8. Copy a clone of this `SOOTHSAYER-ATAK-plugin` repository to the created `plugin-examples` directory.
9. Open the `SOOTHSAYER-ATAK-plugin` directory you just copied in Android Studio.
10. If you have multiple Java versions on your system you may need to set the JDK version from the "File" > "Project Structure" menu.
11. Allow several minutes for Android Studio to download all dependencies and external libraries.
12. Open "Tools" > "Device "Manager" and create a Pixel 5 with Android Image version 28 (version 28 has been tested to work). At this point you may need to download the Android image if it doesn't already exist on your system. When on the "Verify Configuration" menu click on the "Show Advanced Settings" button and in the "Memory and Storage" section set the "VM heap" to 512MB.
13. Generate a `debug.keystore` - you can use default values for all of the prompts:
    
    ```bash
    keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000
    ```
    
14. In the "Device Manager" start the Pixel 5 image you created previously. This may take several minutes to fully start.
15. Take the `atak.apk` file which is included in the root directory of the ATAK version which you downloaded and then drag/drop it over the Pixel 5 device. This will install ATAK on the Pixel 5 device.
16. Load up the ATAK app in the Pixel 5 device.
17. Rotate the Pixel 5 device into landscape mode so that you can work with it easier.
18. Accept the EULA and allow all of the permissions which are prompted for the first time when you start ATAK.
19. When you are prompted for "TAK Device Setup" you can skip this and just press "Done".
20. Allow and enable the prompt which disabled battery optimisation.
21. If all is working as expected then ATAK should be loaded and it should show "DEVELOPER BUILD" in red letters at the bottom of the viewer.
22. In Android Studio select "Run" > "Run app". This will build and compile the plugin. After a successful build you will be prompted on ATAK "Load plugin: SOOTHSAYER. Would you like to load this installed plugin into ATAK? SOOTHSAYER".

## Third-Party Signing

The tak.gov documentation has a bug at the time of writing which assumes the public have access to the maven repo.
This command in particular does not work, don't worry about it:

```
./gradlew -Ptakrepo.force=true -Ptakrepo.url=https://artifacts.tak.gov/artifactory/maven ....
```
When you zip the code and upload it, expect to fail the first few times. Review the debug.log in the TPP page at tak.gov to find out why and if you can't get the solution, search for it in the `TAK Community` discord as it's been asked before!

