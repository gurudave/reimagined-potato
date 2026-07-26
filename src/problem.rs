//! Definition and validation of a one-dimensional simulation domain.

use std::error::Error;
use std::fmt;

/// Minimal spatial definition for a one-dimensional problem.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Problem1D {
    length_metres: f64,
    cell_count: usize,
}

impl Problem1D {
    /// Creates a uniformly partitioned domain.
    ///
    /// # Errors
    ///
    /// Returns [`ProblemError`] when the length is non-finite or non-positive,
    /// when the domain contains no cells, or when the cell count is so large
    /// that the bounding nodes cannot be counted.
    pub fn uniform(length_metres: f64, cell_count: usize) -> Result<Self, ProblemError> {
        if !length_metres.is_finite() || length_metres <= 0.0 {
            return Err(ProblemError::InvalidLength);
        }
        if cell_count == 0 {
            return Err(ProblemError::EmptyMesh);
        }
        // Rejected here so that `node_count` stays infallible.
        if cell_count.checked_add(1).is_none() {
            return Err(ProblemError::MeshTooLarge);
        }

        Ok(Self {
            length_metres,
            cell_count,
        })
    }

    /// Returns the physical domain length in metres.
    #[must_use]
    pub const fn length_metres(self) -> f64 {
        self.length_metres
    }

    /// Returns the number of finite-volume cells.
    #[must_use]
    pub const fn cell_count(self) -> usize {
        self.cell_count
    }

    /// Returns the number of nodes bounding the cells.
    ///
    /// The cell count is bounded by [`Problem1D::uniform`], so this cannot
    /// overflow.
    #[must_use]
    pub const fn node_count(self) -> usize {
        self.cell_count + 1
    }
}

/// Validation error for a [`Problem1D`].
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProblemError {
    /// The physical length was non-finite or non-positive.
    InvalidLength,
    /// No finite-volume cells were requested.
    EmptyMesh,
    /// The cell count leaves no room to count the bounding nodes.
    MeshTooLarge,
}

impl fmt::Display for ProblemError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::InvalidLength => formatter.write_str("domain length must be finite and positive"),
            Self::EmptyMesh => formatter.write_str("domain must contain at least one cell"),
            Self::MeshTooLarge => formatter.write_str("domain contains too many cells"),
        }
    }
}

impl Error for ProblemError {}
