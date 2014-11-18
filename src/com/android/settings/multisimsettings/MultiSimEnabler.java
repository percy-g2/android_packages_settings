/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
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
import android.content.DialogInterface;
import android.app.Activity;
import android.content.res.Resources;

import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.os.Message;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.AsyncResult;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.telephony.TelephonyManager;
import android.telephony.MSimTelephonyManager;
import com.android.internal.telephony.msim.SubscriptionManager;
import com.android.internal.telephony.msim.SubscriptionData;
import com.android.internal.telephony.msim.Subscription;
import com.android.internal.telephony.msim.Subscription.SubscriptionStatus;
import com.android.internal.telephony.msim.CardSubscriptionManager;

import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;

import com.qrd.plugin.feature_query.FeatureQuery;

/**
 * SimEnabler is a helper to manage the slot on/off checkbox
 * preference. It is turns on/off slot and ensures the summary of the
 * preference reflects the current state.
 */
public class MultiSimEnabler extends CheckBoxPreference implements Preference.OnPreferenceChangeListener{
    private final Context mContext;

    private String LOG_TAG = "MultiSimEnabler";
    private final String INTENT_SIM_DISABLED = "com.android.sim.INTENT_SIM_DISABLED";
    private static final boolean DBG = true; //(PhoneApp.DBG_LEVEL >= 2);
    public static final int SUBSCRIPTION_INDEX_INVALID = 99999;

    private static final int EVENT_SIM_STATE_CHANGED = 1;
    private static final int EVENT_SET_SUBSCRIPTION_DONE = 2;
    private static final int EVENT_SIM_DEACTIVATE_DONE = 3;
    private static final int EVENT_SIM_ACTIVATE_DONE = 4;

    // activate/deactivate sub contains several steps like: set sub mode; set uicc sub; disable
    // data; set data subscription (if DDS switch need); enable data in new slot. In previous
    // implement UI will dismiss progress only if receive reponse of step set uicc sub. That
    // will cause some problem if user click UI very fast in short time. So we will add this
    // event which represent UI can dismiss progress dialog safely.
    private static final int EVENT_ACTIVATE_DEACTIVATE_SUB_DONE = 5;
    private static final int EVENT_PROGRESS_DLG_TIME_OUT = 6; // time out to dismiss progress dialog

    private static final int PROGRESS_DLG_TIME_OUT = 45000; // 30 seconds for progress dialog time out
    private final int MAX_SUBSCRIPTIONS = 2;

    private SubscriptionManager mSubscriptionManager;
    private CardSubscriptionManager mCardSubscriptionManager;

    private SubscriptionData[] mCardSubscrInfo;
    private int mSubscriptionId;
    private String mSummary;
    private boolean mState;
    private boolean mRecvUIAcDeacDone = false;
    private boolean mRecvSetSubDone = false;

    private boolean mRequest;
    private boolean mShowAlertDialog = false;
    private Subscription mSubscription = new Subscription();

    private Activity mForegroundActivity;

