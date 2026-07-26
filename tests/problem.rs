//! Domain validation against test plan section 14.

use drift_diffusion_1d::{Problem1D, ProblemError};

#[test]
fn uniform_problem_has_one_more_node_than_cells() {
    let problem = Problem1D::uniform(1.0e-6, 100).expect("valid domain");

    assert_eq!(problem.length_metres(), 1.0e-6);
    assert_eq!(problem.cell_count(), 100);
    assert_eq!(problem.node_count(), 101);
}

#[test]
fn non_positive_lengths_are_rejected() {
    for length in [0.0, -0.0, -1.0e-6, -1.0] {
        assert_eq!(
            Problem1D::uniform(length, 100),
            Err(ProblemError::InvalidLength),
            "length {length:e} was accepted"
        );
    }
}

#[test]
fn non_finite_lengths_are_rejected() {
    for length in [f64::NAN, f64::INFINITY, f64::NEG_INFINITY] {
        assert_eq!(
            Problem1D::uniform(length, 100),
            Err(ProblemError::InvalidLength),
            "length {length:e} was accepted"
        );
    }
}

#[test]
fn an_empty_mesh_is_rejected() {
    assert_eq!(Problem1D::uniform(1.0e-6, 0), Err(ProblemError::EmptyMesh));
}

#[test]
fn a_cell_count_that_cannot_be_bounded_by_nodes_is_rejected() {
    // `node_count` is `cell_count + 1`, which would wrap to zero here.
    assert_eq!(
        Problem1D::uniform(1.0e-6, usize::MAX),
        Err(ProblemError::MeshTooLarge)
    );

    let largest = Problem1D::uniform(1.0e-6, usize::MAX - 1).expect("valid domain");
    assert_eq!(largest.node_count(), usize::MAX);
}

#[test]
fn validation_errors_describe_themselves() {
    for (error, expected) in [
        (
            ProblemError::InvalidLength,
            "domain length must be finite and positive",
        ),
        (
            ProblemError::EmptyMesh,
            "domain must contain at least one cell",
        ),
        (ProblemError::MeshTooLarge, "domain contains too many cells"),
    ] {
        assert_eq!(error.to_string(), expected);
    }
}
