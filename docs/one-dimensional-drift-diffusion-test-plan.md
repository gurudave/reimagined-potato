# Verification Test Plan for a 1D Semiconductor Drift-Diffusion Solver

## Purpose and scope

This document defines a comprehensive verification matrix for a one-dimensional semiconductor drift-diffusion solver. No finite suite can exhaust a continuous parameter space; this plan is exhaustive with respect to the distinct equations, constitutive models, boundary and interface conditions, discretisation paths, nonlinear regimes, and important model interactions normally present in such a solver.

The assumed core system is

$$
\nabla\cdot(\epsilon\nabla\psi)
=-q\left(p-n+N_D^+-N_A^-+\rho_f/q\right),
$$

$$
\frac{\partial n}{\partial t}
=\frac{1}{q}\nabla\cdot J_n+G-R,
\qquad
\frac{\partial p}{\partial t}
=-\frac{1}{q}\nabla\cdot J_p+G-R,
$$

with finite-volume spatial discretisation and Scharfetter-Gummel, or an equivalent exponentially fitted, carrier flux. Tests relating to unsupported optional physics may be marked not applicable, but every implemented model must have tests at the primitive, residual, Jacobian, device, and interaction levels.

## Verification principles

Use several independent forms of oracle:

1. **Exact discrete identities** for constants, linear functions, thermal equilibrium, and locally fitted fluxes.
2. **Analytic continuum solutions** for simple electrostatic and transport problems.
3. **Manufactured solutions** for coupled, spatially varying problems.
4. **Conservation laws and metamorphic relations** where no convenient closed form exists.
5. **Independent implementation comparisons** for sparse/dense assembly, solver backends, and variable formulations.
6. **Convergence studies** rather than single-mesh agreement for continuum results.

Each test record should state:

- governing models and disabled models;
- geometry, mesh, materials, contacts, temperature, doping, and bias;
- initial guess and continuation strategy;
- primary oracle and required tolerance;
- conservation checks;
- expected nonlinear and linear solver status.

## 1. Mathematical primitives

### 1.1 Bernoulli function

- Verify \(B(0)=1\).
- Evaluate \(B(x)\) at \(x=\pm10^{-16},\pm10^{-12},\pm10^{-8}\); verify series accuracy and absence of cancellation.
- Test moderate arguments \(x=\pm0.01,\pm0.1,\pm1,\pm10\).
- Test large positive arguments \(x=50,100,500,700,1000\); require finite, overflow-safe results.
- Test equally large negative arguments.
- Verify \(B(-x)=e^xB(x)\) using an overflow-safe comparison.
- Verify positivity for every finite argument.
- Verify continuity of the value and derivatives where the implementation changes evaluation method.
- Compare first and second derivatives with a high-precision reference.
- Verify scalar and vectorised implementations agree.

### 1.2 Exponentials, transformations, and constants

- Test safe exponential evaluation at zero, small, moderate, and extreme arguments.
- Verify continuity at every exponential-clipping boundary.
- Round-trip density through Slotboom variables.
- Round-trip density through quasi-Fermi levels.
- Verify representation of very small positive densities.
- Verify explicit handling of non-positive densities.
- Check \(V_T=kT/q\) over the supported temperature range.
- Check the Einstein relation \(D=\mu V_T\).
- Verify physical constants and sign conventions against fixed references.
- Verify equivalent SI and supported alternative-unit inputs produce the same physical solution.

## 2. Carrier statistics and material properties

### 2.1 Boltzmann statistics

- Intrinsic material gives \(n=p=n_i\).
- Equilibrium obeys \(np=n_i^2\).
- Electron density is monotonic in electron quasi-Fermi level.
- Hole density has the corresponding monotonic dependence on hole quasi-Fermi level.
- Test limiting behaviour several tens of \(kT\) above and below each band edge.
- Solve charge neutrality in strongly n-type material and verify \(n\approx N_D-N_A\).
- Verify \(p=n_i^2/n\) in the same regime.
- Repeat the majority/minority tests for p-type material.
- Test exact, near, donor-dominated, and acceptor-dominated compensation.
- Test stable neutrality when \(|N_D-N_A|\ll N_D+N_A\).
- Check temperature dependence of \(N_C,N_V,E_g,\) and \(n_i\).

### 2.2 Fermi-Dirac statistics, if supported

