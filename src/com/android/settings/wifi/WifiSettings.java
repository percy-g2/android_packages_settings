/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2013 The Linux Foundation. All rights reserved.
 * Copyright (C) 2012 Thunder Software Technology Co.,Ltd. 
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

import static android.net.wifi.WifiConfiguration.INVALID_NETWORK_ID;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.security.Credentials;
import android.security.KeyStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.wifi.p2p.WifiP2pSettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
/* Add 20120823 TS-FMC-V2 start */
import android.net.wifi.WifiConfiguration.Status;
import android.provider.Settings;
import android.content.res.Resources;

import com.qrd.plugin.feature_query.FeatureQuery;
import android.app.ActionBar;
/* Add 20120823 TS-FMC-V2 end */

/**
 * Two types of UI are provided here.
 *
 * The first is for "usual Settings", appearing as any other Setup fragment.
 *
 * The second is for Setup Wizard, with a simplified interface that hides the action bar
 * and menus.
 */
public class WifiSettings extends SettingsPreferenceFragment
        implements DialogInterface.OnClickListener  {
    private static final String TAG = "WifiSettings";
    private static final int MENU_ID_WPS_PBC = Menu.FIRST;
    private static final int MENU_ID_WPS_PIN = Menu.FIRST + 1;
    private static final int MENU_ID_P2P = Menu.FIRST + 2;
    private static final int MENU_ID_ADD_NETWORK = Menu.FIRST + 3;
    private static final int MENU_ID_ADVANCED = Menu.FIRST + 4;
    private static final int MENU_ID_SCAN = Menu.FIRST + 5;
    private static final int MENU_ID_CONNECT = Menu.FIRST + 6;
    private static final int MENU_ID_FORGET = Menu.FIRST + 7;
    private static final int MENU_ID_MODIFY = Menu.FIRST + 8;
/* Add 20120823 TS-FMC-V2 start */
    private static final int MENU_ID_PRIORITY = Menu.FIRST + 9;
/* Add 20120823 TS-FMC-V2 end */

    private static final int WIFI_DIALOG_ID = 1;
    private static final int WPS_PBC_DIALOG_ID = 2;
    private static final int WPS_PIN_DIALOG_ID = 3;

    // Combo scans can take 5-6s to complete - set to 10s.
    private static final int WIFI_RESCAN_INTERVAL_MS = 10 * 1000;

    // Instance state keys
    private static final String SAVE_DIALOG_EDIT_MODE = "edit_mode";
    private static final String SAVE_DIALOG_ACCESS_POINT_STATE = "wifi_ap_state";

    private final IntentFilter mFilter;
    private final BroadcastReceiver mReceiver;
    private final Scanner mScanner;

    private WifiManager mWifiManager;
    private WifiManager.Channel mChannel;
    private WifiManager.ActionListener mConnectListener;
    private WifiManager.ActionListener mSaveListener;
    private WifiManager.ActionListener mForgetListener;
    private boolean mP2pSupported;


    private WifiEnabler mWifiEnabler;
    // An access point being editted is stored here.
    private AccessPoint mSelectedAccessPoint;

    private DetailedState mLastState;
    private WifiInfo mLastInfo;

    private AtomicBoolean mConnected = new AtomicBoolean(false);

    private int mKeyStoreNetworkId = INVALID_NETWORK_ID;

    private WifiDialog mDialog;

    private TextView mEmptyView;

    /* Used in Wifi Setup context */

    // this boolean extra specifies whether to disable the Next button when not connected
    private static final String EXTRA_ENABLE_NEXT_ON_CONNECT = "wifi_enable_next_on_connect";

    // this boolean extra specifies whether to auto finish when connection is established
    private static final String EXTRA_AUTO_FINISH_ON_CONNECT = "wifi_auto_finish_on_connect";

    // this boolean extra is set if we are being invoked by the Setup Wizard
    private static final String EXTRA_IS_FIRST_RUN = "firstRun";

    // should Next button only be enabled when we have a connection?
    private boolean mEnableNextOnConnection;

    // should activity finish once we have a connection?
    private boolean mAutoFinishOnConnection;

    // Save the dialog details
    private boolean mDlgEdit;
    private AccessPoint mDlgAccessPoint;
    private Bundle mAccessPointSavedState;
    // the action bar uses a different set of controls for Setup Wizard
    private boolean mSetupWizardMode;

    /* End of "used in Wifi Setup context" */

/* Add 20120823 TS-FMC-V2 start */
    private static final String SHOW_PRESET_NETWORK_ACTION = "android.net.wifi.show_preset_network";
    private static final String WIFI_MODIFY_AP_PRIORITY_ACTION = "android.net.wifi.modify_ap_priority";
    private static final boolean isSupportFMC = WifiManager.fmcV2Support();
    private boolean mShowFmcSummary = false;
    private String mFmcSummary;
    private boolean shouldShowErrorMsg = false;
    private FmcConnectApHandler mFmcConnectApHandler = new FmcConnectApHandler();
/* Add 20120823 TS-FMC-V2 end */

    public WifiSettings() {
        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
        mFilter.addAction(WifiManager.LINK_CONFIGURATION_CHANGED_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
/* Add 20120823 TS-FMC-V2 start */
        if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
        mFilter.addAction(WifiManager.FMC_STATE_CHANGED_ACTION);
        }
/* Add 20120823 TS-FMC-V2 end */

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleEvent(context, intent);
            }
        };

        mScanner = new Scanner();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        // Set this flag early, as it's needed by getHelpResource(), which is called by super
        mSetupWizardMode = getActivity().getIntent().getBooleanExtra(EXTRA_IS_FIRST_RUN, false);
        // set the title of ActionBar is Wi-Fi or WLAN.
        String titleActionBar = Utils.replaceAllWiFi(getActivity().getString(
                R.string.wifi_settings_title));
        ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(titleActionBar);
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (mSetupWizardMode) {
            View view = inflater.inflate(R.layout.setup_preference, container, false);
            View other = view.findViewById(R.id.other_network);
            other.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mWifiManager.isWifiEnabled()) {
                        onAddNetworkPressed();
                    }
                }
            });
            final ImageButton b = (ImageButton) view.findViewById(R.id.more);
            if (b != null) {
                b.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mWifiManager.isWifiEnabled()) {
                            PopupMenu pm = new PopupMenu(inflater.getContext(), b);
                            pm.inflate(R.menu.wifi_setup);
                            pm.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                                @Override
                                public boolean onMenuItemClick(MenuItem item) {
                                    if (R.id.wifi_wps == item.getItemId()) {
                                        showDialog(WPS_PBC_DIALOG_ID);
                                        return true;
                                    }
                                    return false;
                                }
                            });
                            pm.show();
                        }
                    }
                });
            }
            return view;
        } else {
            return super.onCreateView(inflater, container, savedInstanceState);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // We don't call super.onActivityCreated() here, since it assumes we already set up
        // Preference (probably in onCreate()), while WifiSettings exceptionally set it up in
        // this method.

        mP2pSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mChannel = mWifiManager.initialize(getActivity(), getActivity().getMainLooper(), null);

        mConnectListener = new WifiManager.ActionListener() {
                                   public void onSuccess() {
                                   }
                                   public void onFailure(int reason) {
                                        Toast.makeText(getActivity(),
                                            R.string.wifi_failed_connect_message,
                                            Toast.LENGTH_SHORT).show();
                                   }
                               };

        mSaveListener = new WifiManager.ActionListener() {
                                public void onSuccess() {
                                }
                                public void onFailure(int reason) {
                                    Toast.makeText(getActivity(),
                                        R.string.wifi_failed_save_message,
                                        Toast.LENGTH_SHORT).show();
                                }
                            };

        mForgetListener = new WifiManager.ActionListener() {
                                   public void onSuccess() {
                                   }
                                   public void onFailure(int reason) {
                                        Toast.makeText(getActivity(),
                                            R.string.wifi_failed_forget_message,
                                            Toast.LENGTH_SHORT).show();
                                   }
                               };

        if (savedInstanceState != null
                && savedInstanceState.containsKey(SAVE_DIALOG_ACCESS_POINT_STATE)) {
            mDlgEdit = savedInstanceState.getBoolean(SAVE_DIALOG_EDIT_MODE);
            mAccessPointSavedState = savedInstanceState.getBundle(SAVE_DIALOG_ACCESS_POINT_STATE);
        }

        final Activity activity = getActivity();
        final Intent intent = activity.getIntent();

        // first if we're supposed to finish once we have a connection
        mAutoFinishOnConnection = intent.getBooleanExtra(EXTRA_AUTO_FINISH_ON_CONNECT, false);

        if (mAutoFinishOnConnection) {
            // Hide the next button
            if (hasNextButton()) {
                getNextButton().setVisibility(View.GONE);
            }

            final ConnectivityManager connectivity = (ConnectivityManager)
                    getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null
                    && connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
                activity.finish();
                return;
            }
        }

        // if we're supposed to enable/disable the Next button based on our current connection
        // state, start it off in the right state
        mEnableNextOnConnection = intent.getBooleanExtra(EXTRA_ENABLE_NEXT_ON_CONNECT, false);

        if (mEnableNextOnConnection) {
            if (hasNextButton()) {
                final ConnectivityManager connectivity = (ConnectivityManager)
                        getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connectivity != null) {
                    NetworkInfo info = connectivity.getNetworkInfo(
                            ConnectivityManager.TYPE_WIFI);
                    changeNextButtonState(info.isConnected());
                }
            }
        }

        addPreferencesFromResource(R.xml.wifi_settings);

        if (mSetupWizardMode) {
            getView().setSystemUiVisibility(
                    View.STATUS_BAR_DISABLE_BACK |
                    View.STATUS_BAR_DISABLE_HOME |
                    View.STATUS_BAR_DISABLE_RECENT |
                    View.STATUS_BAR_DISABLE_NOTIFICATION_ALERTS |
                    View.STATUS_BAR_DISABLE_CLOCK);
        }

        // On/off switch is hidden for Setup Wizard
        if (!mSetupWizardMode) {
            Switch actionBarSwitch = new Switch(activity);

            if (activity instanceof PreferenceActivity) {
                PreferenceActivity preferenceActivity = (PreferenceActivity) activity;
                if (preferenceActivity.onIsHidingHeaders() || !preferenceActivity.onIsMultiPane()) {
                    final int padding = activity.getResources().getDimensionPixelSize(
                            R.dimen.action_bar_switch_padding);
                    actionBarSwitch.setPadding(0, 0, padding, 0);
                    activity.getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                            ActionBar.DISPLAY_SHOW_CUSTOM);
                    activity.getActionBar().setCustomView(actionBarSwitch, new ActionBar.LayoutParams(
                            ActionBar.LayoutParams.WRAP_CONTENT,
                            ActionBar.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER_VERTICAL | Gravity.RIGHT));
                }
            }

            mWifiEnabler = new WifiEnabler(activity, actionBarSwitch);
        }

        mEmptyView = (TextView) getView().findViewById(android.R.id.empty);
        getListView().setEmptyView(mEmptyView);

        if (!mSetupWizardMode) {
            registerForContextMenu(getListView());
        }
        setHasOptionsMenu(true);

        // After confirming PreferenceScreen is available, we call super.
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWifiEnabler != null) {
            mWifiEnabler.resume();
        }

        getActivity().registerReceiver(mReceiver, mFilter);
        if (mKeyStoreNetworkId != INVALID_NETWORK_ID &&
                KeyStore.getInstance().state() == KeyStore.State.UNLOCKED) {
            mWifiManager.connect(mChannel, mKeyStoreNetworkId, mConnectListener);
        }
        mKeyStoreNetworkId = INVALID_NETWORK_ID;

        updateAccessPoints();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mWifiEnabler != null) {
            mWifiEnabler.pause();
        }
        getActivity().unregisterReceiver(mReceiver);
        mScanner.pause();
