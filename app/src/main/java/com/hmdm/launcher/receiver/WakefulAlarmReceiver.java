/*
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
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

package com.hmdm.launcher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// A BroadcastReceiver fired by AlarmManager which needs to do blocking work (network I/O,
// database access) instead of returning immediately. onReceive() runs on the main thread and
// is only guaranteed a few seconds before the OS may consider it unresponsive, and any CPU
// wake lock the system grants for the broadcast is released as soon as onReceive() returns.
// So we take our own partial wake lock, hand off the actual work to a background thread via
// goAsync(), and release everything once it's done - this is the traditional (pre-JobScheduler)
// analogue of a WorkManager Worker, and unlike WorkManager it also works before the first
// unlock, provided the concrete receiver is declared with android:directBootAware="true".
public abstract class WakefulAlarmReceiver extends BroadcastReceiver {

    // Give the background task enough time to complete a network round trip, but do not hold
    // the wake lock forever if something goes wrong.
    private static final long WAKE_LOCK_TIMEOUT_MS = 60000;

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final Context appContext = context.getApplicationContext();
        final PowerManager powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = powerManager != null ?
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getWakeLockTag()) : null;
        if (wakeLock != null) {
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        }

        final PendingResult pendingResult = goAsync();
        executor.execute(() -> {
            try {
                doWork(appContext, intent);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                pendingResult.finish();
            }
        });
    }

    protected abstract String getWakeLockTag();

    // Runs on a background thread; safe to block on network/database calls here.
    protected abstract void doWork(Context context, Intent intent);
}
