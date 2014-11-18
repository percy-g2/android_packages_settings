/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution. Apache license notifications and license are retained
 * for attribution purposes only.
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
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.location.LocationManager;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import java.util.Observable;
import java.util.Observer;

/**
 * Gesture lock pattern settings.
 */
public class LocationSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    // Location Settings
    private static final String KEY_LOCATION_TOGGLE = "location_toggle";
    private static final String KEY_LOCATION_NETWORK = "location_network";
    private static final String KEY_LOCATION_GPS = "location_gps";
    private static final String KEY_ASSISTED_GPS = "assisted_gps";

    private CheckBoxPreference mNetwork;
    private CheckBoxPreference mGps;
    private CheckBoxPreference mAssistedGps;
    private SwitchPreference mLocationAccess;

    // These provide support for receiving notification when Location Manager settings change.
    // This is necessary because the Network Location Provider can change settings
    // if the user does not confirm enabling the provider.
    private ContentQueryMap mContentQueryMap;

    private Observer mSettingsObserver;

    @Override
    public void onStart() {
        super.onStart();
        // listen for Location Manager settings changes
        Cursor settingsCursor = getContentResolver().query(Settings.Secure.CONTENT_URI, null,
                "(" + Settings.System.NAME + "=?)",
                new String[]{Settings.Secure.LOCATION_PROVIDERS_ALLOWED},
                null);
        mContentQueryMap = new ContentQueryMap(settingsCursor, Settings.System.NAME, true, null);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mSettingsObserver != null) {
            mContentQueryMap.deleteObserver(mSettingsObserver);
        }
    }

    private PreferenceScreen createPreferenceHierarchy() {
        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.location_settings);
        root = getPreferenceScreen();

        mLocationAccess = (SwitchPreference) root.findPreference(KEY_LOCATION_TOGGLE);
        mNetwork = (CheckBoxPreference) root.findPreference(KEY_LOCATION_NETWORK);
        mGps = (CheckBoxPreference) root.findPreference(KEY_LOCATION_GPS);
        mAssistedGps = (CheckBoxPreference) root.findPreference(KEY_ASSISTED_GPS);

        mLocationAccess.setOnPreferenceChangeListener(this);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Make sure we reload the preference hierarchy since some of these settings
        // depend on others...
        createPreferenceHierarchy();
        updateLocationToggles();

        if (mSettingsObserver == null) {
            mSettingsObserver = new Observer() {
                public void update(Observable o, Object arg) {
                    updateLocationToggles();
                }
            };
        }

        mContentQueryMap.addObserver(mSettingsObserver);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        final ContentResolver cr = getContentResolver();
        if (preference == mNetwork) {
            Settings.Secure.setLocationProviderEnabled(cr,
                    LocationManager.NETWORK_PROVIDER, mNetwork.isChecked());
        } else if (preference == mGps) {
            boolean enabled = mGps.isChecked();
            Settings.Secure.setLocationProviderEnabled(cr,
                    LocationManager.GPS_PROVIDER, enabled);
            if (mAssistedGps != null) {
                mAssistedGps.setEnabled(enabled);
            }
        } else if (preference == mAssistedGps) {
            Settings.Secure.putInt(cr, Settings.Secure.ASSISTED_GPS_ENABLED,
                    mAssistedGps.isChecked() ? 1 : 0);
        } else {
            // If we didn't handle it, let preferences handle it.
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        return true;
    }

    /*
     * Creates toggles for each available location provider
     */
    private void updateLocationToggles() {
        ContentResolver res = getContentResolver();
        boolean gpsEnabled = Settings.Secure.isLocationProviderEnabled(
                res, LocationManager.GPS_PROVIDER);
        boolean networkEnabled = Settings.Secure.isLocationProviderEnabled(
                res, LocationManager.NETWORK_PROVIDER);
        mGps.setChecked(gpsEnabled);
        // Set network checkbox status.
        // Porting from Jellybean 4.2.
        mNetwork.setChecked(networkEnabled);
        mLocationAccess.setChecked(gpsEnabled || networkEnabled);
        if (mAssistedGps != null) {
            mAssistedGps.setChecked(Settings.Secure.getInt(res,
                    Settings.Secure.ASSISTED_GPS_ENABLED, 2) == 1);
            mAssistedGps.setEnabled(gpsEnabled);
        }
    }

    /**
     * see confirmPatternThenDisableAndClear
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        createPreferenceHierarchy();
    }

    /** Enable or disable all providers when the master toggle is changed. */
    private void onToggleLocationAccess(boolean checked) {
        final ContentResolver cr = getContentResolver();
        Settings.Secure.setLocationProviderEnabled(cr,
                LocationManager.GPS_PROVIDER, checked);
        // Set Network access with Location access status.
        // Porting from Jellybean 4.2.
        Settings.Secure.setLocationProviderEnabled(cr,
                LocationManager.NETWORK_PROVIDER, checked);

        updateLocationToggles();
    }

    /**
     * When the user turn on location access, pop up a dialog to let user choose
     * whether allow Google service collect user location data.
     */
    @Override
    public boolean onPreferenceChange(Preference pref, Object newValue) {
        final boolean chooseValue = (Boolean) newValue;
        if (pref.getKey().equals(KEY_LOCATION_TOGGLE)) {
            if (chooseValue && getActivity() != null) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.location_access_google_service_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.location_access_google_service_text)
                        .setPositiveButton(
                                R.string.location_access_google_service_agree,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        updateGoogleServiceAccess(true);
                                    }
                                })
                        .setNegativeButton(
                                R.string.location_access_google_service_disagree,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        updateGoogleServiceAccess(false);
                                    }
                                })
                        .create()
                        .show();
            } else {
                updateGoogleServiceAccess(false);
            }
            onToggleLocationAccess(chooseValue);
        }
        return true;
    }

    /**
     * Enable or disable google service to collect anonymous location data.
     */
    private void updateGoogleServiceAccess(boolean newValue) {
        ContentResolver cr = getContentResolver();
        Settings.Secure.setLocationProviderEnabled(cr,
                LocationManager.NETWORK_PROVIDER, newValue);
        mNetwork.setChecked(newValue);
    }

}

class WrappingSwitchPreference extends SwitchPreference {

    public WrappingSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public WrappingSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        TextView title = (TextView) view.findViewById(android.R.id.title);
        if (title != null) {
            title.setSingleLine(false);
            title.setMaxLines(3);
        }
    }
}

class WrappingCheckBoxPreference extends CheckBoxPreference {

    public WrappingCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public WrappingCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        TextView title = (TextView) view.findViewById(android.R.id.title);
        if (title != null) {
            title.setSingleLine(false);
            title.setMaxLines(3);
        }
    }
}
