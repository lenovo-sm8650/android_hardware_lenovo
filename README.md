# hardware/lenovo

Lenovo device support shared by the trees in this account, starting with the
Lenovo Yoga Tab Plus (`device/lenovo/lapis`). One Soong namespace
(`hardware/lenovo`); the device tree adds it to `PRODUCT_SOONG_NAMESPACES`.

| Path | Contents |
|---|---|
| `input/` | `lenovo-input`, the Lenovo pen / keyboard / battery bridge in system_server |
| `packages/LenovoParts/` | "Advanced features" and "Pen & keyboard" on the Settings homepage |
| `tools/` | string generators (JSON data) for LenovoParts and the Lenovo strings of the Settings fork |
| `packages/LunarisDolby/` | Dolby Atmos settings (`seventeen`) |
| `packages/VideoMotion/` | video motion smoothing settings (`seventeen`) |
| `custom/` | the maintainer customizations (`seventeen`), see its README |

| Branch | |
|---|---|
| `lineage-24.0` | LineageOS |
| `seventeen` | PixelOS: `lineage-24.0` plus the PixelOS-only packages |

Git LFS is needed for the APKs in `custom/` (`seventeen`).
