# Money map

Personal finance reminder app for Android (Kotlin, Jetpack Compose, Material 3, Room). All data stays on the phone.

## Build

```sh
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # plan, ledger and reminder unit tests
./gradlew lint
```

Requires JDK 17 and an Android SDK with platform 35 (`local.properties` → `sdk.dir=...` or `ANDROID_HOME`).
CI (`.github/workflows/build-apk.yml`) runs tests, builds, lints and uploads the APK as the `moneymap-debug-apk` artifact.

## Layout

- `core/` – pure Kotlin, unit-tested: `Plan.kt` (the hard-coded plan), `Ledger.kt` / `LedgerService.kt` (people ledger), `SeedData.kt`, `Reminders.kt` (what to notify and when), `Money.kt` (₹ Indian formatting).
- `data/` – Room database, repository, JSON/CSV backup.
- `notify/` – AlarmManager scheduling, notification actions, boot/time-change rescheduling.
- `ui/` – Month, Spend, People, entry detail, add/edit.

## Install on a phone

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.moneymap/.MainActivity --ez test_notification true   # posts a test reminder
```

On the phone: allow notifications when asked, and set Settings → Apps → Money map → Battery → **Unrestricted** so reminders fire on time.
