/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2012-2013 The Linux Foundation. All Rights Reserved.
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;

public class TestingSettings extends PreferenceActivity {
    private final int PHONE_INFO = 1;

    private final String SUBSCRIPTION = "SUBSCRIPTION";
    private PreferenceScreen mRadioInfo = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.testing_settings);

        mRadioInfo = (PreferenceScreen) findPreference("testing_phone_info");
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if ((preference == mRadioInfo) &&
                (TelephonyManager.getDefault().isMultiSimEnabled())) {
            int slotSimState = MSimTelephonyManager.getDefault().getSimState(0);
            int slotSimState2 = MSimTelephonyManager.getDefault().getSimState(1);

            // If SIM card's state of two slot all is SIM_STATE_READY, show the select
            // dialog, else show the information of which states is SIM_STATE_READY.
            if (slotSimState == TelephonyManager.SIM_STATE_READY
                    && slotSimState2 == TelephonyManager.SIM_STATE_READY) {
                 showDialog(PHONE_INFO);
            } else {
                int subscription = (slotSimState2 == TelephonyManager.SIM_STATE_READY) ? 1 : 0;
                Intent intent = mRadioInfo.getIntent();
                intent.putExtra(SUBSCRIPTION, subscription);
                startActivity(intent);
            }
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PHONE_INFO:
                // Use the new array R.array.select_slot_phone_info which removed
                // the item "Always ask" to avoid index out of bounds.
                return new AlertDialog.Builder(TestingSettings.this)
                        .setTitle(R.string.testing_slot_choose)
                        .setItems(R.array.select_slot_phone_info,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent = mRadioInfo.getIntent();
                                        intent.putExtra(SUBSCRIPTION, which);
                                        startActivity(intent);
                                    }
                                }).create();
        }
        return null;
    }
}