/* Add 20121013 TS-FMC-V2 start */
        if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
        mFmcConnectApHandler.pause();
        }
/* Add 20121013 TS-FMC-V2 end */
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        final boolean wifiIsEnabled = mWifiManager.isWifiEnabled();
        if (mSetupWizardMode) {
            // FIXME: add setIcon() when graphics are available
            menu.add(Menu.NONE, MENU_ID_WPS_PBC, 0, R.string.wifi_menu_wps_pbc)
                    .setIcon(R.drawable.ic_wps)
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(Menu.NONE, MENU_ID_ADD_NETWORK, 0, R.string.wifi_add_network)
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            menu.add(Menu.NONE, MENU_ID_WPS_PBC, 0, R.string.wifi_menu_wps_pbc)
                    .setIcon(R.drawable.ic_wps)
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(Menu.NONE, MENU_ID_ADD_NETWORK, 0, R.string.wifi_add_network)
                    .setIcon(R.drawable.ic_menu_add)
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(Menu.NONE, MENU_ID_SCAN, 0, R.string.wifi_menu_scan)
                    //.setIcon(R.drawable.ic_menu_scan_network)
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(Menu.NONE, MENU_ID_WPS_PIN, 0, R.string.wifi_menu_wps_pin)
                    .setEnabled(wifiIsEnabled)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            if (mP2pSupported) {
                String memu_P2P = Utils.replaceAllWiFi(getString(R.string.wifi_menu_p2p));
                menu.add(Menu.NONE, MENU_ID_P2P, 0, memu_P2P)
                        .setEnabled(wifiIsEnabled)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
            menu.add(Menu.NONE, MENU_ID_ADVANCED, 0, R.string.wifi_menu_advanced)
                    //.setIcon(android.R.drawable.ic_menu_manage)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
/* Add 20120823 TS-FMC-V2 start */
            Log.i(TAG,"feature_ct_fmc_support == "+FeatureQuery.FEATURE_CT_FMC_SUPPORT);
            if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
            menu.add(Menu.NONE, MENU_ID_PRIORITY, 0, R.string.wifi_menu_priority)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
/* Add 20120823 TS-FMC-V2 end */
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // If the dialog is showing, save its state.
        if (mDialog != null && mDialog.isShowing()) {
            outState.putBoolean(SAVE_DIALOG_EDIT_MODE, mDlgEdit);
            if (mDlgAccessPoint != null) {
                mAccessPointSavedState = new Bundle();
                mDlgAccessPoint.saveWifiState(mAccessPointSavedState);
                outState.putBundle(SAVE_DIALOG_ACCESS_POINT_STATE, mAccessPointSavedState);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ID_WPS_PBC:
                showDialog(WPS_PBC_DIALOG_ID);
                return true;
            case MENU_ID_P2P:
                int settings_title = R.string.wifi_p2p_settings_title;
                if (FeatureQuery.FEATURE_DISPLAY_USE_WLAN_INSTEAD) {
                    settings_title = R.string.wlan_p2p_settings_title;
                }
                if (getActivity() instanceof PreferenceActivity) {
                    ((PreferenceActivity) getActivity()).startPreferencePanel(
                            WifiP2pSettings.class.getCanonicalName(),
                            null,
                            settings_title, null,
                            this, 0);
                } else {
                    startFragment(this, WifiP2pSettings.class.getCanonicalName(), -1, null);
                }
                return true;
            case MENU_ID_WPS_PIN:
                showDialog(WPS_PIN_DIALOG_ID);
                return true;
            case MENU_ID_SCAN:
                if (mWifiManager.isWifiEnabled()) {
                    mScanner.forceScan();
                }
                return true;
            case MENU_ID_ADD_NETWORK:
                if (mWifiManager.isWifiEnabled()) {
                    onAddNetworkPressed();
                }
                return true;
            case MENU_ID_ADVANCED:
                if (getActivity() instanceof PreferenceActivity) {
                    int resId = R.string.wifi_advanced_titlebar;
                    // When the FEATURE_DISPLAY_USE_WLAN_INSTEAD flag is set, we
                    // will display "WLAN" instead of "Wi-Fi"
                    if (FeatureQuery.FEATURE_DISPLAY_USE_WLAN_INSTEAD) {
                        resId = R.string.wlan_advanced_titlebar;
                    }
                    ((PreferenceActivity) getActivity()).startPreferencePanel(
                            AdvancedWifiSettings.class.getCanonicalName(),
                            null,
                            resId, null,
                            this, 0);
                } else {
                    startFragment(this, AdvancedWifiSettings.class.getCanonicalName(), -1, null);
                }
                return true;
/* Add 20120823 TS-FMC-V2 start */
            case MENU_ID_PRIORITY:
                modifyApPriority();
                return true;
/* Add 20120823 TS-FMC-V2 end */
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo info) {
        if (info instanceof AdapterContextMenuInfo) {
            Preference preference = (Preference) getListView().getItemAtPosition(
                    ((AdapterContextMenuInfo) info).position);

            if (preference instanceof AccessPoint) {
                mSelectedAccessPoint = (AccessPoint) preference;
                menu.setHeaderTitle(mSelectedAccessPoint.ssid);
                if (mSelectedAccessPoint.getLevel() != -1
                        && mSelectedAccessPoint.getState() == null) {
                    menu.add(Menu.NONE, MENU_ID_CONNECT, 0, R.string.wifi_menu_connect);
                }
                if (mSelectedAccessPoint.networkId != INVALID_NETWORK_ID) {
                    menu.add(Menu.NONE, MENU_ID_FORGET, 0, R.string.wifi_menu_forget);
                    menu.add(Menu.NONE, MENU_ID_MODIFY, 0, R.string.wifi_menu_modify);

/* Add 20120823 TS-FMC-V2 start */
                    // CT ap list cannot be deleted if exist
                    if (FeatureQuery.FEATURE_CT_FMC_SUPPORT && isSupportFMC) {
                        if (WifiManager.isPresetNetWork(mSelectedAccessPoint.ssid, mSelectedAccessPoint.security)) {
                            menu.findItem(MENU_ID_FORGET).setEnabled(false);
                            menu.findItem(MENU_ID_MODIFY).setEnabled(false);
                        }
                    }
/* Add 20120823 TS-FMC-V2 end */
                }
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mSelectedAccessPoint == null) {
            return super.onContextItemSelected(item);
        }
        switch (item.getItemId()) {
            case MENU_ID_CONNECT: {
                if (mSelectedAccessPoint.networkId != INVALID_NETWORK_ID) {
                    if (!requireKeyStore(mSelectedAccessPoint.getConfig())) {
                        mWifiManager.connect(mChannel, mSelectedAccessPoint.networkId,
                                mConnectListener);
/* Add 20121013 TS-FMC-V2 start */
                        if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
                        handleConnectToAp(mSelectedAccessPoint.getConfig());
                        }
/* Add 20121013 TS-FMC-V2 end */
                    }
                } else if (mSelectedAccessPoint.security == AccessPoint.SECURITY_NONE) {
                    /** Bypass dialog for unsecured networks */
                    mSelectedAccessPoint.generateOpenNetworkConfig();
                    mWifiManager.connect(mChannel, mSelectedAccessPoint.getConfig(),
                            mConnectListener);
                } else {
                    showDialog(mSelectedAccessPoint, true);
                }
                return true;
            }
            case MENU_ID_FORGET: {
                mWifiManager.forget(mChannel, mSelectedAccessPoint.networkId, mForgetListener);
                return true;
            }
            case MENU_ID_MODIFY: {
                showDialog(mSelectedAccessPoint, true);
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

/* Add 20120823 TS-FMC-V2 start */
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
        // get menuItem of priority
        MenuItem menuItem = menu.findItem(MENU_ID_PRIORITY);
        if (!isSupportFMC) {
            menuItem.setVisible(false);
            return;
        }

        if (menuItem != null) {
            menuItem.setEnabled(mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED);
        }
        }
        super.onPrepareOptionsMenu(menu);
    }

/* Add 20120823 TS-FMC-V2 end */

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference instanceof AccessPoint) {
            mSelectedAccessPoint = (AccessPoint) preference;
            /** Bypass dialog for unsecured, unsaved networks */
            if (mSelectedAccessPoint.security == AccessPoint.SECURITY_NONE &&
                    mSelectedAccessPoint.networkId == INVALID_NETWORK_ID) {
                mSelectedAccessPoint.generateOpenNetworkConfig();
                mWifiManager.connect(mChannel, mSelectedAccessPoint.getConfig(), mConnectListener);
            } else {
                showDialog(mSelectedAccessPoint, false);
            }
        } else {
            return super.onPreferenceTreeClick(screen, preference);
        }
        return true;
    }

    private void showDialog(AccessPoint accessPoint, boolean edit) {
        if (mDialog != null) {
            removeDialog(WIFI_DIALOG_ID);
            mDialog = null;
        }

        // Save the access point and edit mode
        mDlgAccessPoint = accessPoint;
        mDlgEdit = edit;

        showDialog(WIFI_DIALOG_ID);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case WIFI_DIALOG_ID:
                AccessPoint ap = mDlgAccessPoint; // For manual launch
                if ((ap == null) && (mSelectedAccessPoint != null)) { // For re-launch from saved state
                    if (mAccessPointSavedState != null) {
                        ap = new AccessPoint(getActivity(), mAccessPointSavedState);
                        // For repeated orientation changes
                        mDlgAccessPoint = ap;
                    }
                }
                // If it's still null, fine, it's for Add Network
                mSelectedAccessPoint = ap;
                mDialog = new WifiDialog(getActivity(), this, ap, mDlgEdit);
                return mDialog;
            case WPS_PBC_DIALOG_ID:
                return new WpsDialog(getActivity(), WpsInfo.PBC);
            case WPS_PIN_DIALOG_ID:
                return new WpsDialog(getActivity(), WpsInfo.DISPLAY);
        }
        return super.onCreateDialog(dialogId);
    }

    private boolean requireKeyStore(WifiConfiguration config) {
        if (WifiConfigController.requireKeyStore(config) &&
                KeyStore.getInstance().state() != KeyStore.State.UNLOCKED) {
            mKeyStoreNetworkId = config.networkId;
            Credentials.getInstance().unlock(getActivity());
            return true;
        }
        return false;
    }

    /**
     * Shows the latest access points available with supplimental information like
     * the strength of network and the security for it.
     */
    private void updateAccessPoints() {
        // Safeguard from some delayed event handling
        if (getActivity() == null) return;

        final int wifiState = mWifiManager.getWifiState();

        switch (wifiState) {
            case WifiManager.WIFI_STATE_ENABLED:
                // AccessPoints are automatically sorted with TreeSet.
                final Collection<AccessPoint> accessPoints = constructAccessPoints();
                getPreferenceScreen().removeAll();
                if(accessPoints.size() == 0) {
                    addMessagePreference(R.string.wifi_empty_list_wifi_on);
                }
                for (AccessPoint accessPoint : accessPoints) {
                    getPreferenceScreen().addPreference(accessPoint);
                }
                if (accessPoints.isEmpty()){
					addMessagePreference(R.string.wifi_empty_list_wifi_on);
				}
                break;

            case WifiManager.WIFI_STATE_ENABLING:
                getPreferenceScreen().removeAll();
                break;

            case WifiManager.WIFI_STATE_DISABLING:
                addMessagePreference(R.string.wifi_stopping);
                break;

            case WifiManager.WIFI_STATE_DISABLED:
                addMessagePreference(R.string.wifi_empty_list_wifi_off);
                break;
        }
    }

    private void addMessagePreference(int messageId) {
        if (mEmptyView != null) {
            mEmptyView.setText(Utils.replaceAllWiFi(getString(messageId)));
        }
        getPreferenceScreen().removeAll();
    }

    /** Returns sorted list of access points */
    private List<AccessPoint> constructAccessPoints() {
        ArrayList<AccessPoint> accessPoints = new ArrayList<AccessPoint>();
        /** Lookup table to more quickly update AccessPoints by only considering objects with the
         * correct SSID.  Maps SSID -> List of AccessPoints with the given SSID.  */
        Multimap<String, AccessPoint> apMap = new Multimap<String, AccessPoint>();

        final List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        if (configs != null) {
            for (WifiConfiguration config : configs) {
                if (config.SSID != null) {
                    AccessPoint accessPoint = new AccessPoint(getActivity(), config);
                    accessPoint.update(mLastInfo, mLastState);
                    accessPoints.add(accessPoint);
                    apMap.put(accessPoint.ssid, accessPoint);
                }
            }
        }

        final List<ScanResult> results = mWifiManager.getScanResults();
        if (results != null) {
            for (ScanResult result : results) {
                // Ignore hidden and ad-hoc networks.
                if (result.SSID == null || result.SSID.length() == 0 ||
                        result.capabilities.contains("[IBSS]")) {
                    continue;
                }

                boolean found = false;
                for (AccessPoint accessPoint : apMap.getAll(result.SSID)) {
		//disable RSSI update for connected AP  through scan result
		    if(accessPoint.getState() == DetailedState.CONNECTED){
			found = true;
			continue;
		    }
                    if (accessPoint.update(result))
                        found = true;
                }
                if (!found) {
                    AccessPoint accessPoint = new AccessPoint(getActivity(), result);
                    accessPoints.add(accessPoint);
                    apMap.put(accessPoint.ssid, accessPoint);
                }
            }
        }
/* Add 20120823 TS-FMC-V2 start */
        if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
        for (AccessPoint ap: accessPoints) {
            if (WifiManager.isPresetNetWork(ap.ssid, ap.security) && mLastInfo != null
                    && ap.networkId == mLastInfo.getNetworkId()) {
                ap.setShowFmcSummary(mShowFmcSummary);
                ap.setFmcSummary(mFmcSummary);
            }
        }
        }
/* Add 20120823 TS-FMC-V2 end */

        // Pre-sort accessPoints to speed preference insertion
        Collections.sort(accessPoints);
        return accessPoints;
    }

    /** A restricted multimap for use in constructAccessPoints */
    private class Multimap<K,V> {
        private HashMap<K,List<V>> store = new HashMap<K,List<V>>();
        /** retrieve a non-null list of values with key K */
        List<V> getAll(K key) {
            List<V> values = store.get(key);
            return values != null ? values : Collections.<V>emptyList();
        }

        void put(K key, V val) {
            List<V> curVals = store.get(key);
            if (curVals == null) {
                curVals = new ArrayList<V>(3);
                store.put(key, curVals);
            }
            curVals.add(val);
        }
    }

    private void handleEvent(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            updateWifiState(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN));
        } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action) ||
                WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION.equals(action) ||
                WifiManager.LINK_CONFIGURATION_CHANGED_ACTION.equals(action)) {
                updateAccessPoints();
        } else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
            //Ignore supplicant state changes when network is connected
            //TODO: we should deprecate SUPPLICANT_STATE_CHANGED_ACTION and
            //introduce a broadcast that combines the supplicant and network
            //network state change events so the apps dont have to worry about
            //ignoring supplicant state change when network is connected
            //to get more fine grained information.
            SupplicantState state = (SupplicantState) intent.getParcelableExtra(
                    WifiManager.EXTRA_NEW_STATE);
            if (!mConnected.get() && SupplicantState.isHandshakeState(state)) {
                updateConnectionState(WifiInfo.getDetailedStateOf(state));
            }

