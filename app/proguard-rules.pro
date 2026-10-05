# DumberKit: App.kt keeps the launcher icon color when a ready-made theme is picked, through these private members
-keepclassmembers class org.fossify.commons.activities.CustomizationActivity {
    private int curAppIconColor;
    private int originalAppIconColor;
    private void pickAppIconColor();
}
