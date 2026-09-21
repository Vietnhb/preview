# Local SVG asset libraries

This directory contains downloaded SVG libraries for optional scene composition. The active PhysLive renderer does not import these directories wholesale; `SvgAssetManifest.ts` continues to select only the small, reviewed assets used by a scene.

| Library | Version | SVG files | License | Source |
| --- | --- | ---: | --- | --- |
| Tabler Icons | 3.47.0 | 6,202 | MIT | https://github.com/tabler/tabler-icons |
| Phosphor Icons | 2.1.1 | 9,072 | MIT | https://github.com/phosphor-icons/core |
| Material Design Icons SVG | 0.14.15 | 10,610 | Apache-2.0 | https://github.com/google/material-design-icons |

The corresponding upstream `LICENSE` and `README.md` files are preserved inside each library folder.

## Usage rule

Treat these libraries as an offline catalog. Before an icon becomes a PhysLive simulation asset, copy or adapt it into the reviewed `svg/` set, add semantic tags and an entry to `SvgAssetManifest.ts`, and verify its visual meaning. Do not expose arbitrary catalog filenames directly to the LLM or let an arbitrary SVG override a physics primitive.

The current downloaded catalog is intentionally broad. The physics renderer remains capability-driven: a schema chooses a scene capability, the manifest chooses a reviewed asset, and numerical parameters still come from the backend schema.
