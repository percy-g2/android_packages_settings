package com.android.settings.applications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.KeyEvent;
import android.view.WindowManager;

public class InstalledAppDetailsTop extends PreferenceActivity {

    private final String broadCastString = "Android.settings.applications.InstalledAppDetails.BROADCAST_KILL_DETAILS";
    private IntentFilter mKillIntentFilter = new IntentFilter(broadCastString);
    private boolean mNeedFinish = false;
    private int mStartMeObjectHashCode;
    private int mBroadcastMeObjectHashCode;

    private final BroadcastReceiver mKillReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            mBroadcastMeObjectHashCode = intent.getIntExtra("hashCode", -2);
            mNeedFinish = true;
        }
    };

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, InstalledAppDetails.class.getName());
        modIntent.putExtra(EXTRA_NO_HEADERS, true);
        return modIntent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mStartMeObjectHashCode = super.getIntent().getIntExtra("hashCode", -1);
    }

    @Override
    public void onResume() {
        super.onResume();
        mNeedFinish = false;
        registerReceiver(mKillReceiver, mKillIntentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mKillReceiver);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mNeedFinish && (mStartMeObjectHashCode == mBroadcastMeObjectHashCode)) {
            finish();
        }
    }
}
