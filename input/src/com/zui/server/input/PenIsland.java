/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.zui.server.input;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.android.internal.graphics.drawable.BackgroundBlurDrawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Stand-in for the ZUI "island": ZUI shows the one-shot notifications of
 * PenService (pen connected, battery level, open Bluetooth ...) as a pill at
 * the top of the screen instead of a notification. This mirrors the stock
 * com.zui.server.notification.NotificationManagerService: records are keyed by
 * (package, id), a repeated id updates the visible pill in place, permanent
 * records stay until cancelled, the others hide after 3 s, and tapping a pill
 * sends the notification's contentIntent.
 *
 * <p>Window position and size are the stock values (framework-res
 * zui_notification_window_*): a fixed 288x60dp pill at TOP|CENTER_HORIZONTAL,
 * y = 60dp portrait / 51dp landscape at sw600dp (44dp / 36dp below), no
 * layout-in-screen so it stays below the status bar. The look (frosted blur,
 * pen name and pen tip close-up for PenService's custom views) is our own.
 *
 * <p>All state is touched on the handler thread only.
 */
final class PenIsland {
    private static final String TAG = "LenovoPenIsland";
    /** Stock NotificationManagerService.SHOW_TIME. */
    private static final long SHOW_MS = 3000;
    private static final long FADE_MS = 250;
    private static final int PILL_COLOR_LIGHT = 0xD9FFFFFF;
    private static final int PILL_COLOR_DARK = 0xCC1E1E1E;

    // Stock framework-res zui_notification_window_* values.
    private static final int WIDTH_DP = 288;
    private static final int HEIGHT_DP = 60;
    private static final int MARGIN_TOP_DP = 44;
    private static final int MARGIN_TOP_DP_SW600 = 60;
    private static final int MARGIN_TOP_LANDSCAPE_DP = 36;
    private static final int MARGIN_TOP_LANDSCAPE_DP_SW600 = 51;
    private static final int START_PADDING_DP = 35;
    private static final int START_PADDING_DP_SW600 = 42;
    private static final int TITLE_WIDTH_DP = 142;
    private static final int TITLE_WIDTH_DP_SW600 = 156;
    private static final int ICON_WIDTH_DP = 80;
    private static final int TITLE_TEXT_DP = 14;
    private static final int SUB_TITLE_TEXT_DP = 14;
    private static final int SUB_TITLE_TOP_PADDING_DP = 2;
    private static final int DESCRIPTION_TEXT_SP = 10;
    private static final int SW_TABLET_DP = 600;
    private static final int TITLE_COLOR_LIGHT = 0xFF000000;
    private static final int TITLE_COLOR_DARK = 0xFFE0E0F0;
    private static final int PROMPT_COLOR_LIGHT = 0xFF333333;
    private static final int PROMPT_COLOR_DARK = 0xFFE0E0F0;
    private static final int WARNING_COLOR = 0xFFFE1818;

    static final String EXTRA_DESCRIPTION = "DescriptionText";
    static final String EXTRA_WARNING = "WarningDescription";

    /**
     * Stock ZUI island size factor for PenService's custom battery view: the
     * constants below were taken from larger sizes. Applied to every size of
     * that view, so the layout stays proportional.
     */
    private static final float SCALE = 0.85f;
    private static final int PEN_HEIGHT_DP = 34;
    private static final int TIP_WIDTH_DP = 120;
    private static final int BLUR_DP = 24;
    private static final String PEN_PACKAGE = "com.lenovo.penservice";

    private static final class Record {
        final String key;
        final String pkg;
        Notification notification;
        boolean permanent;
        int displayId;
        Context ui;
        WindowManager wm;
        LinearLayout pill;
        boolean night;
        float dp;
        boolean added;
        Runnable expire;

        Record(String key, String pkg) {
            this.key = key;
            this.pkg = pkg;
        }
    }

    private final Context mContext;
    private final Handler mHandler;
    private final Map<String, Record> mRecords = new HashMap<>();

    PenIsland(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    /** Shows or updates the pill of (pkg, id); permanent ones stay until cancelled. */
    void post(String pkg, int id, Notification n, boolean permanent, int displayId) {
        if (n == null) return;
        Safe.post(mHandler, "pen island show", () -> showNow(pkg, id, n, permanent, displayId));
    }

    void cancel(String pkg, int id) {
        Safe.post(mHandler, "pen island cancel", () -> {
            Record r = mRecords.get(key(pkg, id));
            if (r != null) remove(r);
        });
    }

    void cancelAll(String pkg) {
        Safe.post(mHandler, "pen island cancel all", () -> {
            for (Record r : new ArrayList<>(mRecords.values())) {
                if (r.pkg.equals(pkg)) remove(r);
            }
        });
    }

    private static String key(String pkg, int id) {
        return pkg + "/" + id;
    }

    private void showNow(String pkg, int id, Notification n, boolean permanent,
            int requestedDisplay) {
        DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        Display display = dm.getDisplay(requestedDisplay);
        if (display == null) display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        String key = key(pkg, id);
        Record r = mRecords.get(key);
        if (r != null && r.displayId != display.getDisplayId()) {
            remove(r);
            r = null;
        }
        boolean created = r == null;
        if (created) r = open(key, pkg, display);
        r.notification = n;
        r.permanent = permanent;
        try {
            fill(r);
            if (created) add(r);
        } catch (RuntimeException e) {
            Log.w(TAG, "show " + key, e);
            remove(r);
            return;
        }
        mHandler.removeCallbacks(r.expire);
        if (!permanent) mHandler.postDelayed(r.expire, SHOW_MS);
        Log.i(TAG, (created ? "shown " : "updated ") + key);
    }

    /** Creates the window context and the empty pill of a record. */
    private Record open(String key, String pkg, Display display) {
        Record r = new Record(key, pkg);
        r.displayId = display.getDisplayId();
        r.expire = Safe.run("pen island expire", () -> remove(r));
        int type = WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
        Context window = mContext.createDisplayContext(display).createWindowContext(type, null);
        r.night = (window.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        r.ui = new ContextThemeWrapper(window, android.R.style.Theme_DeviceDefault_DayNight);
        r.dp = r.ui.getResources().getDisplayMetrics().density;
        r.wm = window.getSystemService(WindowManager.class);

        LinearLayout pill = new LinearLayout(r.ui);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(r.night ? PILL_COLOR_DARK : PILL_COLOR_LIGHT);
        bg.setCornerRadius(HEIGHT_DP * r.dp / 2f);
        pill.setBackground(bg);
        pill.setClipToOutline(true);
        r.pill = pill;
        return r;
    }

    private void add(Record r) {
        Configuration config = r.ui.getResources().getConfiguration();
        boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean tablet = config.smallestScreenWidthDp >= SW_TABLET_DP;
        int marginTop = landscape
                ? (tablet ? MARGIN_TOP_LANDSCAPE_DP_SW600 : MARGIN_TOP_LANDSCAPE_DP)
                : (tablet ? MARGIN_TOP_DP_SW600 : MARGIN_TOP_DP);
        // Stock: NOT_FOCUSABLE | NOT_TOUCH_MODAL, no LAYOUT_IN_SCREEN, no insets override.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (int) (WIDTH_DP * r.dp), (int) (HEIGHT_DP * r.dp),
                WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = (int) (marginTop * r.dp);
        lp.setTitle("PenIsland");

        r.pill.setAlpha(0f);
        r.pill.setScaleX(0.8f);
        r.pill.setScaleY(0.8f);
        r.wm.addView(r.pill, lp);
        r.added = true;
        mRecords.put(r.key, r);
        blurBackground(r.wm, r.pill, r.night ? PILL_COLOR_DARK : PILL_COLOR_LIGHT,
                HEIGHT_DP * r.dp / 2f, r.dp);
        r.pill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(FADE_MS).start();
    }

    /** Removes the pill with a fade; safe to call twice. */
    private void remove(Record r) {
        mHandler.removeCallbacks(r.expire);
        if (mRecords.get(r.key) == r) mRecords.remove(r.key);
        if (!r.added) return;
        r.added = false;
        r.pill.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            try {
                r.wm.removeView(r.pill);
            } catch (RuntimeException e) {
                Log.w(TAG, "remove", e);
            }
        }).start();
    }

    private void onClick(Record r) {
        PendingIntent intent = r.notification != null ? r.notification.contentIntent : null;
        if (intent != null) {
            try {
                intent.send();
            } catch (PendingIntent.CanceledException e) {
                Log.w(TAG, "pending intent of " + r.pkg + " cancelled", e);
            }
        }
        remove(r);
    }

    // ---- content ----

    /** contentView, else bigContentView, else the stock-like built layout. */
    private void fill(Record r) {
        r.pill.removeAllViews();
        Notification n = r.notification;
        RemoteViews custom = n.contentView != null ? n.contentView : n.bigContentView;
        if (custom != null) {
            fillCustom(r, custom);
        } else {
            fillBuilt(r);
        }
        r.pill.setOnClickListener(n.contentIntent != null
                ? v -> Safe.run("pen island click", () -> onClick(r)).run() : null);
        r.pill.setClickable(n.contentIntent != null);
    }

    /**
     * PenService's own view: the pen name above its battery view on the left,
     * a close-up of the pen tip on the right, cut off by the rounded edge.
     */
    private void fillCustom(Record r, RemoteViews content) {
        Context ui = r.ui;
        float dp = r.dp;
        int height = (int) (HEIGHT_DP * dp);
        LinearLayout pill = r.pill;
        pill.setPaddingRelative((int) (26 * SCALE * dp), 0, 0, 0);

        LinearLayout text = new LinearLayout(ui);
        text.setOrientation(LinearLayout.VERTICAL);
        String name = BluetoothPenConnectPolicy.sLastPenName;
        if (!TextUtils.isEmpty(name)) {
            TextView label = new TextView(ui);
            label.setText(name);
            label.setTextColor(r.night ? 0xFFF2F2F2 : 0xFF1F1F1F);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16 * SCALE);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        FrameLayout battery = new FrameLayout(ui);
        View view = content.apply(ui, battery);
        scaleSizes(view, SCALE);
        battery.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.START));
        LinearLayout.LayoutParams lpBattery = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpBattery.topMargin = text.getChildCount() > 0 ? (int) (4 * SCALE * dp) : 0;
        text.addView(battery, lpBattery);
        pill.addView(text, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Drawable picture = penPicture(ui, BluetoothPenConnectPolicy.sLastPenType);
        if (picture != null && picture.getIntrinsicHeight() > 0) {
            ImageView image = new ImageView(ui);
            image.setImageDrawable(picture);
            image.setScaleType(ImageView.ScaleType.MATRIX);
            int penHeight = (int) (PEN_HEIGHT_DP * SCALE * dp);
            float penScale = (float) penHeight / picture.getIntrinsicHeight();
            Matrix m = new Matrix();
            m.setScale(penScale, penScale);
            m.postTranslate(0, (height - penHeight) / 2f);
            image.setImageMatrix(m);
            LinearLayout.LayoutParams lpImage =
                    new LinearLayout.LayoutParams((int) (TIP_WIDTH_DP * SCALE * dp), height);
            lpImage.setMarginStart((int) (28 * SCALE * dp));
            pill.addView(image, lpImage);
        } else {
            pill.setPaddingRelative((int) (26 * SCALE * dp), 0, (int) (26 * SCALE * dp), 0);
        }
    }

    /**
     * Stock-like layout for notifications without a custom view: title
     * (android.title), sub title (android.text) and the description line
     * ("DescriptionText", red when "WarningDescription") on the left, the large
     * icon on the right. As in stock, a description replaces the title.
     */
    private void fillBuilt(Record r) {
        Notification n = r.notification;
        Context ui = r.ui;
        float dp = r.dp;
        boolean tablet = ui.getResources().getConfiguration().smallestScreenWidthDp >= SW_TABLET_DP;
        CharSequence title = extra(n, Notification.EXTRA_TITLE);
        CharSequence subTitle = extra(n, Notification.EXTRA_TEXT);
        CharSequence description = extra(n, EXTRA_DESCRIPTION);
        boolean warning = n.extras != null && n.extras.getBoolean(EXTRA_WARNING);
        int titleColor = r.night ? TITLE_COLOR_DARK : TITLE_COLOR_LIGHT;
        int promptColor = r.night ? PROMPT_COLOR_DARK : PROMPT_COLOR_LIGHT;

        LinearLayout pill = r.pill;
        pill.setPaddingRelative((int) ((tablet ? START_PADDING_DP_SW600 : START_PADDING_DP) * dp),
                0, 0, 0);
        LinearLayout text = new LinearLayout(ui);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        int textWidth = (int) ((tablet ? TITLE_WIDTH_DP_SW600 : TITLE_WIDTH_DP) * dp);

        if (TextUtils.isEmpty(description) && !TextUtils.isEmpty(title)) {
            text.addView(line(ui, title, TypedValue.COMPLEX_UNIT_DIP, TITLE_TEXT_DP, titleColor,
                    textWidth, true), wrap());
        }
        if (!TextUtils.isEmpty(subTitle)) {
            int accent = ui.getColor(r.night ? android.R.color.system_accent1_500
                    : android.R.color.system_accent1_600);
            LinearLayout.LayoutParams lp = wrap();
            lp.topMargin = (int) (SUB_TITLE_TOP_PADDING_DP * dp);
            text.addView(line(ui, subTitle, TypedValue.COMPLEX_UNIT_DIP, SUB_TITLE_TEXT_DP, accent,
                    textWidth, false), lp);
        }
        if (!TextUtils.isEmpty(description)) {
            text.addView(line(ui, description, TypedValue.COMPLEX_UNIT_SP, DESCRIPTION_TEXT_SP,
                    warning ? WARNING_COLOR : promptColor, textWidth, false), wrap());
        }
        pill.addView(text, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Icon large = n.getLargeIcon();
        if (large != null) {
            ImageView image = new ImageView(ui);
            image.setImageIcon(large);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            pill.addView(image, new LinearLayout.LayoutParams((int) (ICON_WIDTH_DP * dp),
                    (int) (HEIGHT_DP * dp)));
        } else {
            pill.setPaddingRelative(pill.getPaddingStart(), 0, pill.getPaddingStart(), 0);
        }
    }

    private static CharSequence extra(Notification n, String key) {
        return n.extras != null ? n.extras.getCharSequence(key) : null;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static TextView line(Context ui, CharSequence s, int unit, float size, int color,
            int maxWidth, boolean bold) {
        TextView tv = new TextView(ui);
        tv.setText(s);
        tv.setTextSize(unit, size);
        tv.setTextColor(color);
        tv.setSingleLine();
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setMaxWidth(maxWidth);
        if (bold) tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return tv;
    }

    /** Frosted background like the ZUI island when cross-window blur is available. */
    private static void blurBackground(WindowManager wm, View pill, int color, float radius,
            float dp) {
        try {
            ViewRootImpl root = pill.getViewRootImpl();
            if (root == null || !wm.isCrossWindowBlurEnabled()) return;
            BackgroundBlurDrawable blur = root.createBackgroundBlurDrawable();
            blur.setBlurRadius((int) (BLUR_DP * SCALE * dp));
            blur.setCornerRadius(radius);
            blur.setColor(color);
            pill.setBackground(blur);
        } catch (RuntimeException e) {
            Log.w(TAG, "blur", e);
        }
    }

    /**
     * Shrinks the inflated PenService battery view to match the pill. The layout
     * of PenService (pen_pair_layout_for_notification_new) uses real dp and sp
     * sizes, so they are rewritten instead of scaling the rendered view: that
     * keeps the battery percentage text crisp.
     */
    private static void scaleSizes(View view, float scale) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            if (lp.width > 0) lp.width = Math.round(lp.width * scale);
            if (lp.height > 0) lp.height = Math.round(lp.height * scale);
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                int top = Math.round(mlp.topMargin * scale);
                int bottom = Math.round(mlp.bottomMargin * scale);
                if (mlp.isMarginRelative()) {
                    // layout_marginStart/End (what the PenService layout uses,
                    // resolved by the inflater): keep them relative.
                    mlp.setMarginsRelative(Math.round(mlp.getMarginStart() * scale), top,
                            Math.round(mlp.getMarginEnd() * scale), bottom);
                } else {
                    mlp.setMargins(Math.round(mlp.leftMargin * scale), top,
                            Math.round(mlp.rightMargin * scale), bottom);
                }
            }
        }
        view.setPadding(Math.round(view.getPaddingLeft() * scale),
                Math.round(view.getPaddingTop() * scale),
                Math.round(view.getPaddingRight() * scale),
                Math.round(view.getPaddingBottom() * scale));
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.getTextSize() * scale);
            tv.setMinWidth(Math.round(tv.getMinimumWidth() * scale));
            tv.setMinHeight(Math.round(tv.getMinimumHeight() * scale));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scaleSizes(group.getChildAt(i), scale);
            }
        }
    }

    /** Pen picture from PenService by pen type (BluetoothPenConnectPolicy.TYPE_*), or null. */
    private static Drawable penPicture(Context ui, int type) {
        String name;
        switch (type) {
            case BluetoothPenConnectPolicy.TYPE_PICASSO: name = "ic_bt_pen_picasso"; break;
            case BluetoothPenConnectPolicy.TYPE_PARKER: name = "ic_bt_pen_parker_v1"; break;
            case BluetoothPenConnectPolicy.TYPE_PARKER_2: name = "ic_bt_pen_omas"; break;
            case BluetoothPenConnectPolicy.TYPE_SHEAFFER: name = "ic_bt_pen_sheaffer"; break;
            default: name = "ic_pen_stylus"; break;
        }
        try {
            Context pen = ui.createPackageContext(PEN_PACKAGE, 0);
            int id = pen.getResources().getIdentifier(name, "drawable", PEN_PACKAGE);
            return id != 0 ? pen.getDrawable(id) : null;
        } catch (Exception e) {
            Log.w(TAG, "pen picture", e);
            return null;
        }
    }
}
