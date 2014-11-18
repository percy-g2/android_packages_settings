/*
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
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

package com.android.settings;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;
import com.android.settings.R;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.msim.MSimPhoneFactory;

public class PRLVersionDisplay extends Activity {

    private TextView phoneType;
    private TextView hardwareVersion;
    private TextView softwareVersion;
    private TextView prlVersion;
    private TextView uimId;
    private TextView esnValue;

    private String strPhoneType = "";
    private String strHW = "";
    private String strSW = "";
    private String strPRL = "";
    private String strUIM = "";
    private String strESN = "";

    private int mNumPhones = 0;
    private Phone cdmaPhone;
    private static final String TAG = "PRLVersionDisplay";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.prl_version_display);
        initResource();  
        mNumPhones = TelephonyManager.getDefault().getPhoneCount();

        for (int i = 0; i < mNumPhones; i++) {
            if ("CDMA".equals(MSimPhoneFactory.getPhone(i).getPhoneName())) {
                cdmaPhone = MSimPhoneFactory.getPhone(i);
                break;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getValuesToDisplay();
        phoneType.setText(getString(R.string.device_type) + " " + strPhoneType);
        hardwareVersion.setText(getString(R.string.hw_version) +  " " + strHW);
        softwareVersion.setText(getString(R.string.sw_version) +  " " + Build.VERSION.RELEASE);
        prlVersion.setText(getString(R.string.prl_ver) +  " " + strPRL);
        uimId.setText(getString(R.string.uimid) +  " " + strUIM);
        esnValue.setText(getString(R.string.esn_num) +  " " + strESN);
    }

    private void initResource() {
        phoneType = (TextView)this.findViewById(R.id.phone_type);
        hardwareVersion = (TextView)findViewById(R.id.hardware_version);
        softwareVersion = (TextView)findViewById(R.id.software_version);
        prlVersion = (TextView)findViewById(R.id.prl_version);
        uimId = (TextView)findViewById(R.id.uim_id);
        esnValue = (TextView)findViewById(R.id.esn);
    }

    private void getValuesToDisplay() {
        strPhoneType = Build.MODEL + DeviceInfoSettings.getMsvSuffix();

        if (cdmaPhone != null) {
            strPRL = cdmaPhone.getCdmaPrlVersion();
            strUIM = cdmaPhone.getEsn();
            strESN = cdmaPhone.getMeid();
        }
    }

}
