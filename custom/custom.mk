#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#
# Optional customizations for the Lenovo Yoga Tab Plus (TB520FU), pulled in by
# device/lenovo/lapis/custom_lapis.mk with inherit-product-if-exists. The
# device tree builds a plain PixelOS without this repository.
#

# Experimental features and game performance apps (opened from Advanced features
# in LenovoParts; no Settings tile of their own) and the framework defaults of the
# first. LenovoParts stays the device port and builds without them.
PRODUCT_PACKAGES += \
    ExperimentalFeatures \
    FrameworksResExperimental \
    LenovoGamePerformance

# Game performance enforcement inside system_server (input/ extension point).
# lenovo-input loads /system_ext/framework/lenovo-input-custom.jar when it is
# present and lets it register with InputExtension.
PRODUCT_PACKAGES += \
    lenovo-input-custom

# Default wallpaper (Pixel "Feathers" Porcelain live wallpaper)
PRODUCT_PACKAGES += \
    FeathersLiveWallpaper

# Overlays
PRODUCT_PACKAGES += \
    UpdaterResTarget \
    FrameworksResCustom

# Updater (PixelOS OTA app; the device tree does not add it)
PRODUCT_PACKAGES += \
    Updater

PRODUCT_COPY_FILES += \
    vendor/custom/config/permissions/privapp-permissions-custom.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-custom.xml

PRODUCT_PRODUCT_PROPERTIES += \
    net.pixelos.build_type=unofficial
