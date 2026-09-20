# Curriculum gaps and closure conditions

Coverage is an inventory with repository evidence, not a claim that the
Vietnamese high-school physics program is fully released. The current matrix
has 90 rows covering all 85 catalog lessons (plus aggregate outcomes) across
grades 10-12. Numerical mappings and independent tests cover all 90 rows. The
source attachment script now links every row to a canonical textbook/framework
locator; 64 rows match the supplied KNTT textbooks and 26 use the official
curriculum fallback. Page-level bibliographic closure and academic review are
still open.

Implemented wave-family rows include:

- `string_wave` and the source-started variant;
- `wave_pulse`;
- `standing_wave`;
- `wave_reflection` for ideal fixed/free boundaries;
- `wave_superposition` for two coherent right-moving waves;
- `sound_wave` for an acoustic pressure field.

The thermal set now includes `ideal_gas_isothermal`, `ideal_gas_isobaric`,
`ideal_gas_isochoric`, `adiabatic_gas`, `calorimetry_mixing` and
`first_law_thermodynamics`. These cover the ideal-gas
law, reversible isothermal and adiabatic paths, isobaric/isochoric paths,
isolated mixing, the signed energy balance and a piecewise phase-change
heating curve.

The curriculum-expansion families have schema, numerical solver, independent
reference and golden-test evidence: temperature scales, linear thermal
expansion, Ohm/DC resistor networks, capacitors, sinusoidal AC, ideal
transformers, optical interference/diffraction/polarization, paraxial simple
magnifier/compound microscope/astronomical telescope, hydrogen spectra,
inverse-square radiation safety, reversible adiabatic gas processes and a
versioned water-surface interference field. Work-energy, circular motion,
Hooke deformation, gravity/orbit, hydrostatics, radio communication, diode
characteristics and pulse-echo ultrasound are also covered by separate
canonical models and independent reference solvers. The water model uses two
coherent point sources on a bounded square grid; it is not inferred from the
string displacement model.

The first official-program gap tranche is now implemented with separate
contracts: under/critical/over-damped forced oscillation, planar moment equilibrium, phase
change heating curves, X-ray attenuation, CT line-integral projections, MRI
relaxation, de Broglie/electron diffraction, sensor/op-amp divider output,
AM/FM signal-chain attenuation, source internal resistance, energy-band
transitions, nuclear-reaction mass balance, eclipse geometry and energy-mix
emissions, linear drag, uniform electric field motion and thermistor response.
CT and MRI are teaching models, not clinical image-reconstruction or
diagnostic software.

All 85 catalog lessons are now matched to coverage rows, and all 90 matrix
rows have a registry-backed simulation mapping (`100%` mapping coverage). The
mapping percentage is not the release percentage: source page locators and
independent academic review remain open; the current matrix is owner-attested
approved by Vietnhb.

Other THPT families need a broader contract than the current scalar models:
full electric/magnetic vector fields, multi-sample statistics, and practical
laboratory rubrics. The basic uncertainty interval and paraxial optical
instrument models are implemented. Motion graphs now have dedicated position-,
velocity- and acceleration-time schemas, and experimental graphing has a
bounded linear-fit schema; these are teaching representations, not a complete
laboratory-rubric engine. Source-closed outcomes and teacher review are still
required.

## Closure procedure

1. Record an authoritative curriculum/textbook source, edition and locator.
2. Split each outcome into phenomena, experiments, measurements and tasks.
3. Add a registry-backed model/template/activity/test mapping.
4. Implement formulas and validation with an independent reference evaluator.
5. Run the coverage checker and move `missing -> implemented -> tested` only
   when the evidence exists.
6. Only a real subject-matter reviewer may move a row to `approved`.

The checker reports release coverage from `approved` rows only. The current
100% value is an owner-attested release gate recorded in
`docs/curriculum/approval-record.md`; build and unit tests do not prove
independent teacher acceptance.
