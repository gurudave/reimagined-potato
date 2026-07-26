//! Numerical primitives used by the drift-diffusion discretisation.

/// Evaluates the Bernoulli function
///
/// \[
/// B(x) = \frac{x}{\exp(x)-1}.
/// \]
///
/// A power series avoids cancellation near the origin, while the large
/// positive branch avoids overflowing `exp(x)`.
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
        let exp_neg_x = (-x).exp();
        x * exp_neg_x / (1.0 - exp_neg_x)
    } else {
        x / x.exp_m1()
    }
}
