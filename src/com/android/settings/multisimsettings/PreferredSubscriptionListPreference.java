/*
 *
 * Copyright (c) 2012-2013,The Linux Foundation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *     Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.

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

import com.android.settings.R;

import android.os.AsyncResult;
import android.os.Message;
import android.os.Handler;
import android.widget.Toast;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.preference.ListPreference;
import android.preference.Preference;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.msim.SubscriptionManager;

import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.AirplaneModeEnabler;
import com.android.internal.telephony.msim.MSimPhoneFactory;

public class PreferredSubscriptionListPreference extends ListPreference implements
                                           Preference.OnPreferenceChangeListener {
    private static final String LOG_TAG = "PreferredSubscriptionListPreference";
    private static final boolean DBG = true;
    static final int EVENT_SET_DATA_SUBSCRIPTION_DONE = 3;
    private static final int ALWAYS_ASK = 2;
    private static final int SUB_COUNT = 2;
    private static final int SHOW_PROGRESSDIALOG = 2;
    private static final int DISMISS_PROGRESSDIALOG = 3;
    private static final int SHOW_ALERTDIALOG = 4;
    private int mPreferredSubscription;
    private int mType;
    private Context mContext;
    private Handler mMultiSimHandler;

    public PreferredSubscriptionListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public PreferredSubscriptionListPreference(Context context) {
        this(context, null);
    }

    public void setType(int listType, Handler handler) {
        mType = listType;
        mMultiSimHandler = handler;
        mPreferredSubscription = getPreferredSubscription(listType);
        setValue(String.valueOf(mPreferredSubscription));
        //get the string from database
        setEntryValues(getEntityValues(listType));
        setEntries(getMultiSimNamesFromDb(listType));
        setSummary(getMultiSimName(mPreferredSubscription));
    }

    public void resume() {
        setOnPreferenceChangeListener(this);
        setType(mType,mMultiSimHandler);
        updatePreferenceState();
        setSummary(getMultiSimName(mPreferredSubscription));
    }

    public void pause() {
        setOnPreferenceChangeListener(null);
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        int preferredSubscription = Integer.parseInt((String) value);
        if(DBG) Log.d(LOG_TAG, "onPreferenceChange: " + preferredSubscription);

        if (preferredSubscription == mPreferredSubscription) {
            // do nothing but warning
            if (DBG) Log.d(LOG_TAG, "preferred subscription not changed");
        } else if (AirplaneModeEnabler.isAirplaneModeOn(mContext)) {
            // do nothing but warning
            if (DBG) Log.e(LOG_TAG, "error! airplane is on");
        } else {
            //setPreferredSubscription(Message.obtain(mHandler,
              //  MultiSimSettingsConstants.EVENT_PREFERRED_SUBSCRIPTION_CHANGED, mType, preferredSubscription));
            setPreferredSubscription(mType, preferredSubscription);
        }

        // Don't update UI to opposite state until we're sure
        return false;
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult)msg.obj;
            switch(msg.what) {
                case EVENT_SET_DATA_SUBSCRIPTION_DONE:
                    Log.d(LOG_TAG, "EVENT_SET_DATA_SUBSCRIPTION_DONE");
                    mMultiSimHandler.sendEmptyMessage(DISMISS_PROGRESSDIALOG);
                    //dismissDialog();
                    setEnabled(true);
                    handlePreferredSubscriptionChanged();
                    String status;
                    Message message = mMultiSimHandler.obtainMessage(SHOW_ALERTDIALOG);
                    if (ar.exception != null) {
                        // This should never happens.  But display an alert message in case.
                        status = mContext.getResources().getString(R.string.set_dds_failed);
                        message.obj = status;
                        mMultiSimHandler.sendMessage(message);
                        //displayAlertDialog(status);
                        break;
                    }

                    //final ProxyManager.SetDdsResult result = (ProxyManager.SetDdsResult)ar.result;
                    boolean result = (Boolean)ar.result;

                    Log.d(LOG_TAG, "SET_DATA_SUBSCRIPTION_DONE: result = " + result);

                    if (result == true/*ProxyManager.SetDdsResult.SUCCESS*/) {
                        MSimPhoneFactory.setDataSubscription(msg.arg1);
                        setSummary(getMultiSimName(msg.arg1));
                        setValue(String.valueOf(msg.arg1));
                        status = mContext.getResources().getString(R.string.set_dds_success);
                        Toast toast = Toast.makeText(mContext, status, Toast.LENGTH_LONG);
                        toast.show();
                    } else {
                        status = mContext.getResources().getString(R.string.set_dds_failed);
                        message.obj = status;
                        mMultiSimHandler.sendMessage(message);
                        //displayAlertDialog(status);
                    }

                    break;
            }
        }
    };

    private void handlePreferredSubscriptionChanged() {
        if(DBG) Log.d(LOG_TAG, "default subscription changed in " + mType);
        int preferredSubscription = getPreferredSubscription(mType);
        if (preferredSubscription == mPreferredSubscription) {
            if(DBG) Log.d(LOG_TAG, "set default subscription fails on " + mType);
        } else {
            mPreferredSubscription = preferredSubscription;
            if(DBG) Log.d(LOG_TAG, "now default subscription is : " + mPreferredSubscription + " on " + mType);
            setSummary(getMultiSimName(mPreferredSubscription));
        }
        setValue(String.valueOf(preferredSubscription));
    }

    private void updatePreferenceState() {
        boolean isEnabled = false;
        boolean isOn = AirplaneModeEnabler.isAirplaneModeOn(mContext);
        if (!isOn) {
            //only not in airplane mode and the number of active subscription is 2, enabled.
            if (SubscriptionManager.getInstance().getActiveSubscriptionsCount() == 2) {
                isEnabled = true;
            }
        }

        if(DBG) Log.d(LOG_TAG, "setEnabled: " + isEnabled);
        setEnabled(isEnabled);
    }

    /*** For preferred subscription process on Voice, Sms, Data ***/
    private int getPreferredSubscription(int listType) {
         int preferredSubscription = 0;
         switch (listType) {
             case MultiSimSettingsConstants.VOICE_SUBSCRIPTION_LIST:
                preferredSubscription = MSimPhoneFactory.getVoiceSubscription();
                break;
             case MultiSimSettingsConstants.SMS_SUBSCRIPTION_LIST:
                preferredSubscription = MSimPhoneFactory.getSMSSubscription();
                break;
             case MultiSimSettingsConstants.DATA_SUBSCRIPTION_LIST:
                preferredSubscription = MSimPhoneFactory.getDataSubscription();
                break;
         }
         return preferredSubscription;
    }

    /** Get EntityValues for ListPreference.
     *  Data Call always use  multi_sim_values
     *  return the string arrays get from arrays.xml
     */
    private String[] getEntityValues(int listType) {
        if (listType != MultiSimSettingsConstants.PREFERRED_SUBSCRIPTION_LISTS[1]) {
            String[] values = new String[3];
            values = mContext.getResources().getStringArray(R.array.multi_sim_values_voice);
            return values;
        } else {
            String[] values = new String[2];
            values = mContext.getResources().getStringArray(R.array.multi_sim_values);
            return values;
        }
    }

   /** Used for get the simNames from the resource file while change the language.
     * return the string arrays get from arrays.xml
     */
    private String[] getMultiSimNamesFromRes(int listType) {
        int count = TelephonyManager.getDefault().getPhoneCount();
        String[] names;
        //When the three conditions are ok, the choice will contains always ask.
        if (count == SUB_COUNT && listType != MultiSimSettingsConstants.PREFERRED_SUBSCRIPTION_LISTS[1]) {
            count += 1; //add 'always ask'
        }

        names = new String[count];

        for (int i = 0; i < count; i++) {
            names[i] = mContext.getResources().getStringArray(R.array.select_slot_items)[i];
        }
        return names;
    }

    /** Used for get the simNames from the database, for the selection item can
    * match with the selected result.
    */
    public String[] getMultiSimNamesFromDb(int listType) {
        int count = TelephonyManager.getDefault().getPhoneCount();
        String[] names;
        // When the three conditions are ok, the choice will contains always ask.
        if (count == SUB_COUNT && listType != MultiSimSettingsConstants.PREFERRED_SUBSCRIPTION_LISTS[1]) {
            count += 1; //add 'always ask'
        }

        names = new String[count];

        for (int i = 0; i < count; i++) {
            names[i] = getMultiSimName(i);
            if (names[i] == null) {
                names[i] = mContext.getResources().getStringArray(R.array.select_slot_items)[i];
            }
        }
        return names;
    }
    private String getMultiSimName(int subscription) {
         if(subscription == ALWAYS_ASK){
             return mContext.getResources().getString(R.string.select_slot_always_ask);
         }else{
             return Settings.System.getString(mContext.getContentResolver(),Settings.System.MULTI_SIM_NAME[subscription]);
         }
    }

    private void setPreferredSubscription(int listType, int subscription) {
        switch (listType) {
            case MultiSimSettingsConstants.VOICE_SUBSCRIPTION_LIST:
                MSimPhoneFactory.setVoiceSubscription(subscription);
                handlePreferredSubscriptionChanged();
                //msg.sendToTarget();
                break;
            case MultiSimSettingsConstants.SMS_SUBSCRIPTION_LIST:
                MSimPhoneFactory.setSMSSubscription(subscription);
                handlePreferredSubscriptionChanged();
                //msg.sendToTarget();
                break;
            case MultiSimSettingsConstants.DATA_SUBSCRIPTION_LIST:
                mMultiSimHandler.sendEmptyMessage(SHOW_PROGRESSDIALOG);
                //showDialog();
                SubscriptionManager subManager = SubscriptionManager.getInstance();
                Message setDdsMsg = Message.obtain(mHandler, EVENT_SET_DATA_SUBSCRIPTION_DONE, subscription,0);
                subManager.setDataSubscription(subscription, setDdsMsg);
                break;
            default:
                Log.e(LOG_TAG, "Not avaliable list type can be processed");
        }
    }

}
