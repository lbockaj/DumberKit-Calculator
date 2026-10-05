# DumberKit: Calculator
<img alt="Logo" src="graphics/icon.png" width="120" />

A calculator and unit converter for **keypad phones** – the Qin F21, Dumber Mini, Doov R77 Pro and other small Android
phones with a D-pad and a T9 keypad. Its core is [Fossify Calculator](https://github.com/FossifyOrg/Calculator), the
way of typing comes from [Calc-U-Later](https://github.com/kouta-kun/Calc-U-Later). No on-screen buttons: the
calculation is typed straight on the keypad, sized for a 480×640 screen.

## How it works

Three tabs, switched with ← / →: **Calculator**, **Unit converter** and **Settings** (icons in the bottom bar). Back
in the converter or settings tab returns to the calculator.

| Screen | Keys |
| --- | --- |
| **Calculator** | 0–9 type numbers · `*` adds +, pressed again −, ×, ÷ (at the start: a negative number) · `#` decimal point · OK or the green call key shows the result · Back deletes (with nothing typed: closes) · hold Back clears |
| **More functions** | hold `*`: %, power, square root, parentheses, ± |
| **History** | ↑ / ↓ go through the calculations above the input, the picked one can be changed and computed again |
| **Unit converter** | ↑ / ↓ and OK pick length, area, volume, mass, temperature, time, speed, pressure or energy |
| **Converting** | digits and `#` type the upper value · ↑ / ↓ pick a unit row · ← / → change its unit · OK lists all units · `*` swaps the units · hold `*` makes a temperature negative |

The **Menu** key shows the other actions: copy the result, clear the history, use a converted value in the calculator.

## Features

- **Correct order of operations** – 10 − 2 + 3 = 11 and 2 + 3 × 4 = 14, exact decimals (0.1 + 0.2 = 0.3).
- **History like a paper tape** – kept while the app is open, gone once it's closed.
- **Keys only** – touches are ignored in the app, *Settings → Allow touch input* turns them back on.
- **Colors and themes** – System (follows Android), light, dark and custom themes; blue app icon by default, other
  colors in *Settings → Customize colors*.
- **Private** – no ads, no tracking, no internet access.

## Install

1. Download `dumberkit-calculator.apk` from the [latest release](../../releases/latest).
2. Open it on the phone and allow installing apps from that source when Android asks.
3. Start **Calculator** with the blue icon.

Requires Android 8.0 or newer.

## Build

```bash
./gradlew assembleFossRelease
```

The release build is signed with the key from `keystore.properties` (see `keystore.properties_sample`), the APK ends up
in `app/build/outputs/apk/foss/release/`. Tests of the calculations: `./gradlew testFossDebugUnitTest`.

## License

DumberKit: Calculator is free software under the [GNU General Public License v3.0](LICENSE).

## Credits

The core of the app is [Fossify Calculator](https://github.com/FossifyOrg/Calculator) and its
[Commons](https://github.com/FossifyOrg/Commons) library (GPL-3.0) – the unit converter, history, themes and
translations come from there. The keypad input follows [Calc-U-Later](https://github.com/kouta-kun/Calc-U-Later) by
kouta-kun (GPL-3.0). DumberKit: Calculator is a modified version, reworked in 2026 for keypad navigation and small
screens. Thanks to the Fossify team, its translators and kouta-kun.
