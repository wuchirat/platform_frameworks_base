/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.dreams.dozetest;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.service.dreams.DozeHardware;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.TextView;

import java.util.Date;

/**
 * Simple test for doze mode.
 * <p>
 * adb shell setprop debug.doze.component com.android.dreams.dozetest/.DozeTestDream
 * </p>
 */
public class DozeTestDream extends DreamService {
    private static final String TAG = DozeTestDream.class.getSimpleName();
    private static final boolean DEBUG = false;

    // Amount of time to allow to update the time shown on the screen before releasing
    // the wakelock.  This timeout is design to compensate for the fact that we don't
    // currently have a way to know when time display contents have actually been
    // refreshed once the dream has finished rendering a new frame.
    private static final int UPDATE_TIME_TIMEOUT = 100;

    // A doze hardware message string we use for end-to-end testing.
    // Doesn't mean anything.  Real hardware won't handle it.
    private static final String TEST_PING_MESSAGE = "test.ping";

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private AlarmManager mAlarmManager;
    private PendingIntent mAlarmIntent;

    private TextView mAlarmClock;

    private final Date mTime = new Date();
    private java.text.DateFormat mTimeFormat;

    private boolean mDreaming;
    private DozeHardware mDozeHardware;

    @Override
    public void onCreate() {
        super.onCreate();

        mPowerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        mAlarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent("com.android.dreams.dozetest.ACTION_ALARM");
        intent.setPackage(getPackageName());
        IntentFilter filter = new IntentFilter();
        filter.addAction(intent.getAction());
        registerReceiver(mAlarmReceiver, filter);
        mAlarmIntent = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mAlarmReceiver);
        mAlarmIntent.cancel();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setLowProfile(true);
        setFullscreen(true);
        setContentView(R.layout.dream);

        mAlarmClock = (TextView)findViewById(R.id.alarm_clock);

        mTimeFormat = DateFormat.getTimeFormat(this);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();

        mDreaming = true;
        mDozeHardware = getDozeHardware();

        Log.d(TAG, "Dream started: canDoze=" + canDoze()
                + ", dozeHardware=" + mDozeHardware);

        performTimeUpdate();

        if (mDozeHardware != null) {
            mDozeHardware.sendMessage(TEST_PING_MESSAGE, null);
            mDozeHardware.setEnableMcu(true);
        }
        startDozing();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();

        mDreaming = false;
        if (mDozeHardware != null) {
            mDozeHardware.setEnableMcu(false);
            mDozeHardware = null;
        }

        Log.d(TAG, "Dream ended: isDozing=" + isDozing());

        stopDozing();
        cancelTimeUpdate();
    }

    private void performTimeUpdate() {
        if (mDreaming) {
            long now = System.currentTimeMillis();
            now -= now % 60000; // back up to last minute boundary

            mTime.setTime(now);
            mAlarmClock.setText(mTimeFormat.format(mTime));

            mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, now + 60000, mAlarmIntent);

            mWakeLock.acquire(UPDATE_TIME_TIMEOUT);
        }
    }

    private void cancelTimeUpdate() {
        mAlarmManager.cancel(mAlarmIntent);
    }

    private final BroadcastReceiver mAlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            performTimeUpdate();
        }
    };
}
