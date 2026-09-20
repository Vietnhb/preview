# `photoelectric_effect`

Einstein's equation is `K_max = max(0, hf − Φ)`, with stopping potential
`V₀=K_max/e` and wavelength `λ=c/f`. The model also emits an explicit
`emissionOccurs` flag (`1` iff `hf≥Φ`); below threshold, `K_max=0` is a
sentinel and no photoelectron is predicted.
