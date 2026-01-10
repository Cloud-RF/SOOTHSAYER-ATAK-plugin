# Plugin signing issues

Following the development of this plugin we had a lot of trouble getting it to work with the release version of ATAK, despite working fine in the debug environment...

We were uploading our source to https://tak.gov/user_builds and passing the signing process with SUCCESS. Only once we loaded it onto a Android device with the Play store version did we notice either UI widgets were missing or it was throwing fatal AbstractErrors due to messed up libraries.

After what seemed like an infinite amount of Proguard changes we achieved success by upgrading our libraries.

## Upgrade your gradle

We followed recommendations from Github and public repos to use libraries which worked for ATAK 4.6 like Gradle 4.2.2 etc. Turns out this was our first mistake.

The one line which made this happen was in build.gradle:

            classpath('com.android.tools.build:gradle:7.2.2') {

Check your version with ./gradlew --version

## Working config for 4.8.1 release

Install gradle 7.2.2

Remove the "android.enableR8=false" line entirely! Trust me.

## build.gradle

    buildscript {
        ext.kotlin_version = '1.6.0'
        repositories {
            google()
            mavenCentral()
            maven {
                url "https://jitpack.io"
            }
        }
        dependencies {
            classpath('com.android.tools.build:gradle:7.2.2') {
                exclude group: "net.sf.proguard", module: "proguard-gradle"
            }
            classpath 'com.guardsquare:proguard-gradle:7.1.0'

            classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
            classpath "org.jetbrains.kotlin:kotlin-serialization:$kotlin_version"
        }


    }

    allprojects {
        repositories {
            google()
            //noinspection JcenterRepositoryObsolete
            jcenter()
            mavenCentral()
            maven {
                url "https://jitpack.io"
            }
        }
    }

    task clean(type: Delete) {
        delete rootProject.buildDir
    }


## gradle.properties

    org.gradle.jvmargs=-Xmx4g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8
    android.useAndroidX=true
    android.bundle.enableUncompressedNativeLibs=false
    org.gradle.caching=false
    android.enableJetifier=true

## proguard-gradle.txt

    # This works with ATAK release 4.8.1
    # Don't ask why we're packing and obfuscating an open source plugin - it works

    -dontskipnonpubliclibraryclasses
    -dontshrink
    -dontoptimize

    # ACRA specifics
    -renamesourcefileattribute SourceFile
    -keepattributes SourceFile,LineNumberTable

    -applymapping <atak.proguard.mapping>

    # Change soothsayer to your plugin name
    -repackageclasses atakplugin.soothsayer

    -keepattributes *Annotation*
    -keepattributes Signature, InnerClasses

    -keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
    }

    -keepclassmembers class **.R$* {
        public static <fields>;
    }

    # For enumeration classes, see http://proguard.sourceforge.net/manual/examples.html#enumerations
    -keepclassmembers enum * {
        public static **[] values();
        public static ** valueOf(java.lang.String);
    }

    # Preserve all native method names and the names of their classes.
    -keepclasseswithmembernames class * {
        native <methods>;
    }

    -keepclassmembers class * {
        @org.simpleframework.xml.* *;
    }

    # For enumeration classes, see http://proguard.sourceforge.net/manual/examples.html#enumerations
    -keepclassmembers enum * {
        public static **[] values();
        public static ** valueOf(java.lang.String);
    }

    # this is going in 4.9 apparently?
    -keep class * extends transapps.maps.plugin.tool.Tool {
    }
    -keep class * implements transapps.maps.plugin.lifecycle.Lifecycle {
    }

    # overcome an existing bug in the gradle subsystem (3.5.x)
    -keep class module-info

    # Change soothsayer to your plugin name
    -keepclassmembers class com.cloudrf.android.soothsayer.interfaces.** { *; }
    -keepclassmembers class com.cloudrf.android.soothsayer.layers.** { *; }
    -keepclassmembers class com.cloudrf.android.soothsayer.models.** { *; }
    -keepclassmembers class com.cloudrf.android.soothsayer.network.** { *; }
    -keepclassmembers class com.cloudrf.android.soothsayer.recyclerview.** { *; }
    -keepclassmembers class com.cloudrf.android.soothsayer.util.** { *; }
    -keepclassmembers class com.cloudrf.android.soothsayer.plugin.** { *; }
    -keepclassmembers class com.atakmap.android.maps.** { *; }

    # this is from trying to sign for 4.8 / switching to ProGuard from R8
    -keep class kotlin.jvm.internal.** {*;}
    -keep class kotlin.coroutines.jvm.internal.** {*;}
    -keep class kotlin.jvm.functions.** {*;}
    -keep class kotlin.coroutines.** {*;}
    -keep class kotlin.collections.** {*;}

    # suppress warnings
    -dontwarn module-info
    -dontwarn java.awt.**
    -dontwarn org.xmlpull.**
    -dontwarn org.xml.sax.ContentHandler
    -dontwarn org.bouncycastle.**
    -dontwarn org.conscrypt.**
    -dontwarn org.openjsse.**
    -dontwarn android.net.**
    -dontwarn java.nio.ByteBuffer
    -dontwarn kotlin.jvm.internal.**
    -dontwarn kotlin.coroutines.jvm.internal.**
    -dontwarn kotlin.jvm.functions.**
    -dontwarn kotlin.coroutines.**
    -dontwarn kotlin.collections.**
    -dontwarn android.content.res.XmlResourceParser
    -dontwarn androidx.**

## gradle-wrapper.properties

    distributionBase=GRADLE_USER_HOME
    distributionPath=wrapper/dists
    distributionUrl=https\://services.gradle.org/distributions/gradle-7.3.3-all.zip
    zipStoreBase=GRADLE_USER_HOME
    zipStorePath=wrapper/dists

# Google Play signing
Run it through the Third Party Pipeline on tak.gov first then sign the .aab with your Google Play keystore and jartool:

	jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -storepass xxxxxxxxxxx -keystore soothsayer_release.keystore ATAK-Plugin-SOOTHSAYER-xxxxxxxxxxx-civ-release.aab soothsayer
