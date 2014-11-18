/*
* Copyright (c) 2012, The Linux Foundation. All rights reserved.
 *
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.settings;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.DialogPreference;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;


import com.qualcomm.cabl.ICABLService;

public class CABLDialogPreference extends DialogPreference{

    private ListView mListView = null;
    private TextView mTextView = null;
    private static String[] mLevels;
    private final static String TAG = "CABLDialogPreference";
    private int mLevel = 0;
    private static ICABLService mCABLService;

    public CABLDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.preference_dialog_cabl);
        setDialogIcon(R.drawable.ic_settings_display);
        this.setPersistent(true);
        mLevel = android.provider.Settings.System.getInt(context.getContentResolver(),
                Settings.System.CABL_LEVELS, 0);
    }

    public static void setCABLService(ICABLService service){
        mCABLService = service;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        // This preference type's value type is Integer, so we read the default
        // value from the attributes as an Integer.
        return a.getInteger(index, 0);
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);
        /*
         * The typical interaction for list-based dialogs is to have
         * click-on-an-item dismiss the dialog instead of the user having to
         * press 'Ok'.
         */
        builder.setPositiveButton(null, null);
    }

     @Override
     protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            // Restore state
            mLevel = getPersistedInt(mLevel);
        } else {
            // Set state
            int value = (Integer) defaultValue;
            mLevel = value;
            persistInt(mLevel);
        }
     }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mListView = (ListView)view.findViewById(R.id.list_cabl);

        //cabl brightness levels
        mLevels = this.getContext().getResources().getStringArray(R.array.cabl_level_values);

        mListView.setAdapter(new ArrayAdapter<String>(this.getContext(),
                android.R.layout.simple_list_item_single_choice, mLevels));

        mListView.setItemsCanFocus(false);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mListView.setItemChecked(mLevel, true);
        mListView.setOnItemClickListener(new OnItemClickListener(){

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                mLevel = Integer.valueOf(mLevels[position]);
                persistInt(mLevel);

                if(null != mCABLService){
                    Log.d(TAG, "set cabl level=" + mLevel);

                    try {
                        mCABLService.setCABLLevel(mLevel);
                    } catch (RemoteException e) {
                        Log.e(TAG, "setCABLLevel, exception");
                    }

                }

                //dismiss the dialog .
                CABLDialogPreference.this.getDialog().dismiss();
            }

        });
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
    }
}
