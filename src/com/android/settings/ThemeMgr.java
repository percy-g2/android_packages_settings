/*
 * Copyright (C) 2012, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above
       copyright notice, this list of conditions and the following
       disclaimer in the documentation and/or other materials provided
       with the distribution.
     * Neither the name of The Linux Foundation nor the names of its
       contributors may be used to endorse or promote products derived
       from this software without specific prior written permission.
 
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

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import java.lang.Thread;
import android.util.Log;
import android.widget.Toast;
import android.os.PowerManager;
import java.io.File;
import java.security.MessageDigest;
import java.io.FileInputStream;  
import java.io.FileOutputStream; 
import com.android.internal.util.HexDump;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.io.InputStream;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.res.Configuration;
//import java.lang.Runtime;
//import android.os.Process;

public class ThemeMgr extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener, OnCancelListener {
    private static final String TAG="ThemeSettings";
	private static final String SYSTHEMEPATH="/system/qrd_theme";
    private static final String EXTTHEMEPATH="/sdcard/qrd_theme";
	private static final String DESTTHEMEPATH="/data/qrd_theme";
    //private static final String PROPERTY_THEME = "persist.sys.theme";
    private static final String PROPERTY_THEME_CONFIG = "persist.sys.qrd_theme.current";
    //private static final String SETTINGS_THEME_RELOAD = "theme_reload";

    private static final String ACTION_THEME_CHANGE = "com.qrd.thememgr.action.SWITCH_THEME";
    //private static final String ACTION_THEME_SAVE_OK = "com.android.launcher.action.SAVE_THEME_OK";

    private ProgressDialog mProgressDialog;
    private String mSelectedThemeConfig;
	private String mSelectedThemeKey;
    private ThemePreference mSelectedTheme;

    // We use static array here for demo.
    private static final String[] themeKeys = {
        "google",
        "qrd"
    };
    
    private static final int[] themeTitlesId = {
        R.string.google_theme,
        R.string.qrd_theme
    };

    private static final String[] themeConfigs = {
        "default",       // default google theme
        "red"    // qrd theme
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.theme_settings);
        getListView().setItemsCanFocus(true);
        fillList();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }

	
	private void populateThemeItem(PreferenceGroup list, String currentTheme, String title, String key, String config) {	
		ThemePreference pref = new ThemePreference(this);
		pref.setKey(key); 
		pref.setTitle(title);
		pref.setConfig(config);
		pref.setOnPreferenceChangeListener(this);

		if (currentTheme.equals(config)) {
			pref.setChecked();
			mSelectedTheme = pref;
		}

		list.addPreference(pref);	
	}
	
    private void fillList() {
        PreferenceGroup themeList = (PreferenceGroup) findPreference("theme_list");
        themeList.removeAll();

        String mSelectedThemeConfig = SystemProperties.get(PROPERTY_THEME_CONFIG, "default");
		
		//first populate the system default
		String defaultTitle = null;
		try {		
		defaultTitle = this.getResources().getString(R.string.default_theme);		
		} catch(Exception e) {		
		defaultTitle = "Default";
		}
		
		populateThemeItem(themeList, mSelectedThemeConfig, defaultTitle, "sys_default","default");
		
		//second, populate the system preload theme
		
		File systemThemeDir = new File(SYSTHEMEPATH);
		File[] innerSysFiles = systemThemeDir.listFiles();
		
		String preLoadThemeTitle = null;
		try {		
		preLoadThemeTitle = this.getResources().getString(R.string.preload_theme);		
		} catch(Exception e) {		
		preLoadThemeTitle = "Preload";
		}
		
		if (innerSysFiles != null) {
			for (File file : innerSysFiles) {  
			  if (file.isFile()) {  
				 String fileName = file.getName(); 
				 Log.e(TAG, "inner file name is :" + fileName);
				 if ((!fileName.startsWith("0x")) && fileName.endsWith("zip")) {
					String themeConfig = fileName.substring(0, fileName.lastIndexOf(".zip"));
					String themeTitle = themeConfig + "(" + preLoadThemeTitle + ")";
					populateThemeItem(themeList, mSelectedThemeConfig, themeTitle, "sys_preload_" + themeConfig, themeConfig);			 
				 }			 
			   } 
			}
		}
		
		
		
		
		//last, populate the external theme
		
		if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
		
		    Log.e(TAG, "Start populate sd card theme");
			File externalThemeDir = new File(EXTTHEMEPATH);
            File[] innerFiles = externalThemeDir.listFiles();
			
            Log.e(TAG, "Start populate sd card theme");
			
			String extLoadThemeTitle = null;
			try {		
			extLoadThemeTitle = this.getResources().getString(R.string.external_theme);		
			} catch(Exception e) {		
			extLoadThemeTitle = "External";
			}
			
			if (innerFiles != null) {
			Log.e(TAG, "sd theme available");
				for (File file : innerFiles) {  
				  if (file.isFile()) {  
					 String fileName = file.getName(); 
					 Log.e(TAG, "external  file name is :" + fileName);
					 if (fileName.endsWith("zip")) {
						try {
						String themeName = fileName.substring(0, fileName.lastIndexOf(".zip"));
						String themeTitle = themeName + "(" + extLoadThemeTitle + ")";
						//currently, only use MD5 of the file name to identify the external theme, later, maybe we need use MD5 of the theme file to identify if the performance is not affected.
						MessageDigest digester = MessageDigest.getInstance("MD5");
						digester.update(themeName.getBytes(), 0, themeName.length());					
						//String themeConfig = "0x" + Base64.encodeToString(digester.digest(), Base64.DEFAULT);
						String themeConfig = "0x" + HexDump.toHexString(digester.digest());
						populateThemeItem(themeList, mSelectedThemeConfig, themeTitle, "external_" + themeName, themeConfig);	
						} catch (Exception e) {
						 Log.e(TAG, "" + e);
						}
						
					 }			 
				   } 
				}
			}			
		}

    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        Log.d(TAG, "preference is " + preference.getKey());
		mSelectedThemeKey = ((ThemePreference) preference).getKey();
        mSelectedThemeConfig = ((ThemePreference) preference).getConfig();
        //mSelectedTheme = preference;
        displayAlertDialog(preference.getTitle());

        // Don't update UI to opposite state until we're sure
        return true;
    }

    public void onCancel(DialogInterface dialog) {
        fillList();
    }
    
    void displayAlertDialog(CharSequence msg) {
        Log.d(TAG, "displayAlertDialog!" + msg);
		
		String warningTitle = null;
		try {		
			warningTitle = this.getResources().getString(R.string.switch_theme_warning);		
			} catch(Exception e) {		
			warningTitle = "Switch to theme: %s? if yes, handset will auto restart to make the theme take effect!";
		}
		warningTitle = warningTitle.replace("%s", msg);
        new AlertDialog.Builder(this).setMessage(warningTitle)
               .setTitle(android.R.string.dialog_alert_title)
               .setIcon(android.R.drawable.ic_dialog_alert)
               .setPositiveButton(android.R.string.yes, changeThemeOnClickListener)
               .setNegativeButton(android.R.string.no, changeThemeOnClickListener)
               .setOnCancelListener(this)
               .show();
    }
/*
    void displaySaveThemeDialog() {
        Log.d(TAG, "displaySaveThemeDialog!");
        new AlertDialog.Builder(this).setMessage(getString(R.string.save_theme_message))
               .setTitle(android.R.string.dialog_alert_title)
               .setIcon(android.R.drawable.ic_dialog_alert)
               .setPositiveButton(android.R.string.yes, saveThemeOnClickListener)
               .setNegativeButton(android.R.string.no, saveThemeOnClickListener)
               .show();
    }

    void displayProgressDialog () {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setTitle(R.string.save_theme);
            mProgressDialog.setMessage(getString(R.string.saving_theme));
        }

        mProgressDialog.show();
    }
*/
    private OnClickListener changeThemeOnClickListener = new OnClickListener() {
        // This is a method implemented for DialogInterface.OnClickListener.
        public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, "onClick!");
            if (which == DialogInterface.BUTTON_POSITIVE) {
                dialog.dismiss();
               // displaySaveThemeDialog();
			   changeTheme();
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                Log.d(TAG, "on cancel click");
                dialog.cancel();
            }
        }
    };
