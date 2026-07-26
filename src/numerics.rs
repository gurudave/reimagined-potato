//! Numerical primitives used by the drift-diffusion discretisation.

/// Evaluates the Bernoulli function `B(x) = x / (exp(x) - 1)`.
///
/// Three regimes are used:
///
/// - near the origin, a Maclaurin series evaluates `B` directly, avoiding both
///   a library call and the cancellation a naive `exp(x) - 1` would suffer
///   (`exp_m1` is accurate here too, so this branch is a speed choice);
/// - for large positive `x`, the equivalent form
///   `x * exp(-x) / (1 - exp(-x))` avoids overflowing `exp(x)`;
/// - elsewhere, `exp_m1` evaluates the definition directly.
///
/// `B` is mathematically positive for every finite argument and satisfies the
/// exact identity `B(-x) - B(x) == x`. In `f64` it decreases past the smallest
/// subnormal and returns `+0.0` for `x` greater than about `745.1`.
///
/// Accuracy is within a few ulp up to `x` of about `708`. Past that, `exp(-x)`
/// is itself subnormal and loses significant bits, so the relative error grows
/// to roughly `1e-14` by `x = 713` and keeps growing; past about `714.6` the
/// result is subnormal too, and neighbouring values stop being reliably
/// ordered. Callers that need this range should work with `ln(B)` instead.
///
/// Non-finite arguments follow the limits of `B`: `NAN` maps to `NAN`,
/// `INFINITY` to `0.0`, and `NEG_INFINITY` to `INFINITY`.
#[must_use]
pub fn bernoulli(x: f64) -> f64 {
    if x.is_nan() {
        return f64::NAN;
    }
    if x == f64::INFINITY {
        return 0.0;
    }
    if x == f64::NEG_INFINITY {
        return f64::INFINITY;
    }

    if x.abs() < 1.0e-4 {
        let x2 = x * x;
        1.0 - x / 2.0 + x2 / 12.0 - x2 * x2 / 720.0 + x2 * x2 * x2 / 30_240.0
    } else if x > 50.0 {
        // `exp(x)` overflows to infinity above x ~= 709.78, which would drive
        // `x / exp_m1(x)` to zero even though `B(x)` stays representable down
        // to x ~= 745.1. Scaling by `exp(-x)` keeps those values.
        let exp_neg_x = (-x).exp();
        x * exp_neg_x / (1.0 - exp_neg_x)
    } else {
        x / x.exp_m1()
    }
}