/* Add 20120823 TS-FMC-V2 start */
            if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
          //  showErrorConnectionMessage(intent);
            }
/* Add 20120823 TS-FMC-V2 end */
        } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(
                    WifiManager.EXTRA_NETWORK_INFO);
            mConnected.set(info.isConnected());
/* Add 20120823 TS-FMC-V2 start */
            if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
            if (!mConnected.get()) {
                Log.d(TAG, "Update fmc state when the network disconnected.");
                updateFmcState(-1);
            } else {
                mFmcConnectApHandler.pause();
            }
            }
/* Add 20120823 TS-FMC-V2 end */
            changeNextButtonState(info.isConnected());
            updateAccessPoints();
            updateConnectionState(info.getDetailedState());
            if (mAutoFinishOnConnection && info.isConnected()) {
                getActivity().finish();
                return;
            }
        } else if (WifiManager.RSSI_CHANGED_ACTION.equals(action)) {
            updateConnectionState(null);
/* Add 20120823 TS-FMC-V2 start */
        } else if (FeatureQuery.FEATURE_CT_FMC_SUPPORT && action.equals(WifiManager.FMC_STATE_CHANGED_ACTION)) {
            Log.d(TAG, "Update fmc state when WifiManager.FMC_STATE_CHANGED_ACTION");
            int newState = intent.getIntExtra(WifiManager.EXTRA_FMC_STATE, WifiManager.FMC_STATE_INVALID);
            if (newState == WifiManager.FMC_STATE_INVALID) {
                return;
            }
            updateFmcState(newState);
        }
