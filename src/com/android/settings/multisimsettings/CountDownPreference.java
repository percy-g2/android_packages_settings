/*
 *
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.settings.multisimsettings;

import android.content.Context;
import android.preference.DialogPreference;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.SeekBar;
import com.android.settings.R;

import com.android.settings.multisimsettings.MultiSimSettingsConstants;

public class CountDownPreference extends DialogPreference implements
        SeekBar.OnSeekBarChangeListener {

    private SeekBar mSeekBar;
    private TextView mLabel;
    private int mCurrentWaitTime;

    private static final int WAIT_INFINITE = -1;
    private static final int MINIMUM_WAIT = 0;
    private static final int MAXIMUM_WAIT = 11;

    public CountDownPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.multi_sim_count_down);
        setDialogIcon(R.drawable.ic_settings_date_time);

        try {
            mCurrentWaitTime = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.MULTI_SIM_COUNTDOWN);
        } catch (SettingNotFoundException snfe) {
            mCurrentWaitTime = MultiSimSettingsConstants.DEFAULT_COUNTDOWN_TIME;
        }
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mLabel = (TextView) view.findViewById(R.id.countdown_time_label);
        mSeekBar = (SeekBar) view.findViewById(com.android.internal.R.id.seekbar);
        mSeekBar.setMax(MAXIMUM_WAIT);
        mSeekBar.setProgress(mCurrentWaitTime == WAIT_INFINITE ? MAXIMUM_WAIT : mCurrentWaitTime);
        mSeekBar.setOnSeekBarChangeListener(this);

        mLabel.setText(getFormatTime(mCurrentWaitTime));
    }

    private String getFormatTime(int time) {
        String waitTime;
        if (time == MINIMUM_WAIT) {
            waitTime = getContext().getResources().getString(R.string.no_wait_time);
        } else if (time == WAIT_INFINITE) {
            waitTime = getContext().getResources().getString(R.string.infinite_wait_time);
        } else {
            waitTime = "" + time + getContext().getResources().getString(R.string.wait_time_unit);
        }
        return waitTime;
    }

    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromTouch) {
        int time = (progress == MAXIMUM_WAIT) ? WAIT_INFINITE : progress;
        mLabel.setText(getFormatTime(time));
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
        // NA
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        // NA
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            int waitTime = mSeekBar.getProgress();
            if (waitTime == MAXIMUM_WAIT) {
                waitTime = WAIT_INFINITE;
            }
            mCurrentWaitTime = waitTime;
            Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.MULTI_SIM_COUNTDOWN, mCurrentWaitTime);
            setSummary(getFormatTime(mCurrentWaitTime));
        }
    }

    public void updateSummary() {
        setSummary(getFormatTime(mCurrentWaitTime));
    }
}