/*
    private OnClickListener saveThemeOnClickListener = new OnClickListener() {
        // This is a method implemented for DialogInterface.OnClickListener.
        public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, "onClick!");
            if (which == DialogInterface.BUTTON_POSITIVE) {
                dialog.dismiss();
                displayProgressDialog();
                doThemeSave();
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                Log.d(TAG, "on cancel click");
                dialog.dismiss();
                changeTheme();
            }
        }
    };

    private void doThemeSave() {
        Intent intent = new Intent(ACTION_THEME_SAVE);
        sendBroadcast(intent);  // make launcher save current theme
        IntentFilter filter = new IntentFilter(ACTION_THEME_SAVE_OK);
        registerReceiver(mReceiver, filter);    // register receiver to receive the response of saving theme action from launcher
    }
*/

    private void extractFile(String entry, ZipFile zipSrc, String destFilePath) throws Exception {
        InputStream fis = null;
        FileOutputStream fos = null;
        ZipEntry zipItem = null;
        File destFile = null;

        if ((zipItem = zipSrc.getEntry(entry)) != null && (fis = zipSrc.getInputStream(zipItem)) != null) {
            destFile = new File(destFilePath);
            fos = new FileOutputStream(destFile); 
            int readLen = 0;  
            byte[] buf = new byte[1024];  
            while ((readLen = fis.read(buf)) != -1) {  
               fos.write(buf, 0, readLen);  
            } 
            
            fos.flush();  
            fos.close();  
            fis.close();  	
            
            if (destFile.exists()) {
                destFile.setReadable(true, false);
                destFile.setExecutable(true, false);
                destFile.setWritable(true, true);			
            }        
        }
	}
    
    private void copySDFile() throws Exception {
		File srcFile = new File(EXTTHEMEPATH + "/" + mSelectedThemeKey.substring("external_".length()) + ".zip");
		File destFile = new File(DESTTHEMEPATH + "/" + mSelectedThemeConfig + ".zip");
		FileInputStream fis = new FileInputStream(srcFile);  
        FileOutputStream fos = new FileOutputStream(destFile);  
		
        int readLen = 0;  
        byte[] buf = new byte[1024];  
        while ((readLen = fis.read(buf)) != -1) {  
           fos.write(buf, 0, readLen);  
        }  
        fos.flush();  
        fos.close();  
        fis.close();  	
		
		if (destFile.exists()) {
		destFile.setReadable(true, false);
		destFile.setExecutable(true, false);
		destFile.setWritable(true, true);			
		}
	}
	
    private void changeTheme() {
	
        //first time, need mkdir the /data/qrd_theme and sub folder
        File checkDir = new File(DESTTHEMEPATH);        
        if (!checkDir.exists()) {
        checkDir.mkdir();
        checkDir.setReadable(true, false);
        checkDir.setExecutable(true, false);
        checkDir.setWritable(true, true);
        
        checkDir = new File(DESTTHEMEPATH + "/boot");  
        checkDir.mkdir();
        checkDir.setReadable(true, false);
        checkDir.setExecutable(true, false);
        checkDir.setWritable(true, true);        
			}
            
		//first remove old boot animation/ringtones
        
        File themeBootDir = new File(DESTTHEMEPATH + "/boot");
        File[] bootFiles = themeBootDir.listFiles();
        
        if (bootFiles != null ) {
            for (File file : bootFiles) {  
              if (file.isFile()) 
                    file.delete();			 
            }
        }
		
	    //for external theme processing
		if (mSelectedThemeKey.startsWith("external_")) {		

			
			File checkFile = new File(DESTTHEMEPATH + "/" + mSelectedThemeConfig + ".zip");
			if (!checkFile.exists()) {
				//make sure only one external them copy on system folder
				
				File systemThemeDir = new File(DESTTHEMEPATH);
				File[] sysFiles = systemThemeDir.listFiles();
				
				if (sysFiles != null ) {
					for (File file : sysFiles) {  
					  if (file.isFile()) {  
						 String fileName = file.getName(); 
						 if (fileName.startsWith("0x") && fileName.endsWith("zip")) {
							file.delete();			 
						 }			 
					   } 
					}
				}
				
				
				
				//copy the external selected theme into sys folder
				try {
				copySDFile();
				} catch (Exception e) {
				Log.e(TAG, "" + e);
				}
			}
		
		}		
		
        //populate the theme's boot animation into theme boot dir
        if (!mSelectedThemeConfig.equals("default")) {
            try {
                String themeBasePath;
                if (!mSelectedThemeConfig.startsWith("0x"))
                                themeBasePath = "/system/qrd_theme/";
                            else
                                themeBasePath = "/data/qrd_theme/";
                            
                ZipFile themeSrc = new ZipFile(themeBasePath + mSelectedThemeConfig + ".zip");                
                
                //extract and copy bootanimation files
                extractFile("boot/bootanimation.zip", themeSrc, DESTTHEMEPATH + "/boot/bootanimation.zip");
                extractFile("boot/shutdownanimation.zip", themeSrc, DESTTHEMEPATH + "/boot/shutdownanimation.zip");
                extractFile("boot/boot.wav", themeSrc, DESTTHEMEPATH + "/boot/boot.wav");
                extractFile("boot/shutdown.wav", themeSrc, DESTTHEMEPATH + "/boot/shutdown.wav");        
                
                themeSrc.close();
            } catch (Exception e) {
                android.util.Log.e("Populate boot files exception", "" + e);	
            }      
        }
        
        
        
        
        SystemProperties.set(PROPERTY_THEME_CONFIG, mSelectedThemeConfig);
		android.util.Log.e("ThemeMgr", "change Theme to: " + PROPERTY_THEME_CONFIG + "with: " + mSelectedThemeConfig);	
		/*
        Settings.System.putInt(getContentResolver(),
            SETTINGS_THEME_RELOAD, 1);
			*/
        String toastText = getString(R.string.apply_theme);
        Toast.makeText(ThemeMgr.this,
            toastText, Toast.LENGTH_LONG).show();
	    Intent intent = new Intent(ACTION_THEME_CHANGE);
		android.util.Log.e("ThemeMgr", "start send broadcast");	
		sendBroadcast(intent); 
		try {
		Thread.sleep(700);
		} catch (Exception e) {
		//nothing
		android.util.Log.e("ThemeMgr", "" + e);
		}
		android.util.Log.e("ThemeMgr", "start restart launcher");	
        restartLauncher();
    }

    private void restartLauncher() {
	
	/*
        ActivityManager acm = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);		
		// Kill all process which support theme.
        acm.restartPackage("com.android.launcher");
		acm.restartPackage("com.android.settings");
		
        Intent homeIntent =  new Intent(Intent.ACTION_MAIN, null);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(homeIntent);
	*/
	    //SystemProperties.set("persist.sys.themeswitch", "true")
        
    /*
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		pm.reboot(null);
        finish();
    */
    
    
    try {
            IActivityManager am = ActivityManagerNative.getDefault();
            Configuration config = am.getConfiguration();

            config.uiMode = 0xffffffff; //use 0xffffffff uimode as theme switch

            am.updateConfiguration(config);
            
        } catch (Exception e) {
            // Intentionally left blank
            android.util.Log.e("ThemeMgr", "switch theme failed:" + e);	
        }

        
    }
    
	
/*
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {
            final String action = intent.getAction();
            Log.i(TAG, "action " + action);
            if (ACTION_THEME_SAVE_OK.equals(action)) {
                unregisterReceiver(this);
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                changeTheme();
            }
        }
    };
	*/

    // When user click the home icon we need finish current activity.
    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }
}
