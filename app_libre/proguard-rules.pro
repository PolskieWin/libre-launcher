# Add project specific ProGuard rules here.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Preserve native JNI methods and their enclosing classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# Launcher core classes, UI controllers, and reflection targets
-keep class git.artdeell.mojo.** { *; }
-keep class git.artdeell.mojoexec.** { *; }
-keep class com.kdt.mcgui.** { *; }
-keep class org.lwjgl.** { *; }
-keep class java.awt.** { *; }
-keep class javax.swing.** { *; }
-keep class sun.awt.** { *; }
-keep class net.java.openjdk.cacio.** { *; }
-keep class io.github.controlwear.** { *; }
-keep class com.github.** { *; }

# Serialization models and data classes (Gson)
-keepclassmembers enum * { *; }
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Math and expression parsing
-keep class net.objecthunter.exp4j.** { *; }
-keepclassmembers class net.objecthunter.exp4j.** { *; }

# File dialog bridge: methods are looked up by name from native code
-keep class git.artdeell.mojo.game.platform.dialog.FileDialogBridge { *; }
-keep class git.artdeell.mojo.game.platform.dialog.SDLFileDialogHandler { *; }

# Home screen tiles are saved as JSON with Gson, the field names must stay
-keep class git.artdeell.mojo.home.HomeTile { *; }

# JNA and dynamic linkers
-keep class com.sun.jna.** { *; }
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn sun.awt.**
-dontwarn com.sun.jna.**
