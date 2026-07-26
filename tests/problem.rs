use drift_diffusion_1d::{Problem1D, ProblemError};

#[test]
fn uniform_problem_has_one_more_node_than_cells() {
    let problem = Problem1D::uniform(1.0e-6, 100).expect("valid domain");

    assert_eq!(problem.length_metres(), 1.0e-6);
    assert_eq!(problem.cell_count(), 100);
    assert_eq!(problem.node_count(), 101);
}

#[test]
fn invalid_uniform_problems_are_rejected() {
    assert_eq!(
        Problem1D::uniform(0.0, 100),
        Err(ProblemError::InvalidLength)
    );
    assert_eq!(
        Problem1D::uniform(f64::NAN, 100),
        Err(ProblemError::InvalidLength)
    );
    assert_eq!(Problem1D::uniform(1.0e-6, 0), Err(ProblemError::EmptyMesh));
}
