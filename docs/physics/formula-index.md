# PhysLive formula/model index

This index separates implemented formulas from curriculum items still awaiting
an implementation and academic review.

## Academic basis

The implemented equations are matched against the relevant OpenStax University
Physics sections: [wave mathematics](https://openstax.org/books/university-physics-volume-1/pages/16-2-mathematics-of-waves),
[work and internal energy](https://openstax.org/books/university-physics-volume-2/pages/3-2-work-heat-and-internal-energy),
[the first law](https://openstax.org/books/university-physics-volume-2/pages/3-3-first-law-of-thermodynamics),
[Faraday's law](https://openstax.org/books/university-physics-volume-2/pages/13-1-faradays-law),
[thin lenses](https://openstax.org/books/university-physics-volume-3/pages/2-4-thin-lenses),
[photoelectric effect](https://openstax.org/books/university-physics-volume-3/pages/6-2-photoelectric-effect), and
[radioactive decay](https://openstax.org/books/university-physics-volume-3/pages/10-3-radioactive-decay).
The added mechanics models also follow OpenStax's [work definition](https://openstax.org/books/university-physics-volume-1/pages/7-1-work),
[work-energy theorem](https://openstax.org/books/university-physics-volume-1/pages/7-3-work-energy-theorem),
and [Newtonian gravitation](https://openstax.org/books/university-physics-volume-1/pages/13-1-newtons-law-of-universal-gravitation).
The source review date is 2026-09-20. Each model remains scoped to the
assumptions documented in `docs/physics/models/`; “tested” means numerical and
reference-solver agreement, not teacher or curriculum approval.

| Model | Formula family | Output | Status |
|---|---|---|---|
| `uniform_acceleration` | `x=x0+v0t+1/2 at²`, `v=v0+at` | timeseries | tested |
| `projectile` | independent x/y constant-acceleration motion | timeseries | tested |
| `forces` | Newton II with friction | timeseries | tested |
| `elastic_collision` | Momentum-conserving elastic collision | timeseries | tested |
| `spring` | simple harmonic oscillator | timeseries | tested |
| `rc_charging`, `rc_discharging` | exponential RC response | timeseries | tested |
| `string_wave` | `u=A cos(kx-omega t+phi)` | scalar field | tested |
| `wave_pulse` | Gaussian travelling pulse | scalar field | tested |
| `wave_reflection` | image pulse, `R=-1/+1` | scalar field | tested |
| `wave_superposition` | sum of two coherent waves | scalar field | tested |
| `standing_wave` | counter-propagating-wave interference | scalar field | tested |
| `sound_wave` | Acoustic pressure wave | scalar field (`Pa`) | tested |
| `ideal_gas_isothermal` | `pV=nRT`, `W=nRT ln(V/V0)` | timeseries | tested |
| `radioactive_decay` | `N=N0 exp(-lambda t)`, `A=lambda N` | timeseries | tested |
| `calorimetry_mixing` | `Tf=(m1c1T1+m2c2T2)/(m1c1+m2c2)`, `Q1+Q2=0` | timeseries | tested |
| `first_law_thermodynamics` | `Delta U=Q-W`, `Uf=U0+Delta U` | timeseries | tested |
| `electromagnetic_induction` | `Phi=BA cos(theta)`, `emf=-N dPhi/dt` | timeseries | tested |
| `ac_rlc_circuit` | `Z=sqrt(R^2+(XL-XC)^2)`, `P=I^2R` | timeseries | tested |
| `photoelectric_effect` | `Kmax=max(0,hf-Phi)`, `V0=Kmax/e`; emission threshold `hf>=Phi` | timeseries | tested |
| `nuclear_energy` | `E=Delta m c^2` | timeseries | tested |
| `point_charge_field` | `E=k|q|/r^2`, `V=kq/r` | scalar magnitude/potential probe | tested |
| `magnetic_force` | `F=|q|vB sin(theta)` (magnitude only) | scalar force-magnitude probe | tested |
| `measurement_uncertainty` | interval `[x-Delta x,x+Delta x]`, relative `|Delta x/x|` when `x!=0` | statistical | tested |
| `temperature_scales` | `T_K=T_C+273.15`, `T_F=9T_C/5+32` | timeseries | tested |
| `thermal_expansion` | `Delta L=alpha L_0 Delta T` | timeseries | tested |
| `ohms_law` | `V=IR`, `P=VI` | timeseries | tested |
| `resistors_series`, `resistors_parallel` | `R_s=sum R_i`, `1/R_p=sum(1/R_i)` | timeseries | tested |
| `capacitor_basic` | `Q=CV`, `U=1/2 CV^2` | timeseries | tested |
| `ac_waveform` | `u(t)=U_0 sin(omega t+phi)`, `U_rms=U_0/sqrt(2)` | timeseries | tested |
| `ideal_transformer` | `V_s/V_p=N_s/N_p`, `V_p I_p=V_s I_s` | timeseries | tested |
| `light_interference` | `I=I_0 cos^2(pi Delta/ lambda)` | timeseries | tested |
| `atomic_spectra` | `1/lambda=R_H(1/n_f^2-1/n_i^2)` | timeseries | tested |
| `radiation_safety` | `dot D(r)=dot D_0(r_0/r)^2` | timeseries | tested |
| `adiabatic_gas` | `PV^gamma=const`, `TV^(gamma-1)=const` | timeseries | tested |
| `water_surface_interference` | `u=A[cos(kr_1-omega t)+cos(kr_2-omega t)]` on a square grid | scalar field | tested |
| `work_energy_power` | `W=F s cos(theta)`, `Delta K=1/2 m(v_1^2-v_0^2)`, `P_bar=W/t` | timeseries | tested |
| `circular_motion` | `omega=v/r`, `a_c=v^2/r`, `F_c=mv^2/r`, `T=2 pi r/v` | timeseries | tested |
| `hooke_law` | `F=-kx`, `U_e=1/2 kx^2` | timeseries | tested |
| `gravity_orbit` | `F=G M m/r^2`, `g=GM/r^2`, `v=sqrt(GM/r)`, `T=2 pi sqrt(r^3/GM)` | timeseries | tested |
| `hydrostatics` | `p_g=rho gh`, `p_abs=p_atm+rho gh`, `F_b=rho g V` | timeseries | tested |
| `radio_communication` | `lambda=c/f_c`, `f_U=f_c+f_m`, `f_L=f_c-f_m` | timeseries | tested |
| `diode_characteristic` | `I=I_s[exp(qV/(n k_B T))-1]` | timeseries | tested |
| `ultrasound_imaging` | `d=v t_echo/2`, `lambda=v/f` | timeseries | tested |
| `ideal_gas_isobaric` | `P=const`, `V_2/V_1=T_2/T_1`, `W=P Delta V` | timeseries | tested |
| `ideal_gas_isochoric` | `V=const`, `P_2/P_1=T_2/T_1`, `W=0` | timeseries | tested |

Still requiring models/contracts: full electric and magnetic vector fields,
multi-sample statistics and practical activity rubrics. The water-surface
field now has an explicit versioned contract and benchmark; source closure and
teacher review remain separate release gates.
