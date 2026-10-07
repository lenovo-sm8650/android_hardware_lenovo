#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#
# Board configuration for the optional TB520FU customizations, included by
# device/lenovo/lapis/BoardConfig.mk with -include (skipped when this
# repository is not synced).
#

# SEPolicy
# tb520fu-input GamePerfController: per-app CPU caps/floors and GPU power levels.
BOARD_VENDOR_SEPOLICY_DIRS += hardware/lenovo/custom/sepolicy/vendor
