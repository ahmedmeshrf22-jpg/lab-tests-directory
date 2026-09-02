# Secure release rules.
# Android/Firebase libraries ship their own consumer rules; keep only attributes
# needed for serialized/generic types while allowing application code to be renamed.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Remove common logging calls from optimized release builds.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
