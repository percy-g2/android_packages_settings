/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.RingtonePreference;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;

import com.qrd.plugin.feature_query.FeatureQuery;

public class DefaultRingtonePreference extends RingtonePreference {
    private static final String TAG = "DefaultRingtonePreference";

    private static final int SELECT_SYSTEM = 0;
    private static final int SELECT_EXTERNAL = 1;
    private int mSelectedItem;

    public DefaultRingtonePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        if (FeatureQuery.FEATURE_SETTINGS_PICK_RINGTONE_FROM_EXTERNAL
                && (getRingtoneType() == RingtoneManager.TYPE_RINGTONE
                || getRingtoneType() == RingtoneManager.TYPE_RINGTONE_2)) {
            String[] items = new String[2];
            items[SELECT_SYSTEM] = getContext().getString(R.string.system_ringtone_item);
            items[SELECT_EXTERNAL] = getContext().getString(R.string.external_ringtone_item);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_list_item_1, android.R.id.text1, items);

            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            AlertDialog dialog = builder.setTitle(R.string.select_ringtone_title)
                    .setAdapter(adapter, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mSelectedItem = which;
                            DefaultRingtonePreference.super.onClick();
                        }
                    })
                    .setCancelable(true)
                    .create();
            dialog.show();
        } else {
            mSelectedItem = SELECT_SYSTEM;
            super.onClick();
        }
    }

    @Override
    protected void onPrepareRingtonePickerIntent(final Intent ringtonePickerIntent) {
        super.onPrepareRingtonePickerIntent(ringtonePickerIntent);

        /*
         * Since this preference is for choosing the default ringtone, it
         * doesn't make sense to show a 'Default' item.
         */
        switch (mSelectedItem) {
            case SELECT_SYSTEM:
                ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
                break;
            case SELECT_EXTERNAL:
                ringtonePickerIntent.setAction(Intent.ACTION_PICK);
                ringtonePickerIntent.setData(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
                ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, onRestoreRingtone());
                break;
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (mSelectedItem) {
            case SELECT_SYSTEM:
                return super.onActivityResult(requestCode, resultCode, data);
            case SELECT_EXTERNAL:
                if (data != null) {
                    Uri uri = data.getData();
                    if (callChangeListener(uri != null ? uri.toString() : "")) {
                        onSaveRingtone(uri);
                    }
                    return true;
                }
        }
        return false;
    }

    @Override
    protected void onSaveRingtone(Uri ringtoneUri) {
        RingtoneManager.setActualDefaultRingtoneUri(getContext(), getRingtoneType(), ringtoneUri);
    }

    @Override
    protected Uri onRestoreRingtone() {
        return RingtoneManager.getActualDefaultRingtoneUri(getContext(), getRingtoneType());
    }
}