    private AlertDialog mErrorDialog = null;
    private AlertDialog mAlertDialog = null;
    private ProgressDialog mProgressDialog = null;
    //flag whether it is activating sub
    private boolean mActivateSub;
    //flag whether to show ProgressDialog when resume.
    private boolean mIsShowingProgressDialog = false;
    private String mDialogString = null;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            // When we get result from mSubscriptionManager,
            // we needn't to show it mProgressDialog again, So set false.
            mIsShowingProgressDialog = false;
            switch (msg.what) {
                case EVENT_SIM_STATE_CHANGED:
                    logd("receive EVENT_SIM_STATE_CHANGED");
                    handleSimStateChanged();
                    break;
                case EVENT_SIM_DEACTIVATE_DONE:
                    logd("receive EVENT_SIM_DEACTIVATE_DONE");
                    mSubscriptionManager.unregisterForSubscriptionDeactivated(mSubscriptionId, this);
                    setEnabled(true);
                    break;
                case EVENT_SIM_ACTIVATE_DONE:
                    logd("receive EVENT_SIM_ACTIVATE_DONE");
                    mSubscriptionManager.unregisterForSubscriptionActivated(mSubscriptionId, this);
                    setEnabled(true);
                    //when activate sub,after completed,it first send EVENT_SET_SUBSCRIPTION_DONE,
                    //and then EVENT_SIM_ACTIVATE_DONE,so we dismiss progressbar and show alert
                    //dialog here.
                    displayAlertDialog();
                    mActivateSub = false;
                    break;
                case EVENT_SET_SUBSCRIPTION_DONE:
                    logd("receive EVENT_SET_SUBSCRIPTION_DONE");
                    String result[] = (String[])((AsyncResult) msg.obj).result;
                    if (result != null) {
                        mDialogString = result[mSubscriptionId];
                    }
                    // If SIM card operation failed, we also need to dismiss progress dialog,
                    // and give user error message.
                    if(mDialogString != null && mDialogString.equals(
                            SubscriptionManager.SUB_DEACTIVATE_NOT_SUPPORTED)) {
                        mActivateSub = false;
                    }
                    mRecvSetSubDone = true;
                    handleSetSubscriptionDone();
                    // To notify CarrierLabel
                    if (!MultiSimEnabler.this.isChecked() && mForegroundActivity!=null) {
                        logd("Broadcast INTENT_SIM_DISABLED");
                        Intent intent = new Intent(INTENT_SIM_DISABLED);
                        intent.putExtra("Subscription", mSubscriptionId);
                        mForegroundActivity.sendBroadcast(intent);
                    }
                    break;
                case EVENT_ACTIVATE_DEACTIVATE_SUB_DONE:
                    logd("receive EVENT_ACTIVATE_DEACTIVATE_SUB_DONE");
                    mSubscriptionManager.unRegisterForActivateDeactivateSubDone(this);
                    mRecvUIAcDeacDone = true;
                    displayAlertDialog();
                    break;
                case EVENT_PROGRESS_DLG_TIME_OUT:
                    logd("receive EVENT_PROGRESS_DLG_TIME_OUT");
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private void handleSimStateChanged() {
        logd("EVENT_SIM_STATE_CHANGED");
        mSubscription = new Subscription();
        SubscriptionData[] cardSubsInfo = new SubscriptionData[MAX_SUBSCRIPTIONS];
        for(SubscriptionData cardSub : cardSubsInfo) {
            if (cardSub != null) {
                for (int i = 0; i < cardSub.getLength(); i++) {
                    Subscription sub = cardSub.subscription[i];
                    if (sub.subId == mSubscriptionId) {
                        mSubscription.copyFrom(sub);
                        break;
                    }
                }
            }
        }
        if (mSubscription.subStatus == SubscriptionStatus.SUB_ACTIVATED
            || mSubscription.subStatus == SubscriptionStatus.SUB_DEACTIVATED) {
            setEnabled(true);
        }
    }

    private void handleSetSubscriptionDone() {
        //set subscription is done, can set check state and summary at here
        boolean ret = false;
        updateSummary();

        mSubscription.copyFrom(mSubscriptionManager.getCurrentSubscription(mSubscriptionId));
        //if it is activating sub,we can dismiss progressbar and show alert dialog.Otherwise
        //do this when EVENT_SIM_ACTIVATE_DONE received.
        // When user enable/disable it, it will show the Alert Dialog,
        // If it is enabled/disabled by turning on/off the airplane mode,
        // then it will not show the alertDialog.
        if (!mActivateSub && mShowAlertDialog) {
            ret = displayAlertDialog();
        }
        if (ret) 
            mShowAlertDialog = false;
    }

    private boolean displayAlertDialog() {
        logd("displayAlertDialog mRecvUIAcDeacDone="+mRecvUIAcDeacDone
                +" mRecvSetSubDone="+mRecvSetSubDone);

        if (!mRecvUIAcDeacDone || !mRecvSetSubDone)
            return false;

        if (mHandler.hasMessages(EVENT_PROGRESS_DLG_TIME_OUT))
            mHandler.removeMessages(EVENT_PROGRESS_DLG_TIME_OUT);

        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        if (mDialogString != null) {
            displayAlertDialog(resultToMsg(mDialogString));
        }
        mShowAlertDialog = false;
        return true;
    }

    private String resultToMsg(String result){
        if(result.equals(SubscriptionManager.SUB_ACTIVATE_SUCCESS)){
            return mContext.getString(R.string.sub_activate_success);
        }
        if (result.equals(SubscriptionManager.SUB_ACTIVATE_FAILED)){
            return mContext.getString(R.string.sub_activate_failed);
        }
        if (result.equals(SubscriptionManager.SUB_DEACTIVATE_SUCCESS)){
            return mContext.getString(R.string.sub_deactivate_success);
        }
        if (result.equals(SubscriptionManager.SUB_DEACTIVATE_FAILED)){
            return mContext.getString(R.string.sub_deactivate_failed);
        }
        if (result.equals(SubscriptionManager.SUB_DEACTIVATE_NOT_SUPPORTED)){
            return mContext.getString(R.string.sub_deactivate_not_supported);
        }
        if (result.equals(SubscriptionManager.SUB_ACTIVATE_NOT_SUPPORTED)){
            return mContext.getString(R.string.sub_activate_not_supported);
        }
        if (result.equals(SubscriptionManager.SUB_NOT_CHANGED)){
            return mContext.getString(R.string.sub_not_changed);
        }
        return mContext.getString(R.string.sub_not_changed);
    }

    public MultiSimEnabler(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mSubscriptionManager = SubscriptionManager.getInstance();
        mCardSubscriptionManager = CardSubscriptionManager.getInstance();
    }

    public MultiSimEnabler(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.checkBoxPreferenceStyle);
    }

