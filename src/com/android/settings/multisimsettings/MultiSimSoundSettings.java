/*
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.settings.multisimsettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.provider.MediaStore;
import android.provider.Settings.System;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.msim.SubscriptionManager;
import com.android.settings.DefaultRingtonePreference;
import com.android.settings.R;
import static com.android.internal.telephony.MSimConstants.SUBSCRIPTION_KEY;

public class MultiSimSoundSettings extends PreferenceActivity {
    private String LOG_TAG = "MultiSimSoundSettings";
    private static final String KEY_RINGSTONE = "ringtone";
    private int[] mRingtones = {
            RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_RINGTONE_2
    };

    private DefaultRingtonePreference mRingtonePref;
    private int mSubscription;
    private SubscriptionManager mSubscriptionManager;

    private Runnable mRingtoneLookupRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRingtonePref != null) {
                Context context = MultiSimSoundSettings.this;
                Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context,
                        mRingtones[mSubscription]);

                // Update ringtone Uri by getRingtone of RingtoneManager, and get the new ringtone Uri.
                RingtoneManager.getRingtone(context, ringtoneUri);
                ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context,
                        mRingtones[mSubscription]);
                CharSequence summary = context
                        .getString(com.android.internal.R.string.ringtone_unknown);
                 CharSequence ringtoneSummary;
                if (ringtoneUri == null) {
                    // silent ringtone
                    summary = context.getString(com.android.internal.R.string.ringtone_silent);
                } else {
                    // Fetch the ringtone title from the media provider
                    try {
                        Cursor cursor = context.getContentResolver().query(
                                ringtoneUri,
                                new String[] { MediaStore.Audio.Media.TITLE },
                                null, null, null);
                        if (cursor != null) {
                            if (cursor.moveToFirst()) {
                                summary = cursor.getString(0);
                            }
                            cursor.close();
                        }
                    } catch (SQLiteException sqle) {
                        // Unknown title for the ringtone
                    }
                }
                mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_SUMMARY, summary));
            }
        }
    };

    BroadcastReceiver mMediaScanDoneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
                new Thread(mRingtoneLookupRunnable).start();
            }
        }
    };

    private static final int MSG_UPDATE_SUMMARY = 0;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_UPDATE_SUMMARY) {
                String summary = (String) msg.obj;
                if (mRingtonePref != null) {
                    mRingtonePref.setSummary(summary);
                }
            }
        }
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSubscriptionManager = SubscriptionManager.getInstance();
        addPreferencesFromResource(R.xml.multi_sim_sound_settings);
        mRingtonePref = (DefaultRingtonePreference) findPreference(KEY_RINGSTONE);
        mSubscription = this.getIntent().getIntExtra(SUBSCRIPTION_KEY, 0);
        mRingtonePref.setRingtoneType(mRingtones[mSubscription]);
        // Register ACTION_MEDIA_SCANNER_FINISHED intent here, to refresh
        // the ringtone's summary.
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        registerReceiver(mMediaScanDoneReceiver, intentFilter);
    }

    protected void onResume() {
        super.onResume();
        mRingtonePref.setEnabled(isSubActivated());
        new Thread(mRingtoneLookupRunnable).start();
    }

    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mMediaScanDoneReceiver);
    }

    //Determine the current card slot is available.
    private boolean isSubActivated() {
        return mSubscriptionManager.isSubActive(mSubscription);
    }

}
