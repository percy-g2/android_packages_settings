/*
 * Copyright (c) 2012-2013, The Linux Foundation. All Rights Reserved.
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.settings;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.MSimTelephonyManager;

import com.android.internal.telephony.PhoneStateIntentReceiver;
import com.android.internal.telephony.TelephonyProperties;

public class AirplaneModeEnabler implements Preference.OnPreferenceChangeListener {

    private final Context mContext;

    private final CheckBoxPreference mCheckBoxPref;

    private static final int EVENT_SERVICE_STATE_CHANGED = 3;

    private PhoneStateListener [] mPhoneStateListener = new PhoneStateListener[TelephonyManager.getDefault().getPhoneCount()];

    private boolean  [] mRadioOff = {false, false};

    private int [] mServiceState = {ServiceState.STATE_POWER_OFF, ServiceState.STATE_POWER_OFF};

    private MSimTelephonyManager mMSimTM = null;
    private TelephonyManager mTM = null;

    class RadioInfoPhoneStateListener extends PhoneStateListener {
        public RadioInfoPhoneStateListener(int subscription) {
            super(subscription);
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            mServiceState[mSubscription] = serviceState.getState();
            if(multiMode) {
                if (mServiceState[0] == ServiceState.STATE_POWER_OFF &&
                        mServiceState[1] == ServiceState.STATE_POWER_OFF) {
                    for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
                        mRadioOff[i] = true;
                    }
                }
            } else {
                if (serviceState.getState() == ServiceState.STATE_POWER_OFF) {
                    mRadioOff[mSubscription] = true;
                }
            }
            if (isAirplaneModeOn(mContext)) {
                if (isAllRadioOff()) {
                    mCheckBoxPref.setEnabled(true);
                    resetRadioOff();
                }
            } else {
                mCheckBoxPref.setEnabled(true);
            }
            onAirplaneModeChanged();
        }
    };

    private ContentObserver mAirplaneModeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            onAirplaneModeChanged();
        }
    };

    public AirplaneModeEnabler(Context context, CheckBoxPreference airplaneModeCheckBoxPreference) {
        mContext = context;
        mCheckBoxPref = airplaneModeCheckBoxPreference;
        airplaneModeCheckBoxPreference.setPersistent(false);
        if (TelephonyManager.isMultiSimEnabled()) {
            mMSimTM= (MSimTelephonyManager)context.getSystemService(Context.MSIM_TELEPHONY_SERVICE);
        } else {
            mTM = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        }
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            mRadioOff[i] = false;
            mPhoneStateListener[i] = new RadioInfoPhoneStateListener(i);
            if (TelephonyManager.isMultiSimEnabled()) {
                mMSimTM.listen(mPhoneStateListener[i],PhoneStateListener.LISTEN_SERVICE_STATE);
            } else {
                mTM.listen(mPhoneStateListener[i],PhoneStateListener.LISTEN_SERVICE_STATE);
            }
        }


    }

    public void resume() {
        mCheckBoxPref.setChecked(isAirplaneModeOn(mContext));
        if (isAirplaneModeOn(mContext)) {
            if (isAllRadioOff()) {
                mCheckBoxPref.setEnabled(true);
                resetRadioOff();
            }
        } else {
            mCheckBoxPref.setEnabled(true);
        }
        mCheckBoxPref.setOnPreferenceChangeListener(this);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.AIRPLANE_MODE_ON), true,
                mAirplaneModeObserver);
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            if (TelephonyManager.isMultiSimEnabled()) {
                mMSimTM.listen(mPhoneStateListener[i],PhoneStateListener.LISTEN_SERVICE_STATE);
            } else {
                mTM.listen(mPhoneStateListener[i],PhoneStateListener.LISTEN_SERVICE_STATE);
            }
        }
    }

    public void pause() {
        mCheckBoxPref.setOnPreferenceChangeListener(null);
        mContext.getContentResolver().unregisterContentObserver(mAirplaneModeObserver);
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            if (TelephonyManager.isMultiSimEnabled()) {
                mMSimTM.listen(mPhoneStateListener[i],PhoneStateListener.LISTEN_NONE);
            } else {
                mTM.listen(mPhoneStateListener[i],PhoneStateListener.LISTEN_NONE);
            }
        }
    }

    public static boolean isAirplaneModeOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void setAirplaneModeOn(boolean enabling) {
        // Change the system setting
        Settings.System.putInt(mContext.getContentResolver(), Settings.System.AIRPLANE_MODE_ON,
                                enabling ? 1 : 0);
        // Update the UI to reflect system setting
        mCheckBoxPref.setChecked(enabling);
        mCheckBoxPref.setEnabled(false);

        // Post the intent
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", enabling);
        mContext.sendBroadcast(intent);
    }

    /**
     * Called when we've received confirmation that the airplane mode was set.
     * TODO: We update the checkbox summary when we get notified
     * that mobile radio is powered up/down. We should not have dependency
     * on one radio alone. We need to do the following:
     * - handle the case of wifi/bluetooth failures
     * - mobile does not send failure notification, fail on timeout.
     */
    private void onAirplaneModeChanged() {
        mCheckBoxPref.setChecked(isAirplaneModeOn(mContext));
    }

    /**
     * Called when someone clicks on the checkbox preference.
     */
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (Boolean.parseBoolean(
                    SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
            // In ECM mode, do not update database at this point
        } else {
            setAirplaneModeOn((Boolean) newValue);
        }
        return true;
    }

    public void setAirplaneModeInECM(boolean isECMExit, boolean isAirplaneModeOn) {
        if (isECMExit) {
            // update database based on the current checkbox state
            setAirplaneModeOn(isAirplaneModeOn);
        } else {
            // update summary
            onAirplaneModeChanged();
        }
    }

    private boolean isAllRadioOff() {
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            if (mRadioOff[i] == false)
                return false;
        }
        return true;
    }

    private void resetRadioOff() {
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            mRadioOff[i] = false;
        }
    }

}