- Verify the non-degenerate limit agrees with Boltzmann statistics.
- Test strongly degenerate n-type and p-type limits.
- Verify positivity and monotonicity of every Fermi integral used.
- Round-trip forward and inverse Fermi-integral evaluation.
- Verify the generalised Einstein relation.
- Verify smooth behaviour through the Boltzmann-to-degenerate transition.

### 2.3 Incomplete ionisation, if supported

- Verify the fully ionised high-temperature limit.
- Verify freeze-out at low temperature.
- Solve neutrality for donor-only, acceptor-only, and compensated material.
- Check ionised-dopant derivatives used in the Jacobian.
- Combine incomplete ionisation with degeneracy.

### 2.4 Material data

- Verify material interpolation rules.
- Test constant and spatially varying permittivity, affinity, bandgap, density of states, and mobility parameters.
- Verify temperature interpolation at tabulation knots and between knots.
- Reject or explicitly handle zero/negative permittivity, density of states, mobility, bandgap, and temperature.

## 3. Local physical models

### 3.1 Mobility

For every mobility model:

- test its zero-field value;
- test the low-field asymptote;
- test the high-field or velocity-saturation asymptote;
- verify continuity at branch and clamp points;
- verify required monotonicity;
- test electron and hole parameters independently;
- test zero, moderate, and extreme doping;
- test the supported temperature range;
- distinguish signed-field from absolute-field dependence correctly;
- compare analytic derivatives with numerical or automatic differentiation;
- test zero carrier density and zero electric field;
- reject negative mobility and invalid saturation parameters.

### 3.2 Shockley-Read-Hall recombination

- Verify \(R_{\mathrm{SRH}}=0\) when \(np=n_i^2\).
- Verify positive net recombination when \(np>n_i^2\).
- Verify negative net recombination when \(np<n_i^2\).
- Test mid-gap and off-centre traps.
- Test equal and unequal electron and hole lifetimes.
- Recover the minority-carrier lifetime limit in n-type material.
- Recover the corresponding p-type limit.
- Test very low- and high-injection limits.
- Verify the infinite-lifetime limit gives zero recombination.
- Check derivatives with respect to every coupled primary variable.
- Test extreme carrier ratios without cancellation or overflow.

### 3.3 Other generation-recombination models

For radiative, Auger, impact-ionisation, tunnelling, optical, and other implemented models:

- verify the equilibrium zero condition where applicable;
- verify the sign under net generation and recombination;
- set each coefficient independently to zero;
- test every dominant limiting regime;
- verify a combined model equals the sum of separately evaluated mechanisms;
- compare all Jacobian derivatives with an independent reference;
- test field reversal according to the model's physical symmetry;
- verify smooth residuals around thresholds and clamps;
- integrate local generation and compare with the prescribed total pair-generation rate.

## 4. Discrete operators and fluxes

### 4.1 Poisson operator

- Constant potential gives zero internal residual.
- Linear potential in uniform permittivity gives zero divergence.
- Quadratic potential gives the expected constant charge.
- Repeat all three on uniform and nonuniform meshes.
- Verify the electric-field sign convention.
- Verify control-volume measures at both boundaries.
- Verify matrix symmetry and the expected definiteness before boundary modification.
- Test the selected permittivity averaging rule.
- An abrupt dielectric interface conserves normal displacement.
- Verify the domain-integrated Gauss law.
- Exercise one-cell, two-cell, and three-cell domains.

### 4.2 Scharfetter-Gummel flux

- Uniform potential and density give zero current.
- Uniform potential with a density gradient gives pure diffusion.
- Uniform density with a small field gives the drift limit.
- A small potential drop agrees with centred drift-diffusion.
- Large positive and negative potential drops remain finite.
- A Boltzmann equilibrium density profile gives exactly zero discrete current.
- Recover the analytic constant-field advection-diffusion solution.
- Reversing edge orientation reverses the flux.
- Verify electron and hole sign conventions independently.
- Verify current continuity between adjacent cells.
- Test the Einstein-relation dependency.
- Test extreme potential drops and density ratios in combination.
- Test extreme cell widths.
- Zero mobility gives zero current.
- Verify equivalent density, logarithmic-density, Slotboom, and quasi-Fermi formulations agree.

### 4.3 Alternative discretisations, if selectable

- Centred, upwind, and Scharfetter-Gummel schemes converge to the same continuum solution.
- Centred flux is correct in its low-Péclet regime.
- Scharfetter-Gummel flux remains monotone in high-Péclet cases.
- Measure the expected spatial convergence order of each scheme.
- Verify scheme selection changes only the intended terms.

## 5. Residuals, Jacobians, and assembly

