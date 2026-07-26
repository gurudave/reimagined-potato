//! Verification of the Bernoulli function against test plan section 1.1.

use drift_diffusion_1d::numerics::bernoulli;

/// `B(x)` evaluated from the definition `x / (exp(x) - 1)` at 80 significant
/// decimal digits, then rounded to the nearest `f64`.
///
/// The table brackets both points at which the implementation changes
/// evaluation method, `|x| = 1e-4` and `x = 50`.
const REFERENCE: [(f64, f64); 33] = [
    (1.0e-16, 1.0),
    (-1.0e-16, 1.0),
    (1.0e-12, 0.999_999_999_999_5),
    (-1.0e-12, 1.000_000_000_000_5),
    (1.0e-8, 0.999_999_995),
    (-1.0e-8, 1.000_000_005),
    (1.0e-6, 0.999_999_500_000_083_3),
    (-1.0e-6, 1.000_000_500_000_083_3),
    (9.9999e-5, 0.999_950_001_333_316_7),
    (-9.9999e-5, 1.000_050_000_333_316_6),
    (1.0e-4, 0.999_950_000_833_333_3),
    (-1.0e-4, 1.000_050_000_833_333_3),
    (1.00001e-4, 0.999_950_000_333_35),
    (-1.00001e-4, 1.000_050_001_333_35),
    (0.01, 0.995_008_333_319_444_5),
    (-0.01, 1.005_008_333_319_444_4),
    (0.1, 0.950_833_194_477_504_9),
    (-0.1, 1.050_833_194_477_505),
    (1.0, 0.581_976_706_869_326_5),
    (-1.0, 1.581_976_706_869_326_5),
    (10.0, 0.000_454_019_910_096_877_7),
    (-10.0, 10.000_454_019_910_096),
    (49.999999, 9.643_758_690_698_449e-21),
    (50.0, 9.643_749_239_819_589e-21),
    (50.000001, 9.643_739_788_949_986e-21),
    (100.0, 3.720_075_976_020_836e-42),
    (500.0, 3.562_288_203_370_643e-215),
    (700.0, 6.901_773_580_631_84e-302),
    (-50.0, 50.0),
    (-100.0, 100.0),
    (-500.0, 500.0),
    (-700.0, 700.0),
    (-1000.0, 1000.0),
];

/// Asserts that `got` matches `want` to within `ulps` units in the last place.
fn assert_within_ulps(x: f64, got: f64, want: f64, ulps: f64) {
    let error = (got - want).abs();
    let tolerance = ulps * f64::EPSILON * want.abs();

    assert!(
        error <= tolerance,
        "B({x:e}) = {got:e}, expected {want:e}: error {error:e} exceeds {tolerance:e}"
    );
}

#[test]
fn bernoulli_has_the_correct_limit_at_the_origin() {
    assert_eq!(bernoulli(0.0), 1.0);
    assert_eq!(bernoulli(-0.0), 1.0);
}

#[test]
fn bernoulli_avoids_cancellation_near_the_origin() {
    // Close to zero the series must reproduce `1 - x/2 + x^2/12`; a naive
    // `x / (exp(x) - 1)` loses most of its significant digits here.
    for x in [1.0e-16_f64, 1.0e-12, 1.0e-8, 1.0e-6] {
        for signed in [x, -x] {
            let expected = 1.0 - signed / 2.0 + signed * signed / 12.0;
            assert_within_ulps(signed, bernoulli(signed), expected, 4.0);
        }
    }
}

#[test]
fn bernoulli_matches_a_high_precision_reference() {
    for (x, expected) in REFERENCE {
        assert_within_ulps(x, bernoulli(x), expected, 4.0);
    }
}

#[test]
fn bernoulli_obeys_its_symmetry_identity() {
    // `B(-x) - B(x) == x` is exact and, unlike `B(-x) == exp(x) * B(x)`,
    // contains no term that overflows. It constrains the large negative
    // branch against the large positive one at every magnitude.
    for x in [
        1.0e-16_f64,
        1.0e-12,
        1.0e-4,
        0.01,
        0.1,
        1.0,
        10.0,
        50.0,
        100.0,
        500.0,
        700.0,
        1000.0,
        1.0e6,
    ] {
        let difference = bernoulli(-x) - bernoulli(x);
        let tolerance = 8.0 * f64::EPSILON * x.max(1.0);

        assert!(
            (difference - x).abs() <= tolerance,
            "B(-{x:e}) - B({x:e}) = {difference:e}, expected {x:e}"
        );
    }
}

