# `measurement_uncertainty`

The lesson keeps a measured value `x` and an absolute uncertainty `Δx`, then
reports `[x−Δx, x+Δx]` and the relative uncertainty `|Δx/x|` when `x≠0`.
For `x=0`, the numeric field is `0` only as a safe sentinel and the separate
`relativeUncertaintyDefined=0` flag marks the ratio as mathematically undefined.
