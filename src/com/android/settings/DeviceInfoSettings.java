/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import android.telephony.TelephonyManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.text.format.DateFormat;
import java.io.IOException;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.qualcomm.qcrilhook.IQcRilHook;
import com.android.qualcomm.qcrilhook.QcRilHook;
import android.os.AsyncResult;

import android.content.SharedPreferences;

public class DeviceInfoSettings extends SettingsPreferenceFragment {

    private static final String LOG_TAG = "DeviceInfoSettings";

    private static final String FILENAME_PROC_VERSION = "/proc/version";
    private static final String FILENAME_MSV = "/sys/board_properties/soc/msv";

    private static final String KEY_CONTAINER = "container";
    private static final String KEY_TEAM = "team";
    private static final String KEY_CONTRIBUTORS = "contributors";
    private static final String KEY_TERMS = "terms";
    private static final String KEY_LICENSE = "license";
    private static final String KEY_COPYRIGHT = "copyright";
    private static final String KEY_SYSTEM_UPDATE_SETTINGS = "system_update_settings";
    private static final String PROPERTY_URL_SAFETYLEGAL = "ro.url.safetylegal";
    private static final String KEY_KERNEL_VERSION = "kernel_version";
    private static final String KEY_BUILD_NUMBER = "build_number";
    private static final String KEY_DEVICE_MODEL = "device_model";
    private static final String KEY_BASEBAND_VERSION = "baseband_version";
    private static final String KEY_FIRMWARE_VERSION = "firmware_version";
    private static final String KEY_UPDATE_SETTING = "additional_system_update_settings";
    private static final String KEY_STATUS = "status_info";
    private static final String KEY_EQUIPMENT_ID = "fcc_equipment_id";
    private static final String PROPERTY_EQUIPMENT_ID = "ro.ril.fccid";

