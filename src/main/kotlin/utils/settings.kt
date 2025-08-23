package utils

import kotlin.io.path.Path

// FAST_STREAK_TAP has to be evolved first,
// then it has to be converted to a Kotlin function named fst (already exists)
// then the others can be evolved
val MODE = Mode.PTH_UP_AFTER_SECOND_DOWN
val PATH = Path("training_data.csv.gz")

const val START_FRESH = false
const val POPULATION_SIZE = 1000
const val START_NUMBER_HILL_CLIMBING = false
const val ONLY_MUTATE_NUMBERS = false
const val HIGH_MUTATION = false

// set to 0.0 to disable
val APPLY_PENALTY_IF_MOD_CORRECT_RATIO_LOWER = if (MODE == Mode.FAST_STREAK_TAP) 0.998 else 0.0
const val PENALTY_IF_MOD_CORRECT_RATIO_LOWER = 200_000.0

// Train with a representative sample to speed up the process.
// Use random sections of the training data for the fitness calculation.
// If false, we will use the same sample of the training data for each program.
// The downside of enabling this is that a program can get lucky with its
// selection, and then others will find it hard to compete, even though
// globally they are better.
const val SAMPLE_TRAINING_DATA_PER_PROGRAM = false

// does not apply to SAMPLE_TRAINING_DATA_PER_PROGRAM
const val MAKE_MOD_NON_MOD_RATIOS_SAME_IN_SAMPLE = false

const val NUM_TRAINING_DATA = 1024 * 32

// should be equal to the constant used in the .h file (!)
const val MS_MAX_DUR_FOR_PREDICTION = 4_096.0
const val MAX_MS_CONSIDERED = 100_000.0


// The following is true for both of the following constants: If their value is
// small, we could get more accidental holds (meant to be taps), but correct
// holds could be faster. If they're large, correct holds could be slower, and
// we could get more accidental taps, but we could get more correct taps too.
// But in the end, they're just guardrails for the prediction function.
//
// Chosen because more than 90 % of mod-first intersection durations are
// longer and the majority of non-mod-first intersection durations are shorter.
// Set to -1 to disable.
const val FIX_MIN_OVERLAP_MS = 39.0

// Chosen because 99.9 % of mod-first intersection durations are shorter.
// The same is true for non-mod-first intersection durations.
// Set to -1 to disable.
const val FIX_MAX_OVERLAP_MS = 232.0

// When the keyboard starts, there is no previous keypress, so what is a good
// initial value for something like prev_up_th_down_dur? Use this to find out.
// It only makes sense to do so, when you have already found a good solution.
const val START_FINDING_BEST_INITIAL_TRAIN_COL_VALUE = false
const val USE_BINARY_SEARCH_TO_FIND_BEST_INITIAL_TRAIN_COL_VALUE = false
val FINDING_BEST_INITIAL_TRAIN_COL = TrainCol.KEY_RELEASE_BEFORE_PTH_TO_PTH_PRESS_DUR
const val FINDING_BEST_INITIAL_TRAIN_COL_FROM = 0
const val FINDING_BEST_INITIAL_TRAIN_COL_TO = 200

// Set to -1 to disable
const val AVOID_FULL_FITNESS_IN_FIRST_N_GENERATIONS = 10
const val FMT_DECIMAL_PLACES = 2
const val PRINT_FOR_EASY_PASTE_TO_CODE = true

// Yeah, this is not the cleanest... FIXME
var decisionTreeGenerateKotlin = false
const val DECISION_TREE_OUTPUT_PROB = true
const val DECISION_TREE_DEPTH = 7