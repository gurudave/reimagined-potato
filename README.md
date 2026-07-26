# 1D drift-diffusion solver

A Rust implementation of a one-dimensional semiconductor drift-diffusion
solver. The project is at the scaffolding stage.

The full verification catalogue is in
[`docs/one-dimensional-drift-diffusion-test-plan.md`](docs/one-dimensional-drift-diffusion-test-plan.md).

## Development

The standard local checks mirror the GitHub Actions workflow:

```console
cargo fmt --all -- --check
cargo clippy --locked --all-targets --all-features -- -D warnings
cargo test --locked --all-targets --all-features
```

The initial code provides:

- a numerically stable Bernoulli function, required by Scharfetter-Gummel
  fluxes;
- a minimal validated one-dimensional problem definition.

## Verification status

`tests/bernoulli.rs` covers the value-level requirements of test plan section
1.1: the limit at the origin, absence of cancellation near zero, agreement with
an embedded high-precision reference, the overflow-safe identity
`B(-x) - B(x) == x`, finiteness at large arguments of either sign, positivity,
monotonicity, and continuity of the value and first derivative where the
implementation changes evaluation method. Comparing the second derivative
against a high-precision reference is still outstanding, as is the
scalar-versus-vectorised check, which needs a vectorised implementation first.

`tests/problem.rs` covers the parts of section 14 that apply to the domain
definition so far: rejection of non-positive, NaN, and infinite lengths, of an
empty mesh, and of a cell count that cannot be bounded by nodes.
