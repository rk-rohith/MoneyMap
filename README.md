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

- **First-run setup**: a new install asks for the salary account, spending account, salary day (1–28), salary and
  spending budget, or restores a backup. Installs that already hold data keep the original setup (HDFC / Jupiter,
  salary on the 25th) without being asked. Account names, the extra savings pod and the loan can be changed later
  in Settings → Accounts & loan.

- **Uneven income**: a different salary for a single cycle, dated extra income (bonus, refund) that tops up that
  cycle's emergency pod, and an option to move salary day to the Friday before when it falls on a weekend.
- **Month**: overdue alerts, next-up card, cycle navigation, salary split, payment checklist with undo.
- **Expenses from notifications** (optional, Settings): with notification access, debits from bank, card and UPI
  notifications appear on Spend as suggestions to add or ignore (duplicates from bank + UPI app are merged).
- **Spend**: log expenses by category, see where the budget went, browse past cycles, "paid for someone" → People, "split the bill" (your share is spending, everyone
  else's equal share becomes money they owe you).
- **Reports & budgets** (⋮ menu): spending per cycle against budget for the last six cycles, each category against
  its earlier average, monthly budgets per category (heads-up at 80% and when over), and payments that look like
  they repeat every month with "Add to plan".
- **Receipts**: tap an expense to attach a photo from the gallery or camera (stored on the phone, scaled down;
  not included in JSON backups).
- **People**: money lent and borrowed, partial returns, reminders on due dates.
- **Save**: pod balances (filled by ticking the salary-day routine, emptied by debt/pod-move ticks, topped up by returns) and goals with "save ₹X per cycle".
- **Home-screen widget**: next payment, what's left to spend, what others owe, and a + Expense button.
- **Quick add**: long-press the app icon for "Log expense" or "Lend / borrow"; the Sunday summary has a Log expense button.
- **Share reminders**: the share icon on a person (or an entry) sends a friendly WhatsApp/SMS reminder.
- **Search** (magnifier in the top bar): expenses, people and pod movements by name, note, category or amount,
  with filters for period (this/last cycle, 90 days, this year), amount range and category; filters alone list
  everything that matches, with the expense total.
- **Settings (⋮ → Settings & plan)**: planned one-offs saved over several cycles, a 12-month emergency-fund comparison before saving, wallpaper colours (Android 12+), reminder times and switches, app lock (fingerprint/face/screen lock), edit salary, spending budget, every regular item (amount, day, type, start/end) and the loan repayments; changes apply from a chosen cycle onward. Weekly automatic backup to a folder, export/import, optional backup password (AES-GCM encrypted `.mmbackup` files), one-tap "Save backup to
  Google Drive" (hands the file to the Drive app; no Google account setup in Money map), test notification.

## Previewing changes

CI renders every screen with sample data (`ScreenshotTest`) and uploads them as the `moneymap-screenshots`
artifact. Locally: `./gradlew testDebugUnitTest -Pscreenshots` → `app/build/screenshots/`.

## Updating the app

Every build is signed with the committed `app/debug.keystore`, so a new APK installs over the old one and keeps your data.
(The very first build before this key existed was signed differently: uninstall that one once, after exporting a backup.)

### Release builds

`./gradlew assembleRelease` builds a minified (R8) APK. To sign it with your own key, set `MONEYMAP_KEYSTORE`,
`MONEYMAP_KEYSTORE_PASSWORD`, `MONEYMAP_KEY_ALIAS` and `MONEYMAP_KEY_PASSWORD`; in CI add the keystore as the
base64 secret `MONEYMAP_KEYSTORE_BASE64` plus the three other secrets. Without them the release APK uses the debug key.
Switching an installed app from the debug key to a release key needs one reinstall: export a backup first.

## Layout

- `core/` – pure Kotlin, unit-tested: `Plan.kt` (plan engine + default plan), `Savings.kt` (pods, goals, categories), `Ledger.kt` / `LedgerService.kt` (people ledger), `SeedData.kt`, `Reminders.kt` (what to notify and when), `Money.kt` (₹ Indian formatting).
- `data/` – Room database, repository, JSON/CSV backup. Schemas are exported to `app/schemas/`; when you bump the
  database version, add the migration to `ALL_MIGRATIONS` and commit the new schema file. `MigrationTest` upgrades a
  real v1 database and fails if a migration doesn't match the entities.
- `notify/` – AlarmManager scheduling (only the next 14 days, at most 200 alarms, topped up by a daily refresh alarm),
  notification actions, boot/time-change rescheduling.
- `ui/` – Month, Spend, People, Save, Settings, entry detail, add/edit.

## Install on a phone

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.moneymap/.MainActivity --ez test_notification true   # posts a test reminder
```

On the phone: allow notifications when asked, and set Settings → Apps → Money map → Battery → **Unrestricted** so reminders fire on time.
