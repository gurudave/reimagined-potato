use drift_diffusion_1d::numerics::bernoulli;

#[test]
fn bernoulli_has_the_correct_limit_at_the_origin() {
    assert_eq!(bernoulli(0.0), 1.0);
    assert!((bernoulli(1.0e-12) - 1.0).abs() < 1.0e-12);
    assert!((bernoulli(-1.0e-12) - 1.0).abs() < 1.0e-12);
}

#[test]
fn bernoulli_obeys_its_symmetry_identity() {
    for x in [-10.0_f64, -1.0, -0.1, 0.1, 1.0, 10.0] {
        let left = bernoulli(-x);
        let right = x.exp() * bernoulli(x);
        let scale = left.abs().max(right.abs()).max(1.0);

        assert!((left - right).abs() <= 1.0e-13 * scale);
    }
}
