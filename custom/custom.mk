#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#
# Optional customizations for the Lenovo Yoga Tab Plus (TB520FU), pulled in by
# device/lenovo/lapis/custom_lapis.mk with inherit-product-if-exists. The
# device tree builds a plain PixelOS without this repository.
#

# Custom Tweaks app (Settings > System), LenovoParts stays the device port)
PRODUCT_PACKAGES += \
    LenovoCustomFeatures

# Report the Pixel fingerprint verified by the cold-boot module test, without
# changing the Lenovo hardware identity. Set TARGET_ENABLE_FP_OVERRIDE=false
# to keep the stock fingerprint from the device product.
TARGET_ENABLE_FP_OVERRIDE ?= true
ifeq ($(TARGET_ENABLE_FP_OVERRIDE),true)
PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildFingerprint=google/mustang/mustang:17/CP2A.260605.012/15430684:user/release-keys
endif

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