For every equation, boundary condition, interface model, and constitutive model:

- evaluate the residual at a constructed exact solution;
- compare each analytic Jacobian block with finite differences, complex-step differentiation, or automatic differentiation;
- perform the directional test

  $$
  \frac{F(x+hv)-F(x)}{h}\longrightarrow J(x)v;
  $$

- run derivative tests at equilibrium, low bias, high bias, and strong injection;
- run them at very low and very high carrier densities;
- check all cross derivatives, including recombination coupling between carriers;
- check boundary and interface Jacobian rows;
- check transient mass-matrix terms;
- verify the Jacobian sparsity pattern;
- verify dense and sparse assembly give identical matrices and residual vectors;
- verify assembly is independent of memory layout and execution order;
- test each side of clipping and piecewise-model transitions without differentiating exactly at a nonsmooth point.

## 6. Boundary and interface conditions

### 6.1 Electrostatic boundaries

- Dirichlet-Dirichlet capacitor.
- Dirichlet-Neumann Poisson problem.
- Neumann-Dirichlet Poisson problem.
- Neumann-Neumann problem with explicit nullspace detection or gauge fixing.
- Specified surface charge.
- Zero-field symmetry boundary.
- Applied work-function difference.
- Contact-voltage sign and reference-potential conventions.
- Common electrostatic gauge shift leaves observables unchanged.

### 6.2 Ohmic contacts

- Intrinsic equilibrium contact.
- n-type majority and minority carrier densities.
- Corresponding p-type case.
- Compensated semiconductor.
- Degenerate contact statistics.
- Incomplete ionisation at the contact.
- Zero bias gives zero terminal current.
- Equal voltage on both contacts gives zero field and current.
- Contact densities remain consistent with local potential.
- Applied bias shifts the electrochemical potential by the intended amount and sign.

### 6.3 Selective, insulating, and surface-recombination contacts

- Electron-blocking and hole-conducting contact.
- Hole-blocking and electron-conducting contact.
- Fully insulating boundary gives \(J_n=J_p=0\).
- Zero surface-recombination velocity.
- Infinite surface-recombination velocity limit.
- Finite surface recombination against an analytic diffusion solution.
- Robin residual and Jacobian verification.

### 6.4 Schottky contacts

- Flat-band condition.
- Zero-bias thermal equilibrium.
- Saturation-current dependence on barrier height.
- Forward- and reverse-bias signs.
- Electron-only and hole-only thermionic limits.
- Image-force barrier lowering, if supported.
- Ohmic limit as barrier height tends to zero.
- Blocking limit at large barrier height.

### 6.5 Material interfaces

- An interface between identical materials has no effect.
- Permittivity discontinuity preserves displacement continuity.
- Conduction-band offset only.
- Valence-band offset only.
- Simultaneous conduction- and valence-band offsets.
- Equilibrium Fermi level is constant across the interface.
- Verify the equilibrium carrier-density discontinuity.
- Interface-transfer current is equal on both sides.
- Interface charge produces the expected displacement jump.
- Interface recombination conserves electron-hole pair accounting.
- Reversing material order reflects the solution.

## 7. Analytic and manufactured PDE solutions

### 7.1 Electrostatics

- Empty parallel-plate capacitor: linear potential and \(C=\epsilon A/L\).
- Uniform fixed charge: quadratic potential.
- Piecewise-uniform charge: piecewise quadratic potential.
- Two-dielectric capacitor: piecewise-linear potential and series capacitance.
- Sheet charge: exact electric-field jump.
- Small-signal Debye screening: exponential potential decay.
- Fully depleted uniformly doped slab.
- Abrupt-junction depletion approximation away from the depletion-edge singularity.

### 7.2 Uncoupled carrier transport

- Zero-field diffusion between fixed concentrations: linear density and constant current.
- Constant field with equal endpoint densities.
- General constant-field advection-diffusion with unequal endpoint densities.
- Exponential equilibrium density with zero Scharfetter-Gummel flux.
- Uniform generation and zero field: parabolic density.
- Uniform first-order recombination: exponential or hyperbolic profile.
- Localised generation with a known piecewise solution.
- Surface recombination with a known diffusion-length solution.

### 7.3 Method of manufactured solutions

Choose nontrivial smooth functions such as

$$
\psi(x)=a_0+a_1x+a_2\sin(kx),\quad
n(x)=n_0e^{b\sin(kx)},\quad
p(x)=p_0e^{c\cos(kx)}
$$

and derive the required doping and source functions. Cover:

