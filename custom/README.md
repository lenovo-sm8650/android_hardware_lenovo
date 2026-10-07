# Maintainer customizations

Maintainer additions on top of PixelOS for the Lenovo Yoga Tab Plus
(lapis, TB520FU), kept apart from the device support so a plain build stays
clean. `device/lenovo/lapis` (`seventeen`) uses them through two hooks; without
them it builds a plain PixelOS.

Contents:

| Path | What |
|---|---|
| `TB520FUCustomFeatures/` | "Custom Tweaks" app (Settings > System): game performance and the apps that see the Play Store as their installer (per app, only to the app itself) |
| `custom.mk` | the packages above |
| `input/` | `tb520fu-input-custom.jar`, the game performance enforcement and the boot-time removal of what the dropped Play Integrity stack (keybox, TEE simulator, PIF) left on devices, loaded into system_server by `tb520fu-input` |
| `FeathersLiveWallpaper/` | Pixel "Feathers" Porcelain live wallpaper, the default wallpaper |
| `overlay/FrameworksResTB520FUCustom/` | the default wallpaper |
| `overlay/UpdaterResTB520FU/` | the updater's SourceForge folder and hidden certified-props item |
| `sepolicy/vendor/` | the cpufreq/kgsl rules of the game performance controller |
| `tools/custom_strings.py` | generates the app's `res/values*/strings.xml` |
| `tools/ota_json.py` | writes the updater description of a build |

## How it hooks into the device tree

- `device/lenovo/lapis/custom_lapis.mk` inherits `custom.mk` with
  `inherit-product-if-exists`.
- `device/lenovo/lapis/BoardConfig.mk` includes `BoardConfigCustom.mk` with
  `-include` (the vendor sepolicy directory).

The source changes they need (installer report for picked apps, the updater
server) are commits in the `frameworks/base` and `packages/apps/Updater` forks.

`tb520fu-input` (`hardware/lenovo/input`) calls into this code only through the
`com.tb520fu.input.InputExtension` interface: `InputCore` loads
`/system_ext/framework/tb520fu-input-custom.jar` with a `PathClassLoader` when
the file exists, so it keeps working on a build without it.

## OTA publishing

Each build is published in two SourceForge folders:

| Folder | Files |
|---|---|
| `seventeen/<date>/` | full packages `PixelOS_lapis-<version>-<date>[-ROW].zip` (recovery / TWRP / full OTA) and the LTBox archives |
| `seventeen/OTA/<date>/` | updater descriptions `PixelOS_lapis-<version>-<date>[-ROW].json` and the incremental packages |

The updater reads the RSS feed of `seventeen/OTA`, takes the newest build
folder with a description of the running variant (PRC / ROW, from the running
dtb) and offers it when it is newer than the running build. It downloads the
incremental package from `seventeen/OTA/<date>/` when update_engine accepts its
payload metadata for the running build, otherwise the full package from
`seventeen/<date>/`; packages from anywhere else are refused. The package
SHA-256 is checked, update_engine checks the AOSP OTA signature.

An incremental OTA is built from the **target files of both builds**, so keep
the target files of every published build (the full OTA ZIP cannot replace
them). For each region separately:

```bash
ota_from_target_files -k build/make/target/product/security/testkey \
    -i BASE_TARGET_FILES TARGET_TARGET_FILES \
    PixelOS_lapis-17.0-TARGET_DATE-incremental-BASE_DATE.zip
python3 hardware/lenovo/custom/tools/ota_json.py \
    --base-full BASE_FULL.zip \
    --incremental PixelOS_lapis-17.0-TARGET_DATE-incremental-BASE_DATE.zip \
    --out-dir OTA_FOLDER TARGET_FULL.zip
```

`--base-build DIR` (`DIR/target_files`) can replace `--base-full`. ota_json.py
rejects a delta whose source/target metadata or streaming ranges do not match,
and writes the package URLs for the folder layout above.