    public MultiSimEnabler(Context context) {
        this(context, null);
    }

    public void setSubscription(Activity activity, int subscription) {
        mSubscriptionId = subscription;

        if (FeatureQuery.FEATURE_SETTINGS_SIM_NAME_AS_SLOT_NAME) {
            setTitle(mContext.getString(R.string.slot_name, subscription + 1));
        } else {
            String alpha = ((MSimTelephonyManager) mContext.getSystemService(Context.MSIM_TELEPHONY_SERVICE))
                    .getSimOperatorName(subscription);
            if (alpha != null && !"".equals(alpha))
                setTitle(alpha);
        }

        mForegroundActivity = activity;
        if (mForegroundActivity == null) logd("error! mForegroundActivity is null!");

        if (getCardSubscriptions() == null){
            logd("card info is not available.");
            setEnabled(false);
        }else{
            mSubscription.copyFrom(mSubscriptionManager.getCurrentSubscription(mSubscriptionId));
            logd("sub status " + mSubscription.subStatus);
            if (mSubscription.subStatus == SubscriptionStatus.SUB_ACTIVATED
                || mSubscription.subStatus == SubscriptionStatus.SUB_DEACTIVATED) {
                setEnabled(true);
            } else {
                setEnabled(false);
            }
        }
    }

    public void resume() {
        setOnPreferenceChangeListener(this);

        updateSummary();

        // If we haven't get result from mSubscriptionManager after we disable or enable simcard,
        // show progressDialog to lock screen when resume.
        if (isShowProgressDialog()) {
            mProgressDialog.show();
        }
        /*mSubscriptionManager.registerForSimStateChanged(mHandler, EVENT_SIM_STATE_CHANGED, null);*/
        mSubscriptionManager.registerForSetSubscriptionCompleted(mHandler, EVENT_SET_SUBSCRIPTION_DONE, null);
    }

