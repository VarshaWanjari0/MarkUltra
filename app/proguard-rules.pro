# MarkUltra uses no reflection, no JSON data binding and no serialization
# framework, so the default Android optimisations are safe.

# Activities are referenced from the manifest and kept automatically, but the
# explicit rules below make the intent obvious and survive R8 upgrades.
-keep class com.markultra.editor.MainActivity { *; }
-keep class com.markultra.editor.SettingsActivity { *; }

# Keep custom views referenced from XML layouts.
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# The Markdown engine is pure Java and heavily inlined; keeping the names makes
# stack traces readable for bug reports.
-keepnames class com.markultra.editor.md.** { *; }

# flexmark builds its extension list from plain classes; nothing is reflected on,
# but keeping the names makes release-build stack traces usable.
-keep class com.vladsch.flexmark.** { *; }
-keep class org.nibor.autolink.** { *; }
-dontwarn com.vladsch.flexmark.**

# Strip verbose Android logging in release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
