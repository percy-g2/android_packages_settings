/*
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
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
 *
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

import static com.android.internal.telephony.MSimConstants.SUBSCRIPTION_KEY;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.EditTextPreference;
import android.provider.Settings.System;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.Button;
import android.widget.EditText;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;

import com.android.internal.telephony.msim.Subscription.SubscriptionStatus;
import com.android.internal.telephony.msim.SubscriptionManager;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.msim.CardSubscriptionManager;
import com.android.settings.R;
import com.qrd.plugin.feature_query.FeatureQuery;

public class MultiSimConfiguration extends PreferenceActivity implements TextWatcher{
    private static final String LOG_TAG = "MultiSimConfiguration";

    private static final String KEY_SIM_NAME = "sim_name_key";
    private static final String KEY_SIM_ENABLER = "sim_enabler_key";
    private static final String KEY_NETWORK_SETTING = "mobile_network_key";
    private static final String KEY_CALL_SETTING = "call_setting_key";

    private static final int EVENT_SET_SUBSCRIPTION_DONE = 2;

    private static final String SUBKEY = "sub";
    private static final int CHANNEL_NAME_MAX_LENGTH = 6;
    private static final int ALWAYS_ASK = 2;

    private PreferenceScreen mPrefScreen;
    private SubscriptionManager mSubscriptionManager;
    private PreferenceScreen mNetworkSetting;
    private PreferenceScreen mCallSetting;

    private int mSubscription;
    private EditTextPreference mNamePreference;
    private int mChangeStartPos;
    private int mChangeCount;

    private IntentFilter mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logd("onReceive " + action);
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action) ||
                Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                setScreenState();
            }
        }
    };

    private class NamePreferenceChangeListener implements Preference.OnPreferenceChangeListener{
        public boolean onPreferenceChange(Preference preference, Object value) {
            Log.i(LOG_TAG, "onPreferenceChange " + value);
            String multiSimName = (String)value;
            String theOtherSimName = System.getString(getContentResolver(),
                    System.MULTI_SIM_NAME[mSubscription == 0 ? 1 : 0]);
            if (multiSimName.equals(theOtherSimName)) {
                new AlertDialog.Builder(preference.getContext())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(android.R.string.dialog_alert_title)
                    .setMessage(R.string.same_name_alert)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                    })
                .show();
                return false;
            }
            System.putString(getContentResolver(),
                    System.MULTI_SIM_NAME[mSubscription], multiSimName);
            mNamePreference.setSummary(multiSimName);
            Intent intent = new Intent(MultiSimSettingsConstants.SUBNAME_CHANGED);
            intent.putExtra(SUBKEY, mSubscription);
            sendBroadcast(intent);

            // If the new name equals to default sim name, we delete the newly inserted record from
            // database so that it can display the right name corresponding to the system language.
            if (!FeatureQuery.FEATURE_SUBSCRIPTION_CARRIER) {
                if (mSubscription == 0 && multiSimName.equals(getString(R.string.default_sim1_name))) {
                    StringBuilder where = new StringBuilder();
                    where.append("name = '" + System.MULTI_SIM_NAME[0] + "'");
                    getContentResolver().delete(Uri.parse("content://settings/system"), where.toString(), null);
                } else if (mSubscription == 1 && multiSimName.equals(getString(R.string.default_sim2_name))) {
                    StringBuilder where = new StringBuilder();
                    where.append("name = '" + System.MULTI_SIM_NAME[1] + "'");
                    getContentResolver().delete(Uri.parse("content://settings/system"), where.toString(), null);
                }
            }
            return true;
        }
    }

    private class NamePreferenceClickListener implements Preference.OnPreferenceClickListener{
        public boolean onPreferenceClick(Preference preference) {
            // The dialog should be created by now
            EditText et = mNamePreference.getEditText();
            if (et != null) {
                et.setText(getMultiSimName(mSubscription));
            }
            return true;
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_SET_SUBSCRIPTION_DONE:
                    setScreenState();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.multi_sim_configuration);

        mPrefScreen = getPreferenceScreen();

        Intent intent = getIntent();
        mSubscription = intent.getIntExtra(SUBSCRIPTION_KEY, 0);

        mSubscriptionManager = SubscriptionManager.getInstance();

        mNamePreference = (EditTextPreference)findPreference(KEY_SIM_NAME);
        mNamePreference.setTitle(R.string.title_sim_alias);
        mNamePreference.setSummary(getMultiSimName(mSubscription));
        mNamePreference.setOnPreferenceChangeListener(new NamePreferenceChangeListener());
        mNamePreference.setOnPreferenceClickListener(new NamePreferenceClickListener());
        EditText et = mNamePreference.getEditText();
        if (et != null) {
            et.addTextChangedListener(this);
            Dialog d = mNamePreference.getDialog();
            if (d instanceof AlertDialog) {
                Button b = ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE);
                //if user inputed whole space and saved,that is to say SLOT1 and SLOT2 may named blank space, it is meaningless.
                b.setEnabled(!"".equals(et.getText().toString().trim()) && et.getText().length() > 0);
            }
        }

        mNetworkSetting = (PreferenceScreen)findPreference(KEY_NETWORK_SETTING);
        mNetworkSetting.getIntent().putExtra(MultiSimSettingsConstants.TARGET_PACKAGE,
                               MultiSimSettingsConstants.NETWORK_PACKAGE)
                                    .putExtra(MultiSimSettingsConstants.TARGET_CLASS,
                               MultiSimSettingsConstants.NETWORK_CLASS)
                                    .putExtra(SUBSCRIPTION_KEY, mSubscription);

        mCallSetting = (PreferenceScreen)findPreference(KEY_CALL_SETTING);
        mCallSetting.getIntent().putExtra(MultiSimSettingsConstants.TARGET_PACKAGE,
                               MultiSimSettingsConstants.CALL_PACKAGE)
                                    .putExtra(MultiSimSettingsConstants.TARGET_CLASS,
                               MultiSimSettingsConstants.CALL_CLASS)
                                    .putExtra(SUBSCRIPTION_KEY, mSubscription)
                                    .putExtra("Title", getResources().getString(R.string.call_settings));

        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    }

    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
        setScreenState();
        mSubscriptionManager.registerForSetSubscriptionCompleted(mHandler, EVENT_SET_SUBSCRIPTION_DONE, null);
    }

    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    private boolean isSubActivated() {
        return mSubscriptionManager.isSubActive(mSubscription);
    }

    private boolean isAirplaneModeOn() {
        return (System.getInt(getContentResolver(), System.AIRPLANE_MODE_ON, 0) != 0);
    }

    private void setScreenState() {
        if (isAirplaneModeOn()) {
            mNetworkSetting.setEnabled(false);
            mCallSetting.setEnabled(false);
        } else {
            mNetworkSetting.setEnabled(isSubActivated());
            mCallSetting.setEnabled(isSubActivated());
        }
    }

    private String getMultiSimName(int subscription) {
         if(subscription == ALWAYS_ASK){
             return getResources().getString(R.string.select_slot_always_ask);
         }else{
             return System.getString(getContentResolver(),System.MULTI_SIM_NAME[subscription]);
         }
    }

    // TextWatcher interface
    public void afterTextChanged(Editable s) {
        limitTextSize(s.toString().trim());
        Dialog d = mNamePreference.getDialog();
        if (d instanceof AlertDialog) {
            //if user inputed whole space and saved,that is to say SLOT1 and SLOT2 may named blank space, it is meaningless.
            ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!"".equals(s.toString().trim()) && s.length() > 0);
        }
    }

    // TextWatcher interface
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // not used
    }

    // TextWatcher interface
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // The start position of new added characters
        mChangeStartPos = start;
        // The number of new added characters
        mChangeCount = count;
    }

    private void limitTextSize(String s) {
        EditText et = mNamePreference.getEditText();

        if (et != null) {
            int wholeLen = 0;
            int i = 0;

            for (i = 0; i < s.length(); i++) {
                wholeLen += getCharacterVisualLength(s, i);
            }

            // Too many characters,cut off the new added characters
            if (wholeLen > CHANNEL_NAME_MAX_LENGTH) {
                int cutNum = wholeLen - CHANNEL_NAME_MAX_LENGTH;
                // Get start position of characters that will be cut off
                int changeEndPos = mChangeStartPos + mChangeCount - 1;
                int cutLen = 0;
                for (i = changeEndPos; i >= 0; i--) {
                    cutLen += getCharacterVisualLength(s, i);
                    if (cutLen >= cutNum) {
                        break;
                    }
                }
                // The cut off characters is in range [i,mChangeStartPos +
                // mChangeCount)
                int headStrEndPos = i;
                // Head substring that is before the cut off characters
                String headStr = "";
                // Rear substring that is after the cut off characters
                String rearStr = "";
                if (headStrEndPos > 0) {
                    // Get head substring if the cut off characters is not
                    // at the beginning
                    headStr = s.substring(0, headStrEndPos);
                }
                int rearStrStartPos = mChangeStartPos + mChangeCount;
                if (rearStrStartPos < s.length()) {
                    // Get rear substring if the cut off characters is not
                    // at the end
                    rearStr = s.substring(rearStrStartPos, s.length());
                }
                // headStr + rearStr is the new string after characters are
                // cut off
                et.setText(headStr + rearStr);
                // Move cursor to the original position
                et.setSelection(i);
            }
        }
    }

    // A character beyond 0xff is twice as big as a character within 0xff in
    // width when showing.
    private int getCharacterVisualLength(String seq, int index) {
        int cp = Character.codePointAt(seq, index);
        if (cp >= 0x00 && cp <= 0xFF) {
            return 1;
        } else {
            return 2;
        }
    }
    private void logd(String msg) {
        Log.d(LOG_TAG, "[" + LOG_TAG + "(" + mSubscription + ")] " + msg);
    }
}
