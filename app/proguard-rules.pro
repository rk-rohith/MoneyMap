# Room, Compose, AndroidX and coroutines ship their own consumer rules.

# Enum names are stored in the database, backups and intents (Direction.valueOf, ReminderType.valueOf, ...),
# so keep enum constants' names stable.
-keepclassmembers enum com.moneymap.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Broadcast receivers, the widget and the tile are created by name from the manifest (kept by AGP),
# but keep their no-arg constructors explicitly for safety.
-keep class com.moneymap.notify.** extends android.content.BroadcastReceiver { <init>(); }
