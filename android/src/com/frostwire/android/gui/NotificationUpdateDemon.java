/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.services.EngineService;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.android.gui.views.TimerService;
import com.frostwire.android.gui.views.TimerSubscription;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 */
public final class NotificationUpdateDemon implements TimerObserver {

    private static final Logger LOG = Logger.getLogger(NotificationUpdateDemon.class);

    private static final int FROSTWIRE_STATUS_NOTIFICATION_UPDATE_INTERVAL_IN_SECS = 5;

    private Context mParentContext;
    private OnTimeRunnable mOnTimeRunnable;
    private TimerSubscription mTimerSubscription;

    private RemoteViews notificationViews;
    private Notification notificationObject;

    public NotificationUpdateDemon(Context parentContext) {
        mParentContext = parentContext;
        mOnTimeRunnable = new OnTimeRunnable(this);
        setupNotification();
    }

    public void start() {
        if (mTimerSubscription != null) {
            LOG.debug("Stopping before (re)starting permanent notification demon");
            mTimerSubscription.unsubscribe();
        }
        mTimerSubscription = TimerService.subscribe(this, FROSTWIRE_STATUS_NOTIFICATION_UPDATE_INTERVAL_IN_SECS);
    }

    public void stop() {
        LOG.debug("Stopping permanent notification demon");
        mTimerSubscription.unsubscribe();

        NotificationManager manager = (NotificationManager) mParentContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            try {
                manager.cancel(EngineService.FROSTWIRE_STATUS_NOTIFICATION);
            } catch (SecurityException t) {
                // possible java.lang.SecurityException
            }
        }
    }

    private void updatePermanentStatusNotification() {
        if (!ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION)) {
            return;
        }

        if (notificationViews == null || notificationObject == null) {
            LOG.warn("Notification views or object are null, review your logic");
            return;
        }

        // number of uploads (seeding) and downloads
        TransferManager transferManager;

        try {
            transferManager = TransferManager.instance();
        } catch (IllegalStateException btEngineNotReadyException) {
            return;
        }

        if (transferManager != null) {
            int downloads = transferManager.getActiveDownloads();
            int uploads = transferManager.getActiveUploads();
            if (downloads == 0 && uploads == 0) {
                NotificationManager manager = (NotificationManager) mParentContext.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    try {
                        manager.cancel(EngineService.FROSTWIRE_STATUS_NOTIFICATION);
                    } catch (SecurityException ignored) {
                        // possible java.lang.SecurityException
                    }
                }
                return; // quick return
            }
            //  format strings
            String sDown = UIUtils.rate2speed(transferManager.getDownloadsBandwidth() / 1024);
            String sUp = UIUtils.rate2speed(transferManager.getUploadsBandwidth() / 1024);
            // Transfers status.
            notificationViews.setTextViewText(R.id.view_permanent_status_text_downloads, downloads + " @ " + sDown);
            notificationViews.setTextViewText(R.id.view_permanent_status_text_uploads, uploads + " @ " + sUp);
            final NotificationManager notificationManager = (NotificationManager) mParentContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                try {
                    notificationManager.notify(EngineService.FROSTWIRE_STATUS_NOTIFICATION, notificationObject);
                } catch (SecurityException ignored) {
                    // possible java.lang.SecurityException
                }
            }
        }
    }

    private void setupNotification() {
        if (!ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION)) {
            return;
        }

        RemoteViews remoteViews = new RemoteViews(mParentContext.getPackageName(),
                R.layout.view_permanent_status_notification);

        PendingIntent showFrostWireIntent = createShowFrostwireIntent();
        PendingIntent shutdownIntent = createShutdownIntent();

        remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_shutdown, shutdownIntent);
        remoteViews.setOnClickPendingIntent(R.id.view_permanent_status_text_title, showFrostWireIntent);

        Notification notification = new NotificationCompat.Builder(mParentContext, "").
                setSmallIcon(R.drawable.frostwire_notification_flat).
                setContentIntent(showFrostWireIntent).
                setContent(remoteViews).
                build();
        notification.flags |= Notification.FLAG_NO_CLEAR;

        notificationViews = remoteViews;
        notificationObject = notification;
    }

    private PendingIntent createShowFrostwireIntent() {
        return PendingIntent.getActivity(mParentContext,
                0,
                new Intent(mParentContext,
                        MainActivity.class).
                        setAction(Constants.ACTION_SHOW_TRANSFERS).
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                0);
    }

    private PendingIntent createShutdownIntent() {
        return PendingIntent.getActivity(mParentContext,
                1,
                new Intent(mParentContext,
                        MainActivity.class).
                        setAction(Constants.ACTION_REQUEST_SHUTDOWN).
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                0);
    }

    @Override
    public void onTime() {
        Engine.instance().getThreadPool().execute(mOnTimeRunnable);
    }

    @SuppressWarnings("deprecation")
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) mParentContext.getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    private static class OnTimeRunnable implements Runnable {

        private WeakReference<NotificationUpdateDemon> updateDemonRef;

        OnTimeRunnable(NotificationUpdateDemon updateDemon) {
            updateDemonRef = Ref.weak(updateDemon);
        }

        @Override
        public void run() {
            if (!Ref.alive(updateDemonRef)) {
                return;
            }
            NotificationUpdateDemon updateDemon = updateDemonRef.get();
            if (updateDemon.isScreenOn()) {
                // the context we have is the main app context, not an activity
                Handler mainHandler = new Handler(updateDemon.mParentContext.getMainLooper());
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (Ref.alive(updateDemonRef)) {
                            updateDemonRef.get().updatePermanentStatusNotification();
                        }
                    }
                });
            }
        }
    }
}
