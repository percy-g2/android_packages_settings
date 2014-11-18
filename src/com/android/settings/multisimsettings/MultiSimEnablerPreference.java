/**
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
	 * Redistributions of source code must retain the above copyright
	   notice, this list of conditions and the following disclaimer.
	 * Redistributions in binary form must reproduce the above
	   copyright notice, this list of conditions and the following
	   disclaimer in the documentation and/or other materials provided
	   with the distribution.
	 * Neither the name of The Linux Foundation, Inc. nor the names of its
	   contributors may be used to endorse or promote products derived
	   from this software without specific prior written permission.
 
 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

package com.android.settings.multisimsettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Resources;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.os.Message;
import android.os.Handler;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.AsyncResult;
import android.telephony.TelephonyManager;
import android.telephony.MSimTelephonyManager;
import com.android.internal.telephony.msim.SubscriptionManager;
import com.android.internal.telephony.msim.SubscriptionData;
import com.android.internal.telephony.msim.Subscription;
import com.android.internal.telephony.msim.Subscription.SubscriptionStatus;
import android.widget.CompoundButton;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;

import com.qrd.plugin.feature_query.FeatureQuery;

/**
 * SimEnabler is a helper to manage the slot on/off checkbox
 * preference. It is turns on/off slot and ensures the summary of the
 * preference reflects the current state.
 */
public class MultiSimEnablerPreference extends Preference implements OnCheckedChangeListener{
    private final Context mContext;

    private String TAG = "MultiSimEnablerPreference";
    private final String INTENT_SIM_DISABLED = "com.android.sim.INTENT_SIM_DISABLED";
    private static final boolean DBG = true; //(PhoneApp.DBG_LEVEL >= 2);
    public static final int SUBSCRIPTION_INDEX_INVALID = 99999;

    private static final int EVENT_SET_SUBSCRIPTION_DONE = 1;
    private static final int EVENT_SIM_DEACTIVATE_DONE = 2;
    private static final int EVENT_SIM_ACTIVATE_DONE = 3;

    // activate/deactivate sub contains several steps like: set sub mode; set uicc sub; disable
    // data; set data subscription (if DDS switch need); enable data in new slot. In previous
    // implement UI will dismiss progress only if receive reponse of step set uicc sub. That
    // will cause some problem if user click UI very fast in short time. So we will add this
    // event which represent UI can dismiss progress dialog safely.
    private static final int EVENT_ACTIVATE_DEACTIVATE_SUB_DONE = 4;
    private final int MAX_SUBSCRIPTIONS = 2;

    private static final int RESUME = 1;
    private static final int SHOW_DISABLE_ENABLE_PROGRESSDIALOG = 7;
    private static final int SHOW_DISABLE_ENABLE_RESULT_ALERTDIALOG = 5;
    private static final String MSUBID_STR = "mSubId";
    private static final String MENABLED_STR = "mEnabled";
    private static final String MESSAGE_STR = "message";
    private static final String MSTATE_STR = "mState";
    private static final String NEEDDISMISS_STR = "needDismiss";

    private SubscriptionManager mSubscriptionManager;

    private int mSubscriptionId;
    private String mSummary;
    private boolean mState;
    private boolean mRecvUIAcDeacDone = false;
    private boolean mRecvSetSubDone = false;
    private boolean mRequest;
    private boolean mShowAlertDialog = false;
    private Subscription mSubscription = new Subscription();

    //flag whether it is activating sub
    private boolean mActivateSub;
    //flag whether to show ProgressDialog when resume.
    private boolean mIsShowingProgressDialog = false;
    private String mDialogString = null;
    private TextView mSubTitle,mSubSummary;
    private Switch mSwitch;
    private Handler mParentHandler = null;
    private AlertDialog mAlertDialog = null;

    private static boolean mIsShowAlertDialog = false;
    public static boolean mIsShowDialog = false;
    public static int mActiveSubId = -1;
    private static String mCurrentStr = "";
    private static boolean mCurrentStatus = true;

