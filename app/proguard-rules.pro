# Customization screen: its themes keep the launcher icon color (helpers/KeepIconColor.kt)
-keepclassmembers class org.fossify.commons.activities.CustomizationActivity {
    private java.util.LinkedHashMap predefinedThemes;
    private int curAppIconColor;
}
