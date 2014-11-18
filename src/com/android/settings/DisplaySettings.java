/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (c) 2011-2013, The Linux Foundation. All rights reserved.
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

import com.qrd.plugin.feature_query.FeatureQuery;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.ActivityManagerNative;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

import com.android.internal.view.RotationPolicy;
import com.android.settings.DreamSettings;

import java.util.ArrayList;

import java.io.IOException;
import java.io.FileOutputStream;


public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_BRIGHTNESS_SETTINGS = "brightness_settings";
    private static final String KEY_BRIGHTNESS = "brightness";
    private static final String KEY_SCREEN_SAVER = "screensaver";
    private static final String KEY_LOGO_LIGHT_TURNON = "logo_light_turnon";

    private BrightnessPreference mBrightnessPreference;
    private Preference mBrightnessSettingsPreference;
    private CheckBoxPreference mAccelerometer;
    private ListPreference mFontSizePref;
    private CheckBoxPreference mNotificationPulse;

    private CheckBoxPreference mLogoLightTurnOn;

    private final Configuration mCurConfig = new Configuration();
    
    private ListPreference mScreenTimeoutPreference;
    private Preference mScreenSaverPreference;

    private final RotationPolicy.RotationPolicyListener mRotationPolicyListener =
            new RotationPolicy.RotationPolicyListener() {
        @Override
        public void onChange() {
            updateAccelerometerRotationCheckbox();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getActivity().getContentResolver();

        if ("Y".equals(android.os.SystemProperties.get("ro.ft.feature.logo"))){
            addPreferencesFromResource(R.xml.display_settings);
        }
        else {
            addPreferencesFromResource(R.xml.display_settings_no_logo);
        }

        //add content adaptive backlight support
        mBrightnessPreference = (BrightnessPreference) findPreference(KEY_BRIGHTNESS);
        mBrightnessSettingsPreference = (Preference) findPreference(KEY_BRIGHTNESS_SETTINGS);
        if (FeatureQuery.FEATURE_SETTINGS_USE_CONTENT_ADAPTIVE_BACKLIGHT) {
            this.getPreferenceScreen().removePreference(mBrightnessPreference);
        } else {
            this.getPreferenceScreen().removePreference(mBrightnessSettingsPreference);
        }
        
        mAccelerometer = (CheckBoxPreference) findPreference(KEY_ACCELEROMETER);
        mAccelerometer.setPersistent(false);
        if (RotationPolicy.isRotationLockToggleSupported(getActivity())) {
            // If rotation lock is supported, then we do not provide this option in
            // Display settings.  However, is still available in Accessibility settings.
            getPreferenceScreen().removePreference(mAccelerometer);
        }

        mScreenSaverPreference = findPreference(KEY_SCREEN_SAVER);
        if (mScreenSaverPreference != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_enableDreams) == false) {
            getPreferenceScreen().removePreference(mScreenSaverPreference);
        }
        
        mScreenTimeoutPreference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        final long currentTimeout = Settings.System.getLong(resolver, SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);
        disableUnusableTimeouts(mScreenTimeoutPreference);
        updateTimeoutPreferenceDescription(currentTimeout);

        mFontSizePref = (ListPreference) findPreference(KEY_FONT_SIZE);
        mFontSizePref.setOnPreferenceChangeListener(this);
        mNotificationPulse = (CheckBoxPreference) findPreference(KEY_NOTIFICATION_PULSE);
        if (mNotificationPulse != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_intrusiveNotificationLed) == false) {
            getPreferenceScreen().removePreference(mNotificationPulse);
        } else {
            try {
                mNotificationPulse.setChecked(Settings.System.getInt(resolver,
                        Settings.System.NOTIFICATION_LIGHT_PULSE) == 1);
                mNotificationPulse.setOnPreferenceChangeListener(this);
            } catch (SettingNotFoundException snfe) {
                Log.e(TAG, Settings.System.NOTIFICATION_LIGHT_PULSE + " not found");
            }
        }

        if ("Y".equals(android.os.SystemProperties.get("ro.ft.feature.logo"))) {
                mLogoLightTurnOn = (CheckBoxPreference) findPreference(KEY_LOGO_LIGHT_TURNON);
                mLogoLightTurnOn.setPersistent(false);
                mLogoLightTurnOn.setChecked(Settings.System.getInt(resolver, Settings.System.LOGO_LIGHT_TURN_ON, 1) != 0);
        }
    }

    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        ListPreference preference = mScreenTimeoutPreference;
        String summary;
        if (currentTimeout == -1) {
            summary = preference.getContext().getString(R.string.screensaver_timeout_zero_summary);
        } else if (currentTimeout < -1) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            int best = 0;
            for (int i = 0; i < values.length; i++) {
                long timeout = Long.parseLong(values[i].toString());
                if ((currentTimeout >= timeout)&&(timeout > 0)) {
                    best = i;
                }
            }
            summary = preference.getContext().getString(R.string.screen_timeout_summary,
                    entries[best]);
        }
        preference.setSummary(summary);
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.parseLong(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    int floatToIndex(float val) {
        String[] indices = getResources().getStringArray(R.array.entryvalues_font_size);
        float lastVal = Float.parseFloat(indices[0]);
        for (int i=1; i<indices.length; i++) {
            float thisVal = Float.parseFloat(indices[i]);
            if (val < (lastVal + (thisVal-lastVal)*.5f)) {
                return i-1;
            }
            lastVal = thisVal;
        }
        return indices.length-1;
    }
    
    public void readFontSizePreference(ListPreference pref) {
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to retrieve font size");
        }

        // mark the appropriate item in the preferences list
        int index = floatToIndex(mCurConfig.fontScale);
        pref.setValueIndex(index);

        // report the current size in the summary text
        final Resources res = getResources();
        String[] fontSizeNames = res.getStringArray(R.array.entries_font_size);
        pref.setSummary(String.format(res.getString(R.string.summary_font_size),
                fontSizeNames[index]));
    }
    
    @Override
    public void onResume() {
        super.onResume();

        updateState();

        RotationPolicy.registerRotationPolicyListener(getActivity(),
                mRotationPolicyListener);
    }

    @Override
    public void onPause() {
        super.onPause();

        RotationPolicy.unregisterRotationPolicyListener(getActivity(),
                mRotationPolicyListener);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        readFontSizePreference(mFontSizePref);
    }

    private void updateState() {
        updateAccelerometerRotationCheckbox();
        readFontSizePreference(mFontSizePref);
        updateScreenSaverSummary();
    }

    private void updateScreenSaverSummary() {
        mScreenSaverPreference.setSummary(
            DreamSettings.isScreenSaverEnabled(mScreenSaverPreference.getContext())
                ? R.string.screensaver_settings_summary_on
                : R.string.screensaver_settings_summary_off);
    }

    private void updateAccelerometerRotationCheckbox() {
        if (getActivity() == null) return;

        mAccelerometer.setChecked(!RotationPolicy.isRotationLocked(getActivity()));
    }

    public void writeFontSizePreference(Object objValue) {
        try {
            mCurConfig.fontScale = Float.parseFloat(objValue.toString());
            ActivityManagerNative.getDefault().updatePersistentConfiguration(mCurConfig);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to save font size");
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mAccelerometer) {
            RotationPolicy.setRotationLockForAccessibility(
                    getActivity(), !mAccelerometer.isChecked());
        } else if (preference == mNotificationPulse) {
            boolean value = mNotificationPulse.isChecked();
            Settings.System.putInt(getContentResolver(), Settings.System.NOTIFICATION_LIGHT_PULSE,
                    value ? 1 : 0);
            return true;
        } else if (preference == mBrightnessSettingsPreference) {
            return false;//the xml reference will have effect now.
        } else if (preference == mLogoLightTurnOn && "Y".equals(android.os.SystemProperties.get("ro.ft.feature.logo"))) {
            boolean value = mLogoLightTurnOn.isChecked();

            try { 
                if (value) {
                    Log.d(TAG, "logo turnon=" + value);
                    writeFile("/sys/class/leds/button-backlight/brightness", "102");
                }
                else {
                    Log.d(TAG, "logo turnon=" + value);
                    writeFile("/sys/class/leds/button-backlight/brightness", "101");
                }

                Settings.System.putInt(getContentResolver(), Settings.System.LOGO_LIGHT_TURN_ON, value ? 1 : 0);
            } catch (Exception e) {
			    Log.e(TAG, "write button-backlight brightness: " + e);
            }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            int oldvalue = Integer.parseInt(((ListPreference)preference).getValue());
            if (value != oldvalue) {
                Log.d(TAG, "update screen timeout from "+ oldvalue + " to " + value);
                try {
                    Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                    updateTimeoutPreferenceDescription(value);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "could not persist screen timeout setting", e);
                }
            }
        }
        if (KEY_FONT_SIZE.equals(key)) {
            writeFontSizePreference(objValue);
        }

        return true;
    }

    private void writeFile(String path, String data) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(path);
            fos.write(data.getBytes());
        } catch (IOException e) {
            Log.w(TAG, "Unable to write " + path);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                }
            }
        }
    }
}