# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep BLE related classes
-keep class android.bluetooth.** { *; }

# Keep data classes
-keep class com.trailcam.mesh.data.** { *; }


