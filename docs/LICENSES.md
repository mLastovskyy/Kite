# Third-party assets and licenses

| Asset | License | Where | Notes |
|---|---|---|---|
| Inter (font) | SIL Open Font License 1.1 | `core/src/main/res/font/` | Closest free metric match to SF Pro (see `DESIGN_SYSTEM.md`) |
| Lucide icons | ISC | `core/src/main/res/drawable/ic_lucide_*.xml` | https://lucide.dev — SVGs converted 1:1 to VectorDrawables; stroke 2 on a 24 grid, tinted at use. Do not mix with another icon set |
| MapLibre Native Android | BSD-2-Clause | Gradle dependency | GMS-free map renderer |
| OpenFreeMap tiles | Free, commercial use allowed | runtime | https://openfreemap.org — no API key; attribution required on the map |
| OpenStreetMap data | ODbL | runtime (tiles, Nominatim) | «© OpenStreetMap contributors» attribution on the map and near addresses |

ISC (Lucide): Permission to use, copy, modify, and/or distribute this software for any purpose
with or without fee is hereby granted, provided that the above copyright notice and this
permission notice appear in all copies. Copyright (c) for portions of Lucide are held by Cole
Bemis 2013-2022 as part of Feather (MIT). All other copyright (c) for Lucide are held by Lucide
Contributors 2022.
