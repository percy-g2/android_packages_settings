/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2012-2013, The Linux Foundation. All Rights Reserved.
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only
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

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.MSimConstants;

import java.util.ArrayList;

import com.qrd.plugin.feature_query.FeatureQuery;

public class ApnSettings extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {

    private static final boolean DEBUG = true;

    static final String TAG = "ApnSettings";

    public static final String EXTRA_POSITION = "position";
    public static final String RESTORE_CARRIERS_URI =
        "content://telephony/carriers/restore";
    public static final String PREFERRED_APN_URI =
        "content://telephony/carriers/preferapn";
    public static final String OPERATOR_NUMERIC_EXTRA = "operator";

    public static final String APN_ID = "apn_id";

    private static final int ID_INDEX = 0;
    private static final int NAME_INDEX = 1;
    private static final int APN_INDEX = 2;
    private static final int TYPES_INDEX = 3;

    private static final int MENU_NEW = Menu.FIRST;
    private static final int MENU_RESTORE = Menu.FIRST + 1;

    private static final int EVENT_RESTORE_DEFAULTAPN_START = 1;
    private static final int EVENT_RESTORE_DEFAULTAPN_COMPLETE = 2;

    private static final int DIALOG_RESTORE_DEFAULTAPN = 1001;

    private static final Uri DEFAULTAPN_URI = Uri.parse(RESTORE_CARRIERS_URI);
    private static final Uri PREFERAPN_URI = Uri.parse(PREFERRED_APN_URI);

    private static final String CHINA_UNION_PLMN = "46001";
    private static final String CT_DEFAULT_NAME = "China Telecom";
    private static final String CTWAP = "ctwap";
    private static final String CTNET = "ctnet";

    private static boolean mRestoreDefaultApnMode;

    private RestoreApnUiHandler mRestoreApnUiHandler;
    private RestoreApnProcessHandler mRestoreApnProcessHandler;

    private String mSelectedKey;
    private int mSubscription = 0;

    private boolean mUseNvOperatorForEhrpd = SystemProperties.getBoolean(
            "persist.radio.use_nv_for_ehrpd", false);

    private IntentFilter mMobileStateFilter;
    // remember the mccMncFromSim the last time
    private String historyMccMncFromSim = null;