    private static boolean mHasDisableOneSimCard;
    private static Object mSyncLock = new Object();

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            // When we get result from mSubscriptionManager,
            // we needn't to show it mProgressDialog again, So set false.
            mIsShowingProgressDialog = false;
            switch (msg.what) {
                case EVENT_SIM_DEACTIVATE_DONE:
                    logd("receive EVENT_SIM_DEACTIVATE_DONE");
                    mSubscriptionManager.unregisterForSubscriptionDeactivated(mSubscriptionId, this);
                    mSwitch.setEnabled(true);
                    break;
                case EVENT_SIM_ACTIVATE_DONE:
                    logd("receive EVENT_SIM_ACTIVATE_DONE");
                    mSubscriptionManager.unregisterForSubscriptionActivated(mSubscriptionId, this);
                    mSwitch.setEnabled(true);
                    //when activate sub,after completed,it first send EVENT_SET_SUBSCRIPTION_DONE,
                    //and then EVENT_SIM_ACTIVATE_DONE,so we dismiss progressbar and show alert
                    //dialog here.
                    if (displayAlertDialog())
                        mShowAlertDialog = false;
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
                    if (!mSwitch.isChecked() && mContext!=null) {
                        logd("Broadcast INTENT_SIM_DISABLED");
                        Intent intent = new Intent(INTENT_SIM_DISABLED);
                        intent.putExtra("Subscription", mSubscriptionId);
                        mContext.sendBroadcast(intent);
                    }
                    break;
                case EVENT_ACTIVATE_DEACTIVATE_SUB_DONE:
                    logd("receive EVENT_ACTIVATE_DEACTIVATE_SUB_DONE");
                    mSubscriptionManager.unRegisterForActivateDeactivateSubDone(this);
                    mRecvUIAcDeacDone = true;
                    if (displayAlertDialog())
                        mShowAlertDialog = false;
                    break;
                default:
                    break;
            }
        }
    };

    private IntentFilter mIntentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                if (mParentHandler == null) {
                    return;
                }
                resume();
                Message message = mParentHandler.obtainMessage(RESUME);
                Bundle b = new Bundle();
                b.putBoolean( NEEDDISMISS_STR, false);
                message.setData( b );
                mParentHandler.sendMessage(message);
            }
        }
    };

    private void setScreenState() {
        if (isAirplaneModeOn()) {
            setEnabled(false);
        } else {
            setEnabled(hasCard());
        }
    }

    private boolean hasCard() {
        return MSimTelephonyManager.getDefault().hasIccCard(mSubscriptionId);
    }

    private boolean isAirplaneModeOn() {
        return (Settings.System.getInt(mContext.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0);
    }

    private void handleSetSubscriptionDone() {
        boolean ret = false;
        //set subscription is done, can set check state and summary at here
        updateSummary();

        mSubscription.copyFrom(mSubscriptionManager.getCurrentSubscription(mSubscriptionId));
        logd("handleSetSubscriptionDone mActivateSub = "+mActivateSub +" , mShowAlertDialog = "+mShowAlertDialog);
        if (!mActivateSub&& mShowAlertDialog) {
            ret = displayAlertDialog();
        }
        if (ret) 
            mShowAlertDialog = false;
    }

    private boolean displayAlertDialog() {
        logd("displayAlertDialog mRecvUIAcDeacDone="+mRecvUIAcDeacDone
                +" mRecvSetSubDone="+mRecvSetSubDone);

        if (!mRecvUIAcDeacDone || !mRecvSetSubDone || mParentHandler == null)
            return false;

        Message message = mParentHandler.obtainMessage(RESUME);
        Bundle b = new Bundle();
        b.putBoolean( NEEDDISMISS_STR, true);
        message.setData( b );
        mParentHandler.sendMessage(message);

        //after show dialog, to avoid show alertdialog again
        mRecvUIAcDeacDone = false;

        if (mDialogString != null) {
            showAlertDialogWithMessage(resultToMsg(mDialogString));
        }
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

    public MultiSimEnablerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mSubscriptionManager = SubscriptionManager.getInstance();
    }

    public MultiSimEnablerPreference(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.checkBoxPreferenceStyle);
    }

    public MultiSimEnablerPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onBindView( View view ){
        super.onBindView( view );
        mSubTitle = (TextView)view.findViewById(R.id.subtitle);
        mSubSummary = (TextView)view.findViewById(R.id.subsummary);
        mSwitch = (Switch)view.findViewById(R.id.subSwitchWidget);
        mSwitch.setOnCheckedChangeListener(this);

        if (hasCard()){
            mSwitch.setEnabled(false);
        }else{
            if (mSubscriptionManager.isSubActive(mSubscriptionId)
                || mSubscriptionManager.isSubInactive(mSubscriptionId)) {
                mSwitch.setEnabled(true);
            } else {
                mSwitch.setEnabled(false);
            }
        }

        resume();

        mContext.registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onPrepareForRemoval() {
        super.onPrepareForRemoval();
        mContext.unregisterReceiver(mReceiver);
    }

    public void setSubscription(int subscription, Handler hanler) {
        mSubscriptionId = subscription;
        mParentHandler = hanler;
    }

    public void resume() {
        setScreenState();
        updateTitle();
        updateSummary();
        mSubscriptionManager.registerForSetSubscriptionCompleted(mHandler, EVENT_SET_SUBSCRIPTION_DONE, null);
    }

    private void updateTitle(){
        if (mContext == null) return;
        String alpha = MSimTelephonyManager.getDefault().getSimOperatorName(mSubscriptionId);
        String slotInfoStr = mContext.getString(R.string.slot_name, mSubscriptionId + 1);
        if (mSubTitle == null) return;
        if (FeatureQuery.FEATURE_SETTINGS_SIM_NAME_AS_SLOT_NAME) {
            mSubTitle.setText(slotInfoStr);
        } else if (alpha != null && !"".equals(alpha)){
            mSubTitle.setText(slotInfoStr+": "+alpha);
        }else{
            if ((mContext.getResources() != null) && (mContext.getResources().getString(R.string.sim_enabler) != null)) {
                mSubTitle.setText(slotInfoStr+": "+String.format(mContext.getResources().getString(R.string.sim_enabler)));
            }
        }

    }

    private void setChecked(boolean state){
        if( mSwitch!= null ){
            mSwitch.setOnCheckedChangeListener(null);
            mSwitch.setChecked(state);
            mSwitch.setOnCheckedChangeListener(this);
        }
    }

    public void setEnabled(boolean isEnbled){
        if( mSwitch!= null ){
            mSwitch.setEnabled(isEnbled);
        }
    }

    private void sendCommand(boolean enabled){
        if (mParentHandler == null) {
            return;
        }
        mIsShowDialog = true;
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

        Message message = mParentHandler.obtainMessage(SHOW_DISABLE_ENABLE_PROGRESSDIALOG);
        Bundle b = new Bundle();
        b.putInt( MSUBID_STR, mSubscriptionId);
        b.putBoolean( MENABLED_STR, enabled);
        message.setData( b );
        mParentHandler.sendMessage(message);

        mSubscriptionManager.setSubscription(subData);
    }

    private void updateSummary() {
        Resources res = mContext.getResources();
        boolean isActivated = mSubscriptionManager.isSubActive(mSubscriptionId);

        // If rotating the device,the method updateSummary() will be
        // executed.So,if dialog is showing,the status of switch is the same as
        // rotate before.
        if (mAlertDialog != null) {
            mIsShowAlertDialog = mAlertDialog.isShowing();
        }
        if ((mActiveSubId == mSubscriptionId)
                && ((mIsShowAlertDialog) || (mIsShowDialog))) {
            mSummary = mCurrentStr;
            mState = mCurrentStatus;
        } else {
            if (isActivated) {
                mState = true;
                mSummary = String.format(
                        res.getString(R.string.sim_enabler_summary),
                        res.getString(R.string.sim_enabled));
            } else {
                mState = false;
                mSummary = String.format(res
                        .getString(R.string.sim_enabler_summary), res
                        .getString(hasCard() ? R.string.sim_disabled
                                : R.string.sim_missing));
            }
        }

        if (mSubSummary != null) {
            mSubSummary.setText(mSummary);
        }

        setChecked(mState);
    }

    private void logd(String msg) {
        Log.d(TAG + "(" + mSubscriptionId + ")", msg);
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        mRequest = isChecked;

        synchronized (mSyncLock) {
            if (mHasDisableOneSimCard) {
                resume();
                mHasDisableOneSimCard = false;
                return;
            }

            if (!isChecked && (mSubscriptionManager.getActiveSubscriptionsCount()>1)) {
                mHasDisableOneSimCard = true;
            }

            disableOrEnableSIMcard();
        }
        // save the current status information of Switch widget.
        mActiveSubId = mSubscriptionId;
        mCurrentStatus = isChecked;
        mCurrentStr = mSubSummary.getText().toString();
        mSubSummary.setText(mCurrentStr);

    }

    private void disableOrEnableSIMcard(){
        logd("onClick: " + mRequest);
        if (isAirplaneModeOn()) {
            // do nothing but warning
            logd("airplane is on, show error!");
            showAlertDialogWithMessage(mContext.getString(R.string.sim_enabler_airplane_on));
            return;
        }

        for (int i=0; i<TelephonyManager.getDefault().getPhoneCount(); i++) {
            if (MSimTelephonyManager.getDefault().getCallState(i) != TelephonyManager.CALL_STATE_IDLE) {
                //when one SIM not idle and another idle ,if disable the idle SIM,do not show "error !in call" popup
                //but show "deactivate success"  pop up
                if(MSimTelephonyManager.getDefault().getCallState(0) == TelephonyManager.CALL_STATE_IDLE||
                   MSimTelephonyManager.getDefault().getCallState(1) == TelephonyManager.CALL_STATE_IDLE)
                        break;
                if (DBG) logd("call state " + i + " is not idle, show error!");
                showAlertDialogWithMessage(mContext.getString(R.string.sim_enabler_in_call));
                return;
            }
        }

        if (!mRequest){
            if (mSubscriptionManager.getActiveSubscriptionsCount() > 1){
                if(DBG) logd("disable, both are active,can do");
                displayConfirmDialog();
            }else{
                if (DBG) logd("only one is active,can not do");
                showAlertDialogWithMessage(mContext.getString(R.string.sim_enabler_both_inactive));
                return;
            }
        }else{
            if (DBG) logd("enable, do it");
            mShowAlertDialog = true;
            mSwitch.setEnabled(false);
            mActivateSub = true;
            sendCommand(mRequest);
        }

    }

    private void displayConfirmDialog() {
        String message = mContext.getString(R.string.sim_enabler_need_disable_sim);
        // Confirm only one AlertDialog instance to show.
        if(null != mAlertDialog) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        mAlertDialog = new AlertDialog.Builder(mContext)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, mDialogClickListener)
                .setNegativeButton(android.R.string.no, mDialogClickListener)
                .setOnCancelListener(mDialogCanceListener).create();
        mAlertDialog.setCanceledOnTouchOutside(false);
        mAlertDialog.show();

    }


    private DialogInterface.OnClickListener mDialogClickListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                mShowAlertDialog = true;
                mSwitch.setEnabled(false);
                sendCommand(mRequest);
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                setChecked(true);
                mSubSummary.setText(String.format(
                        mContext.getString(R.string.sim_enabler_summary),
                        mContext.getString(R.string.sim_enabled)));
            }
            mHasDisableOneSimCard = false;
        }
    };

    private DialogInterface.OnCancelListener mDialogCanceListener = new DialogInterface.OnCancelListener() {
        public void onCancel(DialogInterface dialog) {
            setChecked(true);
            mHasDisableOneSimCard = false;
        }
    };

    private void showAlertDialogWithMessage(String msg){
        if (mParentHandler == null) {
            return;
        }
        mIsShowDialog = true;
        Message message = mParentHandler.obtainMessage(SHOW_DISABLE_ENABLE_RESULT_ALERTDIALOG);
        Bundle b = new Bundle();
        b.putInt( MSUBID_STR, mSubscriptionId);
        b.putString( MESSAGE_STR, msg);
        message.setData( b );
        mParentHandler.sendMessage(message);
    }
}