- Poisson alone;
- electron continuity alone;
- hole continuity alone;
- fully coupled steady state;
- fully coupled transient state;
- nonuniform permittivity;
- spatially varying mobility and diffusivity;
- nonuniform generation and recombination;
- heterogeneous material coefficients;
- uniform and strongly nonuniform meshes;
- spatial convergence order;
- temporal convergence order;
- combined space-time refinement.

## 8. Canonical complete devices

### 8.1 Uniform semiconductor resistor

- Intrinsic, n-type, and p-type slabs.
- Zero-bias equilibrium.
- Low-bias Ohmic law:

  $$
  J=q(\mu_n n+\mu_p p)V/L.
  $$

- Resistance scales with length, area, mobility, and carrier density.
- Bias reversal gives \(J(-V)=-J(V)\) for symmetric physics.
- High-field velocity saturation.
- Self-consistent redistribution under large bias.
- Nearly neutral bulk with contact Debye layers.

### 8.2 Abrupt p-n junction

- Symmetric and asymmetric doping.
- Verify

  $$
  V_{bi}=V_T\ln\left(\frac{N_AN_D}{n_i^2}\right)
  $$

  in the nondegenerate, fully ionised limit.
- Verify depletion charge balance \(N_Ax_p=N_Dx_n\).
- Verify electric-field continuity and junction peak.
- Verify zero electron, hole, and total current at equilibrium.
- Verify minority-carrier mass action in neutral regions.
- Recover exponential forward current at low injection.
- Extract ideality factor near one in the diffusion-dominated regime.
- Recover ideality factor near two for depletion-region SRH recombination.
- Verify reverse saturation current and its temperature dependence.
- Test high-injection departure from ideal-diode behaviour.
- Test series-resistance-limited current.
- Test breakdown models, if implemented.
- Sweep bias in both directions and verify the same DC branch.
- Test doping, length, mobility, and lifetime scaling.

### 8.3 Graded junction

- Linear and smooth doping profiles.
- Built-in field in the graded quasi-neutral region.
- Mesh convergence near the junction.
- Convergence towards the abrupt-junction result as grading width tends to zero.

### 8.4 p-i-n diode

- Equilibrium depletion across the intrinsic region.
- Reverse-bias field distribution.
- Forward injection.
- Uniform optical generation.
- Photocurrent scaling with generation and active width.
- Collection efficiency versus lifetime and electric field.
- Dark and illuminated current superposition in its valid regime.

### 8.5 Schottky diode

- Equilibrium band bending.
- Thermionic-emission forward-current law.
- Barrier-height and temperature scaling.
- Reverse saturation.
- Transition towards Ohmic behaviour at small barrier height.

### 8.6 Heterojunction diode

- Equilibrium band alignment.
- Correct carrier discontinuities.
- Zero equilibrium current across band offsets.
- Forward/reverse transport asymmetry.
- Homojunction limit as offsets and material differences vanish.

### 8.7 MOS capacitor or gated slab

- Flat-band voltage.
- Accumulation, depletion, and inversion.
- Semiconductor charge balances gate charge.
- Low- and high-frequency C-V limits, if modelled.
- Oxide-thickness and permittivity scaling.
- Work-function and fixed-charge shifts.
- Mesh convergence in the inversion layer.
- Reflection symmetry in a symmetric double-gate structure.

### 8.8 Bipolar structures, if supported

- npn and pnp complementary symmetry.
- Emitter injection efficiency.
- Base transport and recombination.
- Collector current versus base-emitter bias.
- Current conservation across all terminals.
- Thin- and thick-base limits.

## 9. Transient verification

### 9.1 Pure temporal behaviour

- Uniform excess carriers with first-order recombination decay as \(e^{-t/\tau}\).
- Constant generation approaches steady state exponentially.
- Switching generation off returns to equilibrium.
- Total carrier number is conserved with insulating boundaries and no source or recombination.
- Pair generation changes electron and hole counts equally.
- Small time steps approach the semidiscrete continuous-time solution.
- A large implicit step approaches the DC solution.

### 9.2 Diffusion and transport

- Diffusion eigenmode:

  $$
  \delta n(x,t)\propto
  \sin(\pi x/L)e^{-D\pi^2t/L^2}.
  $$

- Gaussian-packet variance increases as \(2Dt\).
- Packet centroid in a constant field moves at \(\mu Et\).
- Combined drift and diffusion.
- Reflection at an insulating boundary.
- Absorption at an ideal recombination contact.
- Transit time through a uniform slab.

