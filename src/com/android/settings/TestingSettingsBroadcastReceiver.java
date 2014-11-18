package com.android.settings;

import android.provider.Telephony;
import static android.provider.Telephony.Intents.SECRET_CODE_ACTION;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.util.Log;
import android.view.KeyEvent;

import com.qrd.plugin.feature_query.FeatureQuery;

public class TestingSettingsBroadcastReceiver extends BroadcastReceiver {
  
    public TestingSettingsBroadcastReceiver() {
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(SECRET_CODE_ACTION)) {
            // if close eng mode, do nothing.
            if (FeatureQuery.FEATURE_SETTINGS_CLOSE_ENG_MODE) {
                return;
            }

            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClass(context, TestingSettings.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        } else if (intent.getAction().equals("android.provider.Telephony.PRL_VERSION")) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClass(context, PRLVersionDisplay.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }
}