/* Add 20120823 TS-FMC-V2 end */
    }

    private void updateConnectionState(DetailedState state) {
        /* sticky broadcasts can call this when wifi is disabled */
        if (!mWifiManager.isWifiEnabled()) {
            mScanner.pause();
            return;
        }

        if (state == DetailedState.OBTAINING_IPADDR) {
            mScanner.pause();
        } else {
            mScanner.resume();
        }

        mLastInfo = mWifiManager.getConnectionInfo();
        if (state != null) {
            mLastState = state;
        }

        if(mSelectedAccessPoint != null && state != null && state != DetailedState.CONNECTED){
            mLastInfo.setRssi(mSelectedAccessPoint.getRssi());
        }

        for (int i = getPreferenceScreen().getPreferenceCount() - 1; i >= 0; --i) {
            // Maybe there's a WifiConfigPreference
            Preference preference = getPreferenceScreen().getPreference(i);
            if (preference instanceof AccessPoint) {
                final AccessPoint accessPoint = (AccessPoint) preference;
                accessPoint.update(mLastInfo, mLastState);
            }
        }
    }

    private void updateWifiState(int state) {
        getActivity().invalidateOptionsMenu();

        switch (state) {
            case WifiManager.WIFI_STATE_ENABLED:
                mScanner.resume();
                return; // not break, to avoid the call to pause() below

            case WifiManager.WIFI_STATE_ENABLING:
                addMessagePreference(R.string.wifi_starting);
                break;

            case WifiManager.WIFI_STATE_DISABLED:
                addMessagePreference(R.string.wifi_empty_list_wifi_off);
/* Add 20120823 TS-FMC-V2 start */
                if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
                updateFmcState(-1);
                mFmcConnectApHandler.pause();
                }
/* Add 20120823 TS-FMC-V2 end */
                break;
        }

        mLastInfo = null;
        mLastState = null;
        mScanner.pause();
    }

    private class Scanner extends Handler {
        private int mRetry = 0;

        void resume() {
            if (!hasMessages(0)) {
                sendEmptyMessage(0);
            }
        }

        void forceScan() {
            removeMessages(0);
            sendEmptyMessage(0);
        }

        void pause() {
            mRetry = 0;
            removeMessages(0);
        }

        @Override
        public void handleMessage(Message message) {
            if (mWifiManager.startScanActive()) {
                mRetry = 0;
            } else if (++mRetry >= 3) {
                mRetry = 0;
                Toast.makeText(getActivity(), R.string.wifi_fail_to_scan,
                        Toast.LENGTH_LONG).show();
                return;
            }
            sendEmptyMessageDelayed(0, WIFI_RESCAN_INTERVAL_MS);
        }
    }

    /**
     * Renames/replaces "Next" button when appropriate. "Next" button usually exists in
     * Wifi setup screens, not in usual wifi settings screen.
     *
     * @param connected true when the device is connected to a wifi network.
     */
    private void changeNextButtonState(boolean connected) {
        if (mEnableNextOnConnection && hasNextButton()) {
            getNextButton().setEnabled(connected);
        }
    }

    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == WifiDialog.BUTTON_FORGET && mSelectedAccessPoint != null) {
            forget();
        } else if (button == WifiDialog.BUTTON_SUBMIT) {
/* Add 20120823 TS-FMC-V2 start */
            if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
            shouldShowErrorMsg = true;
            }
