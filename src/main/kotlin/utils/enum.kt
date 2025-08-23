package utils

import java.util.*

operator fun <T> Array<T>.get(e: Enum<*>): T {
    return this[e.ordinal]
}

operator fun <T> List<T>.get(e: Enum<*>): T {
    return this[e.ordinal]
}

operator fun <T> Array<T>.set(e: Enum<*>, value: T) {
    set(e.ordinal, value)
}


enum class InputTrainCol {
    IS_MOD,
    SECOND_IS_MOD,

    // These are about the press that was right before the PTH.
    // It may be released after the PTH press time.
    // If not, then these will be equal to KEY_RELEASED_BEFORE_PTH values.
    //
    // We don't use this. Programs almost never evolved to use it, and it's
    // somewhat hard to acquire inside the tap hold logic due to some keys
    // being released by us, and some being released normally.
    KEY_PRESSED_BEFORE_PTH_IS_MOD,
    KEY_PRESSED_BEFORE_PTH_RELEASE_TIME,

    // This is always about the release that happened before the PTH.
    KEY_RELEASED_BEFORE_PTH_IS_MOD,
    KEY_RELEASED_BEFORE_PTH_RELEASE_TIME,

    PTH_PRESS_TIME,
    SECOND_PRESS_TIME,
    SECOND_RELEASE_TIME,
    PTH_RELEASE_TIME,
    THIRD_PRESS_TIME,

    //PP1,
    //PP2,
    PTH_PREV_PREV_PRESS_TO_PREV_PRESS_DUR, // PP3
    PTH_PREV_PRESS_TO_PTH_PRESS_DUR, // PP4

    //OV1,
    //OV2,
    PTH_PREV_PREV_OVERLAP_DUR, // OV3
    PTH_PREV_OVERLAP_DUR, // OV4

    DOWN_COUNT,

    // if you want ALL the training data, uncomment the following and the ones
    // in TrainCol, but first in the Python code (and re-run it)
    //NON_MOD_DUR_AVG,
    //MOD_DUR_AVG,
    //OVERLAP_DUR_AVG,
    //RECENT_DUR_AVG,
    //RECENT_IS_MOD_AVG,

    // Number of times a key was pressed in the last 400 ms.
    //PRESSED_COUNT_LAST_400_MS,
    //PTH_AND_SECOND_SAME_SIDE_BUT_THIRD_NOT
}

val secondUpOrThirdDown: EnumSet<Mode> = EnumSet.of(Mode.PTH_UP_AFTER_SECOND_UP, Mode.THIRD_DOWN)
val allButFST: EnumSet<Mode> = EnumSet.complementOf(EnumSet.of(Mode.FAST_STREAK_TAP))
val none: EnumSet<Mode> = EnumSet.noneOf(Mode::class.java)
val third: EnumSet<Mode> = EnumSet.of(Mode.THIRD_DOWN)

enum class TrainCol(
    val mayUseInModes: EnumSet<Mode> = EnumSet.allOf(Mode::class.java),
    val isDur: Boolean = false,
    val isPP: Boolean = false,
    val isOV: Boolean = false,
    val isComposite: Boolean = false
) {
    IS_MOD(mayUseInModes = none),

    // not used, because did not improve performance at all
    //FAKE_SECOND_IS_TAP_HOLD(mayUseInModes = third),

    KEY_RELEASED_BEFORE_PTH_IS_MOD,

    // While it may not be pretty, we use the long names that are used in the
    // C code to avoid having to map them back and forth.
    KEY_RELEASE_BEFORE_PTH_TO_PTH_PRESS_DUR(isDur = true),
    
    PTH_PRESS_TO_SECOND_PRESS_DUR(mayUseInModes = allButFST, isDur = true),
    SECOND_PRESS_TIME(mayUseInModes = none),
    SECOND_RELEASE_TIME(mayUseInModes = none),
    PTH_RELEASE_TIME(mayUseInModes = none),

    // optional for THIRD_DOWN as the next key may not have been released yet
    OPT_NEXT_DUR(mayUseInModes = secondUpOrThirdDown, isDur = true),
    OPT_TH_DOWN_NEXT_UP_DUR(mayUseInModes = secondUpOrThirdDown, isDur = true),

    THIRD_PRESS_TIME(mayUseInModes = none),
    PTH_SECOND_PRESS_TO_THIRD_PRESS_DUR(mayUseInModes = third, isDur = true),

    // press to press duration
    PTH_PREV_PREV_PRESS_TO_PREV_PRESS_DUR(isDur = true, isPP = true),
    PTH_PREV_PRESS_TO_PTH_PRESS_DUR(isDur = true, isPP = true), // newest

    // overlap duration
    PTH_PREV_PREV_OVERLAP_DUR(isDur = true, isOV = true),
    PTH_PREV_OVERLAP_DUR(isDur = true, isOV = true), // newest

    DOWN_COUNT,

    // These do increase performance (0.2 %), but there are a couple downsides:
    // - the user will presumably use tap-hold keys, yet we can't (easily)
    //   know if a tap or hold was intentional. However, because this is an
    //   average, it is probably fine. Mostly they will be correct.
    // - it requires a bunch of code to acquire this average
    // - all averages over a longer time frame have the downside that the
    //   behavior in the beginning (when the average is mostly determined by
    //   the first couple of events) could be vastly different from what
    //   happens later on. This will make the system feel unpredictable.
    //NON_MOD_DUR_AVG,
    //MOD_DUR_AVG,

    // not used, because achieves only about a 0.1 % improvement and has the
    // aforementioned downside of long-term averages
    OVERLAP_DUR_AVG(isDur = true),

    // not used, because the effort required to collect this in the C code
    // doesn't seem worth the minor increase in performance
    //RECENT_DUR_AVG(isDur = true),
    //RECENT_IS_MOD_AVG(),

    // weighted average (generated)
    PTH_PRESS_TO_PRESS_W_AVG(isDur = true, isComposite = true),
    PTH_OVERLAP_W_AVG(isDur = true, isComposite = true),

    // not used, because these were not used a single time (at least by the classifier)
    //PRESSED_COUNT_LAST_400_MS,
    //PTH_AND_SECOND_SAME_SIDE_BUT_THIRD_NOT(mayUseInModes = EnumSet.of(Mode.THIRD_DOWN))
}

enum class Mode {
    // fast streak tap (x) -> tap or unknown
    FAST_STREAK_TAP,

    // PTH release after second down (xnX=overlap) -> tap or hold
    PTH_UP_AFTER_SECOND_DOWN,

    // PTH release after second up (xnNX=wrapped) -> tap or hold
    PTH_UP_AFTER_SECOND_UP,

    // third pressed (xnt..) -> tap or hold
    THIRD_DOWN,

    // the only mode where we're not predicting probability but the duration
    // two keys must be pressed down simultaneously (overlap) for it to be
    // considered a hold rather than a tap
    OVERLAP_MS_FOR_HOLD,
}