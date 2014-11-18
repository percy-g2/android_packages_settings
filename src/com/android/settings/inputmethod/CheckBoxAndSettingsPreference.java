/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2012, The Linux Foundation. All Rights Reserved.
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

package com.android.settings.inputmethod;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import android.content.Context;
import android.content.Intent;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

public class CheckBoxAndSettingsPreference extends CheckBoxPreference {
    private static final float DISABLED_ALPHA = 0.4f;

    private SettingsPreferenceFragment mFragment;
    private TextView mTitleText;
    private TextView mSummaryText;
    private ImageView mSettingsButton;
    private Intent mSettingsIntent;

    private Callbacks mCallbacks ;

    public CheckBoxAndSettingsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_inputmethod);
        setWidgetLayoutResource(R.layout.preference_inputmethod_widget);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        View textLayout = view.findViewById(R.id.inputmethod_pref);
        textLayout.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        onCheckBoxClicked();
                        if(null != mCallbacks){
                             mCallbacks.onCheckBoxClicked();
                        }
                    }
                });

        mSettingsButton = (ImageView) view.findViewById(R.id.inputmethod_settings);
        mTitleText = (TextView)view.findViewById(android.R.id.title);
        mSummaryText = (TextView)view.findViewById(android.R.id.summary);
        mSettingsButton.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View clickedView) {
                        onSettingsButtonClicked();
                        if(null != mCallbacks){
                            mCallbacks.onSettingsButtonClicked(clickedView);
                        }
                    }
                });
        enableSettingsButton();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        enableSettingsButton();
    }

    @Override
    public void setChecked(boolean enabled) {
        super.setChecked(enabled);
        // handle enableSettingsButton according to the preference's checkbox
        enableSettingsButton();
    }

    public void setFragmentIntent(SettingsPreferenceFragment fragment, Intent intent) {
        mFragment = fragment;
        mSettingsIntent = intent;
    }

    protected void onCheckBoxClicked() {
        if (isChecked()) {
            setChecked(false);
        } else {
            setChecked(true);
        }
    }

    protected void onSettingsButtonClicked() {
        if (mFragment != null && mSettingsIntent != null) {
            mFragment.startActivity(mSettingsIntent);
        }
    }

    private void enableSettingsButton() {
        if (mSettingsButton != null) {
            if (mSettingsIntent == null) {
                mSettingsButton.setVisibility(View.GONE);
            } else {
                // Enable or disable mSettingsButton according to the preference, if the
                // preference is checked, enabled mSettingsButton, otherwise disable it.
                final boolean checked = isChecked();
                mSettingsButton.setEnabled(checked);
                mSettingsButton.setClickable(checked);
                mSettingsButton.setFocusable(checked);
                if (!checked) {
                    mSettingsButton.setAlpha(DISABLED_ALPHA);
                }
            }
        }
        if (mTitleText != null) {
            mTitleText.setEnabled(true);
        }
        if (mSummaryText != null) {
            mSummaryText.setEnabled(true);
        }
    }

    public void setCallbacks(Callbacks callbacks){
        mCallbacks = callbacks;
    }

    public interface Callbacks {
        public void onCheckBoxClicked();
        public void onSettingsButtonClicked(View arg0);
    }
}
