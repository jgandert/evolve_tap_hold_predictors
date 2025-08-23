# Evolving Tap-Hold Predictors

This repository is for finding functions that can be used to make predictions about whether certain cases are likely to be a tap or a hold. It's used in the [Predictive Tap-Hold community module for QMK](https://github.com/jgandert/qmk_modules/predictive_tap_hold).

The predictions are made using data points (e.g., duration between previous release and PTH press) that are available at the time of the respective event.

## Prediction Functions
### `FAST_STREAK_TAP`

Predicts if a tap-hold is a tap (if tap is **not** chosen, then it could be a tap or a hold, so the remaining prediction functions, or the PTH logic handles the case)

### `OVERLAP_MS_FOR_HOLD`

Estimates the duration overlap above which a key combination is considered a hold instead of a tap.

**Example**: <kbd>LCTL_T(KC_A)</kbd> down, <kbd>KC_V</kbd> down

### `PTH_UP_AFTER_SECOND_DOWN`

Predict hold or tap for an overlapping (non-wrapped) case.

If it returns a value > 0.5, it predicts hold. The same is true for the remaining functions.

**Example**: <kbd>LCTL_T(KC_A)</kbd> down, <kbd>KC_V</kbd> down, <kbd>LCTL_T(KC_A)</kbd> up

### `PTH_UP_AFTER_SECOND_UP`

Predict hold or tap for a wrapped case.

**Example**: <kbd>LCTL_T(KC_A)</kbd> down, <kbd>KC_V</kbd> down, <kbd>KC_V</kbd> up, <kbd>LCTL_T(KC_A)</kbd> up (`V` is wrapped)

### `THIRD_DOWN`

Predict hold or tap for a triple-down case.

**Example**: <kbd>LCTL_T(KC_A)</kbd> down, <kbd>KC_V</kbd> down, <kbd>KC_E</kbd> down

## Setup

1. Download `training_data.csv.gz` from [here](https://github.com/jgandert/analyze_keystrokes/releases).

2. Move it into this repository.

3. Configure via the [settings.kt](src/main/kotlin/settings.kt).

## Usage (evolving functions)

▶️ Start [evolve.kt](src/main/kotlin/evolve.kt).

The `initialPopulation` variable contains the best found solutions. Note that we're using the decision trees in many cases.

It uses the [jenetics library](https://github.com/jenetics/jenetics).

## Usage (classifiers using decision trees)

▶️ Start [classify.kt](src/main/kotlin/classify.kt).

The best found solutions are in the `best_pth_default*.log` files.

It uses the [smile library](https://haifengl.github.io/).

## Acknowledgments

Thanks to the creators of both libraries! 🙂
