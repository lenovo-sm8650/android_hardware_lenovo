/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.zui.server.input;

import android.app.Notification;
import android.content.Context;
import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

/**
 * "zuinotification" service (zui.notification.INotificationManager). ZUI
 * shows these as its own island pill, never as a system notification;
 * PenService calls it for the pen connect / battery / open-Bluetooth popups
 * without a null check, so without the service every pen broadcast crashed the
 * persistent PenService process.
 *
 * <p>Like stock com.zui.server.notification.NotificationManagerService,
 * addNotification (1), addPermanentNotification (2) and
 * updatePermanentNotification (3) all show a pill ({@link PenIsland}) on the
 * given display; cancelNotification (4) and cancelAllNotifications (5) remove
 * pills by package.
 */
final class ZuiNotificationBinder extends Binder {
    private static final String TAG = "LenovoZuiNotif";
    static final String SERVICE = "zuinotification";
    static final String DESCRIPTOR = "zui.notification.INotificationManager";

    private static final int TRANSACTION_addNotification = 1;
    private static final int TRANSACTION_addPermanentNotification = 2;
    private static final int TRANSACTION_updatePermanentNotification = 3;
    private static final int TRANSACTION_cancelNotification = 4;
    private static final int TRANSACTION_cancelAllNotifications = 5;

    private final Context mContext;
    private final PenIsland mIsland;

    ZuiNotificationBinder(Context context, android.os.Handler handler) {
        mContext = context;
        mIsland = new PenIsland(context, handler);
        attachInterface(null, DESCRIPTOR);
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code < TRANSACTION_addNotification || code > TRANSACTION_cancelAllNotifications) {
            return super.onTransact(code, data, reply, flags);
        }
        data.enforceInterface(DESCRIPTOR);
        String pkg = callerPackage(Binder.getCallingUid());
        try {
            switch (code) {
                case TRANSACTION_addNotification:
                case TRANSACTION_addPermanentNotification:
                case TRANSACTION_updatePermanentNotification: {
                    int id = data.readInt();
                    Notification n = data.readInt() != 0
                            ? Notification.CREATOR.createFromParcel(data) : null;
                    int displayId = data.readInt();
                    mIsland.post(pkg, id, n, code != TRANSACTION_addNotification, displayId);
                    break;
                }
                case TRANSACTION_cancelNotification: {
                    String cancelPkg = data.readString();
                    mIsland.cancel(cancelPkg != null ? cancelPkg : pkg, data.readInt());
                    break;
                }
                case TRANSACTION_cancelAllNotifications: {
                    String cancelPkg = data.readString();
                    mIsland.cancelAll(cancelPkg != null ? cancelPkg : pkg);
                    break;
                }
            }
        } catch (Throwable t) {
            // Never fail the caller: stock PenService does not handle errors here.
            Log.w(TAG, "call " + code + " from " + pkg + " failed", t);
        }
        reply.writeNoException();
        return true;
    }

    /** The calling package; PenService (uid system) shares its uid with other packages. */
    private String callerPackage(int uid) {
        long token = Binder.clearCallingIdentity();
        try {
            String[] pkgs = mContext.getPackageManager().getPackagesForUid(uid);
            if (pkgs != null) {
                for (String p : pkgs) {
                    if (ZuiPenHapticPolicy.PKG_PENSERVICE.equals(p)) return p;
                }
                if (pkgs.length > 0) return pkgs[0];
            }
            return ZuiPenHapticPolicy.PKG_PENSERVICE;
        } catch (RuntimeException e) {
            return ZuiPenHapticPolicy.PKG_PENSERVICE;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }
}