    public void destroy() {
        // Remove dialog to avoid AlertDialog dismissing when lock screen.
        if (mAlertDialog != null) {
            logd("destroy: dismiss alert dialog");
            mAlertDialog.dismiss();
            mAlertDialog = null;
            mDialogString = null;
        }

        if (mHandler.hasMessages(EVENT_PROGRESS_DLG_TIME_OUT))
            mHandler.removeMessages(EVENT_PROGRESS_DLG_TIME_OUT);
    }

    // Check if need to show progressDialog when resume.
    public boolean isShowProgressDialog(){
        return (mProgressDialog != null) && (!mProgressDialog.isShowing())
                && mIsShowingProgressDialog;
    }

    public void pause() {
        setOnPreferenceChangeListener(null);

        //dismiss all dialogs: alert and progress dialogs
        if (mErrorDialog != null) {
            logd("pause: dismiss error dialog");
            mErrorDialog.dismiss();
            mErrorDialog = null;
        }

        // Recored progressbar's state with a flag(mIsShowingProgressDialog)
        // Not set mProgressDialog to null, because maybe use it when resume.
        if (mProgressDialog != null && mProgressDialog.isShowing()){
            logd("pause: dismiss progress dialog");
            mProgressDialog.dismiss();
            mIsShowingProgressDialog = true;
        }
        mSubscriptionManager.unRegisterForSetSubscriptionCompleted(mHandler);
        /*mSubscriptionManager.unRegisterForSimStateChanged(mHandler);*/
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        mRequest = ((Boolean)value).booleanValue();
        displayConfirmDialog();

        // Don't update UI to opposite state until we're sure
        return false;
    }

    public void updateSimEnablerPreference() {
        //need to update card sub info
        for(int i=0; i<MAX_SUBSCRIPTIONS; i++) {
            mCardSubscrInfo[i] = mCardSubscriptionManager.getCardSubscriptions(i);
        }

        updateSummary();
    }

