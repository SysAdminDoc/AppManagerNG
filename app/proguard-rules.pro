# Specify compression level
-optimizationpasses 5
# Algorithm for confusion
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
# Allow access to and modification of classes and class members with modifiers during optimization
-allowaccessmodification
# Rename file source to "Sourcefile" string
-renamesourcefileattribute SourceFile
# Keep line number
-keepattributes SourceFile,LineNumberTable
# Keep generics
-keepattributes Signature
# Keep all class members that implement the serializable interface
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
# Keep all class members that implement the Parcelable interface
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
    public int describeContents();
    public void writeToParcel(android.os.Parcel, int);
}
# Keep preference fragments
-keep public class * extends androidx.preference.PreferenceFragmentCompat {}
# Keep XML parser implementations intact. These classes are selected through
# framework XML factories and can fail late with AbstractMethodError if R8 trims
# interface bridge methods aggressively.
-keep public class * extends org.xmlpull.v1.XmlPullParser { *; }
-keep public class * extends org.xmlpull.v1.XmlSerializer { *; }
# Keep privileged server and binder surfaces stable across the app/server
# boundary. The app copies and loads server artifacts out-of-process, so these
# entry points are not all visible to whole-program analysis.
-keep public class io.github.muntashirakon.AppManager.servermanager.** { *; }
-keep public class io.github.muntashirakon.AppManager.server.** { *; }
-keep public class io.github.muntashirakon.AppManager.ipc.** { *; }
# Don't minify debug-sepcific resource file
-keep public class io.github.muntashirakon.AppManager.debug.R$raw {*;}
# Don't minify OpenPGP API
-keep public class org.openintents.openpgp.IOpenPgpService { *; }
-keep public class org.openintents.openpgp.IOpenPgpService2 { *; }
# Don't minify Spake2 library
-keep public class io.github.muntashirakon.crypto.spake2.** { *; }
# Don't minify AOSP private APIs
-keep class android.** { *; }
-keep class com.android.** { *; }
-keep class libcore.util.** { *; }
-keep class org.xmlpull.v1.** { *; }
