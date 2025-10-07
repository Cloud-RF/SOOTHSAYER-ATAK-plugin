#!/bin/bash

# Script to build and install SOOTHSAYER ATAK plugin to ALL connected devices/emulators
# Usage: ./install-soothsayer-plugin.sh

set -e

# Find the latest built APK
APK_DIR="app/build/outputs/apk/civ/debug"
PLUGIN_PACKAGE="com.atakmap.android.soothsayer.plugin"
PLUGIN_NAME="SOOTHSAYER"
ATAK_PACKAGE="com.atakmap.app.civ"

echo "=== $PLUGIN_NAME Plugin Builder & Multi-Device Installer ==="
echo ""

# Configure Java 17
echo "Configuring Java 17 for build..."

# Function to find Java 17 installation
find_java17() {
    local java_candidates=(
        "/usr/lib/jvm/java-17-openjdk"
        "/usr/lib/jvm/java-17-openjdk-amd64"
        "/usr/lib/jvm/adoptium-17-hotspot"
        "/usr/lib/jvm/temurin-17-jdk"
        "/opt/homebrew/opt/openjdk@17"
        "/usr/local/opt/openjdk@17"
        "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
        "/Library/Java/JavaVirtualMachines/adoptopenjdk-17.jdk/Contents/Home"
        "/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home"
    )
    
    # First try to find Java 17 using java_home (macOS)
    if command -v /usr/libexec/java_home &> /dev/null; then
        if JAVA17_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null); then
            echo "$JAVA17_HOME"
            return 0
        fi
    fi
    
    # Try common installation paths
    for candidate in "${java_candidates[@]}"; do
        if [ -d "$candidate" ] && [ -x "$candidate/bin/java" ]; then
            echo "$candidate"
            return 0
        fi
    done
    
    return 1
}

# Find and set Java 17
if JAVA17_HOME=$(find_java17); then
    export JAVA_HOME="$JAVA17_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "✓ Found Java 17 at: $JAVA_HOME"
else
    echo "Error: Java 17 not found!"
    echo "Please install Java 17 using one of these methods:"
    echo "  • macOS: brew install openjdk@17"
    echo "  • Ubuntu/Debian: sudo apt install openjdk-17-jdk"
    echo "  • CentOS/RHEL: sudo yum install java-17-openjdk-devel"
    echo "  • Or download from: https://adoptium.net/temurin/releases/"
    exit 1
fi

# Verify Java version
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2)
echo "Using Java version: $JAVA_VERSION"

if [[ ! "$JAVA_VERSION" =~ ^17\. ]]; then
    echo "Warning: Java version is $JAVA_VERSION, expected 17.x"
    echo "Continuing anyway..."
fi

echo ""

# Verify Java is accessible
echo "Verifying Java 17 is accessible..."
if ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q "17\."; then
    echo "Error: Java 17 verification failed at $JAVA_HOME"
    exit 1
fi
echo "✓ Java 17 verified and ready for build"
echo ""

# Step 1: Build the plugin
echo "Step 1: Building $PLUGIN_NAME plugin..."
echo "Running: ./gradlew clean assembleCivDebug with Java 17"
./gradlew clean assembleCivDebug -Dorg.gradle.java.home="$JAVA_HOME"

if [ $? -ne 0 ]; then
    echo "Error: Build failed!"
    exit 1
fi
echo "✓ Build completed successfully"

# Step 2: Find the APK
echo ""
echo "Step 2: Finding plugin APKs..."
if [ ! -d "$APK_DIR" ]; then
    echo "Error: Build directory not found even after successful build."
    exit 1
fi

# Find all recent APK files
APK_FILES=$(ls -t "$APK_DIR"/ATAK-Plugin-${PLUGIN_NAME}-*.apk 2>/dev/null)

if [ -z "$APK_FILES" ]; then
    echo "Error: No plugin APKs found in $APK_DIR"
    exit 1
fi

# Step 3: Get list of connected devices
echo ""
echo "Step 3: Getting list of connected devices/emulators..."
DEVICE_LIST=$(adb devices | grep -v "List of devices attached" | grep "device$" | awk '{print $1}')

if [ -z "$DEVICE_LIST" ]; then
    echo "Error: No devices/emulators found. Please connect a device or start an emulator."
    exit 1
fi

echo "Found devices:"
for DEVICE_ID in $DEVICE_LIST; do
    echo "  - $DEVICE_ID"
done

# Step 4 & 5: Loop through each APK and then each device to uninstall, install, and verify
for APK_FILE in $APK_FILES; do
    echo ""
    echo "====================================================="
    echo "Processing APK: $APK_FILE"
    echo "Size: $(ls -lh "$APK_FILE" | awk '{print $5}')"
    echo "====================================================="

    for DEVICE_ID in $DEVICE_LIST; do
        echo ""
        echo "-----------------------------------------------------"
        echo "Processing device: $DEVICE_ID for $APK_FILE"
        echo "-----------------------------------------------------"

        echo ""
        echo "Step 4.1 ($DEVICE_ID): Checking for existing plugin installation..."
        if adb -s "$DEVICE_ID" shell pm list packages | grep -q "$PLUGIN_PACKAGE"; then
            echo "Found existing plugin on $DEVICE_ID, uninstalling..."
            adb -s "$DEVICE_ID" uninstall "$PLUGIN_PACKAGE"
            echo "Old plugin uninstalled from $DEVICE_ID"
        else
            echo "No existing plugin found on $DEVICE_ID"
        fi

        echo ""
        echo "Step 4.2 ($DEVICE_ID): Installing $PLUGIN_NAME plugin..."
        adb -s "$DEVICE_ID" install -r "$APK_FILE"

        echo ""
        echo "Step 5 ($DEVICE_ID): Verifying installation..."
        if adb -s "$DEVICE_ID" shell pm list packages | grep -q "$PLUGIN_PACKAGE"; then
            echo "✓ $PLUGIN_NAME plugin installed successfully on $DEVICE_ID from $APK_FILE"
            
            # Get version info
            VERSION_INFO=$(adb -s "$DEVICE_ID" shell dumpsys package "$PLUGIN_PACKAGE" | grep -E "versionCode|versionName" | head -2)
            echo ""
            echo "($DEVICE_ID) Version info:"
            echo "$VERSION_INFO"
            
            # Check if ATAK is installed
            echo ""
            if adb -s "$DEVICE_ID" shell pm list packages | grep -q "$ATAK_PACKAGE"; then
                echo "✓ ($DEVICE_ID) ATAK (CIV) is installed"
            else
                echo "⚠ ($DEVICE_ID) ATAK (CIV) not found - please install ATAK first"
            fi
            
        else
            echo "✗ ($DEVICE_ID) Plugin installation FAILED for $APK_FILE. Skipping further checks for this device."
        fi
    done
done

echo ""
echo "====================================================="
echo "=== Multi-Device Installation Complete            ==="
echo "====================================================="
echo ""

echo "Next steps (for each device):"
echo "1. Open ATAK"
echo "2. Go to Settings → Tool Preferences → $PLUGIN_NAME"
echo "3. Configure your API credentials"
echo "4. Use the $PLUGIN_NAME icon in the toolbar to access the plugin"
echo ""
echo "Troubleshooting (for each device):"
echo "- Check 'adb -s <DEVICE_ID> logcat | grep -E \"$PLUGIN_NAME\"' for debug logs"
echo "- Ensure you have a working internet connection"
echo "- Verify your API credentials are correct" 