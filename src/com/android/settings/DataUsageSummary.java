/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2012-2013, The Linux Foundation. All Rights Reserved.
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

import static android.net.ConnectivityManager.TYPE_ETHERNET;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.ConnectivityManager.TYPE_WIMAX;
import static android.net.NetworkPolicy.LIMIT_DISABLED;
import static android.net.NetworkPolicy.WARNING_DISABLED;
import static android.net.NetworkPolicyManager.EXTRA_NETWORK_TEMPLATE;
import static android.net.NetworkPolicyManager.POLICY_NONE;
import static android.net.NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND;
import static android.net.NetworkPolicyManager.computeLastCycleBoundary;
import static android.net.NetworkPolicyManager.computeNextCycleBoundary;
import static android.net.NetworkTemplate.MATCH_MOBILE_3G_LOWER;
import static android.net.NetworkTemplate.MATCH_MOBILE_4G;
import static android.net.NetworkTemplate.MATCH_MOBILE_ALL;
import static android.net.NetworkTemplate.MATCH_WIFI;
import static android.net.NetworkTemplate.buildTemplateEthernet;
import static android.net.NetworkTemplate.buildTemplateMobile3gLower;
import static android.net.NetworkTemplate.buildTemplateMobile4g;
import static android.net.NetworkTemplate.buildTemplateMobileAll;
import static android.net.NetworkTemplate.buildTemplateWifiWildcard;
import static android.net.TrafficStats.GB_IN_BYTES;
import static android.net.TrafficStats.MB_IN_BYTES;
import static android.net.TrafficStats.UID_REMOVED;
import static android.net.TrafficStats.UID_TETHERING;
import static android.telephony.TelephonyManager.SIM_STATE_ABSENT;
import static android.telephony.TelephonyManager.SIM_STATE_READY;
import static android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
import static android.text.format.DateUtils.FORMAT_SHOW_DATE;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.settings.Utils.prepareCustomPreferencesList;

import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.INetworkPolicyManager;
import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.net.NetworkStats;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.INetworkManagementService;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserId;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.text.format.Time;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.TextView;

import com.android.internal.telephony.Phone;
import com.android.settings.drawable.InsetBoundsDrawable;
import com.android.settings.net.ChartData;
import com.android.settings.net.ChartDataLoader;
import com.android.settings.net.DataUsageMeteredSettings;
import com.android.settings.net.NetworkPolicyEditor;
import com.android.settings.net.SummaryForAllUidLoader;
import com.android.settings.net.UidDetail;
import com.android.settings.net.UidDetailProvider;
import com.android.settings.widget.ChartDataUsageView;
import com.android.settings.widget.ChartDataUsageView.DataUsageChartListener;
import com.android.settings.widget.DataUsageTextView;
import com.android.settings.widget.PieChartView;
import com.google.android.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import libcore.util.Objects;
import com.qrd.plugin.feature_query.FeatureQuery;

/**
 * Panel showing data usage history across various networks, including options
 * to inspect based on usage cycle and control through {@link NetworkPolicy}.
 */
public class DataUsageSummary extends Fragment {
    private static final String TAG = "DataUsage";
    private static final boolean LOGD = false;

    // TODO: remove this testing code
    private static final boolean TEST_ANIM = false;
    private static final boolean TEST_RADIOS = false;

    private static final String TEST_RADIOS_PROP = "test.radios";
    private static final String TEST_SUBSCRIBER_PROP = "test.subscriberid";

    private static final String TAB_3G = "3g";
    private static final String TAB_4G = "4g";
    private static final String TAB_MOBILE = "mobile";
    private static final String TAB_WIFI = "wifi";
    private static final String TAB_ETHERNET = "ethernet";
    private static final String TAB_SLOT1 = "slot1";
    private static final String TAB_SLOT2 = "slot2";
    private static final String ACTIVE_SUBSCRIBER = "MULTI_SIM_DATA_ACTIVE_SUB";

    private static final String TAG_CONFIRM_DATA_DISABLE = "confirmDataDisable";
    private static final String TAG_CONFIRM_DATA_ROAMING = "confirmDataRoaming";
    private static final String TAG_CONFIRM_LIMIT = "confirmLimit";
    private static final String TAG_CYCLE_EDITOR = "cycleEditor";
    private static final String TAG_WARNING_EDITOR = "warningEditor";
    private static final String TAG_LIMIT_EDITOR = "limitEditor";
    private static final String TAG_CONFIRM_RESTRICT = "confirmRestrict";
    private static final String TAG_DENIED_RESTRICT = "deniedRestrict";
    private static final String TAG_CONFIRM_APP_RESTRICT = "confirmAppRestrict";
    private static final String TAG_CONFIRM_AUTO_SYNC_CHANGE = "confirmAutoSyncChange";
    private static final String TAG_APP_DETAILS = "appDetails";
    private static final String TAG_WARNING_DATA_STATISTICS = "confirmDataStatistics";

    private static final int LOADER_CHART_DATA = 2;
    private static final int LOADER_SUMMARY = 3;
    private static final long DEFAULT_LIMITED_BYTES = 5 * GB_IN_BYTES;
    // Set input max value as 102400000, so NumberPicker can compare with integer
    private static final int INPUT_MAX_VALUE = (int)(Math.pow(10,5) * 1024);

    private INetworkManagementService mNetworkService;
    private INetworkStatsService mStatsService;
    private NetworkPolicyManager mPolicyManager;
    private ConnectivityManager mConnService;

    private INetworkStatsSession mStatsSession;

    private static final String PREF_FILE = "data_usage";
    private static final String PREF_SHOW_WIFI = "show_wifi";
    private static final String PREF_SHOW_ETHERNET = "show_ethernet";

    private SharedPreferences mPrefs;

    private TabHost mTabHost;
    private ViewGroup mTabsContainer;
    private TabWidget mTabWidget;
    private ListView mListView;
    private DataUsageAdapter mAdapter;

    /** Distance to inset content from sides, when needed. */
    private int mInsetSide = 0;

    private ViewGroup mHeader;

    private ViewGroup mNetworkSwitchesContainer;
    private LinearLayout mNetworkSwitches;
    private Switch mDataEnabled;
    private View mDataEnabledView;
    private CheckBox mDisableAtLimit;
    private View mDisableAtLimitView;

    private View mCycleView;
    private Spinner mCycleSpinner;
    private CycleAdapter mCycleAdapter;

    private ChartDataUsageView mChart;
    private DataUsageTextView mUsageSummary;
    private TextView mEmpty;

    private View mAppDetail;
    private ImageView mAppIcon;
    private ViewGroup mAppTitles;
    private PieChartView mAppPieChart;
    private TextView mAppForeground;
    private TextView mAppBackground;
    private Button mAppSettings;

    private Switch mDataStatisticsSwitch;
    private LinearLayout mAppSwitches;
    private CheckBox mAppRestrict;
    private View mAppRestrictView;

    private boolean mShowWifi = false;
    private boolean mShowEthernet = false;

    private NetworkTemplate mTemplate;
    private ChartData mChartData;

    private AppItem mCurrentApp = null;

    private Intent mAppSettingsIntent;

    private NetworkPolicyEditor mPolicyEditor;

    private String mCurrentTab = null;
    private String mIntentTab = null;

    private MenuItem mMenuDataRoaming;
    private MenuItem mMenuRestrictBackground;
    private MenuItem mMenuAutoSync;

    /** Flag used to ignore listeners during binding. */
    private boolean mBinding;

    private UidDetailProvider mUidDetailProvider;
    private int mSummaryRes;
    private String mRangePhrase = null;
    private NetworkStatusChangeIntentReceiver mReceiver;
    private AirPlaneModeChangeIntentReceiver mAirPlaneModeReceiver;

    /**Flag used to check the network and radio state.*/
    private boolean mMobileRadioStateOld;
    private boolean mMobile4gRadioStateOld;
    private boolean mWifiRadioStateOld;
    private boolean mEthernetStateOld;

    private static boolean isScreenOn=true;