### 9.3 Device transients

- Capacitor voltage step.
- MOS accumulation, depletion, and inversion steps.
- p-n diode turn-on.
- Reverse recovery.
- Photodiode light pulse.
- Generation impulse and integrated collected charge.
- Terminal current includes displacement current where required.
- Kirchhoff current law holds throughout the transient.
- Charge change equals integrated terminal current plus generation minus recombination.

### 9.4 Time-integrator behaviour

For each supported integrator:

- verify formal order of accuracy;
- test step rejection and retry;
- test adaptive response to a sharp transient;
- preserve a steady-state solution;
- maintain positivity according to the solver's stated policy;
- verify restart gives the same trajectory.

## 10. Mesh coverage

- One-, two-, and three-cell meshes.
- Uniform meshes with even and odd node counts.
- Smoothly graded mesh.
- Abrupt adjacent-cell size changes.
- Maximum supported mesh-spacing ratio.
- Junction exactly on a node, at a cell face, and inside a cell.
- Material interface at each of those positions.
- Very small contact-adjacent cells.
- Long neutral region combined with a very thin depletion region.
- Spatially reflected mesh and device.
- Reject duplicate, unsorted, and zero-spacing coordinates.
- Refinement by factors of two demonstrates expected convergence.
- Coarsening and subsequent refinement preserve physical inputs.
- Adaptive indicators target field, charge, and recombination layers.
- Integrated charge and current converge even where pointwise fields converge more slowly.

## 11. Nonlinear and linear solver robustness

- Exact initial solution converges immediately.
- Equilibrium initial guess.
- Charge-neutral initial guess.
- Flat-potential initial guess.
- Previous-bias continuation.
- Continuation from positive and negative bias directions.
- Deliberately poor but physical initial guess.
- Extremely small and large initial densities.
- Locally non-positive initial densities exercise the documented recovery policy.
- Abrupt voltage step versus ramped continuation.
- Damped and undamped Newton in their applicable regimes.
- Gummel and fully coupled Newton converge to the same solution.
- Dense and sparse direct solvers agree.
- Iterative solver agrees with a direct reference.
- Linear tolerance is tight enough to preserve nonlinear convergence.
- Residual scaling balances Poisson and continuity equations.
- Variable scaling handles voltage and density magnitude differences.
- Line search reduces its defined merit function.
- Nonconvergence is reported rather than returned as a valid state.
- Maximum-iteration and minimum-step failures are explicit.
- Continuation recovers cleanly after a rejected step.
- Repeated solves are deterministic to the specified tolerance.

## 12. Extreme physical regimes

- Undoped intrinsic material and exactly zero doping.
- Very light, heavy, and degenerate doping.
- Doping spanning many decades.
- Near-exact donor-acceptor compensation.
- Device length much shorter than, comparable with, and much longer than the Debye length.
- Depletion width much smaller than, comparable with, and greater than device length.
- Applied voltage \(\ll V_T\), \(\sim V_T\), and \(\gg V_T\).
- Near-zero and very large electric fields.
- Mobility approaching zero.
- Lifetime approaching zero and infinity.
- Large permittivity contrast.
- Carrier density near underflow and near the supported maximum.
- Low and high temperatures within model validity.
- Very small \(n_i\) in wide-bandgap material.
- Large band offsets.
- Strong generation and high-level injection.
- Quasi-neutral charge computed as the small difference of large terms.
- Space-charge-limited current, where assumptions apply:

  $$
  J=\frac{9}{8}\epsilon\mu\frac{V^2}{L^3}.
  $$

## 13. Conservation and metamorphic tests

- Global electron conservation.
- Global hole conservation.
- Integrated Poisson equation equals the boundary displacement-flux difference.
- Steady-state total current is spatially constant.
- Electron and hole currents vary consistently with pair generation-recombination while their sum remains constant in steady state.
- Terminal charge balances integrated space charge.
- Transient total current includes conduction and displacement components.
- Equilibrium quasi-Fermi levels are spatially constant.
- Transport proceeds down the electrochemical-potential gradient.
- Reflecting \(x\mapsto L-x\), contacts, and profiles reflects the solution and reverses current.
- Swapping identical contacts has no effect.
- Bias reversal in a symmetric device reverses current.
- Electron-hole transformation gives the complementary device when all parameters are transformed.
- A common electrostatic gauge offset changes no physical observable.
- Splitting a homogeneous cell does not materially change the solution.
- A zero-offset identical-material interface has no effect.
- Setting an optional coefficient to zero recovers the simpler model.
- Combined generation-recombination equals the sum of independently evaluated mechanisms.
- Known nondimensional similarity transformations give the corresponding scaled solution.
- Unit-system conversion leaves observables invariant.

