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

# R8 can otherwise choose either Path.findFile or PathImpl.findFile as the owner of the same
# virtual archive-extraction call. Keep this dispatch boundary stable so two clean release builds
# produce the same DEX and baseline profile.
-keep,allowobfuscation class io.github.muntashirakon.io.Path {
    io.github.muntashirakon.io.Path findFile(java.lang.String);
    io.github.muntashirakon.io.Path findFileOrNull(java.lang.String);
}
-keep,allowobfuscation class io.github.muntashirakon.io.PathImpl {
    io.github.muntashirakon.io.Path findFile(java.lang.String);
}

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
# Keep ComponentCallbacks2 implementations intact — R8 can devirtualize empty
# onTrimMemory/onLowMemory bodies and merge the class, causing
# AbstractMethodError when Android dispatches onTrimMemory to the callback list.
-keep class * implements android.content.ComponentCallbacks2 {
    void onTrimMemory(int);
    void onLowMemory();
    void onConfigurationChanged(android.content.res.Configuration);
}
# Don't minify debug-sepcific resource file
-keep public class io.github.muntashirakon.AppManager.debug.R$raw {*;}
# Don't minify OpenPGP API
-keep public class org.openintents.openpgp.IOpenPgpService { *; }
-keep public class org.openintents.openpgp.IOpenPgpService2 { *; }
# Keep the bundled BouncyCastle provider's SPI implementations. AppManager
# replaces Android's built-in BC provider with the bundled one (AppManager.java),
# and JCA resolves every algorithm/keystore implementation from the provider's
# service map REFLECTIVELY by class name. Without these keeps R8 strips the
# unreferenced SPI classes and KeyStore.getInstance("BKS") fails with
# "BKS not found" in every release build — which made the recovery password
# regenerate on every launch (issue #7) because the keystore could never load.
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.pqc.jcajce.provider.** { *; }
# The kept provider classes include X509LDAPCertStoreSpi, which references
# javax.naming.* — a desktop-JVM API that does not exist on Android. The class
# is only instantiated if an LDAP CertStore is requested, which this app never
# does; suppress the missing-class error instead of losing the provider keeps.
-dontwarn javax.naming.**
# Don't minify Spake2 library
-keep public class io.github.muntashirakon.crypto.spake2.** { *; }
# Don't minify AOSP private APIs
-keep class android.** { *; }
-keep class com.android.** { *; }
-keep class libcore.util.** { *; }
-keep class org.xmlpull.v1.** { *; }