    private Handler mHandler = new Handler();
    private Runnable runnable = new Runnable(){
        @Override
        public void run(){
            boolean mMobileRadioStateNew = (hasReadyMobileRadio(getActivity()));
            boolean mMobile4gRadioStateNew = (isMobilePolicySplit() && hasReadyMobile4gRadio(getActivity()));
            boolean mWifiRadioStateNew = (mShowWifi && hasWifiRadio(getActivity()));
            boolean mEthernetStateNew = (mShowEthernet && hasEthernet(getActivity()));

            if ((mMobileRadioStateNew != mMobileRadioStateOld) || (mMobile4gRadioStateNew != mMobile4gRadioStateOld) || (mWifiRadioStateNew != mWifiRadioStateOld) || (mEthernetStateNew != mEthernetStateOld)) {
                updateBody();
                mMobileRadioStateOld = mMobileRadioStateNew;
                mMobile4gRadioStateOld = mMobile4gRadioStateNew;
                mWifiRadioStateOld = mWifiRadioStateNew;
                mEthernetStateOld = mEthernetStateNew;
            } else {
                mHandler.postDelayed(runnable,2000);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Context context = getActivity();

        mNetworkService = INetworkManagementService.Stub.asInterface(
                ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE));
        mStatsService = INetworkStatsService.Stub.asInterface(
                ServiceManager.getService(Context.NETWORK_STATS_SERVICE));
        mPolicyManager = NetworkPolicyManager.from(context);
        mConnService = ConnectivityManager.from(context);

        mPrefs = getActivity().getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);

        mPolicyEditor = new NetworkPolicyEditor(mPolicyManager);
        mPolicyEditor.read();

        mShowWifi = mPrefs.getBoolean(PREF_SHOW_WIFI, true);
        mShowEthernet = mPrefs.getBoolean(PREF_SHOW_ETHERNET, false);

        // override preferences when no mobile radio
        if (!hasReadyMobileRadio(context)) {
            mShowWifi = hasWifiRadio(context);
            mShowEthernet = hasEthernet(context);
        }
        isScreenOn=true;
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        final Context context = inflater.getContext();
        final View view = inflater.inflate(R.layout.data_usage_summary, container, false);

        mUidDetailProvider = new UidDetailProvider(context);

        try {
            mStatsSession = mStatsService.openSession();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        mTabHost = (TabHost) view.findViewById(android.R.id.tabhost);
        mTabsContainer = (ViewGroup) view.findViewById(R.id.tabs_container);
        mTabWidget = (TabWidget) view.findViewById(android.R.id.tabs);
        mListView = (ListView) view.findViewById(android.R.id.list);

        // decide if we need to manually inset our content, or if we should rely
        // on parent container for inset.
        final boolean shouldInset = mListView.getScrollBarStyle()
                == View.SCROLLBARS_OUTSIDE_OVERLAY;
        if (shouldInset) {
            mInsetSide = view.getResources().getDimensionPixelOffset(
                    com.android.internal.R.dimen.preference_fragment_padding_side);
        } else {
            mInsetSide = 0;
        }

        // adjust padding around tabwidget as needed
        prepareCustomPreferencesList(container, view, mListView, true);

        mTabHost.setup();
        mTabHost.setOnTabChangedListener(mTabListener);

        mHeader = (ViewGroup) inflater.inflate(R.layout.data_usage_header, mListView, false);
        mHeader.setClickable(true);

        mListView.addHeaderView(mHeader, null, true);
        mListView.setItemsCanFocus(true);

        if (mInsetSide > 0) {
            // inset selector and divider drawables
            insetListViewDrawables(mListView, mInsetSide);
            mHeader.setPadding(mInsetSide, 0, mInsetSide, 0);
        }

        {
            // bind network switches
            mNetworkSwitchesContainer = (ViewGroup) mHeader.findViewById(
                    R.id.network_switches_container);
            mNetworkSwitches = (LinearLayout) mHeader.findViewById(R.id.network_switches);

            mDataEnabled = new Switch(inflater.getContext());
            mDataEnabledView = inflatePreference(inflater, mNetworkSwitches, mDataEnabled);
            mDataEnabled.setOnCheckedChangeListener(mDataEnabledListener);
            mDataEnabledView.setClickable(true);
            mNetworkSwitches.addView(mDataEnabledView);

            mDataStatisticsSwitch =
                (Switch) mHeader.findViewById(R.id.data_usage_data_statistics_switch);
            mDataStatisticsSwitch.setChecked(isDataStatisticsEnabled());
            mDataStatisticsSwitch.setOnCheckedChangeListener(mDataStatisticsEnabledListener);
            //just hide mDataStatisticsSwitch now,not to close data Statistics
            mDataStatisticsSwitch.setVisibility(View.GONE);

            mDisableAtLimit = new CheckBox(inflater.getContext());
            mDisableAtLimit.setClickable(false);
            mDisableAtLimit.setFocusable(false);
            mDisableAtLimitView = inflatePreference(inflater, mNetworkSwitches, mDisableAtLimit);
            mDisableAtLimitView.setClickable(true);
            mDisableAtLimitView.setFocusable(true);
            mDisableAtLimitView.setOnClickListener(mDisableAtLimitListener);
            mNetworkSwitches.addView(mDisableAtLimitView);
        }

        // bind cycle dropdown
        mCycleView = mHeader.findViewById(R.id.cycles);
        mCycleSpinner = (Spinner) mCycleView.findViewById(R.id.cycles_spinner);
        mCycleAdapter = new CycleAdapter(context);
        mCycleSpinner.setAdapter(mCycleAdapter);
        mCycleSpinner.setOnItemSelectedListener(mCycleListener);

        mChart = (ChartDataUsageView) mHeader.findViewById(R.id.chart);
        mChart.setListener(mChartListener);
        mChart.bindNetworkPolicy(null);
        mChart.mActivity = getActivity();

        {
            // bind app detail controls
            mAppDetail = mHeader.findViewById(R.id.app_detail);
            mAppIcon = (ImageView) mAppDetail.findViewById(R.id.app_icon);
            mAppTitles = (ViewGroup) mAppDetail.findViewById(R.id.app_titles);
            mAppPieChart = (PieChartView) mAppDetail.findViewById(R.id.app_pie_chart);
            mAppForeground = (TextView) mAppDetail.findViewById(R.id.app_foreground);
            mAppBackground = (TextView) mAppDetail.findViewById(R.id.app_background);
            mAppSwitches = (LinearLayout) mAppDetail.findViewById(R.id.app_switches);

            mAppSettings = (Button) mAppDetail.findViewById(R.id.app_settings);
            mAppSettings.setOnClickListener(mAppSettingsListener);

            mAppRestrict = new CheckBox(inflater.getContext());
            mAppRestrict.setClickable(false);
            mAppRestrict.setFocusable(false);
            mAppRestrictView = inflatePreference(inflater, mAppSwitches, mAppRestrict);
            mAppRestrictView.setClickable(true);
            mAppRestrictView.setFocusable(true);
            mAppRestrictView.setOnClickListener(mAppRestrictListener);
            mAppSwitches.addView(mAppRestrictView);
        }

        mUsageSummary = (DataUsageTextView) mHeader.findViewById(R.id.usage_summary);
        mEmpty = (TextView) mHeader.findViewById(android.R.id.empty);

        mAdapter = new DataUsageAdapter(mUidDetailProvider, mInsetSide);
        mListView.setOnItemClickListener(mListListener);
        mListView.setAdapter(mAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        // pick default tab based on incoming intent
        final Intent intent = getActivity().getIntent();
        mIntentTab = computeTabFromIntent(intent);

        // this kicks off chain reaction which creates tabs, binds the body to
        // selected network, and binds chart, cycles and detail list.
        updateTabs();

        // Register a broadcast receiver to listen the mobile connectivity
        // changed.
        mReceiver = new NetworkStatusChangeIntentReceiver();
        IntentFilter filter = new IntentFilter(
                ConnectivityManager.MOBILE_CONNECTIVITY_ACTION);
        getActivity().registerReceiver(mReceiver, filter);

        //Register a broadcast receiver to listen the airplane mode changed.
        mAirPlaneModeReceiver = new AirPlaneModeChangeIntentReceiver();
        IntentFilter mFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        getActivity().registerReceiver(mAirPlaneModeReceiver, mFilter);

        // kick off background task to update stats
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    // wait a few seconds before kicking off
                    Thread.sleep(2 * DateUtils.SECOND_IN_MILLIS);
                    mStatsService.forceUpdate();
                } catch (InterruptedException e) {
                } catch (RemoteException e) {
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                if (isAdded()) {
                    updateBody();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        mMobileRadioStateOld = (hasReadyMobileRadio(getActivity()));
        mMobile4gRadioStateOld = (isMobilePolicySplit() && hasReadyMobile4gRadio(getActivity()));
        mWifiRadioStateOld = (mShowWifi && hasWifiRadio(getActivity()));
        mEthernetStateOld = (mShowEthernet && hasEthernet(getActivity()));
    }

    /**
     * Receives notifications when enable/disable mobile data.
     */
    private class NetworkStatusChangeIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String actionStr = intent.getAction();
            if (ConnectivityManager.MOBILE_CONNECTIVITY_ACTION
                    .equals(actionStr)) {
                boolean enabled = intent.getBooleanExtra(ConnectivityManager.EXTRA_ENABLED, false);
                // Make the DataEnabled button to correct state.
                mDataEnabled.setChecked(enabled);
            }
        }
    }

    /**
     * Receives notifications when enable/disable airplane mode.
     */
    private class AirPlaneModeChangeIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String actionStr = intent.getAction();
            if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(actionStr)){
                boolean enabled = intent.getBooleanExtra("state", false);
                if (enabled) {
                    mHandler.post(runnable);
                }
            }
        }
    }

    private static boolean isAirplaneModeOn(Context context) {
        return (Settings.System.getInt(context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1 ? true : false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.data_usage, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        final Context context = getActivity();
        final boolean appDetailMode = isAppDetailMode();

        mMenuDataRoaming = menu.findItem(R.id.data_usage_menu_roaming);
        mMenuDataRoaming.setVisible(hasReadyMobileRadio(context) && !appDetailMode);
        mMenuDataRoaming.setChecked(getDataRoaming());

        mMenuRestrictBackground = menu.findItem(R.id.data_usage_menu_restrict_background);
        mMenuRestrictBackground.setVisible(hasReadyMobileRadio(context) && !appDetailMode);
        mMenuRestrictBackground.setChecked(mPolicyManager.getRestrictBackground());

        mMenuAutoSync = menu.findItem(R.id.data_usage_menu_auto_sync);
        mMenuAutoSync.setChecked(ContentResolver.getMasterSyncAutomatically());

        final MenuItem split4g = menu.findItem(R.id.data_usage_menu_split_4g);
        split4g.setVisible(hasReadyMobile4gRadio(context) && !appDetailMode);
        split4g.setChecked(isMobilePolicySplit());

        final MenuItem showWifi = menu.findItem(R.id.data_usage_menu_show_wifi);
        String showWifiTittle = Utils.replaceAllWiFi(showWifi.getTitle().toString());
        showWifi.setTitle(showWifiTittle);
        if (hasWifiRadio(context)) {
            showWifi.setVisible(!appDetailMode);
            showWifi.setChecked(mShowWifi);
        } else {
            showWifi.setVisible(false);
        }

        final MenuItem showEthernet = menu.findItem(R.id.data_usage_menu_show_ethernet);
        if (hasEthernet(context) && hasReadyMobileRadio(context)) {
            showEthernet.setVisible(!appDetailMode);
            showEthernet.setChecked(mShowEthernet);
        } else {
            showEthernet.setVisible(false);
        }

        final MenuItem metered = menu.findItem(R.id.data_usage_menu_metered);
        if (hasReadyMobileRadio(context) || hasWifiRadio(context)) {
            metered.setVisible(!appDetailMode);
        } else {
            metered.setVisible(false);
        }

        final MenuItem help = menu.findItem(R.id.data_usage_menu_help);
        String helpUrl;
        if (!TextUtils.isEmpty(helpUrl = getResources().getString(R.string.help_url_data_usage))) {
            Intent helpIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl));
            helpIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            help.setIntent(helpIntent);
            help.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        } else {
            help.setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.data_usage_menu_roaming: {
                final boolean dataRoaming = !item.isChecked();
                if (dataRoaming) {
                    ConfirmDataRoamingFragment.show(this);
                } else {
                    // no confirmation to disable roaming
                    setDataRoaming(false);
                }
                return true;
            }
            case R.id.data_usage_menu_restrict_background: {
                final boolean restrictBackground = !item.isChecked();
                if (restrictBackground) {
                    ConfirmRestrictFragment.show(this);
                } else {
                    // no confirmation to drop restriction
                    setRestrictBackground(false);
                }
                return true;
            }
            case R.id.data_usage_menu_split_4g: {
                final boolean mobileSplit = !item.isChecked();
                setMobilePolicySplit(mobileSplit);
                item.setChecked(isMobilePolicySplit());
                updateTabs();
                return true;
            }
            case R.id.data_usage_menu_show_wifi: {
                mShowWifi = !item.isChecked();
                mPrefs.edit().putBoolean(PREF_SHOW_WIFI, mShowWifi).apply();
                item.setChecked(mShowWifi);
                updateTabs();
                return true;
            }
            case R.id.data_usage_menu_show_ethernet: {
                mShowEthernet = !item.isChecked();
                mPrefs.edit().putBoolean(PREF_SHOW_ETHERNET, mShowEthernet).apply();
                item.setChecked(mShowEthernet);
                updateTabs();
                return true;
            }
            case R.id.data_usage_menu_metered: {
                final PreferenceActivity activity = (PreferenceActivity) getActivity();
                activity.startPreferencePanel(DataUsageMeteredSettings.class.getCanonicalName(), null,
                        R.string.data_usage_metered_title, null, this, 0);
                return true;
            }
            case R.id.data_usage_menu_auto_sync: {
                ConfirmAutoSyncChangeFragment.show(this, !item.isChecked());
                return true;
            }
        }
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister the broadcast receiver when the fragment is out of
        // foreground.
        getActivity().unregisterReceiver(mReceiver);
        getActivity().unregisterReceiver(mAirPlaneModeReceiver);

        mHandler.removeCallbacks(runnable);
        isScreenOn=false;
    }

    @Override
    public void onDestroy() {
        mDataEnabledView = null;
        mDisableAtLimitView = null;

        mUidDetailProvider.clearCache();
        mUidDetailProvider = null;

        TrafficStats.closeQuietly(mStatsSession);

        if (this.isRemoving()) {
            getFragmentManager()
                    .popBackStack(TAG_APP_DETAILS, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        super.onDestroy();
    }

    /**
     * Build and assign {@link LayoutTransition} to various containers. Should
     * only be assigned after initial layout is complete.
     */
    private void ensureLayoutTransitions() {
        // skip when already setup
        if (mChart.getLayoutTransition() != null) return;

        mTabsContainer.setLayoutTransition(buildLayoutTransition());
        mHeader.setLayoutTransition(buildLayoutTransition());
        mNetworkSwitchesContainer.setLayoutTransition(buildLayoutTransition());

        final LayoutTransition chartTransition = buildLayoutTransition();
        chartTransition.disableTransitionType(LayoutTransition.APPEARING);
        chartTransition.disableTransitionType(LayoutTransition.DISAPPEARING);
        mChart.setLayoutTransition(chartTransition);
    }

    private static LayoutTransition buildLayoutTransition() {
        final LayoutTransition transition = new LayoutTransition();
        if (TEST_ANIM) {
            transition.setDuration(1500);
        }
        transition.setAnimateParentHierarchy(false);
        return transition;
    }

    /**
     * Rebuild all tabs based on {@link NetworkPolicyEditor} and
     * {@link #mShowWifi}, hiding the tabs entirely when applicable. Selects
     * first tab, and kicks off a full rebind of body contents.
     */
    private void updateTabs() {
        final Context context = getActivity();
        if(isScreenOn==false){
            isScreenOn=true;
            return;
        }
        mTabHost.clearAllTabs();

        // Record current tap before rebuild all tabs, and finally restore the tab.
        String lastTab = null;
        if (mCurrentTab != null) {
            lastTab = mCurrentTab;
        }

        final boolean mobileSplit = isMobilePolicySplit();
        if (mobileSplit && hasReadyMobile4gRadio(context)) {
            mTabHost.addTab(buildTabSpec(TAB_3G, R.string.data_usage_tab_3g));
            mTabHost.addTab(buildTabSpec(TAB_4G, R.string.data_usage_tab_4g));
        } else if (hasPresentMobileRadio(context)) {
            if(FeatureQuery.FEATURE_DUAL_SIM_DATA_USAGE) {
                if (TelephonyManager.getDefault().getPhoneCount() == 2) {
                    mTabHost.addTab(buildTabSpec(TAB_SLOT1,
                            R.string.data_usage_tab_slot1));
                    mTabHost.addTab(buildTabSpec(TAB_SLOT2,
                            R.string.data_usage_tab_slot2));
                } else {
                    mTabHost.addTab(buildTabSpec(TAB_MOBILE,
                    R.string.data_usage_tab_mobile));
                }
            } else {
                mTabHost.addTab(buildTabSpec(TAB_MOBILE,
                R.string.data_usage_tab_mobile));
            }

        }
        if (mShowWifi && hasWifiRadio(context)) {
            mTabHost.addTab(buildTabSpec(TAB_WIFI, R.string.data_usage_tab_wifi));
        }
        if (mShowEthernet && hasEthernet(context)) {
            mTabHost.addTab(buildTabSpec(TAB_ETHERNET, R.string.data_usage_tab_ethernet));
        }

        final boolean noTabs = mTabWidget.getTabCount() == 0;
        final boolean multipleTabs = mTabWidget.getTabCount() > 1;
        mTabWidget.setVisibility(multipleTabs ? View.VISIBLE : View.GONE);
        if (mIntentTab != null && lastTab == null) {
            if (Objects.equal(mIntentTab, mTabHost.getCurrentTabTag())) {
                // already hit updateBody() when added; ignore
                updateBody();
            } else {
                mTabHost.setCurrentTabByTag(mIntentTab);
            }
            mIntentTab = null;
        } else if (noTabs) {
            // no usable tabs, so hide body
            updateBody();
        } else if (lastTab != null) {
            mTabHost.setCurrentTabByTag(lastTab);
        } else {
            // already hit updateBody() when added; ignore
        }
    }

    /**
     * Factory that provide empty {@link View} to make {@link TabHost} happy.
     */
    private TabContentFactory mEmptyTabContent = new TabContentFactory() {
        @Override
        public View createTabContent(String tag) {
            return new View(mTabHost.getContext());
        }
    };

    /**
     * Build {@link TabSpec} with thin indicator, and empty content.
     */
    private TabSpec buildTabSpec(String tag, int titleRes) {
        return mTabHost.newTabSpec(tag).setIndicator(Utils.replaceAllWiFi((String)getText(titleRes))).setContent(
                mEmptyTabContent);
    }

    private OnTabChangeListener mTabListener = new OnTabChangeListener() {
        @Override
        public void onTabChanged(String tabId) {
            // user changed tab; update body
            updateBody();
        }
    };

    /**
     * Update body content based on current tab. Loads
     * {@link NetworkStatsHistory} and {@link NetworkPolicy} from system, and
     * binds them to visible controls.
     */
    private void updateBody() {
        mBinding = true;
        if (!isAdded()) return;

        final Context context = getActivity();
        final String currentTab = mTabHost.getCurrentTabTag();

        // save the current tab.
        Settings.System.putString(context.getContentResolver(),
                ACTIVE_SUBSCRIBER, currentTab);
        if (currentTab == null) {
            Log.w(TAG, "no tab selected; hiding body");
            mListView.setVisibility(View.GONE);
            return;
        } else {
            mListView.setVisibility(View.VISIBLE);
        }

        final boolean tabChanged = !currentTab.equals(mCurrentTab);
        mCurrentTab = currentTab;

        if (LOGD) Log.d(TAG, "updateBody() with currentTab=" + currentTab);

        mDataEnabledView.setVisibility(View.VISIBLE);
        if(isMobileDataEnabled() && mDataStatisticsSwitch.isChecked() &&
                !isAirplaneModeOn(getActivity())) {
            mDisableAtLimitView.setVisibility(View.VISIBLE);
        } else {
            // If one of the checkbox is unchecked, set the limit view to invisible
            mDisableAtLimitView.setVisibility(View.GONE);
        }

        // TODO: remove mobile tabs when SIM isn't ready
        final TelephonyManager tele = TelephonyManager.from(context);

        int dataSub = MSimTelephonyManager.getDefault().getPreferredDataSubscription();
        if (TAB_MOBILE.equals(currentTab)) {
            setPreferenceTitle(mDataEnabledView, R.string.data_usage_enable_mobile);
            setPreferenceTitle(mDisableAtLimitView, R.string.data_usage_disable_mobile_limit);
            mTemplate = buildTemplateMobileAll(getActiveSubscriberId(context));

        } else if (TAB_SLOT1.equals(currentTab)) {
            setPreferenceTitle(mDataEnabledView, R.string.data_usage_enable_mobile);
            setPreferenceTitle(mDisableAtLimitView, R.string.data_usage_disable_mobile_limit);
            mTemplate = buildTemplateMobileAll(getActiveSubscriberId(0));
            if (dataSub == 0)
                mDataEnabledView.setVisibility(View.VISIBLE);
            else
                mDataEnabledView.setVisibility(View.GONE);

        } else if (TAB_SLOT2.equals(currentTab)) {
            setPreferenceTitle(mDataEnabledView, R.string.data_usage_enable_mobile);
            setPreferenceTitle(mDisableAtLimitView, R.string.data_usage_disable_mobile_limit);
            mTemplate = buildTemplateMobileAll(getActiveSubscriberId(1));
            if (dataSub == 1)
                mDataEnabledView.setVisibility(View.VISIBLE);
            else
                mDataEnabledView.setVisibility(View.GONE);

        } else if (TAB_3G.equals(currentTab)) {
            setPreferenceTitle(mDataEnabledView, R.string.data_usage_enable_3g);
            setPreferenceTitle(mDisableAtLimitView, R.string.data_usage_disable_3g_limit);
            // TODO: bind mDataEnabled to 3G radio state
            mTemplate = buildTemplateMobile3gLower(getActiveSubscriberId(context));

        } else if (TAB_4G.equals(currentTab)) {
            setPreferenceTitle(mDataEnabledView, R.string.data_usage_enable_4g);
            setPreferenceTitle(mDisableAtLimitView, R.string.data_usage_disable_4g_limit);
            // TODO: bind mDataEnabled to 4G radio state
            mTemplate = buildTemplateMobile4g(getActiveSubscriberId(context));

        } else if (TAB_WIFI.equals(currentTab)) {
            // wifi doesn't have any controls
            mDataEnabledView.setVisibility(View.GONE);
            mDisableAtLimitView.setVisibility(View.GONE);
            mTemplate = buildTemplateWifiWildcard();

        } else if (TAB_ETHERNET.equals(currentTab)) {
            // ethernet doesn't have any controls
            mDataEnabledView.setVisibility(View.GONE);
            mDisableAtLimitView.setVisibility(View.GONE);
            mTemplate = buildTemplateEthernet();

        } else {
            throw new IllegalStateException("unknown tab: " + currentTab);
        }

        //this view move to parent view.
        mDataEnabledView.setVisibility(View.GONE);

        // kick off loader for network history
        // TODO: consider chaining two loaders together instead of reloading
        // network history when showing app detail.
        getLoaderManager().restartLoader(LOADER_CHART_DATA,
                ChartDataLoader.buildArgs(mTemplate, mCurrentApp), mChartDataCallbacks);

        // detail mode can change visible menus, invalidate
        getActivity().invalidateOptionsMenu();

        mBinding = false;
    }

    private boolean isAppDetailMode() {
        return mCurrentApp != null;
    }

    /**
     * Update UID details panels to match {@link #mCurrentApp}, showing or
     * hiding them depending on {@link #isAppDetailMode()}.
     */
    private void updateAppDetail() {
        final Context context = getActivity();
        final PackageManager pm = context.getPackageManager();
        final LayoutInflater inflater = getActivity().getLayoutInflater();

        if (isAppDetailMode()) {
            mAppDetail.setVisibility(View.VISIBLE);
            mCycleAdapter.setChangeVisible(false);
        } else {
            mAppDetail.setVisibility(View.GONE);
            mCycleAdapter.setChangeVisible(true);

            // hide detail stats when not in detail mode
            mChart.bindDetailNetworkStats(null);
            return;
        }

        // remove warning/limit sweeps while in detail mode
        mChart.bindNetworkPolicy(null);

        // show icon and all labels appearing under this app
        final int appId = mCurrentApp.appId;
        final UidDetail detail = mUidDetailProvider.getUidDetail(appId, true);
        mAppIcon.setImageDrawable(detail.icon);

        mAppTitles.removeAllViews();
        if (detail.detailLabels != null) {
            for (CharSequence label : detail.detailLabels) {
                mAppTitles.addView(inflateAppTitle(inflater, mAppTitles, label));
            }
        } else {
            mAppTitles.addView(inflateAppTitle(inflater, mAppTitles, detail.label));
        }

        // enable settings button when package provides it
        // TODO: target torwards entire UID instead of just first package
        final String[] packageNames = pm.getPackagesForUid(appId);
        if (packageNames != null && packageNames.length > 0) {
            mAppSettingsIntent = new Intent(Intent.ACTION_MANAGE_NETWORK_USAGE);
            mAppSettingsIntent.setPackage(packageNames[0]);
            mAppSettingsIntent.addCategory(Intent.CATEGORY_DEFAULT);

            final boolean matchFound = pm.resolveActivity(mAppSettingsIntent, 0) != null;
            mAppSettings.setEnabled(matchFound);
            mAppSettings.setVisibility(View.VISIBLE);

        } else {
            mAppSettingsIntent = null;
            mAppSettings.setVisibility(View.GONE);
        }

        updateDetailData();

        if (UserId.isApp(appId) && !mPolicyManager.getRestrictBackground()
                && isBandwidthControlEnabled() && hasReadyMobileRadio(context)) {
            setPreferenceTitle(mAppRestrictView, R.string.data_usage_app_restrict_background);
            setPreferenceSummary(mAppRestrictView,
                    getString(R.string.data_usage_app_restrict_background_summary));

            mAppRestrictView.setVisibility(View.VISIBLE);
            mAppRestrict.setChecked(getAppRestrictBackground());

        } else {
            mAppRestrictView.setVisibility(View.GONE);
        }
    }

    private void setPolicyWarningBytes(long warningBytes) {
        if (LOGD) Log.d(TAG, "setPolicyWarningBytes()");
        // Same process of setPolicyLimitBytes().
        if (warningBytes <= (INPUT_MAX_VALUE * MB_IN_BYTES)) {
            mPolicyEditor.setPolicyWarningBytes(mTemplate, warningBytes);
        } else {
            mPolicyEditor.setPolicyWarningBytes(mTemplate, INPUT_MAX_VALUE * MB_IN_BYTES);
        }
        updatePolicy(false);
    }

    private void setPolicyLimitBytes(long limitBytes) {
        if (LOGD)
            Log.d(TAG, "setPolicyLimitBytes()");

        // There are two ways for setting data limit:
        // 1. sweep up or down the limit bar
        // 2. input number in dialog popped up when click label to the left of
        // limit bar
        // the maximum number in way 2 is INPUT_MAX_VALUE,ie 102400000;
        // the maximum number in way 1 is Long.MAX_VALUE. Number bigger than
        // INPUT_MAX_VALUE got in way 1 will not be shown properly in dialog
        // in way 2. so maximum number will be fixed at INPUT_MAX_VALUE in way
        // 1 athough in way 1 limit bar can be dragged up getting a number
        // bigger than INPUT_MAX_VALUE and after dragging up done,the number
        // will be reverted to INPUT_MAX_VALUE.
        if (limitBytes <= (INPUT_MAX_VALUE * MB_IN_BYTES)) {
            mPolicyEditor.setPolicyLimitBytes(mTemplate, limitBytes);
        } else {
            mPolicyEditor.setPolicyLimitBytes(mTemplate, INPUT_MAX_VALUE * MB_IN_BYTES);
        }
        updatePolicy(false);
    }

    /**
     * Local cache of value, used to work around delay when
     * {@link ConnectivityManager#setMobileDataEnabled(boolean)} is async.
     */
    private Boolean mMobileDataEnabled;

    private boolean isMobileDataEnabled() {
        return mConnService.getMobileDataEnabled();
    }

    private void setMobileDataEnabled(boolean enabled) {
        if (LOGD) Log.d(TAG, "setMobileDataEnabled()");
        mConnService.setMobileDataEnabled(enabled);
        mMobileDataEnabled = enabled;
        updatePolicy(false);
    }

    private boolean isNetworkPolicyModifiable(NetworkPolicy policy) {
        return policy != null && isBandwidthControlEnabled() && isMobileDataEnabled();
    }

    private boolean isBandwidthControlEnabled() {
        try {
            return mNetworkService.isBandwidthControlEnabled();
        } catch (RemoteException e) {
            Log.w(TAG, "problem talking with INetworkManagementService: " + e);
            return false;
        }
    }

    private boolean isDataStatisticsEnabled() {
        final ContentResolver resolver = getActivity().getContentResolver();
        return Settings.Secure.getInt(resolver, Settings.Secure.NETSTATS_ENABLED, 1) != 0;
    }

    private void setDataStatistics(boolean enabled) {
        final ContentResolver resolver = getActivity().getContentResolver();
        Settings.Secure.putInt(resolver, Settings.Secure.NETSTATS_ENABLED, enabled ? 1 : 0);
        mDataStatisticsSwitch.setChecked(enabled);
    }

    private boolean getDataRoaming() {
        final ContentResolver resolver = getActivity().getContentResolver();
        return Settings.Secure.getInt(resolver, Settings.Secure.DATA_ROAMING, 0) != 0;
    }

    private void setDataRoaming(boolean enabled) {
        // TODO: teach telephony DataConnectionTracker to watch and apply
        // updates when changed.
        final ContentResolver resolver = getActivity().getContentResolver();
        Settings.Secure.putInt(resolver, Settings.Secure.DATA_ROAMING, enabled ? 1 : 0);
        mMenuDataRoaming.setChecked(enabled);
    }

    public void setRestrictBackground(boolean restrictBackground) {
        mPolicyManager.setRestrictBackground(restrictBackground);
        mMenuRestrictBackground.setChecked(restrictBackground);
    }

    private boolean getAppRestrictBackground() {
        final int appId = mCurrentApp.appId;
        final int uidPolicy = mPolicyManager.getAppPolicy(appId);
        return (uidPolicy & POLICY_REJECT_METERED_BACKGROUND) != 0;
    }

    private void setAppRestrictBackground(boolean restrictBackground) {
        if (LOGD) Log.d(TAG, "setAppRestrictBackground()");
        final int appId = mCurrentApp.appId;
        mPolicyManager.setAppPolicy(appId,
                restrictBackground ? POLICY_REJECT_METERED_BACKGROUND : POLICY_NONE);
        mAppRestrict.setChecked(restrictBackground);
    }

    private void updateOnSelection(boolean confirmSelection) {
        boolean currSelection = mDataStatisticsSwitch.isChecked();
        if (LOGD) Log.d(TAG, "updateOnSelection() " + "Current State: " +
                currSelection + "User Confirmation Selction: " + confirmSelection);
        mDataStatisticsSwitch.setOnCheckedChangeListener(null);

        if(confirmSelection) {
            setDataStatistics(currSelection);
        } else {
            // User canceled the operation. Revert to the previous state
            setDataStatistics(!currSelection);
        }
        // Enable/disable the Limit view if current tab is not wifi tab.
        if (!TAB_WIFI.equals(mCurrentTab)) {
            mDisableAtLimitView.setVisibility(mDataStatisticsSwitch.isChecked() ?
                    View.VISIBLE : View.GONE);
        }
        mDataStatisticsSwitch.setOnCheckedChangeListener(mDataStatisticsEnabledListener);
    }

    /**
     * Update chart sweeps and cycle list to reflect {@link NetworkPolicy} for
     * current {@link #mTemplate}.
     */
    private void updatePolicy(boolean refreshCycle) {
        if (isAppDetailMode()) {
            mNetworkSwitches.setVisibility(View.GONE);
        } else {
            mNetworkSwitches.setVisibility(View.VISIBLE);
        }

        final NetworkPolicy policy = mPolicyEditor.getPolicy(mTemplate);
        // TODO: move enabled state directly into policy
        if (TAB_MOBILE.equals(mCurrentTab) || TAB_SLOT1.equals(mCurrentTab) || TAB_SLOT2.equals(mCurrentTab)) {
            mBinding = true;
            mDataEnabled.setChecked(isMobileDataEnabled());
            mBinding = false;
            // when Data enable and CurrentTab is TAB_SLOT1,TAB_SLOT2 or TAB_MOBILE,
            // always show warning line. If policy is null,
            // create one for it by function setPolicyWarningBytes.
            if (policy == null && mDataEnabled.isChecked()) {
                setPolicyWarningBytes(0);
            }
        }

        if (isNetworkPolicyModifiable(policy) && !isAirplaneModeOn(getActivity())) {
            // Only both of the checkbox is checked, set the limit view to visible
            if(isMobileDataEnabled() && mDataStatisticsSwitch.isChecked()) {
                mDisableAtLimitView.setVisibility(View.VISIBLE);
            }
            mDisableAtLimit.setChecked(policy != null && policy.limitBytes != LIMIT_DISABLED);
            if (!isAppDetailMode()) {
                mChart.bindNetworkPolicy(policy);
            }

        } else {
            // controls are disabled; don't bind warning/limit sweeps
            if (!isMobileDataEnabled()) mDisableAtLimitView.setVisibility(View.GONE);
            mChart.bindNetworkPolicy(null);
        }

        if (refreshCycle) {
            // generate cycle list based on policy and available history
            updateCycleList(policy);
        }
    }

    /**
     * Rebuild {@link #mCycleAdapter} based on {@link NetworkPolicy#cycleDay}
     * and available {@link NetworkStatsHistory} data. Always selects the newest
     * item, updating the inspection range on {@link #mChart}.
     */
    private void updateCycleList(NetworkPolicy policy) {
        // stash away currently selected cycle to try restoring below
        final CycleItem previousItem = (CycleItem) mCycleSpinner.getSelectedItem();
        mCycleAdapter.clear();

        final Context context = mCycleSpinner.getContext();

        long historyStart = Long.MAX_VALUE;
        long historyEnd = Long.MIN_VALUE;
        if (mChartData != null) {
            historyStart = mChartData.network.getStart();
            historyEnd = mChartData.network.getEnd();
        }

        final long now = System.currentTimeMillis();
        if (historyStart == Long.MAX_VALUE) historyStart = now;
        if (historyEnd == Long.MIN_VALUE) historyEnd = now + 1;

        boolean hasCycles = false;
        if (policy != null) {
            // find the next cycle boundary
            long cycleEnd = computeNextCycleBoundary(historyEnd, policy);

            // walk backwards, generating all valid cycle ranges
            while (cycleEnd > historyStart) {
                final long cycleStart = computeLastCycleBoundary(cycleEnd, policy);
                Log.d(TAG, "generating cs=" + cycleStart + " to ce=" + cycleEnd + " waiting for hs="
                        + historyStart);
                mCycleAdapter.add(new CycleItem(context, cycleStart, cycleEnd));
                cycleEnd = cycleStart;
                hasCycles = true;
            }

            // one last cycle entry to modify policy cycle day
            mCycleAdapter.setChangePossible(isNetworkPolicyModifiable(policy));
        }

        if (!hasCycles) {
            // no policy defined cycles; show entry for each four-week period
            long cycleEnd = historyEnd;
            while (cycleEnd > historyStart) {
                final long cycleStart = cycleEnd - (DateUtils.WEEK_IN_MILLIS * 4);
                mCycleAdapter.add(new CycleItem(context, cycleStart, cycleEnd));
                cycleEnd = cycleStart;
            }

            mCycleAdapter.setChangePossible(false);
        }

        // force pick the current cycle (first item)
        if (mCycleAdapter.getCount() > 0) {
            final int position = mCycleAdapter.findNearestPosition(previousItem);
            mCycleSpinner.setSelection(position);

            // only force-update cycle when changed; skipping preserves any
            // user-defined inspection region.
            mCycleListener.onItemSelected(mCycleSpinner, null, position, 0);
        } else {
            updateDetailData();
        }
    }

    private OnCheckedChangeListener mDataStatisticsEnabledListener =
        new OnCheckedChangeListener() {
        /** {@inheritDoc} */
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (mBinding) return;

            final boolean dataStatsEnabled = isChecked;
            final String currentTab = mCurrentTab;
            if (TAB_MOBILE.equals(currentTab) || TAB_SLOT1.equals(currentTab) || TAB_SLOT2.equals(currentTab)) {
                ConfirmDisableDataStatistics.show(DataUsageSummary.this, dataStatsEnabled);
                mDisableAtLimitView.setVisibility(dataStatsEnabled ? View.VISIBLE : View.GONE);
            } else {
                if (TAB_WIFI.equals(currentTab)) {
                    // show data statistics prompt and not show Limit view in wifi tab.
                    ConfirmDisableDataStatistics.show(DataUsageSummary.this, dataStatsEnabled);
                }
                //when the currentTab is other type such as wifi ,it was not saved state.so save it.
                setDataStatistics(isChecked);
            }
        }
    };

    private OnCheckedChangeListener mDataEnabledListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (mBinding) return;

            final boolean dataEnabled = isChecked;
            final String currentTab = mCurrentTab;
            if (TAB_MOBILE.equals(currentTab) || TAB_SLOT1.equals(currentTab) || TAB_SLOT2.equals(currentTab)) {
                if (dataEnabled) {
                    setMobileDataEnabled(true);
                } else {
                    // disabling data; show confirmation dialog which eventually
                    // calls setMobileDataEnabled() once user confirms.
                    ConfirmDataDisableFragment.show(DataUsageSummary.this);
                }
            }

            updatePolicy(false);
        }
    };

    private View.OnClickListener mDisableAtLimitListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final boolean disableAtLimit = !mDisableAtLimit.isChecked();
            if (disableAtLimit) {
                // enabling limit; show confirmation dialog which eventually
                // calls setPolicyLimitBytes() once user confirms.
                ConfirmLimitFragment.show(DataUsageSummary.this);
            } else {
                setPolicyLimitBytes(LIMIT_DISABLED);
            }
        }
    };

    private View.OnClickListener mAppRestrictListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final boolean restrictBackground = !mAppRestrict.isChecked();
            if (restrictBackground) {
                // enabling restriction; show confirmation dialog which
                // eventually calls setRestrictBackground() once user
                // confirms.
                ConfirmAppRestrictFragment.show(DataUsageSummary.this);
            } else {
                setAppRestrictBackground(false);
            }
        }
    };

    private OnClickListener mAppSettingsListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!isAdded()) return;

            // TODO: target torwards entire UID instead of just first package
            startActivity(mAppSettingsIntent);
        }
    };

    private OnItemClickListener mListListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final Context context = view.getContext();
            final AppItem app = (AppItem) parent.getItemAtPosition(position);

            // TODO: sigh, remove this hack once we understand 6450986
            if (mUidDetailProvider == null || app == null) return;

            final UidDetail detail = mUidDetailProvider.getUidDetail(app.appId, true);
            AppDetailsFragment.show(DataUsageSummary.this, app, detail.label);
        }
    };

    private OnItemSelectedListener mCycleListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            final CycleItem cycle = (CycleItem) parent.getItemAtPosition(position);
            if (cycle instanceof CycleChangeItem) {
                // show cycle editor; will eventually call setPolicyCycleDay()
                // when user finishes editing.
                CycleEditorFragment.show(DataUsageSummary.this);

                // reset spinner to something other than "change cycle..."
                mCycleSpinner.setSelection(0);

            } else {
                if (LOGD) {
                    Log.d(TAG, "showing cycle " + cycle + ", start=" + cycle.start + ", end="
                            + cycle.end + "]");
                }

                // update chart to show selected cycle, and update detail data
                // to match updated sweep bounds.
                mChart.setVisibleRange(cycle.start, cycle.end);

                updateDetailData();
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // ignored
        }
    };

    /**
     * Update details based on {@link #mChart} inspection range depending on
     * current mode. In network mode, updates {@link #mAdapter} with sorted list
     * of applications data usage, and when {@link #isAppDetailMode()} update
     * app details.
     */
    private void updateDetailData() {
        if (LOGD) Log.d(TAG, "updateDetailData()");

        final long start = mChart.getInspectStart();
        final long end = mChart.getInspectEnd();
        final long now = System.currentTimeMillis();

        final Context context = getActivity();

        NetworkStatsHistory.Entry entry = null;
        if (isAppDetailMode() && mChartData != null && mChartData.detail != null) {
            // bind foreground/background to piechart and labels
            entry = mChartData.detailDefault.getValues(start, end, now, entry);
            final long defaultBytes = entry.rxBytes + entry.txBytes;
            entry = mChartData.detailForeground.getValues(start, end, now, entry);
            final long foregroundBytes = entry.rxBytes + entry.txBytes;

            mAppPieChart.setOriginAngle(175);

            mAppPieChart.removeAllSlices();
            mAppPieChart.addSlice(foregroundBytes, Color.parseColor("#d88d3a"));
            mAppPieChart.addSlice(defaultBytes, Color.parseColor("#666666"));

            mAppPieChart.generatePath();

            mAppBackground.setText(Formatter.formatFileSize(context, defaultBytes));
            mAppForeground.setText(Formatter.formatFileSize(context, foregroundBytes));

            // and finally leave with summary data for label below
            entry = mChartData.detail.getValues(start, end, now, null);

            getLoaderManager().destroyLoader(LOADER_SUMMARY);

        } else {
            if (mChartData != null) {
                entry = mChartData.network.getValues(start, end, now, null);
            }

            // kick off loader for detailed stats
            getLoaderManager().restartLoader(LOADER_SUMMARY,
                    SummaryForAllUidLoader.buildArgs(mTemplate, start, end), mSummaryCallbacks);
        }

        mRangePhrase = formatDateRange(context, start, end);

        if (TAB_MOBILE.equals(mCurrentTab) || TAB_3G.equals(mCurrentApp)
                || TAB_4G.equals(mCurrentApp) || TAB_SLOT1.equals(mCurrentTab) || TAB_SLOT2.equals(mCurrentTab)) {
            mSummaryRes = R.string.data_usage_total_during_range_mobile;
        } else {
            mSummaryRes = R.string.data_usage_total_during_range;
        }

        // set usage summary
        final long totalBytes = entry != null ? entry.rxBytes + entry.txBytes : 0;
        mUsageSummary.setText(getString(mSummaryRes,Formatter.formatFileSize(context, totalBytes), mRangePhrase));

        // initial layout is finished above, ensure we have transitions
        ensureLayoutTransitions();
    }

    private final LoaderCallbacks<ChartData> mChartDataCallbacks = new LoaderCallbacks<
            ChartData>() {
        @Override
        public Loader<ChartData> onCreateLoader(int id, Bundle args) {
            return new ChartDataLoader(getActivity(), mStatsSession, args);
        }

        @Override
        public void onLoadFinished(Loader<ChartData> loader, ChartData data) {
            mChartData = data;
            mChart.bindNetworkStats(mChartData.network);
            mChart.bindDetailNetworkStats(mChartData.detail);

            // calcuate policy cycles based on available data
            updatePolicy(true);
            updateAppDetail();

        }

        @Override
        public void onLoaderReset(Loader<ChartData> loader) {
            mChartData = null;
            mChart.bindNetworkStats(null);
            mChart.bindDetailNetworkStats(null);
        }
    };

    private final LoaderCallbacks<NetworkStats> mSummaryCallbacks = new LoaderCallbacks<
            NetworkStats>() {
        @Override
        public Loader<NetworkStats> onCreateLoader(int id, Bundle args) {
            return new SummaryForAllUidLoader(getActivity(), mStatsSession, args);
        }

        @Override
        public void onLoadFinished(Loader<NetworkStats> loader, NetworkStats data) {
            final int[] restrictedAppIds;
            if (isAppDetailMode() && mChartData != null && mChartData.detail != null) {
                restrictedAppIds = new int[1];
                restrictedAppIds[0] = mCurrentApp.appId;
            } else {
                restrictedAppIds = mPolicyManager.getAppsWithPolicy(
                        POLICY_REJECT_METERED_BACKGROUND);
            }
            mAdapter.bindStats(data, restrictedAppIds);
            updateEmptyVisible();
        }

        @Override
        public void onLoaderReset(Loader<NetworkStats> loader) {
            mAdapter.bindStats(null, new int[0]);
            updateEmptyVisible();
        }

        private void updateEmptyVisible() {
            final boolean isEmpty = mAdapter.isEmpty() && !isAppDetailMode();
            mEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    };

    @Deprecated
    private boolean isMobilePolicySplit() {
        final Context context = getActivity();
        if (hasReadyMobileRadio(context)) {
            final TelephonyManager tele = TelephonyManager.from(context);
            return mPolicyEditor.isMobilePolicySplit(getActiveSubscriberId(context));
        } else {
            return false;
        }
    }

    @Deprecated
    private void setMobilePolicySplit(boolean split) {
        final Context context = getActivity();
        if (hasReadyMobileRadio(context)) {
            final TelephonyManager tele = TelephonyManager.from(context);
            mPolicyEditor.setMobilePolicySplit(getActiveSubscriberId(context), split);
        }
    }

    private static String getActiveSubscriberId(Context context) {
        final TelephonyManager tele = TelephonyManager.from(context);
        final String actualSubscriberId = tele.getSubscriberId();
        return SystemProperties.get(TEST_SUBSCRIBER_PROP, actualSubscriberId);
    }

    private static String getActiveSubscriberId(int sub) {
        return MSimTelephonyManager.getDefault().getSubscriberId(sub);
    }

    private DataUsageChartListener mChartListener = new DataUsageChartListener() {
        @Override
        public void onInspectRangeChanged() {
            if (LOGD) Log.d(TAG, "onInspectRangeChanged()");
            updateDetailData();
        }

        @Override
        public void onWarningChanged() {
            setPolicyWarningBytes(mChart.getWarningBytes());
        }

        @Override
        public void onLimitChanged() {
            setPolicyLimitBytes(mChart.getLimitBytes());
        }

        @Override
        public void requestWarningEdit() {
            WarningEditorFragment.show(DataUsageSummary.this);
        }

        @Override
        public void requestLimitEdit() {
            LimitEditorFragment.show(DataUsageSummary.this);
        }
    };

    /**
     * List item that reflects a specific data usage cycle.
     */
    public static class CycleItem implements Comparable<CycleItem> {
        public CharSequence label;
        public long start;
        public long end;

        CycleItem(CharSequence label) {
            this.label = label;
        }

        public CycleItem(Context context, long start, long end) {
            this.label = formatDateRange(context, start, end);
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return label.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CycleItem) {
                final CycleItem another = (CycleItem) o;
                return start == another.start && end == another.end;
            }
            return false;
        }

        @Override
        public int compareTo(CycleItem another) {
            return Long.compare(start, another.start);
        }
    }

    private static final StringBuilder sBuilder = new StringBuilder(50);
    private static final java.util.Formatter sFormatter = new java.util.Formatter(
            sBuilder, Locale.getDefault());

    public static String formatDateRange(Context context, long start, long end) {
        final int flags = FORMAT_SHOW_DATE | FORMAT_ABBREV_MONTH;

        synchronized (sBuilder) {
            sBuilder.setLength(0);
            return DateUtils.formatDateRange(context, sFormatter, start, end, flags, null)
                    .toString();
        }
    }

    /**
     * Special-case data usage cycle that triggers dialog to change
     * {@link NetworkPolicy#cycleDay}.
     */
    public static class CycleChangeItem extends CycleItem {
        public CycleChangeItem(Context context) {
            super(context.getString(R.string.data_usage_change_cycle));
        }
    }

    public static class CycleAdapter extends ArrayAdapter<CycleItem> {
        private boolean mChangePossible = false;
        private boolean mChangeVisible = false;

        private final CycleChangeItem mChangeItem;

        public CycleAdapter(Context context) {
            super(context, android.R.layout.simple_spinner_item);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mChangeItem = new CycleChangeItem(context);
        }

        public void setChangePossible(boolean possible) {
            mChangePossible = possible;
            updateChange();
        }

        public void setChangeVisible(boolean visible) {
            mChangeVisible = visible;
            updateChange();
        }

        private void updateChange() {
            remove(mChangeItem);
            if (mChangePossible && mChangeVisible) {
                add(mChangeItem);
            }
        }

        /**
         * Find position of {@link CycleItem} in this adapter which is nearest
         * the given {@link CycleItem}.
         */
        public int findNearestPosition(CycleItem target) {
            if (target != null) {
                final int count = getCount();
                for (int i = count - 1; i >= 0; i--) {
                    final CycleItem item = getItem(i);
                    if (item instanceof CycleChangeItem) {
                        continue;
                    } else if (item.compareTo(target) >= 0) {
                        return i;
                    }
                }
            }
            return 0;
        }
    }

    public static class AppItem implements Comparable<AppItem>, Parcelable {
        public final int appId;
        public boolean restricted;
        public SparseBooleanArray uids = new SparseBooleanArray();
        public long total;

        public AppItem(int appId) {
            this.appId = appId;
        }

        public AppItem(Parcel parcel) {
            appId = parcel.readInt();
            uids = parcel.readSparseBooleanArray();
            total = parcel.readLong();
        }

        public void addUid(int uid) {
            uids.put(uid, true);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(appId);
            dest.writeSparseBooleanArray(uids);
            dest.writeLong(total);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public int compareTo(AppItem another) {
            return Long.compare(another.total, total);
        }

        public static final Creator<AppItem> CREATOR = new Creator<AppItem>() {
            @Override
            public AppItem createFromParcel(Parcel in) {
                return new AppItem(in);
            }

            @Override
            public AppItem[] newArray(int size) {
                return new AppItem[size];
            }
        };
    }

    /**
     * Adapter of applications, sorted by total usage descending.
     */
    public class DataUsageAdapter extends BaseAdapter {
        private final UidDetailProvider mProvider;
        private final int mInsetSide;

        private ArrayList<AppItem> mItems = Lists.newArrayList();
        private long mLargest;

        public DataUsageAdapter(UidDetailProvider provider, int insetSide) {
            mProvider = checkNotNull(provider);
            mInsetSide = insetSide;
        }

        /**
         * Bind the given {@link NetworkStats}, or {@code null} to clear list.
         */
        public void bindStats(NetworkStats stats, int[] restrictedAppIds) {
            mItems.clear();

            final Context context = getActivity();
            final AppItem systemItem = new AppItem(android.os.Process.SYSTEM_UID);
            final SparseArray<AppItem> knownUids = new SparseArray<AppItem>();

            NetworkStats.Entry entry = null;
            final int size = stats != null ? stats.size() : 0;
            for (int i = 0; i < size; i++) {
                entry = stats.getValues(i, entry);

                final boolean isApp = UserId.isApp(entry.uid);
                final int appId = isApp ? UserId.getAppId(entry.uid) : entry.uid;
                if (isApp || appId == UID_REMOVED || appId == UID_TETHERING) {
                    AppItem item = knownUids.get(appId);
                    if (item == null) {
                        item = new AppItem(appId);
                        knownUids.put(appId, item);
                        mItems.add(item);
                    }

                    item.total += entry.rxBytes + entry.txBytes;
                    item.addUid(entry.uid);
                } else {
                    systemItem.total += entry.rxBytes + entry.txBytes;
                    systemItem.addUid(entry.uid);
                }
            }

            for (int appId : restrictedAppIds) {
                AppItem item = knownUids.get(appId);
                if (item == null) {
                    item = new AppItem(appId);
                    item.total = -1;
                    mItems.add(item);
                }
                item.restricted = true;
            }

            if (systemItem.total > 0) {
                mItems.add(systemItem);
            }


            Collections.sort(mItems);
            mLargest = (mItems.size() > 0) ? mItems.get(0).total : 0;
            notifyDataSetChanged();
        }

        /**
         * get total consumes datas with apps and system.
         */
        private long getTotalData() {
            long summary = 0;
            for (AppItem mItem : mItems) {
                summary += mItem.total;
            }

            return summary;
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public Object getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mItems.get(position).appId;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.data_usage_item, parent, false);

                if (mInsetSide > 0) {
                    convertView.setPadding(mInsetSide, 0, mInsetSide, 0);
                }
            }

            final Context context = parent.getContext();

            final TextView text1 = (TextView) convertView.findViewById(android.R.id.text1);
            final ProgressBar progress = (ProgressBar) convertView.findViewById(
                    android.R.id.progress);

            // kick off async load of app details
            final AppItem item = mItems.get(position);
            UidDetailTask.bindView(mProvider, item, convertView);

            if (item.restricted && item.total <= 0) {
                text1.setText(R.string.data_usage_app_restricted);
                progress.setVisibility(View.GONE);
            } else {
                text1.setText(Formatter.formatFileSize(context, item.total));
                progress.setVisibility(View.VISIBLE);
            }

            final int percentTotal = mLargest != 0 ? (int) (item.total * 100 / mLargest) : 0;
            progress.setProgress(percentTotal);

            return convertView;
        }
    }

    /**
     * Empty {@link Fragment} that controls display of UID details in
     * {@link DataUsageSummary}.
     */
    public static class AppDetailsFragment extends Fragment {
        private static final String EXTRA_APP = "app";

        public static void show(DataUsageSummary parent, AppItem app, CharSequence label) {
            if (!parent.isAdded()) return;

            final Bundle args = new Bundle();
            args.putParcelable(EXTRA_APP, app);

            final AppDetailsFragment fragment = new AppDetailsFragment();
            fragment.setArguments(args);
            fragment.setTargetFragment(parent, 0);
            final FragmentTransaction ft = parent.getFragmentManager().beginTransaction();
            ft.add(fragment, TAG_APP_DETAILS);
            ft.addToBackStack(TAG_APP_DETAILS);
            ft.setBreadCrumbTitle(label);
            ft.commitAllowingStateLoss();
        }

        @Override
        public void onStart() {
            super.onStart();
            final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            target.mCurrentApp = getArguments().getParcelable(EXTRA_APP);
            target.updateBody();
        }

        @Override
        public void onStop() {
            super.onStop();
            final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            target.mCurrentApp = null;
            target.updateBody();
        }
    }

    /**
     * Dialog to request user confirmation before setting
     * {@link NetworkPolicy#limitBytes}.
     */
    public static class ConfirmLimitFragment extends DialogFragment {
        private static final String EXTRA_MESSAGE = "message";
        private static final String EXTRA_LIMIT_BYTES = "limitBytes";

        public static void show(DataUsageSummary parent) {
            if (null == parent || !parent.isAdded())
                return;

            final Resources res = parent.getResources();
            final CharSequence message;
            NetworkPolicy currentNetworkPolicy = parent.mPolicyEditor.getPolicy(parent.mTemplate);
            // When we don't create NetworkPolicy for this current table,
            // we need to defined a default limited bytes(5 * GB_IN_BYTES)
            // instead of to getting it from our networkPolicy.
            // TODO: DEFAULT_LIMITED_BYTES based on network template
            final long minLimitBytes = (currentNetworkPolicy != null) ? ((long) (
                    currentNetworkPolicy.warningBytes * 1.2f)) : DEFAULT_LIMITED_BYTES;
            final long limitBytes;

            // TODO: customize default limits based on network template
            final String currentTab = parent.mCurrentTab;
            if (TAB_3G.equals(currentTab)) {
                message = res.getString(R.string.data_usage_limit_dialog_mobile);
                limitBytes = Math.max(5 * GB_IN_BYTES, minLimitBytes);
            } else if (TAB_4G.equals(currentTab)) {
                message = res.getString(R.string.data_usage_limit_dialog_mobile);
                limitBytes = Math.max(5 * GB_IN_BYTES, minLimitBytes);
            } else if (TAB_MOBILE.equals(currentTab)) {
                message = res.getString(R.string.data_usage_limit_dialog_mobile);
                limitBytes = Math.max(5 * GB_IN_BYTES, minLimitBytes);
            } else if (TAB_SLOT1.equals(currentTab)) {
                message = res.getString(R.string.data_usage_limit_dialog_mobile);
                limitBytes = Math.max(5 * GB_IN_BYTES, minLimitBytes);
            } else if (TAB_SLOT2.equals(currentTab)) {
                message = res.getString(R.string.data_usage_limit_dialog_mobile);
                limitBytes = Math.max(5 * GB_IN_BYTES, minLimitBytes);
            } else {
                throw new IllegalArgumentException("unknown current tab: " + currentTab);
            }

            final Bundle args = new Bundle();
            args.putCharSequence(EXTRA_MESSAGE, message);
            args.putLong(EXTRA_LIMIT_BYTES, limitBytes);

            final ConfirmLimitFragment dialog = new ConfirmLimitFragment();
            dialog.setArguments(args);
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_CONFIRM_LIMIT);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();

            final CharSequence message = getArguments().getCharSequence(EXTRA_MESSAGE);
            final long limitBytes = getArguments().getLong(EXTRA_LIMIT_BYTES);

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.data_usage_limit_dialog_title);
            builder.setMessage(message);

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
                    if (target != null) {
                        target.setPolicyLimitBytes(limitBytes);
                    }
                }
            });

            return builder.create();
        }
    }

    /**
     * Dialog to edit {@link NetworkPolicy#cycleDay}.
     */
    public static class CycleEditorFragment extends DialogFragment {
        private static final String EXTRA_TEMPLATE = "template";

        public static void show(DataUsageSummary parent) {
            if (!parent.isAdded()) return;

            final Bundle args = new Bundle();
            args.putParcelable(EXTRA_TEMPLATE, parent.mTemplate);

            final CycleEditorFragment dialog = new CycleEditorFragment();
            dialog.setArguments(args);
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_CYCLE_EDITOR);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            final NetworkPolicyEditor editor = target.mPolicyEditor;

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            final LayoutInflater dialogInflater = LayoutInflater.from(builder.getContext());

            final View view = dialogInflater.inflate(R.layout.data_usage_cycle_editor, null, false);
            final NumberPicker cycleDayPicker = (NumberPicker) view.findViewById(R.id.cycle_day);

            final NetworkTemplate template = getArguments().getParcelable(EXTRA_TEMPLATE);
            final int cycleDay = editor.getPolicyCycleDay(template);

            cycleDayPicker.setMinValue(1);
            cycleDayPicker.setMaxValue(31);
            cycleDayPicker.setValue(cycleDay);
            cycleDayPicker.setWrapSelectorWheel(true);

            builder.setTitle(R.string.data_usage_cycle_editor_title);
            builder.setView(view);

            builder.setPositiveButton(R.string.data_usage_cycle_editor_positive,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final int cycleDay = cycleDayPicker.getValue();
                            final String cycleTimezone = new Time().timezone;
                            editor.setPolicyCycleDay(template, cycleDay, cycleTimezone);
                            target.updatePolicy(true);
                        }
                    });

            return builder.create();
        }
    }

    /**
     * Dialog to edit {@link NetworkPolicy#warningBytes}.
     */
    public static class WarningEditorFragment extends DialogFragment {
        private static final String EXTRA_TEMPLATE = "template";

        public static void show(DataUsageSummary parent) {
            if (!parent.isAdded()) return;

            final Bundle args = new Bundle();
            args.putParcelable(EXTRA_TEMPLATE, parent.mTemplate);

            final WarningEditorFragment dialog = new WarningEditorFragment();
            dialog.setArguments(args);
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_WARNING_EDITOR);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            final NetworkPolicyEditor editor = target.mPolicyEditor;

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            final LayoutInflater dialogInflater = LayoutInflater.from(builder.getContext());

            final View view = dialogInflater.inflate(R.layout.data_usage_bytes_editor, null, false);
            final NumberPicker bytesPicker = (NumberPicker) view.findViewById(R.id.bytes);

            final NetworkTemplate template = getArguments().getParcelable(EXTRA_TEMPLATE);
            final long warningBytes = editor.getPolicyWarningBytes(template);
            final long limitBytes = editor.getPolicyLimitBytes(template);

            bytesPicker.setMinValue(0);
            if (limitBytes != LIMIT_DISABLED) {
                bytesPicker.setMaxValue((int) (limitBytes / MB_IN_BYTES) - 1);
            } else {
                bytesPicker.setMaxValue(INPUT_MAX_VALUE);
            }
            bytesPicker.setValue((int) (warningBytes / MB_IN_BYTES));
            bytesPicker.setWrapSelectorWheel(false);

            builder.setTitle(R.string.data_usage_warning_editor_title);
            builder.setView(view);

            builder.setPositiveButton(R.string.data_usage_cycle_editor_positive,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // clear focus to finish pending text edits
                            bytesPicker.clearFocus();

                            final long bytes = bytesPicker.getValue() * MB_IN_BYTES;
                            editor.setPolicyWarningBytes(template, bytes);
                            target.updatePolicy(false);
                        }
                    });

            return builder.create();
        }
    }

    /**
     * Dialog to edit {@link NetworkPolicy#limitBytes}.
     */
    public static class LimitEditorFragment extends DialogFragment {
        private static final String EXTRA_TEMPLATE = "template";

        public static void show(DataUsageSummary parent) {
            if (!parent.isAdded()) return;

            final Bundle args = new Bundle();
            args.putParcelable(EXTRA_TEMPLATE, parent.mTemplate);

            final LimitEditorFragment dialog = new LimitEditorFragment();
            dialog.setArguments(args);
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_LIMIT_EDITOR);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            final NetworkPolicyEditor editor = target.mPolicyEditor;

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            final LayoutInflater dialogInflater = LayoutInflater.from(builder.getContext());

            final View view = dialogInflater.inflate(R.layout.data_usage_bytes_editor, null, false);
            final NumberPicker bytesPicker = (NumberPicker) view.findViewById(R.id.bytes);

            final NetworkTemplate template = getArguments().getParcelable(EXTRA_TEMPLATE);
            final long warningBytes = editor.getPolicyWarningBytes(template);
            final long limitBytes = editor.getPolicyLimitBytes(template);

            bytesPicker.setMaxValue(INPUT_MAX_VALUE);
            if (warningBytes != WARNING_DISABLED && limitBytes > 0) {
                int MinValue = (int) (warningBytes / MB_IN_BYTES) +
                        ((warningBytes / MB_IN_BYTES) > (INPUT_MAX_VALUE-1) ? 0:1);
                bytesPicker.setMinValue(MinValue);
            } else {
                bytesPicker.setMinValue(0);
            }
            // Set smaller value to limbitBytes, for drag interval set to 5MB
            // Estimated limitbytes maybe smaller than bytesPicker's minValue.
            int value = (limitBytes / MB_IN_BYTES) > bytesPicker.getMinValue()
                    ? ((int) (limitBytes / MB_IN_BYTES)) : bytesPicker.getMinValue();
            bytesPicker.setValue(value);
            bytesPicker.setWrapSelectorWheel(false);

            builder.setTitle(R.string.data_usage_limit_editor_title);
            builder.setView(view);

            builder.setPositiveButton(R.string.data_usage_cycle_editor_positive,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // clear focus to finish pending text edits
                            bytesPicker.clearFocus();

                            final long bytes = bytesPicker.getValue() * MB_IN_BYTES;
                            editor.setPolicyLimitBytes(template, bytes);
                            target.updatePolicy(false);
                        }
                    });

            return builder.create();
        }
    }
    /**
     * Dialog to request user confirmation before disabling data.
     */
    public static class ConfirmDataDisableFragment extends DialogFragment {
        public static void show(DataUsageSummary parent) {
            if (!parent.isAdded()) return;

            final ConfirmDataDisableFragment dialog = new ConfirmDataDisableFragment();
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_CONFIRM_DATA_DISABLE);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(R.string.data_usage_disable_mobile);

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
                    if (target != null) {
                        // TODO: extend to modify policy enabled flag.
                        target.setMobileDataEnabled(false);
                    }
                }
            });
            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
                    if (target != null) {
                        // TODO: extend to modify policy enabled flag.
                        //when press cancel button, should update UI.
                        // If user choose not close the DataEnable
                        // button, we should open this DataEnable again.
                        target.setMobileDataEnabled(true);
                    }
                }
            });

            return builder.create();
        }
    }

    /**
     * Dialog to request user confirmation before setting
     * {@link android.provider.Settings.Secure#DATA_ROAMING}.
     */
    public static class ConfirmDataRoamingFragment extends DialogFragment {
        public static void show(DataUsageSummary parent) {
            if (!parent.isAdded()) return;

            final ConfirmDataRoamingFragment dialog = new ConfirmDataRoamingFragment();
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_CONFIRM_DATA_ROAMING);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.roaming_reenable_title);
            builder.setMessage(R.string.roaming_warning);

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
                    if (target != null) {
                        target.setDataRoaming(true);
                    }
                }
            });
            builder.setNegativeButton(android.R.string.cancel, null);

            return builder.create();
        }
    }

    /**
     * Dialog to request user confirmation before setting
     * {@link INetworkPolicyManager#setRestrictBackground(boolean)}.
     */
    public static class ConfirmRestrictFragment extends DialogFragment {
        public static void show(DataUsageSummary parent) {
            if (!parent.isAdded()) return;

            final ConfirmRestrictFragment dialog = new ConfirmRestrictFragment();
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_CONFIRM_RESTRICT);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.data_usage_restrict_background_title);
            builder.setMessage(getString(R.string.data_usage_restrict_background));

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
                    if (target != null) {
                        target.setRestrictBackground(true);
                    }
                }
            });
            builder.setNegativeButton(android.R.string.cancel, null);

            return builder.create();
        }
    }

    /**
     * Dialog to inform user that {@link #POLICY_REJECT_METERED_BACKGROUND}
     * change has been denied, usually based on
     * {@link DataUsageSummary#hasLimitedNetworks()}.
     */
    public static class DeniedRestrictFragment extends DialogFragment {
        public static void show(DataUsageSummary parent) {
            if (!parent.isAdded()) return;

            final DeniedRestrictFragment dialog = new DeniedRestrictFragment();
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_DENIED_RESTRICT);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.data_usage_app_restrict_background);
            builder.setMessage(R.string.data_usage_restrict_denied_dialog);
            builder.setPositiveButton(android.R.string.ok, null);

            return builder.create();
        }
    }

    /**
     * Dialog to request user confirmation before setting
     * {@link #POLICY_REJECT_METERED_BACKGROUND}.
     */
    public static class ConfirmAppRestrictFragment extends DialogFragment {
        public static void show(DataUsageSummary parent) {
            if (!parent.isAdded()) return;

            final ConfirmAppRestrictFragment dialog = new ConfirmAppRestrictFragment();
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_CONFIRM_APP_RESTRICT);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.data_usage_app_restrict_dialog_title);
            builder.setMessage(R.string.data_usage_app_restrict_dialog);

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
                    if (target != null) {
                        target.setAppRestrictBackground(true);
                    }
                }
            });
            builder.setNegativeButton(android.R.string.cancel, null);

            return builder.create();
        }

        public void onCancel(DialogInterface dialog) {
            final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            if (target != null) {
                target.updateOnSelection(false);
            }
        }
    }

    /**
     * Dialog to inform user about changing auto-sync setting
     */
    public static class ConfirmAutoSyncChangeFragment extends DialogFragment {
        private static final String SAVE_ENABLING = "enabling";
        private boolean mEnabling;

        public static void show(DataUsageSummary parent, boolean enabling) {
            if (!parent.isAdded()) return;

            final ConfirmAutoSyncChangeFragment dialog = new ConfirmAutoSyncChangeFragment();
            dialog.mEnabling = enabling;
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_CONFIRM_AUTO_SYNC_CHANGE);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            if (savedInstanceState != null) {
                mEnabling = savedInstanceState.getBoolean(SAVE_ENABLING);
            }

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            if (!mEnabling) {
                builder.setTitle(R.string.data_usage_auto_sync_off_dialog_title);
                builder.setMessage(R.string.data_usage_auto_sync_off_dialog);
            } else {
                builder.setTitle(R.string.data_usage_auto_sync_on_dialog_title);
                builder.setMessage(R.string.data_usage_auto_sync_on_dialog);
            }

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ContentResolver.setMasterSyncAutomatically(mEnabling);
                }
            });
            builder.setNegativeButton(android.R.string.cancel, null);

            return builder.create();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean(SAVE_ENABLING, mEnabling);
        }
    }
	
	    public static class ConfirmDisableDataStatistics extends DialogFragment {
        private static boolean mUserSelection;
        public static void show(DataUsageSummary parent, boolean enable) {
            if (!parent.isAdded()) return;
            mUserSelection = enable;
            final ConfirmDisableDataStatistics dialog = new ConfirmDisableDataStatistics();
            dialog.setTargetFragment(parent, 0);
            dialog.show(parent.getFragmentManager(), TAG_WARNING_DATA_STATISTICS);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            builder.setTitle(R.string.data_usage_enable_mobile_statistics);
            int id = mUserSelection ?  R.string.data_usage_enable_mobile_statistics_dialog :
                R.string.data_usage_disable_mobile_statistics_dialog;
            builder.setMessage(id);

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if (target != null) {
                        target.updateOnSelection(true);
                    }
                }
            });

            builder.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
                    if (target != null) {
                        target.updateOnSelection(false);
                    }
                }
            });

            return builder.create();
        }

        // When user click back key, we need reset the switchbutton
        @Override
        public void onCancel(DialogInterface dialog) {
            final DataUsageSummary target = (DataUsageSummary) getTargetFragment();
            if (target != null) {
                target.updateOnSelection(false);
            }
        }
    }

    /**
     * Compute default tab that should be selected, based on
     * {@link NetworkPolicyManager#EXTRA_NETWORK_TEMPLATE} extra.
     */
    private String computeTabFromIntent(Intent intent) {
        final NetworkTemplate template = intent.getParcelableExtra(EXTRA_NETWORK_TEMPLATE);
        if (template == null) return null;

        switch (template.getMatchRule()) {
            case MATCH_MOBILE_3G_LOWER:
                return TAB_3G;
            case MATCH_MOBILE_4G:
                return TAB_4G;
            case MATCH_MOBILE_ALL:
                int sub = -1;
                try {
                    sub = Settings.System.getInt(getActivity().getContentResolver(),
                            Settings.System.MULTI_SIM_DATA_CALL_SUBSCRIPTION);
                } catch (SettingNotFoundException snfe) {
                    Log.e(TAG, "Read current data port value exception.", snfe);
                }
                if (sub == 1) {
                    return TAB_SLOT2;
                }
                return TAB_MOBILE;
            case MATCH_WIFI:
                return TAB_WIFI;
            default:
                return null;
        }
    }

    /**
     * Background task that loads {@link UidDetail}, binding to
     * {@link DataUsageAdapter} row item when finished.
     */
    private static class UidDetailTask extends AsyncTask<Void, Void, UidDetail> {
        private final UidDetailProvider mProvider;
        private final AppItem mItem;
        private final View mTarget;

        private UidDetailTask(UidDetailProvider provider, AppItem item, View target) {
            mProvider = checkNotNull(provider);
            mItem = checkNotNull(item);
            mTarget = checkNotNull(target);
        }

        public static void bindView(
                UidDetailProvider provider, AppItem item, View target) {
            final UidDetailTask existing = (UidDetailTask) target.getTag();
            if (existing != null) {
                existing.cancel(false);
            }

            final UidDetail cachedDetail = provider.getUidDetail(item.appId, false);
            if (cachedDetail != null) {
                bindView(cachedDetail, target);
            } else {
                target.setTag(new UidDetailTask(provider, item, target).executeOnExecutor(
                        AsyncTask.THREAD_POOL_EXECUTOR));
            }
        }

        private static void bindView(UidDetail detail, View target) {
            final ImageView icon = (ImageView) target.findViewById(android.R.id.icon);
            final TextView title = (TextView) target.findViewById(android.R.id.title);

            if (detail != null) {
                icon.setImageDrawable(detail.icon);
                title.setText(detail.label);
            } else {
                icon.setImageDrawable(null);
                title.setText(null);
            }
        }

        @Override
        protected void onPreExecute() {
            bindView(null, mTarget);
        }

        @Override
        protected UidDetail doInBackground(Void... params) {
            return mProvider.getUidDetail(mItem.appId, true);
        }

        @Override
        protected void onPostExecute(UidDetail result) {
            bindView(result, mTarget);
        }
    }

    /**
     * Test if device has a mobile data radio with SIM in ready state.
     */
    public static boolean hasReadyMobileRadio(Context context) {
        if (TEST_RADIOS) {
            return SystemProperties.get(TEST_RADIOS_PROP).contains("mobile");
        }

        final ConnectivityManager conn = ConnectivityManager.from(context);
        final TelephonyManager tele = TelephonyManager.from(context);

        // require both supported network and ready SIM
        return conn.isNetworkSupported(TYPE_MOBILE) && tele.getSimState() == SIM_STATE_READY;
    }

    /**
     * Test if device has a mobile data radio with SIM not in absent state.
     */
    private static boolean hasPresentMobileRadio(Context context) {
        if (TEST_RADIOS) {
            return SystemProperties.get(TEST_RADIOS_PROP).contains("mobile");
        }

        final ConnectivityManager conn = ConnectivityManager.from(context);
        final TelephonyManager tele = TelephonyManager.from(context);

        // require both supported network and present SIM
        return conn.isNetworkSupported(TYPE_MOBILE) && tele.getSimState() != SIM_STATE_ABSENT;
    }

    /**
     * Test if device has a mobile 4G data radio.
     */
    public static boolean hasReadyMobile4gRadio(Context context) {
        if (!NetworkPolicyEditor.ENABLE_SPLIT_POLICIES) {
            return false;
        }
        if (TEST_RADIOS) {
            return SystemProperties.get(TEST_RADIOS_PROP).contains("4g");
        }

        final ConnectivityManager conn = ConnectivityManager.from(context);
        final TelephonyManager tele = TelephonyManager.from(context);

        final boolean hasWimax = conn.isNetworkSupported(TYPE_WIMAX);
        final boolean hasLte = (tele.getLteOnCdmaMode() == Phone.LTE_ON_CDMA_TRUE)
                && hasReadyMobileRadio(context);
        return hasWimax || hasLte;
    }

    /**
     * Test if device has a Wi-Fi data radio.
     */
    public static boolean hasWifiRadio(Context context) {
        if (TEST_RADIOS) {
            return SystemProperties.get(TEST_RADIOS_PROP).contains("wifi");
        }

        final ConnectivityManager conn = ConnectivityManager.from(context);
        return conn.isNetworkSupported(TYPE_WIFI);
    }

    /**
     * Test if device has an ethernet network connection.
     */
    public boolean hasEthernet(Context context) {
        if (TEST_RADIOS) {
            return SystemProperties.get(TEST_RADIOS_PROP).contains("ethernet");
        }

        final ConnectivityManager conn = ConnectivityManager.from(context);
        final boolean hasEthernet = conn.isNetworkSupported(TYPE_ETHERNET);

        final long ethernetBytes;
        if (mStatsSession != null) {
            try {
                ethernetBytes = mStatsSession.getSummaryForNetwork(
                        NetworkTemplate.buildTemplateEthernet(), Long.MIN_VALUE, Long.MAX_VALUE)
                        .getTotalBytes();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        } else {
            ethernetBytes = 0;
        }

        // only show ethernet when both hardware present and traffic has occurred
        return hasEthernet && ethernetBytes > 0;
    }

    /**
     * Inflate a {@link Preference} style layout, adding the given {@link View}
     * widget into {@link android.R.id#widget_frame}.
     */
    private static View inflatePreference(LayoutInflater inflater, ViewGroup root, View widget) {
        final View view = inflater.inflate(R.layout.preference, root, false);
        final LinearLayout widgetFrame = (LinearLayout) view.findViewById(
                android.R.id.widget_frame);
        widgetFrame.addView(widget, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        return view;
    }

    private static View inflateAppTitle(
            LayoutInflater inflater, ViewGroup root, CharSequence label) {
        final TextView view = (TextView) inflater.inflate(
                R.layout.data_usage_app_title, root, false);
        view.setText(label);
        return view;
    }

    /**
     * Test if any networks are currently limited.
     */
    private boolean hasLimitedNetworks() {
        return !buildLimitedNetworksList().isEmpty();
    }

    /**
     * Build string describing currently limited networks, which defines when
     * background data is restricted.
     */
    @Deprecated
    private CharSequence buildLimitedNetworksString() {
        final List<CharSequence> limited = buildLimitedNetworksList();

        // handle case where no networks limited
        if (limited.isEmpty()) {
            limited.add(getText(R.string.data_usage_list_none));
        }

        return TextUtils.join(limited);
    }

    /**
     * Build list of currently limited networks, which defines when background
     * data is restricted.
     */
    @Deprecated
    private List<CharSequence> buildLimitedNetworksList() {
        final Context context = getActivity();

        // build combined list of all limited networks
        final ArrayList<CharSequence> limited = Lists.newArrayList();

        final TelephonyManager tele = TelephonyManager.from(context);
        if (tele.getSimState() == SIM_STATE_READY) {
            final String subscriberId = getActiveSubscriberId(context);
            if (mPolicyEditor.hasLimitedPolicy(buildTemplateMobileAll(subscriberId))) {
                limited.add(getText(R.string.data_usage_list_mobile));
            }
            if (mPolicyEditor.hasLimitedPolicy(buildTemplateMobile3gLower(subscriberId))) {
                limited.add(getText(R.string.data_usage_tab_3g));
            }
            if (mPolicyEditor.hasLimitedPolicy(buildTemplateMobile4g(subscriberId))) {
                limited.add(getText(R.string.data_usage_tab_4g));
            }
        }

        if (mPolicyEditor.hasLimitedPolicy(buildTemplateWifiWildcard())) {
            limited.add(getText(R.string.data_usage_tab_wifi));
        }
        if (mPolicyEditor.hasLimitedPolicy(buildTemplateEthernet())) {
            limited.add(getText(R.string.data_usage_tab_ethernet));
        }

        return limited;
    }

    /**
     * Inset both selector and divider {@link Drawable} on the given
     * {@link ListView} by the requested dimensions.
     */
    private static void insetListViewDrawables(ListView view, int insetSide) {
        final Drawable selector = view.getSelector();
        final Drawable divider = view.getDivider();

        // fully unregister these drawables so callbacks can be maintained after
        // wrapping below.
        final Drawable stub = new ColorDrawable(Color.TRANSPARENT);
        view.setSelector(stub);
        view.setDivider(stub);

        view.setSelector(new InsetBoundsDrawable(selector, insetSide));
        view.setDivider(new InsetBoundsDrawable(divider, insetSide));
    }

    /**
     * Set {@link android.R.id#title} for a preference view inflated with
     * {@link #inflatePreference(LayoutInflater, ViewGroup, View)}.
     */
    private static void setPreferenceTitle(View parent, int resId) {
        final TextView title = (TextView) parent.findViewById(android.R.id.title);
        title.setText(resId);
    }

    /**
     * Set {@link android.R.id#summary} for a preference view inflated with
     * {@link #inflatePreference(LayoutInflater, ViewGroup, View)}.
     */
    private static void setPreferenceSummary(View parent, CharSequence string) {
        final TextView summary = (TextView) parent.findViewById(android.R.id.summary);
        summary.setVisibility(View.VISIBLE);
        summary.setText(string);
    }
}
