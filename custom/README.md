# Maintainer customizations

Maintainer additions on top of PixelOS for the Lenovo Yoga Tab Plus
(lapis, TB520FU), kept apart from the device support so a plain build stays
clean. `device/lenovo/lapis` (`seventeen`) uses them through one hook; without
them it builds a plain PixelOS.

Contents:

| Path | What |
|---|---|
| `ExperimentalFeatures/` | "Experimental features" app (`org.lineageos.settings.experimental`), opened from the Advanced features page of LenovoParts (no Settings tile of its own): app compatibility (the device identity selected apps see, default: Netflix sees the OnePlus Pad Go 2, and the apps that see the Play Store as their installer, per app and only to the app itself) and a Play Integrity Fix row that opens the separate PIF app (`io.github.wnduddld0513.pif`) and is hidden while it is not installed |
| `ExperimentalFeatures/overlay/FrameworksResExperimental/` | the device identity defaults of the app |
| `custom.mk` | the packages above and the global Pixel build fingerprint (`TARGET_ENABLE_FP_OVERRIDE=false` keeps the stock device fingerprint) |
| `FeathersLiveWallpaper/` | Pixel "Feathers" Porcelain live wallpaper, the default wallpaper |
| `overlay/FrameworksResCustom/` | the default wallpaper |
| `overlay/UpdaterResTarget/` | the updater's SourceForge folder and hidden certified-props item |
| `tools/custom_strings.py` | generates the Experimental features app's `res/values*/strings.xml` |
| `tools/ota_json.py` | writes the updater description of a build |

## How it hooks into the device tree

- `device/lenovo/lapis/custom_lapis.mk` inherits `custom.mk` with
  `inherit-product-if-exists`.

The source changes they need (installer report for picked apps, blocking Play
Store installs/updates of `com.google.android.GoogleCamera`, the updater server)
are commits in the `frameworks/base` and `packages/apps/Updater` forks. Google
Camera can still be installed manually through adb or a file manager.

The Experimental features app is reached only from the "Experimental features"
row of LenovoParts (Advanced features), which starts
`org.lineageos.settings.experimental/.ExperimentalFeaturesActivity` and hides itself while that app is
not installed. Without this repository (for example a `lineage_lapis` build)
LenovoParts and `lenovo-input` build and work unchanged and nothing refers to
the app. The game performance page and `GamePerfController` are part of
LenovoParts and `lenovo-input`, not of the customizations.

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
