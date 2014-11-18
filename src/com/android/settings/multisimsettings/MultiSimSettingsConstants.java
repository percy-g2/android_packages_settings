/*
 *
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *     Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
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

public class MultiSimSettingsConstants {
   /**
    * Default countdown time
    */
    public static final int DEFAULT_COUNTDOWN_TIME = 5;

   /**
     * Event for sim name has been changed.
     */
    public static final int EVENT_MULTI_SIM_NAME_CHANGED = 1;

   /**
     * Event for preferred subscription change
     */
    public static final int EVENT_PREFERRED_SUBSCRIPTION_CHANGED = 2;

   /**
     * Indicates the voice list, data list and sms list respectively.
     */
    public static final int[] PREFERRED_SUBSCRIPTION_LISTS = {100, 200, 300};

   /**
     * Indicates the voice list
     */
    public static final int VOICE_SUBSCRIPTION_LIST = 100;

   /**
     * Indicates the data list
     */
    public static final int DATA_SUBSCRIPTION_LIST  = 200;

   /**
     * Indicates the sms list
     */
    public static final int SMS_SUBSCRIPTION_LIST   = 300;

   /**
       * Indicates target package
       */
    public static final String TARGET_PACKAGE = "PACKAGE";

   /**
       * Indicates target class
       */
    public static final String TARGET_CLASS = "TARGET_CLASS";

   /**
       * Indicates multi sim network related setting package
       */
    public static final String NETWORK_PACKAGE = "com.android.phone";

   /**
       * Indicates multi sim network related setting class
       */
    public static final String NETWORK_CLASS = "com.android.phone.MSimMobileNetworkSubSettings";

   /**
       * Indicates multi sim call related setting package
       */
    public static final String CALL_PACKAGE = "com.android.phone";

   /**
       * Indicates multi sim call related setting class
       */
    public static final String CALL_CLASS = "com.android.phone.MSimCallFeaturesSubSetting";

   /**
       * Indicates multi sim config related settings package
       */
    public static final String CONFIG_PACKAGE = "com.android.settings";

   /**
       * Indicates multi sim config related settings class
       */
    public static final String CONFIG_CLASS = "com.android.settings.multisimsettings.MultiSimConfiguration";

   /**
       * Indicates multi sim sound related setting package
       */
    public static final String SOUND_PACKAGE = "com.android.settings";

   /**
       * Indicates multi sim sound related setting class
       */
    public static final String SOUND_CLASS = "com.android.settings.multisimsettings.MultiSimSoundSettings";

   /**
       * Indicates multi sim name changed
       */
    public static final String SUBNAME_CHANGED = "com.android.settings.SUBNAME_CHANGED";
}
