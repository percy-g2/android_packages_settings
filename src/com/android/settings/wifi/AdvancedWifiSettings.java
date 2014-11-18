/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2012 Thunder Software Technology Co.,Ltd. 
 * Copyright (c) 2012,The Linux Foundation. All rights reserved.
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

package com.android.settings.wifi;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.DialogPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.MccTable;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

/* Add 20120823 TS-FMC-V2 start */
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import com.qrd.plugin.feature_query.FeatureQuery;

/* Add 20120823 TS-FMC-V2 end */

public class AdvancedWifiSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener{

    private static final String TAG = "AdvancedWifiSettings";
    private static final String KEY_MAC_ADDRESS = "mac_address";
    private static final String KEY_CURRENT_IP_ADDRESS = "current_ip_address";
    private static final String KEY_FREQUENCY_BAND = "frequency_band";
    private static final String KEY_NOTIFY_OPEN_NETWORKS = "notify_open_networks";
    private static final String KEY_SLEEP_POLICY = "sleep_policy";
    private static final String KEY_POOR_NETWORK_DETECTION = "wifi_poor_network_detection";

/* Add 20120823 TS-FMC-V2 start */
    private static final String KEY_WAG_ADDRESS = "wag_address";
    private static final String MODIFY_WAG_ADDRESS_ACTION = "android.net.wifi.modify_wag_address";
    private static final String ENABLE_WAG_ADDRESS_BUTTON_ACTION = "android.ts.fmc.enable_wag_address_button";
    private static final String ENABLED = "enabled";
    private static final String SUMMARY = "summary";

    IntentFilter mFilter = new IntentFilter(ENABLE_WAG_ADDRESS_BUTTON_ACTION);
    private Preference mWagAddress;
/* Add 20120823 TS-FMC-V2 end */

    private WifiManager mWifiManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.wifi_advanced_settings);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
/* Add 20120823 TS-FMC-V2 start */
        if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
        mWagAddress = findPreference(KEY_WAG_ADDRESS);
        getActivity().registerReceiver(mReceiver, mFilter);
        }else {
        getPreferenceScreen().removePreference(findPreference(KEY_WAG_ADDRESS)); 
        }
       
/* Add 20120823 TS-FMC-V2 end */
    }

    @Override
    public void onResume() {
        super.onResume();
        initPreferences();
        refreshWifiInfo();
    }

/* Add 20120823 TS-FMC-V2 start */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
        getActivity().unregisterReceiver(mReceiver);
        }
    }