#[test]
fn bernoulli_stays_finite_for_large_arguments() {
    for x in [50.0_f64, 100.0, 500.0, 700.0, 1000.0] {
        let positive = bernoulli(x);
        let negative = bernoulli(-x);

        assert!(
            positive.is_finite(),
            "B({x:e}) = {positive:e} is not finite"
        );
        assert!(
            negative.is_finite(),
            "B(-{x:e}) = {negative:e} is not finite"
        );

        // `B(-x)` approaches `-x` from above; by this magnitude the two are
        // indistinguishable in `f64`.
        assert_within_ulps(-x, negative, x, 4.0);
    }
}

#[test]
fn bernoulli_survives_the_window_where_exp_overflows() {
    // Between x ~= 709.78, where `exp(x)` overflows, and x ~= 714.6, where the
    // result stops being a normal `f64`, lies the reason the large positive
    // branch exists: `x / exp_m1(x)` divides by infinity and collapses to zero
    // across this whole window, while the true values are representable.
    const WINDOW: [(f64, f64); 5] = [
        (709.8, 3.880_723_853_403_031e-306),
        (710.0, 3.178_163_220_229_342_4e-306),
        (711.0, 1.170_827_643_084_826_8e-306),
        (712.0, 4.313_292_185_103_229e-307),
        (713.0, 1.589_000_130_348_443_4e-307),
    ];

    // `exp(-x)` is itself subnormal here, so the rescued values carry a
    // relative error of order 1e-14 rather than the 1e-16 available
    // elsewhere. Measured worst case across the window is 33 ulp.
    for (x, expected) in WINDOW {
        let value = bernoulli(x);

        assert!(value > 0.0, "B({x:e}) collapsed to {value:e}");
        assert_within_ulps(x, value, expected, 64.0);
    }
}

#[test]
fn bernoulli_is_never_negative() {
    for i in -400_000..=400_000 {
        let x = f64::from(i) * 0.005;
        let value = bernoulli(x);

        assert!(value >= 0.0, "B({x:e}) = {value:e} is negative");
    }
}

#[test]
fn bernoulli_underflows_to_positive_zero() {
    // `B` is mathematically positive everywhere, but decreases past the
    // smallest subnormal `f64`. It must saturate at `+0.0` rather than
    // turning negative or returning `-0.0`.
    assert!(bernoulli(745.0) > 0.0);
    assert_eq!(bernoulli(746.0), 0.0);

    for x in [746.0_f64, 1000.0, f64::MAX] {
        assert!(
            bernoulli(x).is_sign_positive(),
            "B({x:e}) underflowed to negative zero"
        );
    }
}

#[test]
fn bernoulli_decreases_monotonically() {
    // Checked only while `B` is a normal `f64`. Past that point `exp(-x)` is
    // itself subnormal and retains just a few significant bits, so
    // neighbouring values stop being reliably ordered. The tail is covered by
    // `bernoulli_is_never_negative` and `bernoulli_underflows_to_positive_zero`
    // instead.
    let mut previous = f64::INFINITY;

    for i in -200_000..=200_000 {
        let x = f64::from(i) * 0.005;
        let current = bernoulli(x);

        if current < f64::MIN_POSITIVE {
            break;
        }

        assert!(
            current <= previous,
            "B increased between {:e} and {x:e}",
            x - 0.005
        );

        previous = current;
    }
}

#[test]
fn bernoulli_is_continuous_where_the_evaluation_method_changes() {
    for seam in [1.0e-4_f64, -1.0e-4, 50.0] {
        let step = seam.abs() * 1.0e-10;
        let at = bernoulli(seam);

        for neighbour in [bernoulli(seam - step), bernoulli(seam + step)] {
            let relative = (neighbour - at).abs() / at.abs();

            assert!(
                relative <= 10.0 * step,
                "B jumps by {relative:e} across the seam at {seam:e}"
            );
        }
    }
}

#[test]
fn bernoulli_has_a_continuous_first_derivative_at_the_seams() {
    for seam in [1.0e-4_f64, -1.0e-4, 50.0] {
        let step = seam.abs() * 1.0e-6;
        let at = bernoulli(seam);

        // One-sided slopes taken entirely within each branch. A kink at the
        // seam shows up as a mismatch far larger than the truncation error.
        let below = (at - bernoulli(seam - step)) / step;
        let above = (bernoulli(seam + step) - at) / step;
        let scale = below.abs().max(above.abs());

        assert!(
            (below - above).abs() <= 1.0e-3 * scale,
            "one-sided slopes at {seam:e} disagree: {below:e} against {above:e}"
        );
    }
}

#[test]
fn bernoulli_handles_non_finite_arguments() {
    assert!(bernoulli(f64::NAN).is_nan());

    assert_eq!(bernoulli(f64::INFINITY), 0.0);
    assert!(bernoulli(f64::INFINITY).is_sign_positive());

    assert_eq!(bernoulli(f64::NEG_INFINITY), f64::INFINITY);
}