    private final BroadcastReceiver mMobileStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(
                    TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                Phone.DataState state = getMobileDataState(intent);
                switch (state) {
                case CONNECTED:
                    if (!mRestoreDefaultApnMode) {
                        if (!isAirplaneOn() && isAPNNumericLoaded()) {
                            fillList();
                        }
                    } else {
                        showDialog(DIALOG_RESTORE_DEFAULTAPN);
                    }
                    break;
                }
            }
        }
    };


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            invalidateOptionsMenu();
        }
    };

    private ContentObserver mPreferApnObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (DEBUG) Log.i(TAG, "PreferApnObserver, will try to update the preference.");
            fillList();
        }
    };

    private static Phone.DataState getMobileDataState(Intent intent) {
        String str = intent.getStringExtra(Phone.STATE_KEY);
        if (str != null) {
            return Enum.valueOf(Phone.DataState.class, str);
        } else {
            return Phone.DataState.DISCONNECTED;
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.apn_settings);
        getListView().setItemsCanFocus(true);
        mSubscription = getIntent().getIntExtra(SelectSubscription.SUBSCRIPTION_KEY,
                MSimTelephonyManager.getDefault().getDefaultSubscription());
        if (DEBUG) Log.d(TAG, "onCreate received sub :" + mSubscription);
        mMobileStateFilter = new IntentFilter(
                TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mReceiver, intentFilter);
        // caused by we could switch the apn on status bar, so we need register this observer.
        if (FeatureQuery.FEATURE_NOTIFICATION_BAR_NETWORK_CHOOSE) {
            getContentResolver().registerContentObserver(PREFERAPN_URI, true, mPreferApnObserver);
        }
    }

    @Override
    protected void onDestroy() {
        if (FeatureQuery.FEATURE_NOTIFICATION_BAR_NETWORK_CHOOSE
                && mPreferApnObserver != null) {
            getContentResolver().unregisterContentObserver(mPreferApnObserver);
        }
        super.onDestroy();

        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mSubscription = intent.getIntExtra(SelectSubscription.SUBSCRIPTION_KEY,
                        MSimTelephonyManager.getDefault().getDefaultSubscription());
        if (DEBUG) Log.d(TAG,"onNewIntent mSubscription="+mSubscription);
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mMobileStateReceiver, mMobileStateFilter);

        if (!mRestoreDefaultApnMode) {
            fillList();
        } else {
            showDialog(DIALOG_RESTORE_DEFAULTAPN);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        removeDialog(DIALOG_RESTORE_DEFAULTAPN);

        unregisterReceiver(mMobileStateReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private boolean isAirplaneOn() {
        return Settings.System.getInt(getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;
    }

    //get apn property to judge if it has been loaded
    private boolean isAPNNumericLoaded(){
        String property = mSubscription == 0 ? TelephonyProperties.PROPERTY_APN_SIM_OPERATOR_NUMERIC :
                TelephonyProperties.PROPERTY_APN_SIM2_OPERATOR_NUMERIC;
        String mccMncFromSim = MSimTelephonyManager.getTelephonyProperty(property, null);

        return !((null == mccMncFromSim) || mccMncFromSim.equals(""));
    }


    private void fillList() {
        String where = getOperatorNumericSelection();
        Cursor cursor = getContentResolver().query(Telephony.Carriers.CONTENT_URI, new String[] {
                "_id", "name", "apn", "type"}, where, null,
                Telephony.Carriers.DEFAULT_SORT_ORDER);

        PreferenceGroup apnList = (PreferenceGroup) findPreference("apn_list");
        apnList.removeAll();

        if (where.equals("")){
            return;
        }

        ArrayList<String> apnKeyList = new ArrayList<String>();
        ArrayList<Preference> mmsApnList = new ArrayList<Preference>();

        mSelectedKey = getSelectedApnKey();

        int defaultSub = TelephonyManager.getDefault().isMultiSimEnabled() ? MSimTelephonyManager.getDefault().getPreferredDataSubscription() : MSimConstants.DEFAULT_SUBSCRIPTION;
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            String name = cursor.getString(NAME_INDEX);
            String apn = cursor.getString(APN_INDEX);
            String key = cursor.getString(ID_INDEX);
            String type = cursor.getString(TYPES_INDEX);

            //remove AGPS for china union
            if ( FeatureQuery.FEATURE_HIDE_CHINAUNION_SUPL
                    && (CHINA_UNION_PLMN.equals(MSimTelephonyManager.getDefault().getSimOperator(mSubscription)) || (historyMccMncFromSim != null && historyMccMncFromSim.equals(CHINA_UNION_PLMN)) )
                    && type.equals("supl") ){
                cursor.moveToNext();
                continue;
            }

            apnKeyList.add(key);

            ApnPreference pref = new ApnPreference(this);
            pref.setSubscription(mSubscription);

            if (name.contains("ro.")){
                //for china union
                if (CHINA_UNION_PLMN.equals(MSimTelephonyManager.getDefault().getSimOperator(mSubscription))
                        ||(historyMccMncFromSim != null && historyMccMncFromSim.equals(CHINA_UNION_PLMN))){
                    if (type.equals("default")){
                        if (name.contains("wap")){
                            name = getString(R.string.china_union_wap_apn_name);
                        }else{
                            name = getString(R.string.china_union_net_apn_name);
                        }
                    }
                    if (type.equals("mms")) name = getString(R.string.china_union_mms_apn_name);
                    if (type.equals("supl")) name = getString(R.string.china_union_supl_apn_name);
                    pref.setIsDefault(true);
                }
            }

            //for china telecom,if user added a apn like this, we also change the name
            if(name.equals(CT_DEFAULT_NAME)){
                if (apn.equalsIgnoreCase(CTNET)){
                    name = getString(R.string.china_telecom_net_apn_name);
                }else if (apn.equalsIgnoreCase(CTWAP)){
                    name = getString(R.string.china_telecom_wap_apn_name);
                }
            }

            pref.setKey(key);
            pref.setTitle(name);
            pref.setSummary(apn);
            pref.setPersistent(false);
            pref.setOnPreferenceChangeListener(this);

            boolean selectable = ((type == null) || !type.equals("mms"));
            pref.setSelectable(selectable);
            if (selectable) {
                // if device is airplane mode or APNNumeric is not loaded, the radiobutton should be gray.
                if (isAirplaneOn() || !isAPNNumericLoaded()) {
                    pref.setClickable(false);
                }else {
                    pref.setClickable(defaultSub == mSubscription);
                    if(defaultSub == mSubscription){
                        if((mSelectedKey == null && null != type && type.contains("default") && null!=apn && !apn.contains("wap"))
                                ||((mSelectedKey != null) && mSelectedKey.equals(key))) {
                            pref.setChecked();
                        }
                    }
                }
                apnList.addPreference(pref);
            } else {
                mmsApnList.add(pref);
            }
            cursor.moveToNext();
        }
        cursor.close();

        for (Preference preference : mmsApnList) {
            apnList.addPreference(preference);
        }
        // if device is airplane mode or APNNumeric is not loaded, the all items should be gray.
        if (isAirplaneOn() || !isAPNNumericLoaded()) {
            apnList.setEnabled(false);
        }else{
            apnList.setEnabled(true);
        }

        //if mSelectedKey not in current slot's apn list, reset mSelectedKey and save it in preference,
        //and then refresh the page of apnlist.
        if(defaultSub == mSubscription && !"-1".equals(mSelectedKey)
                && null != mSelectedKey && !apnKeyList.contains(mSelectedKey)){
            setSelectedApnKey("-1");
            fillList();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        boolean enable = !isAirplaneOn() && isAPNNumericLoaded();
        menu.add(0, MENU_NEW, 0,
                getResources().getString(R.string.menu_new))
                .setIcon(android.R.drawable.ic_menu_add)
                .setEnabled(enable);
        menu.add(0, MENU_RESTORE, 0,
                getResources().getString(R.string.menu_restore))
                .setIcon(android.R.drawable.ic_menu_upload)
                .setEnabled(enable);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // If airplane mode is on, set options menu items disable.
        // Restrict user to new APN or reset to default.
        boolean enable = !isAirplaneOn() && isAPNNumericLoaded();
        menu.findItem(MENU_NEW).setEnabled(enable);
        menu.findItem(MENU_RESTORE).setEnabled(enable);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_NEW:
            addNewApn();
            return true;

        case MENU_RESTORE:
            restoreDefaultApn();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void addNewApn() {
        Intent intent = new Intent(Intent.ACTION_INSERT, Telephony.Carriers.CONTENT_URI);
        intent.putExtra(OPERATOR_NUMERIC_EXTRA, getOperatorNumeric()[0]);
        intent.putExtra("fromOptionMenu", true);
        startActivity(intent);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        int pos = Integer.parseInt(preference.getKey());
        Uri url = ContentUris.withAppendedId(Telephony.Carriers.CONTENT_URI, pos);
        Intent intent = new Intent(Intent.ACTION_EDIT, url);
        intent.putExtra(SelectSubscription.SUBSCRIPTION_KEY,mSubscription);
        startActivity(intent);
        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (DEBUG) Log.d(TAG, "onPreferenceChange(): Preference - " + preference
                + ", newValue - " + newValue + ", newValue type - "
                + newValue.getClass());
        if (newValue instanceof String) {
            setSelectedApnKey((String) newValue);
        }

        return true;
    }

    private void setSelectedApnKey(String key) {
        mSelectedKey = key;
        ContentResolver resolver = getContentResolver();

        ContentValues values = new ContentValues();
        values.put(APN_ID, mSelectedKey);
        resolver.update(PREFERAPN_URI, values, null, null);
    }

    private String getSelectedApnKey() {
        String key = null;

        Cursor cursor = getContentResolver().query(PREFERAPN_URI, new String[] {"_id"},
                null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            key = cursor.getString(ID_INDEX);
        }
        cursor.close();
        return key;
    }

    private boolean restoreDefaultApn() {
        showDialog(DIALOG_RESTORE_DEFAULTAPN);
        mRestoreDefaultApnMode = true;

        if (mRestoreApnUiHandler == null) {
            mRestoreApnUiHandler = new RestoreApnUiHandler();
        }

        if (mRestoreApnProcessHandler == null) {
            HandlerThread restoreDefaultApnThread = new HandlerThread(
                    "Restore default APN Handler: Process Thread");
            restoreDefaultApnThread.start();
            mRestoreApnProcessHandler = new RestoreApnProcessHandler(
                    restoreDefaultApnThread.getLooper(), mRestoreApnUiHandler);
        }

        mRestoreApnProcessHandler
                .sendEmptyMessage(EVENT_RESTORE_DEFAULTAPN_START);
        return true;
    }

    private class RestoreApnUiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RESTORE_DEFAULTAPN_COMPLETE:
                    fillList();
                    getPreferenceScreen().setEnabled(true);
                    mRestoreDefaultApnMode = false;
                    dismissDialog(DIALOG_RESTORE_DEFAULTAPN);
                    Toast.makeText(
                        ApnSettings.this,
                        getResources().getString(
                                R.string.restore_default_apn_completed),
                        Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

    private class RestoreApnProcessHandler extends Handler {
        private Handler mRestoreApnUiHandler;

        public RestoreApnProcessHandler(Looper looper, Handler restoreApnUiHandler) {
            super(looper);
            this.mRestoreApnUiHandler = restoreApnUiHandler;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RESTORE_DEFAULTAPN_START:
                    ContentResolver resolver = getContentResolver();
                    Uri.Builder builder = DEFAULTAPN_URI.buildUpon();

                    // Transfer slot information to provider.
                    String subscription = String.valueOf(mSubscription);
                    builder.appendQueryParameter(MSimConstants.SUBSCRIPTION_KEY, subscription);

                    // Restore the APN with the numerics.
                    String where = Telephony.Carriers.NUMERIC + "=?" + " or "
                            + Telephony.Carriers.NUMERIC + "=?";
                    resolver.delete(builder.build(), where, getOperatorNumeric());

                    mRestoreApnUiHandler
                        .sendEmptyMessage(EVENT_RESTORE_DEFAULTAPN_COMPLETE);
                    break;
            }
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_RESTORE_DEFAULTAPN) {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setMessage(getResources().getString(R.string.restore_default_apn));
            dialog.setCancelable(false);
            return dialog;
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (id == DIALOG_RESTORE_DEFAULTAPN) {
            getPreferenceScreen().setEnabled(false);
        }
    }

    private String getOperatorNumericSelection() {
        String[] mccmncs = getOperatorNumeric();
        String where;
        where = (mccmncs[0] != null) ? "numeric=\"" + mccmncs[0] + "\"" : "";
        where += (mccmncs[1] != null) ? " or numeric=\"" + mccmncs[1] + "\"" : "";
        if (DEBUG) Log.d(TAG, "getOperatorNumericSelection: " + where);
        return where;
    }

    private String[] getOperatorNumeric() {
        ArrayList<String> result = new ArrayList<String>();
        if (mUseNvOperatorForEhrpd) {
            String mccMncForEhrpd = SystemProperties.get("ro.cdma.home.operator.numeric", null);
            if (mccMncForEhrpd != null && mccMncForEhrpd.length() > 0) {
                result.add(mccMncForEhrpd);
            }
        }

        String property = mSubscription == 0 ? TelephonyProperties.PROPERTY_APN_SIM_OPERATOR_NUMERIC :
                                               TelephonyProperties.PROPERTY_APN_SIM2_OPERATOR_NUMERIC;
        String mccMncFromSim = MSimTelephonyManager.getTelephonyProperty(property, null);

        // If the value of mccMncFromSim is null, mccMncFromSim should use the value of historyMccMncFromSim.
        if (mccMncFromSim != null && mccMncFromSim.length() > 0) {
            historyMccMncFromSim = mccMncFromSim;
        }else{
            if (historyMccMncFromSim == null) {
                return result.toArray(new String[2]);
            }else {
                mccMncFromSim = historyMccMncFromSim;
            }
        }
        result.add(mccMncFromSim);
        return result.toArray(new String[2]);
    }
}
