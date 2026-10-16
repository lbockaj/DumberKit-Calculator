# Changelog

## [1.1.0] - 2026-10-16
### Added
- Currency converter tab (before Settings, dollar and yen icon): about 30 currencies at the European Central Bank's
  reference rates, the same two rows and keys as the unit converter. The rates are downloaded when the tab opens and
  the kept ones are a few hours old, or from the menu; offline the last ones stay, the time of the last download is shown
  under the converter. The app now asks for internet access, used only for this
### Fixed
- The warning "You are using a fake version of the app", which Commons showed now and then, no longer shows up
- The selected dialog button is much easier to see
- On phones with an on-screen navigation bar the tabs and the last rows of About stay above it

## [1.0.0] - 2026-10-09
First release, based on Fossify Calculator 1.4.0.

### Added
- Calculations typed straight on the keypad, like Calc-U-Later: `*` puts in an operator and turns it into the next
  one (+ − × ÷), `#` is the decimal point, OK or the green call key shows the result
- A new calculation engine with the usual order of operations and exact decimals, plus percent, powers, square
  roots and parentheses
- Symbol palette on a held `*`: square tiles laid out like the keypad, picked with the digit at their place or with
  the arrows and OK
- History above the input like a paper tape, ↑ / ↓ go through it; it's cleared when the app is closed
- Three tabs at the bottom – calculator, unit converter, settings – switched with ← / →
- Unit converter for keys: ↑ / ↓ select a row, ← / → change its unit, works in both directions, `*` swaps the units,
  *Use in the calculator* from the Menu
- Copy the result from the Menu
- Settings and an About screen with all the keys, made for keypad navigation
- App icon with a calculator pictogram, blue by default; any other color can be picked and a theme keeps it

### Changed
- Smaller app (APK 5.4 → 3.2 MB): Commons' own About, license, FAQ, contributors, donation and blocked numbers
  screens, never opened here, are left out, and only languages with a real translation are included

### Removed
- The on-screen button pad and the home screen widget
