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
- a minimal validated one-dimensional problem definition;
- integration-test scaffolding for both.