/* Add 20120823 TS-FMC-V2 start */
            submit(mDialog.getController());
        }
    }

    /* package */ void submit(WifiConfigController configController) {

        final WifiConfiguration config = configController.getConfig();

        if (config == null) {
            if (mSelectedAccessPoint != null
                    && !requireKeyStore(mSelectedAccessPoint.getConfig())
                    && mSelectedAccessPoint.networkId != INVALID_NETWORK_ID) {
                mWifiManager.connect(mChannel, mSelectedAccessPoint.networkId,
                        mConnectListener);
/* Add 20121013 TS-FMC-V2 start */
                if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
                handleConnectToAp(mSelectedAccessPoint.getConfig());
                }
/* Add 20121013 TS-FMC-V2 end */
            }
        } else if (config.networkId != INVALID_NETWORK_ID) {
            if (mSelectedAccessPoint != null) {
                mWifiManager.save(mChannel, config, mSaveListener);
            }
        } else {
            if (configController.isEdit() || requireKeyStore(config)) {
                mWifiManager.save(mChannel, config, mSaveListener);
            } else {
                mWifiManager.connect(mChannel, config, mConnectListener);
/* Add 20121013 TS-FMC-V2 start */
                if (FeatureQuery.FEATURE_CT_FMC_SUPPORT) {
                handleConnectToAp(config);
                }
/* Add 20121013 TS-FMC-V2 end */
            }
        }

        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }
        updateAccessPoints();
    }

    /* package */ void forget() {
        if (mSelectedAccessPoint.networkId == INVALID_NETWORK_ID) {
            // Should not happen, but a monkey seems to triger it
            Log.e(TAG, "Failed to forget invalid network " + mSelectedAccessPoint.getConfig());
            return;
        }

        mWifiManager.forget(mChannel, mSelectedAccessPoint.networkId, mForgetListener);

        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }
        updateAccessPoints();

        // We need to rename/replace "Next" button in wifi setup context.
        changeNextButtonState(false);
    }

    /**
     * Refreshes acccess points and ask Wifi module to scan networks again.
     */
    /* package */ void refreshAccessPoints() {
        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }

        getPreferenceScreen().removeAll();
    }

    /**
     * Called when "add network" button is pressed.
     */
    /* package */ void onAddNetworkPressed() {
        // No exact access point is selected.
        mSelectedAccessPoint = null;
        showDialog(null, true);
    }

    /* package */ int getAccessPointsCount() {
        final boolean wifiIsEnabled = mWifiManager.isWifiEnabled();
        if (wifiIsEnabled) {
            return getPreferenceScreen().getPreferenceCount();
        } else {
            return 0;
        }
    }

    /**
     * Requests wifi module to pause wifi scan. May be ignored when the module is disabled.
     */
    /* package */ void pauseWifiScan() {
        if (mWifiManager.isWifiEnabled()) {
            mScanner.pause();
        }
    }

    /**
     * Requests wifi module to resume wifi scan. May be ignored when the module is disabled.
     */
    /* package */ void resumeWifiScan() {
        if (mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }
    }

    @Override
    protected int getHelpResource() {
        if (mSetupWizardMode) {
            return 0;
        }
        return R.string.help_url_wifi;
    }

    /**
     * Used as the outer frame of all setup wizard pages that need to adjust their margins based
     * on the total size of the available display. (e.g. side margins set to 10% of total width.)
     */
    public static class ProportionalOuterFrame extends RelativeLayout {
        public ProportionalOuterFrame(Context context) {
            super(context);
        }
        public ProportionalOuterFrame(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
        public ProportionalOuterFrame(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        /**
         * Set our margins and title area height proportionally to the available display size
         */
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
            int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
            final Resources resources = getContext().getResources();
            float titleHeight = resources.getFraction(R.dimen.setup_title_height, 1, 1);
            float sideMargin = resources.getFraction(R.dimen.setup_border_width, 1, 1);
            int bottom = resources.getDimensionPixelSize(R.dimen.setup_margin_bottom);
            setPadding(
                    (int) (parentWidth * sideMargin),
                    0,
                    (int) (parentWidth * sideMargin),
                    bottom);
            View title = findViewById(R.id.title_area);
            if (title != null) {
                title.setMinimumHeight((int) (parentHeight * titleHeight));
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
/* Add 20120823 TS-FMC-V2 start */
    private void updateFmcState(int state) {
        Log.d(TAG, "updateFmcState, state: " + state);
        Resources r = getResources();
        mShowFmcSummary = true;
        if (state == WifiManager.FMC_STATE_AUTH_SUCCESS) {
            mFmcSummary = r.getString(R.string.sip_registered);
        } else if (state == WifiManager.FMC_STATE_PPP_CONNECTING) {
            mFmcSummary = r.getString(R.string.ppp_connecting);
        } else if (state == WifiManager.FMC_STATE_PPP_CONNECTED) {
            mFmcSummary = r.getString(R.string.ppp_connected);
        } else if (state == WifiManager.FMC_STATE_PPP_FAIL) {
            mFmcSummary = r.getString(R.string.ppp_failed_msg);
        } else {
            mShowFmcSummary = false;
            mFmcSummary = null;
        }
        updateAccessPoints();
    }

    private void modifyApPriority() {
        Intent intent = new Intent(WIFI_MODIFY_AP_PRIORITY_ACTION);
        getActivity().sendBroadcast(intent);
    }

    private void showErrorConnectionMessage(Intent intent) {
        int errorMessageId = 0;

        if (intent.hasExtra(WifiManager.EXTRA_SUPPLICANT_ERROR)) {
            errorMessageId = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 0);
        }
        DetailedState state = WifiInfo.getDetailedStateOf((SupplicantState) intent
                .getParcelableExtra(WifiManager.EXTRA_NEW_STATE));

        if (errorMessageId == WifiManager.ERROR_AUTHENTICATING) {
            if (shouldShowErrorMsg) {
                Toast.makeText(getActivity(), R.string.error_authenticating,
                        Toast.LENGTH_LONG).show();
                shouldShowErrorMsg = false;
            }
            handleConnectApFailed();
        }

        if (state == DetailedState.FAILED) {
            if (shouldShowErrorMsg) {
                Toast.makeText(getActivity(), R.string.error_connecting,
                        Toast.LENGTH_LONG).show();

                shouldShowErrorMsg = false;
            }
            handleConnectApFailed();
        }

    }

    private void handleConnectApFailed() {
        disableSelectedNetwork();
        subtractSelectedNetworkPriority();
        enableNetworks();
        mWifiManager.startScan();
    }

    private void disableSelectedNetwork() {
        Log.d(TAG, "disableSelectedNetwork");
        mSelectedAccessPoint = (AccessPoint) getPreferenceScreen().getPreference(0);
        boolean result = mWifiManager.disableNetwork(mSelectedAccessPoint.networkId);
        Log.d(TAG, "result: " + result);
    }

    private void subtractSelectedNetworkPriority() {
        Log.d(TAG, "subtractSelectedNetworkPriority");
        WifiConfiguration config = mSelectedAccessPoint.getConfig();
        if (config != null) {
            config.priority -= getPreferenceScreen().getPreferenceCount() + 1;
//            mWifiManager.updateNetwork(config);
//            mWifiManager.saveConfiguration();
            mWifiManager.save(mChannel, config, mSaveListener);
        }
    }

    private void enableNetworks() {
        for (int i = getPreferenceScreen().getPreferenceCount() - 1; i >= 0; --i) {
            WifiConfiguration config = ((AccessPoint) getPreferenceScreen()
                    .getPreference(i)).getConfig();
            if (config != null && config.status != Status.ENABLED) {
                mWifiManager.enableNetwork(config.networkId, false);
            }
        }
    }

    private void handleConnectToAp(WifiConfiguration config) {
        boolean isWepAp = AccessPoint.getSecurity(config) == AccessPoint.SECURITY_WEP;
        Log.d(TAG, "handleConnectToAp, if wep ap? " + isWepAp);
        if (isWepAp) {
            mFmcConnectApHandler.resume();
        }
    }

    private class FmcConnectApHandler extends Handler {
        void resume() {
            Log.d(TAG, "FmcConnectApHandler.resume()");
            if (!hasMessages(0)) {
                sendEmptyMessageDelayed(0, 8000);
            }
        }

        void pause() {
            Log.d(TAG, "FmcConnectApHandler.pause()");
            if (hasMessages(0)) {
                removeMessages(0);
            }
        }

        @Override
        public void handleMessage(Message message) {
            NetworkInfo info = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE))
                    .getActiveNetworkInfo();
            Log.d(TAG, "networkInfo: " + info);
            if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI
                    && info.getState() == NetworkInfo.State.CONNECTED) {
                return;
            }
            Toast.makeText(getActivity(), R.string.wifi_failed_connect_msg,
                    Toast.LENGTH_LONG).show();

            handleConnectApFailed();
        }
    }
/* Add 20120823 TS-FMC-V2 end */
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // If the PopupWindow is shown when rotate screen, dismiss it.
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismissPop();
        }
    }
}
