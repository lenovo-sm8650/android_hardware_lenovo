# hardware/lenovo

Lenovo device support shared by the trees in this account, starting with the
Lenovo Yoga Tab Plus (`device/lenovo/lapis`). One Soong namespace
(`hardware/lenovo`); the device tree adds it to `PRODUCT_SOONG_NAMESPACES`.

| Path | Contents |
|---|---|
| `input/` | `tb520fu-input`, the Lenovo pen / keyboard / battery bridge in system_server |
| `packages/TB520FUParts/` | "Lenovo features" in Settings > System |
| `tools/` | string generators for TB520FUParts |
| `packages/LunarisDolby/` | Dolby Atmos settings (`seventeen`) |
| `packages/VideoMotion/` | video motion smoothing settings (`seventeen`) |
| `custom/` | the maintainer customizations (`seventeen`), see its README |

| Branch | |
|---|---|
| `lineage-24.0` | LineageOS |
| `seventeen` | PixelOS: `lineage-24.0` plus the PixelOS-only packages |

Git LFS is needed for the APKs in `custom/` (`seventeen`).