    long[] mHits = new long[3];

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.device_info_settings);

        setStringSummary(KEY_FIRMWARE_VERSION, Build.VERSION.RELEASE);
        findPreference(KEY_FIRMWARE_VERSION).setEnabled(true);
        setValueSummary(KEY_BASEBAND_VERSION, "gsm.version.baseband");
        setStringSummary(KEY_DEVICE_MODEL, Build.MODEL + getMsvSuffix());
        setValueSummary(KEY_EQUIPMENT_ID, PROPERTY_EQUIPMENT_ID);
        setStringSummary(KEY_DEVICE_MODEL, Build.MODEL);
        setStringSummary(KEY_BUILD_NUMBER, Build.DISPLAY);
		try {
            String nv_oem_2 = new String(doNvRead(6854));
			String deviceModel = Build.MODEL.toLowerCase();
            //Log.e(LOG_TAG, "NV_OEM_ITEM_2_I is " + nv_oem_2 +  " deviceModel " + deviceModel);
			if (nv_oem_2 != null){
				if (deviceModel.equals("i-mobile i-note 3")){
					setStringSummary(KEY_DEVICE_MODEL, Build.MODEL + getMsvSuffix() + nv_oem_2);
				}
				else{
					setStringSummary(KEY_BUILD_NUMBER, Build.DISPLAY + "(" + nv_oem_2 + ")");
				}
			}
			else{
				Log.e(LOG_TAG, "NV_OEM_ITEM_2_I is null");
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, "Reading NV_OEM_ITEM_2_I failed");
		}
        findPreference(KEY_KERNEL_VERSION).setSummary(getFormattedKernelVersion());

        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            findPreference(KEY_STATUS).getIntent().setClassName(
                    "com.android.settings","com.android.settings.deviceinfo.MSimStatus");
        }

        // Remove Safety information preference if PROPERTY_URL_SAFETYLEGAL is not set
        removePreferenceIfPropertyMissing(getPreferenceScreen(), "safetylegal",
                PROPERTY_URL_SAFETYLEGAL);

        // Remove Equipment id preference if FCC ID is not set by RIL
        removePreferenceIfPropertyMissing(getPreferenceScreen(), KEY_EQUIPMENT_ID,
                PROPERTY_EQUIPMENT_ID);

        // Remove Baseband version if wifi-only device
        if (Utils.isWifiOnly(getActivity()) ||
                (MSimTelephonyManager.getDefault().isMultiSimEnabled())) {
            getPreferenceScreen().removePreference(findPreference(KEY_BASEBAND_VERSION));
        }

        /*
         * Settings is a generic app and should not contain any device-specific
         * info.
         */
        final Activity act = getActivity();
        // These are contained in the "container" preference group
        PreferenceGroup parentPreference = (PreferenceGroup) findPreference(KEY_CONTAINER);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_TERMS,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_LICENSE,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_COPYRIGHT,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_TEAM,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);

        // These are contained by the root preference screen
        parentPreference = getPreferenceScreen();
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference,
                KEY_SYSTEM_UPDATE_SETTINGS,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(act, parentPreference, KEY_CONTRIBUTORS,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);

        // Read platform settings for additional system update setting
        boolean isUpdateSettingAvailable =
                getResources().getBoolean(R.bool.config_additional_system_update_setting_enable);
        if (isUpdateSettingAvailable == false) {
            getPreferenceScreen().removePreference(findPreference(KEY_UPDATE_SETTING));
        }
    }

	public byte[] doNvRead(int itemId) throws IOException {
		AsyncResult result = null;
		QcRilHook mQcRilOemHook = new QcRilHook();
		result = mQcRilOemHook.sendQcRilHookMsg(IQcRilHook.QCRILHOOK_NV_READ, itemId);

		if (result == null) {
			throw new IOException();
		}
		else if(result.exception != null){
			//result.exception.printStackTrace();
			throw new IOException();
		}

		return (byte[]) result.result;
	}
	
	public String getNvValue(int id) throws IOException {
		String value = null;
		final int NV_UE_IMEI_I = 550;
		final int NV_RF_CAL_DATE_I = 571;
		final int NV_SN_I = 6853;
		final int NV_OEM_2_I = 6854;
		int i = 5;
		while ((i--) != 0) {
			try {
				switch (id) {
				case NV_RF_CAL_DATE_I:
					value = IccUtils.bytesToHexString(doNvRead(id));
					long cal_date = 0;
					try {
						cal_date = Long.parseLong(value, 16);
					} catch (NumberFormatException e) {
					}
					cal_date = Integer.reverseBytes((int) cal_date);
					if (cal_date >= 0x4E216685) {
						String datetime = DateFormat.format("yyyy/MM/dd kk:mm:ss", cal_date * 1000).toString();
						value = datetime;
					} else {
						value = null;
					}
					break;
				case NV_UE_IMEI_I:
					value = IccUtils.bytesToHexString(doNvRead(id));
					break;
				case NV_SN_I:
                    Log.e(LOG_TAG, "get NV_SN_I");
					value = new String(doNvRead(id)); 
                    Log.e(LOG_TAG, "get NV_SN_I: " + value);
					break;
				case NV_OEM_2_I:
					value = new String(doNvRead(id)); 
					break;
				}
			} catch (IOException e) {
			    value = new String("0"); 
				e.printStackTrace();
				if (i <= 0)
					throw e;
			}
			return value;
		}
        Log.e(LOG_TAG, "getNvValue end");
		return value;
	}

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String serial = Build.SERIAL ;

		try {
            serial = new String(doNvRead(6853));
            if (serial != null) {
                SharedPreferences testinfo = getActivity().getSharedPreferences("sn_from_nv", getActivity().MODE_WORLD_READABLE);
                SharedPreferences.Editor editor = testinfo.edit();
                
                editor.putString("sn_from_nv", serial);
                editor.commit();
            }
		} catch (IOException e) {
            Log.e(LOG_TAG, "read NV 6853 faile");
            serial = Build.SERIAL;
		}

        if (preference.getKey().equals(KEY_FIRMWARE_VERSION)) {
            System.arraycopy(mHits, 1, mHits, 0, mHits.length-1);
            mHits[mHits.length-1] = SystemClock.uptimeMillis();
            if (mHits[0] >= (SystemClock.uptimeMillis()-500)) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("android",
                        com.android.internal.app.PlatLogoActivity.class.getName());
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Unable to start activity " + intent.toString());
                }
            }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void removePreferenceIfPropertyMissing(PreferenceGroup preferenceGroup,
            String preference, String property ) {
        if (SystemProperties.get(property).equals(""))
        {
            // Property is missing so remove preference from group
            try {
                preferenceGroup.removePreference(findPreference(preference));
            } catch (RuntimeException e) {
                Log.d(LOG_TAG, "Property '" + property + "' missing and no '"
                        + preference + "' preference");
            }
        }
    }

    private void setStringSummary(String preference, String value) {
        try {
            findPreference(preference).setSummary(value);
        } catch (RuntimeException e) {
            findPreference(preference).setSummary(
                getResources().getString(R.string.device_info_default));
        }
    }

    private void setValueSummary(String preference, String property) {
        try {
            findPreference(preference).setSummary(
                    SystemProperties.get(property,
                            getResources().getString(R.string.device_info_default)));
        } catch (RuntimeException e) {
            // No recovery
        }
    }

    /**
     * Reads a line from the specified file.
     * @param filename the file to read from
     * @return the first line, if any.
     * @throws IOException if the file couldn't be read
     */
    private static String readLine(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename), 256);
        try {
            return reader.readLine();
        } finally {
            reader.close();
        }
    }

    private String getFormattedKernelVersion() {
        String procVersionStr;

        try {
            procVersionStr = readLine(FILENAME_PROC_VERSION);

            final String PROC_VERSION_REGEX =
                "\\w+\\s+" + /* ignore: Linux */
                "\\w+\\s+" + /* ignore: version */
                "([^\\s]+)\\s+" + /* group 1: 2.6.22-omap1 */
                "\\(([^\\s@]+(?:@[^\\s.]+)?)[^)]*\\)\\s+" + /* group 2: (xxxxxx@xxxxx.constant) */
                "\\((?:[^(]*\\([^)]*\\))?[^)]*\\)\\s+" + /* ignore: (gcc ..) */
                "([^\\s]+)\\s+" + /* group 3: #26 */
                "(?:PREEMPT\\s+)?" + /* ignore: PREEMPT (optional) */
                "(.+)"; /* group 4: date */

            Pattern p = Pattern.compile(PROC_VERSION_REGEX);
            Matcher m = p.matcher(procVersionStr);

            if (!m.matches()) {
                Log.e(LOG_TAG, "Regex did not match on /proc/version: " + procVersionStr);
                return "Unavailable";
            } else if (m.groupCount() < 4) {
                Log.e(LOG_TAG, "Regex match on /proc/version only returned " + m.groupCount()
                        + " groups");
                return "Unavailable";
            } else {
//                return (new StringBuilder(m.group(1)).append("\n").append(
//                        m.group(2)).append(" ").append(m.group(3)).append("\n")
//                        .append(m.group(4))).toString();
                return (new StringBuilder("3.4.0-perf")).toString();
            }
        } catch (IOException e) {
            Log.e(LOG_TAG,
                "IO Exception when getting kernel version for Device Info screen",
                e);

            return "Unavailable";
        }
    }

    /**
     * Returns " (ENGINEERING)" if the msv file has a zero value, else returns "".
     * @return a string to append to the model number description.
     */
    public static String getMsvSuffix() {
        // Production devices should have a non-zero value. If we can't read it, assume it's a
        // production device so that we don't accidentally show that it's an ENGINEERING device.
        try {
            String msv = readLine(FILENAME_MSV);
            // Parse as a hex number. If it evaluates to a zero, then it's an engineering build.
            if (Long.parseLong(msv, 16) == 0) {
                return " (ENGINEERING)";
            }
        } catch (IOException ioe) {
            // Fail quietly, as the file may not exist on some devices.
        } catch (NumberFormatException nfe) {
            // Fail quietly, returning empty string should be sufficient
        }
        return "";
    }
}
