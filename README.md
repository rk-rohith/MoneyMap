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

## Features

- **Month**: overdue alerts, next-up card, cycle navigation, salary split, payment checklist with undo.
- **Spend**: log expenses by category, see where the budget went, browse past cycles, "paid for someone" → People.
- **People**: money lent and borrowed, partial returns, reminders on due dates.
- **Save**: pod balances (filled by ticking the salary-day routine, emptied by debt/pod-move ticks, topped up by returns) and goals with "save ₹X per cycle".
- **Settings (⋮ → Settings & plan)**: edit salary, spending budget, every regular item (amount, day, type, start/end) and the loan repayments; changes apply from a chosen cycle onward. Weekly automatic backup to a folder, export/import, test notification.

## Updating the app

Every build is signed with the committed `app/debug.keystore`, so a new APK installs over the old one and keeps your data.
(The very first build before this key existed was signed differently: uninstall that one once, after exporting a backup.)

## Layout

- `core/` – pure Kotlin, unit-tested: `Plan.kt` (plan engine + default plan), `Savings.kt` (pods, goals, categories), `Ledger.kt` / `LedgerService.kt` (people ledger), `SeedData.kt`, `Reminders.kt` (what to notify and when), `Money.kt` (₹ Indian formatting).
- `data/` – Room database, repository, JSON/CSV backup.
- `notify/` – AlarmManager scheduling, notification actions, boot/time-change rescheduling.
- `ui/` – Month, Spend, People, Save, Settings, entry detail, add/edit.

## Install on a phone

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.moneymap/.MainActivity --ez test_notification true   # posts a test reminder
```

On the phone: allow notifications when asked, and set Settings → Apps → Money map → Battery → **Unrestricted** so reminders fire on time.
