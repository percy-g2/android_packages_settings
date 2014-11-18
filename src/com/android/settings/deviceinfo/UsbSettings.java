/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.settings.deviceinfo;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemProperties;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import com.qrd.plugin.feature_query.FeatureQuery;

/**
 * USB storage settings.
 */
public class UsbSettings extends SettingsPreferenceFragment {

    private static final String TAG = "UsbSettings";

    private static final String KEY_MTP = "usb_mtp";
    private static final String KEY_PTP = "usb_ptp";
    private static final String KEY_SDCARD = "usb_sdcard";
    private static final String KEY_CHARGING = "usb_charging";

    private UsbManager mUsbManager;
    private CheckBoxPreference mMtp;
    private CheckBoxPreference mPtp;
    private CheckBoxPreference mSDCard;
    private CheckBoxPreference mCharging;

    public static final String USB_FUNCTION_CHARGING = "charging";
    //this function string defined in init.qcom.usb.rc,
    //we take it as charge mode,enable some functions,
    //and set charge as default mode in msm7627a.mk
    private static final String CURRENT_FUNCTIONS = "mtp,diag,serial_smd,serial_tty,rmnet_smd,mass_storage,serial_smd";
    private static final String CURRENT_FUNCTIONS_ADB = "mtp,diag,serial_smd,serial_tty,rmnet_smd,mass_storage,serial_smd,adb";

    private StorageManager mStorageManager = null;

    private boolean mDestroyed;
    private boolean externalToastOn ;
    private boolean externalToastOff ;
    private boolean internalToastOn ;
    private boolean internalToastOff ;
    private static boolean mEnableUMS = false;
    // UI thread
    private Handler mUIHandler;

    private Handler mAsyncStorageHandler;
    static final boolean localLOGV = true;