    private void displayConfirmDialog() {
        if (mForegroundActivity == null){
            logd("can not display alert dialog,no foreground activity");
            return;
        }
        String message = mContext.getString(mRequest?R.string.sim_enabler_need_enable_sim:R.string.sim_enabler_need_disable_sim);
        // Confirm only one AlertDialog instance to show.
        if(null != mAlertDialog) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        // Need an activity context to show a dialog
        mAlertDialog = new AlertDialog.Builder(mForegroundActivity)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, mDialogClickListener)
                .setNegativeButton(android.R.string.no, mDialogClickListener)
                .show();

    }


    private DialogInterface.OnClickListener mDialogClickListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                logd("onClick: " + mRequest);

                if (Settings.System.getInt(mContext.getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0) {
                    // do nothing but warning
                    logd("airplane is on, show error!");
                    displayAlertDialog(mContext.getString(R.string.sim_enabler_airplane_on));
                    return;
                }

                for (int i=0; i<TelephonyManager.getDefault().getPhoneCount(); i++) {
                    if (MSimTelephonyManager.getDefault().getCallState(i) != TelephonyManager.CALL_STATE_IDLE) {
                        // do nothing but warning
                        if (DBG) logd("call state " + i + " is not idle, show error!");
                        displayAlertDialog(mContext.getString(R.string.sim_enabler_in_call));
                        return;
                    }
                }

                if (!mRequest){
                    if (mSubscriptionManager.getActiveSubscriptionsCount() > 1){
                        if(DBG) logd("disable, both are active,can do");
                        mShowAlertDialog = true;
                        setEnabled(false);
                        sendCommand(mRequest);
                    }else{
                        if (DBG) logd("only one is active,can not do");
                        displayAlertDialog(mContext.getString(R.string.sim_enabler_both_inactive));
                        return;
                    }
                }else{
                    if (DBG) logd("enable, do it");
                    mShowAlertDialog = true;
                    setEnabled(false);
                    mActivateSub = true;
                    sendCommand(mRequest);
                }
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                if (DBG) logd("onClick Cancel, revert checkbox status");
            }
        }
    };

    private void sendCommand(boolean enabled){
        SubscriptionData subData = new SubscriptionData(MAX_SUBSCRIPTIONS);
        for(int i=0;i<MAX_SUBSCRIPTIONS;i++) {
            subData.subscription[i].copyFrom(mSubscriptionManager.getCurrentSubscription(i));
        }
        if (enabled){
            subData.subscription[mSubscriptionId].slotId = mSubscriptionId;
            subData.subscription[mSubscriptionId].subId = mSubscriptionId;
            mSubscriptionManager.setDefaultAppIndex(subData.subscription[mSubscriptionId]);
            subData.subscription[mSubscriptionId].subStatus = SubscriptionStatus.SUB_ACTIVATE;
            mSubscriptionManager.registerForSubscriptionActivated(
                mSubscriptionId, mHandler, EVENT_SIM_ACTIVATE_DONE, null);
        }else{
            subData.subscription[mSubscriptionId].slotId = SUBSCRIPTION_INDEX_INVALID;
            subData.subscription[mSubscriptionId].m3gppIndex = SUBSCRIPTION_INDEX_INVALID;
            subData.subscription[mSubscriptionId].m3gpp2Index = SUBSCRIPTION_INDEX_INVALID;
            subData.subscription[mSubscriptionId].subId = mSubscriptionId;
            subData.subscription[mSubscriptionId].subStatus = SubscriptionStatus.SUB_DEACTIVATE;
            mSubscriptionManager.registerForSubscriptionDeactivated(
                mSubscriptionId, mHandler, EVENT_SIM_DEACTIVATE_DONE, null);
        }
        mSubscriptionManager.registerForActivateDeactivateSubDone(
                mHandler, EVENT_ACTIVATE_DEACTIVATE_SUB_DONE, null);
        mRecvUIAcDeacDone = false;
        mRecvSetSubDone = false;
        displayProgressDialog(enabled);
        mSubscriptionManager.setSubscription(subData);
    }

    private void displayProgressDialog(boolean enabled){
        String title = Settings.System.getString(mContext.getContentResolver(),Settings.System.MULTI_SIM_NAME[mSubscriptionId]);
        String msg = mContext.getString(enabled?R.string.sim_enabler_enabling:R.string.sim_enabler_disabling);
        mProgressDialog = new ProgressDialog(mForegroundActivity);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setTitle(title);
        mProgressDialog.setMessage(msg);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
        mHandler.sendEmptyMessageDelayed(EVENT_PROGRESS_DLG_TIME_OUT, PROGRESS_DLG_TIME_OUT);
    }

    private void displayAlertDialog(String msg) {
        mErrorDialog = new AlertDialog.Builder(mForegroundActivity)
             .setTitle(android.R.string.dialog_alert_title)
             .setMessage(msg)
             .setCancelable(false)
             .setNeutralButton(R.string.close_dialog, null)
             .show();
    }

    private void updateSummary() {
        Resources res = mContext.getResources();
        boolean isActivated = mSubscriptionManager.isSubActive(mSubscriptionId);
        if (isActivated) {
            mState = true;
            mSummary = String.format(res.getString(R.string.sim_enabler_summary), res.getString(R.string.sim_enabled));
        } else {
            mState = false;
            mSummary = String.format(res.getString(R.string.sim_enabler_summary), res.getString(mCardSubscrInfo[mSubscriptionId] != null  ?
                R.string.sim_disabled :R.string.sim_missing));
        }

        setSummary(mSummary);
        setChecked(mState);
    }

    private void logd(String msg) {
        Log.d(LOG_TAG + "(" + mSubscriptionId + ")", msg);
    }

    private SubscriptionData[] getCardSubscriptions() {
        mCardSubscrInfo = new SubscriptionData[MAX_SUBSCRIPTIONS];
        for(int i=0; i<MAX_SUBSCRIPTIONS; i++) {
            mCardSubscrInfo[i] = mCardSubscriptionManager.getCardSubscriptions(i);
        }
        return mCardSubscrInfo;
    }

}

