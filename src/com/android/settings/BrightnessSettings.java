/*
* Copyright (c) 2012,The Linux Foundation. All Rights Reserved.
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

package com.android.settings;

import com.android.settings.inputmethod.CheckBoxAndSettingsPreference;
import com.android.settings.inputmethod.CheckBoxAndSettingsPreference.Callbacks;

import android.provider.Settings;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.DialogPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.qualcomm.cabl.ICABLService;

import android.os.Handler;
import android.os.Message;

public class BrightnessSettings extends PreferenceActivity  implements Preference.OnPreferenceChangeListener{
    private final static String TAG = "BrightnessSettings";
    private final static int MODE_NORMAL = 0;
    private final static int MODE_ENHANCED = 1;

    /**
     * content adaptive backlight settings
     */
    private final static String KEY_ENABLE_ENHANCED_BRIGHTNESS = "cabl_brightness";
    private final static String KEY_ENABLE_NORMAL_BRIGHTNESS = "normal_brightness";
    private static final String KEY_BRIGHTNESS = "brightness";
    private static final String KEY_CABL_LEVEL_DIALOG = "cabl_dialog_pref";

    private static final String CABL_PACKAGE = "com.qualcomm.cabl";
    private static final String CABL_PREFS_CLASS = "com.qualcomm.cabl.CABLPreferences";

    private static final String BRIGHTNESS_PACKAGE = "com.android.settings";
    private static final String BRIGHTNESS_PREFS_CLASS = "com.android.settings.BrightnessPreference";

    private static final String NORMAL_SETTINGS = "Normal Brightness";
    private static final String ENHANCED_SETTINGS = "Enhanced Brightness";


    CheckBoxAndSettingsPreference mEnableNormalPreference;
    CheckBoxAndSettingsPreference mEnableEnhancedPreference;
    private BrightnessPreference mBrightnessPreference;
    private CABLDialogPreference mCABLDlgPreference;

    //used to store mode values
    SharedPreferences mPref = null;

    private static boolean mCablAvailable;
    private static boolean mTempDisableCabl;


    //added to invoke CABL related functions
    static ICABLService mCABLService = null;
    CABLServiceConnection mCABLServiceConn = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Replace PreferenceActivity's default Contentview, because its layout
        // will be added some paddings according to resolution
        setContentView(R.layout.brightness_settings_screen);
        addPreferencesFromResource(R.xml.brightness_settings);

        mPref = PreferenceManager.getDefaultSharedPreferences(this);
        PreferenceManager.setDefaultValues(this, R.xml.brightness_settings, false);
        Log.d(TAG, "BrightnessSettings,entering");


        /**
         * disable CABL if it is running
         */
        mCablAvailable = SystemProperties.getBoolean("ro.qualcomm.cabl", false);
        mTempDisableCabl = false;

        mBrightnessPreference = (BrightnessPreference) findPreference(KEY_BRIGHTNESS);
        mCABLDlgPreference = (CABLDialogPreference)findPreference(KEY_CABL_LEVEL_DIALOG);

        Intent intent = new Intent();

        mEnableNormalPreference = (CheckBoxAndSettingsPreference) this.findPreference(KEY_ENABLE_NORMAL_BRIGHTNESS);
        mEnableEnhancedPreference = (CheckBoxAndSettingsPreference) this.findPreference(KEY_ENABLE_ENHANCED_BRIGHTNESS);

        mEnableNormalPreference.setFragmentIntent(null, intent);//ensure settings button visible
        mEnableNormalPreference.setCallbacks(new Callbacks(){

            @Override
            public void onCheckBoxClicked() {
                final boolean checked = mEnableNormalPreference.isChecked();

                if (!checked) {
                    // Must sure one of the settings is checked, so it's can't be unchecked
                    mEnableNormalPreference.setChecked(true);
                    return;
                }

                // Make sure both settings can not be checked at the same time
                mEnableEnhancedPreference.setChecked(false);
                if (mCablAvailable
                        && SystemProperties.get("init.svc.ppd").equals(
                                "running")) {
                    if (null != mCABLService) {

                        // Create a new thread execution
                        // mCABLService.stopCABL().
                        // sleep will execution in the this thread.dosen't
                        // Obstruction main thread.
                        new Thread() {
                            public void run() {
                                try {
                                    Log.d(TAG, "stopCABL");
                                    boolean result = mCABLService.stopCABL();
                                    //0-disable cabl 1-enable cabl
                                    Settings.System.putInt(BrightnessSettings.this
                                            .getBaseContext().getContentResolver(),
                                             Settings.System.CABL_ENABLED,
                                            !result?1:0);
                                } catch (RemoteException e) {
                                    Log.e(TAG, "stopCABL, exception");
                                }
                            }
                        }.start();
                    }

                }
            }

            @Override
            public void onSettingsButtonClicked(View arg0) {
                if(null != mBrightnessPreference){
                    mBrightnessPreference.showDialog(null);
                }


            }

        });



        mEnableEnhancedPreference.setFragmentIntent(null, intent);
        mEnableEnhancedPreference.setCallbacks(new Callbacks(){

            @Override
            public void onCheckBoxClicked() {
                final boolean checked = mEnableEnhancedPreference.isChecked();

                if (!checked) {
                    // Make sure one the settings is checked, so it's can't be unchecked
                    mEnableEnhancedPreference.setChecked(true);
                    return;
                }

                // Make sure both settings can not be checked at the same time
                mEnableNormalPreference.setChecked(false);
                if (mCablAvailable) {
                    if (null != mCABLService) {
                        // Create a new thread execution
                        // mCABLService.startCABL().
                        // sleep will execution in the this thread.dosen't
                        // Obstruction main thread.
                        new Thread() {

                            public void run() {
                                try {
                                    Log.d(TAG, "startCABL");
                                    boolean result = mCABLService.startCABL();
                                    //0-disable cabl 1-enable cabl
                                    Settings.System.putInt(BrightnessSettings.this
                                            .getBaseContext().getContentResolver(),
                                            Settings.System.CABL_ENABLED,
                                            result?1:0);
                                } catch (RemoteException e) {
                                    Log.e(TAG, "startCABL, exception");
                                }
                            }
                        }.start();

                    }

                }

            }

            @Override
            public void onSettingsButtonClicked(View arg0) {

                mCABLDlgPreference.showDialog(null);
            }

        });


        initCABLService();
        Log.d(TAG, "BrightnessSettings,ending");
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean cablEnabled = (1 == android.provider.Settings.System.getInt(this.getContentResolver(),
                Settings.System.CABL_ENABLED, 0));
        //set default value for CABL check status
        if(null != mEnableEnhancedPreference){
            mEnableEnhancedPreference.setChecked(cablEnabled);
        }

        if(null != mEnableNormalPreference){
            mEnableNormalPreference.setChecked(!cablEnabled);
        }
    }

    private void setCABLStateOnResume(){
        boolean status = (1 == android.provider.Settings.System.getInt(this.getContentResolver(),
                Settings.System.CABL_ENABLED, 0));
        Log.d(TAG, "CABL services status = "+status );
        try{
            if (null != mCABLService)
                mCABLService.setCABLStateOnResume(status);
        }catch(RemoteException e){
            Log.e(TAG, "CABL services setCABLStateOnResume exception");
        }
    }


    private class CABLServiceConnection implements ServiceConnection{

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mCABLService = ICABLService.Stub.asInterface((IBinder)service);
            Log.d(TAG, "onServiceConnected, service=" + mCABLService);
            CABLDialogPreference.setCABLService(mCABLService);
            setCABLStateOnResume();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if(null != mCABLService){
                //end thread
                try{
                    Log.d(TAG, "stopListener");
                    mCABLService.stopListener();
                }catch(RemoteException e){
                    Log.e(TAG, "stopListener, exception");
                }
            }
            mCABLService = null;
            CABLDialogPreference.setCABLService(null);
        }

    }

    private void initCABLService(){
        Log.d(TAG, "initCABLService");
        //we don't need brightness UI here
        if(null != mBrightnessPreference){
            this.getPreferenceScreen().removePreference(mBrightnessPreference);
        }

        if(null != mCABLDlgPreference){
            this.getPreferenceScreen().removePreference(mCABLDlgPreference);
        }

        mCABLServiceConn = new CABLServiceConnection();
        Intent i = new Intent(ICABLService.class.getName());
        boolean ret = bindService(i, mCABLServiceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        unbindService(mCABLServiceConn);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // TODO Auto-generated method stub
        return false;
    }

}