    private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context content, Intent intent) {
            if (intent.getAction().equals(UsbManager.ACTION_USB_STATE)) {
                boolean connected = intent.getExtras().getBoolean(UsbManager.USB_CONNECTED);
                if (!connected) {
                    // It was disconnected from the plug, so finish
                    finish();
                } else {
                    //we need take only CURRENT_FUNCTIONS functions as charge mode,so add filter here
                      String functions = SystemProperties.get("persist.sys.usb.config", "");
                      if ( CURRENT_FUNCTIONS.equalsIgnoreCase(functions) || CURRENT_FUNCTIONS_ADB.equalsIgnoreCase(functions) ){
                          functions = USB_FUNCTION_CHARGING;
                      } else {
                          functions = mUsbManager.getDefaultFunction();
                      }
                      updateToggles(functions);
                      //Once us connected, need update the UMS status
                      clickUMS();
                }
            }
        }
    };

    private PreferenceScreen createPreferenceHierarchy() {
        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.usb_settings);
        root = getPreferenceScreen();

        mMtp = (CheckBoxPreference)root.findPreference(KEY_MTP);
        mPtp = (CheckBoxPreference)root.findPreference(KEY_PTP);
        mSDCard = (CheckBoxPreference)root.findPreference(KEY_SDCARD);
        mCharging = (CheckBoxPreference)root.findPreference(KEY_CHARGING);

        //if FeatureQuery.FEATURE_ADD_USB_MODE is false , should keep the original UsbSettings
        if(!FeatureQuery.FEATURE_ADD_USB_MODE){
            root.removePreference(mSDCard);
            root.removePreference(mCharging);
        }

        return root;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mUsbManager = (UsbManager)getSystemService(Context.USB_SERVICE);

        mUIHandler = new Handler();
        HandlerThread thr = new HandlerThread("SystemUI UsbSettings");
        thr.start();
        mAsyncStorageHandler = new Handler(thr.getLooper());
        if (mStorageManager == null) {
            mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            if (mStorageManager == null) {
                Log.w(TAG, "Failed to get StorageManager");
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mStateReceiver);

        if (mStorageManager != null && mStorageListener != null) {
            mStorageManager.unregisterListener(mStorageListener);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Make sure we reload the preference hierarchy since some of these settings
        // depend on others...
        createPreferenceHierarchy();

        // ACTION_USB_STATE is sticky so this will call updateToggles
        getActivity().registerReceiver(mStateReceiver,
                new IntentFilter(UsbManager.ACTION_USB_STATE));

        externalToastOn = false;
        externalToastOff = false;
        internalToastOn =false;
        internalToastOff =false;
        mStorageManager.registerListener(mStorageListener);

        switchDisplay(isAllStorageShared());
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        // Quit the looper, release the Handler thread.
        mAsyncStorageHandler.getLooper().quit();
        mDestroyed = true;
    }

    private void updateToggles(String function) {
        if (UsbManager.USB_FUNCTION_MTP.equals(function)) {
            mMtp.setChecked(true);
            mPtp.setChecked(false);
            mSDCard.setChecked(false);
            mCharging.setChecked(false);
        } else if (UsbManager.USB_FUNCTION_PTP.equals(function)) {
            mMtp.setChecked(false);
            mPtp.setChecked(true);
            mSDCard.setChecked(false);
            mCharging.setChecked(false);
        } else if (UsbManager.USB_FUNCTION_MASS_STORAGE.equals(function)) {
            mMtp.setChecked(false);
            mPtp.setChecked(false);
            mSDCard.setChecked(true);
            mCharging.setChecked(false);
        } else if (USB_FUNCTION_CHARGING.equals(function)){
            mMtp.setChecked(false);
            mPtp.setChecked(false);
            mSDCard.setChecked(false);
            mCharging.setChecked(true);
        } else  {
            mMtp.setChecked(false);
            mPtp.setChecked(false);
            mSDCard.setChecked(false);
            mCharging.setChecked(false);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {

        // Don't allow any changes to take effect as the USB host will be disconnected, killing
        // the monkeys
        if (Utils.isMonkeyRunning()) {
            return true;
        }
        // temporary hack - using check boxes as radio buttons
        // don't allow unchecking them
        if (preference instanceof CheckBoxPreference) {
            CheckBoxPreference checkBox = (CheckBoxPreference)preference;
            if (!checkBox.isChecked()) {
                checkBox.setChecked(true);
                return true;
            }
        }
        if (preference == mMtp) {
            mUsbManager.setCurrentFunction(UsbManager.USB_FUNCTION_MTP, true);
            updateToggles(UsbManager.USB_FUNCTION_MTP);
        } else if (preference == mPtp) {
            mUsbManager.setCurrentFunction(UsbManager.USB_FUNCTION_PTP, true);
            updateToggles(UsbManager.USB_FUNCTION_PTP);
        } else if (preference == mSDCard) {
            mUsbManager.setCurrentFunction(UsbManager.USB_FUNCTION_MASS_STORAGE, true);
            updateToggles(UsbManager.USB_FUNCTION_MASS_STORAGE);
        } else if (preference == mCharging) {
            mUsbManager.setCurrentFunction(CURRENT_FUNCTIONS,true);
            updateToggles(USB_FUNCTION_CHARGING);
        }
        //once switch mode, we disable screen,until operation completed
        getPreferenceScreen().setEnabled(false);
        return true;
    }

    private StorageEventListener mStorageListener = new StorageEventListener() {
        @Override
        public void onStorageStateChanged(String path, String oldState,
                String newState) {
            if (localLOGV)
                Log.i(TAG, "onStorageStateChanged path= " + path
                        + " oldState = " + oldState + " newState= " + newState);
            final boolean on = newState.equals(Environment.MEDIA_SHARED);
            final boolean off =oldState.equals(Environment.MEDIA_SHARED)&& newState.equals(Environment.MEDIA_UNMOUNTED);
            final boolean isExternalPath = (Environment.getExternalStorageDirectory().getPath().equals(path));

            if(on){
                if (isExternalPath && !externalToastOn) {
                    externalToastOn =true;
                    externalToastOff =false;
                    Toast.makeText(getActivity(), R.string.external_storage_turn_on, Toast.LENGTH_SHORT).show();
                }
                else if(!isExternalPath && !internalToastOn){
                    internalToastOn =true;
                    internalToastOff =false;
                    Toast.makeText(getActivity(), R.string.internal_storage_turn_on, Toast.LENGTH_SHORT).show();
                }
            }
            if(off){
                if (isExternalPath && !externalToastOff) {
                    externalToastOn =false;
                    externalToastOff = true;
                    Toast.makeText(getActivity(), R.string.external_storage_turn_off, Toast.LENGTH_SHORT).show();
                }
                else if(!isExternalPath && !internalToastOff) {
                    internalToastOn =false;
                    internalToastOff = true;
                    Toast.makeText(getActivity(), R.string.internal_storage_turn_off, Toast.LENGTH_SHORT).show();
                }
            }

            switchDisplay(isAllStorageShared());
        }
    };

    private boolean isAllStorageShared() {
        //NOTICE: some OEMs support auto-switch vold ,then Phone storage will be removable
        //You should assume all storages are removable
        final String ext_state = Environment.getExternalStorageState();
        final String int_state = Environment.getInternalStorageState();
        Log.d(TAG,"isAllStorageShared external-storage state:" + ext_state);
        Log.d(TAG,"isAllStorageShared internal-storage state:" + int_state);
        return (ext_state.equals(Environment.MEDIA_SHARED)
                   ||ext_state.equals(Environment.MEDIA_REMOVED)
                   ||ext_state.equals(Environment.MEDIA_BAD_REMOVAL)) &&
               (int_state.equals(Environment.MEDIA_SHARED)
                   ||int_state.equals(Environment.MEDIA_REMOVED)
                   ||int_state.equals(Environment.MEDIA_BAD_REMOVAL));
    }

    private void switchDisplay(final boolean usbStorageInUse) {
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                if(usbStorageInUse){
                   getPreferenceScreen().setEnabled(true);
                } else if (mEnableUMS){
                   getPreferenceScreen().setEnabled(false);
                } else {
                   getPreferenceScreen().setEnabled(true);
                }
            }
        });
    }

    //port  turn on/off UMS method from UsbStorageActivity.java
    private void switchUsbMassStorage(final boolean on) {
        // things to do elsewhere
        mAsyncStorageHandler.post(new Runnable() {
            @Override
            public void run() {
                if (on) {
                    mEnableUMS = true;
                    mStorageManager.enableUsbMassStorage();
                } else {
                    mEnableUMS = false;
                    mStorageManager.disableUsbMassStorage();
                }
            }
        });
    }

    private void clickUMS(){
        //we only support UMS when this feature is true
        if(!FeatureQuery.FEATURE_ADD_USB_MODE)
            return;
        //we turn on UMS in UsbDeviceManager, here just do turn off operation
        if ( !mSDCard.isChecked() && mStorageManager.isUsbMassStorageEnabled() == true) {
            if (localLOGV)
                Log.i(TAG, "Disabling UMS");
            switchUsbMassStorage(false);
        } else if (!mSDCard.isChecked()){
            //just need update UI if not switch UMS
            switchDisplay(isAllStorageShared());
        }
    }
}