/* Add 20120823 TS-FMC-V2 end */

    private void initPreferences() {
        CheckBoxPreference notifyOpenNetworks =
            (CheckBoxPreference) findPreference(KEY_NOTIFY_OPEN_NETWORKS);
        notifyOpenNetworks.setChecked(Secure.getInt(getContentResolver(),
                Secure.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, 0) == 1);
        notifyOpenNetworks.setEnabled(mWifiManager.isWifiEnabled());

        CheckBoxPreference poorNetworkDetection =
            (CheckBoxPreference) findPreference(KEY_POOR_NETWORK_DETECTION);
        if (poorNetworkDetection != null) {
            if (Utils.isWifiOnly(getActivity())) {
                getPreferenceScreen().removePreference(poorNetworkDetection);
            } else {
                poorNetworkDetection.setChecked(Secure.getInt(getContentResolver(),
                        Secure.WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED, 1) == 1);
            }
        }

        ListPreference frequencyPref = (ListPreference) findPreference(KEY_FREQUENCY_BAND);
        if (mWifiManager.isDualBandSupported()) {
            frequencyPref.setOnPreferenceChangeListener(this);
            int value = mWifiManager.getFrequencyBand();
            if (value != -1) {
                frequencyPref.setValue(String.valueOf(value));
            } else {
                Log.e(TAG, "Failed to fetch frequency band");
            }
        } else {
            if (frequencyPref != null) {
                // null if it has already been removed before resume
                getPreferenceScreen().removePreference(frequencyPref);
            }
        }

        ListPreference sleepPolicyPref = (ListPreference) findPreference(KEY_SLEEP_POLICY);
        if (sleepPolicyPref != null) {
            if (Utils.isWifiOnly(getActivity())) {
                sleepPolicyPref.setEntries(R.array.wifi_sleep_policy_entries_wifi_only);
            }
            sleepPolicyPref.setOnPreferenceChangeListener(this);
            int value = Settings.System.getInt(getContentResolver(),
                    Settings.System.WIFI_SLEEP_POLICY,
                    Settings.System.WIFI_SLEEP_POLICY_NEVER);
            String stringValue = String.valueOf(value);
            sleepPolicyPref.setValue(stringValue);
            updateSleepPolicySummary(sleepPolicyPref, stringValue);
        }

        replaceWifiToWlan(frequencyPref);
        replaceWifiToWlan(sleepPolicyPref);
        String poorNetwok = Utils.replaceAllWiFi(poorNetworkDetection.getSummary().toString());
        poorNetworkDetection.setSummary(poorNetwok);
    }

    private void replaceWifiToWlan(ListPreference preference) {
        if(null == preference) return ;
        String prefString = Utils.replaceAllWiFi(preference.getTitle().toString());
        preference.setTitle(prefString);
        preference.setDialogTitle(prefString);
    }

    private void updateSleepPolicySummary(Preference sleepPolicyPref, String value) {
        if (value != null) {
            String[] values = getResources().getStringArray(R.array.wifi_sleep_policy_values);
            final int summaryArrayResId = Utils.isWifiOnly(getActivity()) ?
                    R.array.wifi_sleep_policy_entries_wifi_only : R.array.wifi_sleep_policy_entries;
            String[] summaries = getResources().getStringArray(summaryArrayResId);
            for (int i = 0; i < values.length; i++) {
                if (value.equals(values[i])) {
                    if (i < summaries.length) {
                        sleepPolicyPref.setSummary(summaries[i]);
                        return;
                    }
                }
            }
        }

        sleepPolicyPref.setSummary("");
        Log.e(TAG, "Invalid sleep policy value: " + value);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        String key = preference.getKey();

        if (KEY_NOTIFY_OPEN_NETWORKS.equals(key)) {
            Secure.putInt(getContentResolver(),
                    Secure.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
        } else if (KEY_POOR_NETWORK_DETECTION.equals(key)) {
            Secure.putInt(getContentResolver(),
                    Secure.WIFI_WATCHDOG_POOR_NETWORK_TEST_ENABLED,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
/* Add 20120823 TS-FMC-V2 start */
        } else if (FeatureQuery.FEATURE_CT_FMC_SUPPORT && preference == mWagAddress) {
            showWagAddressDialog();
/* Add 20120823 TS-FMC-V2 end */

        }else {
            return super.onPreferenceTreeClick(screen, preference);
        }
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        if (KEY_FREQUENCY_BAND.equals(key)) {
            try {
                mWifiManager.setFrequencyBand(Integer.parseInt((String) newValue), true);
            } catch (NumberFormatException e) {
                Toast.makeText(getActivity(), R.string.wifi_setting_frequency_band_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        if (KEY_SLEEP_POLICY.equals(key)) {
            try {
                String stringValue = (String) newValue;
                Settings.System.putInt(getContentResolver(), Settings.System.WIFI_SLEEP_POLICY,
                        Integer.parseInt(stringValue));
                updateSleepPolicySummary(preference, stringValue);
            } catch (NumberFormatException e) {
                Toast.makeText(getActivity(), R.string.wifi_setting_sleep_policy_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        return true;
    }

    private void refreshWifiInfo() {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

        Preference wifiMacAddressPref = findPreference(KEY_MAC_ADDRESS);
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        wifiMacAddressPref.setSummary(!TextUtils.isEmpty(macAddress) ? macAddress
                : getActivity().getString(R.string.status_unavailable));

        Preference wifiIpAddressPref = findPreference(KEY_CURRENT_IP_ADDRESS);
        String ipAddress = Utils.getWifiIpAddresses(getActivity());
        wifiIpAddressPref.setSummary(ipAddress == null ?
                getActivity().getString(R.string.status_unavailable) : ipAddress);
    }

/* Add 20120823 TS-FMC-V2 start */
    private void showWagAddressDialog() {
        Intent intent = new Intent(MODIFY_WAG_ADDRESS_ACTION);
        getActivity().sendBroadcast(intent);
    }

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "receive action: " + action);

            if (ENABLE_WAG_ADDRESS_BUTTON_ACTION.equals(action)) {
                boolean enable = intent.getBooleanExtra(ENABLED, false);

                Log.i(TAG, "mWagAddress.isEnabled(): " + mWagAddress.isEnabled() + ", enable: " + enable);
                if (mWagAddress.isEnabled() != enable) {
                    mWagAddress.setEnabled(enable);

                    String summary = intent.getStringExtra(SUMMARY);
                    if (summary != null && summary.length() > 0) {
                        mWagAddress.setSummary(summary);
                    } else {
                        mWagAddress.setSummary(R.string.wifi_advanced_wag_address_summary);
                    }
                }
            }
        }
    };
/* Add 20120823 TS-FMC-V2 end */
}
