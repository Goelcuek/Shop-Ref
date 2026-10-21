# Compose and the core library need no special keep rules; R8 defaults are sufficient.
# Keep enum names stable because settings are persisted by enum name.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