## 14. Input validation and failure behaviour

- Reject nonmonotonic or repeated mesh coordinates.
- Reject insufficient mesh points.
- Reject negative temperature.
- Reject zero or negative permittivity, mobility, lifetime, and density of states.
- Reject invalid material-region extents.
- Reject gaps and overlaps between regions unless explicitly meaningful.
- Reject missing contacts.
- Reject incompatible boundary conditions on one variable.
- Detect a pure-Neumann Poisson system without a gauge constraint.
- Reject NaN and infinity in parameters, states, and boundary values.
- Reject unknown materials and model names.
- Reject invalid statistics/model combinations.
- Reject unsupported interface/contact combinations.
- Detect contact data inconsistent with selected statistics.
- Reject zero or negative time steps and reversed time intervals.
- Reject invalid nonlinear and linear tolerances.
- Reject restart data with incompatible mesh, models, or variable ordering.
- Report overflow, singular Jacobian, and nonconvergence diagnostically.

## 15. Software and regression tests

- Residual assembly is independent of thread count.
- Parallel and serial assembly agree.
- Repeated runs are deterministic where promised.
- Checkpoint and restart round-trip.
- Input serialisation round-trip.
- Derived current, charge, and field are independently reproducible from primary variables.
- Node- and edge-centred output locations are labelled correctly.
- Current direction and terminal signs are fixed by tests.
- First/last-node and material/contact indexing.
- Sparse-pattern reuse leaves no stale entries.
- Reusing a solver instance leaves no old device state.
- Parameter sweeps agree with equivalent independent runs.
- Rejected continuation steps do not contaminate the accepted state.
- Golden values use both absolute and scale-aware relative tolerances.
- Performance regression for assembly, factorisation, and bias sweeps.
- Memory use scales linearly with the number of 1D nodes.
- Large-mesh tests detect accidental dense matrix construction.

## 16. Required interaction coverage

The core representative matrix is:

| Dimension | Representative values |
|---|---|
| Carrier formulation | density; logarithmic density; Slotboom; quasi-Fermi |
| Statistics | Boltzmann; Fermi-Dirac |
| Mesh | coarse; refined; graded; interface-misaligned |
| Device | intrinsic slab; resistor; p-n; p-i-n; Schottky; heterojunction |
| Bias | equilibrium; low forward; high forward; reverse |
| Temperature | low; nominal; high |
| Injection | equilibrium; low; high |
| Nonlinear solver | Newton; Gummel |
| Linear solver | each direct and iterative backend |
| Time | DC; small step; large step; adaptive transient |
| Recombination | none; each individual model; combined models |

Use complete Cartesian coverage for the particularly hazardous interactions:

- extreme Scharfetter-Gummel argument × extreme density ratio × nonuniform mesh;
- heavy doping × degeneracy × incomplete ionisation;
- heterojunction offset × interface placement × bias direction;
- recombination model × injection level × Jacobian verification;
- contact type × majority-carrier polarity × bias direction;
- transient integrator × boundary type × generation-recombination;
- poor initial guess × scaling × continuation strategy.

For the remaining matrix, use pairwise or higher-strength combinatorial selection, supplemented by model-specific judgement.

## 17. Acceptance hierarchy

Run and diagnose tests in this order:

1. primitive numerical identities;
2. exact discrete operator and flux identities;
3. residual and Jacobian agreement;
4. analytic solutions;
5. manufactured-solution convergence;
6. conservation and metamorphic tests;
7. canonical devices;
8. extreme regimes;
9. software and performance regression.

This ordering localises defects: a full-device failure should not be the first indication of an error in Bernoulli evaluation, flux orientation, boundary assembly, or a recombination derivative.

## Suggested tolerance policy

Tolerances should be stated in dimensionless, scaled quantities wherever possible:

- primitive identities: near machine precision away from conditioning limits;
- analytic Jacobian directional tests: error decreases linearly with perturbation until roundoff dominates;
- exact discrete equilibria: residual and current near scaled solver precision;
- analytic continuum tests: agreement consistent with measured discretisation error;
- conservation: tighter than the requested solution accuracy after appropriate scaling;
- regression values: tolerances derived from numerical conditioning, not historical output noise.

No test should use a relaxed tolerance merely because a defective implementation has become a longstanding reference result.
