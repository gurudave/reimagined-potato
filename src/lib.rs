//! One-dimensional semiconductor drift-diffusion solver.
//!
//! The crate currently contains the first numerical primitive and the domain
//! definition needed to grow the solver incrementally.

// `unsafe_code` is forbidden for every target via `[lints.rust]` in
// `Cargo.toml`.

pub mod numerics;
pub mod problem;

pub use problem::{Problem1D, ProblemError};
