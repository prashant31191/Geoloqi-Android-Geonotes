package com.geoloqi.android.ui;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import com.geoloqi.android.Build;
import com.geoloqi.android.R;
import com.geoloqi.android.sdk.LQBuild;
import com.geoloqi.android.sdk.LQSession;
import com.geoloqi.android.sdk.LQSharedPreferences;
import com.geoloqi.android.sdk.LQTracker.LQTrackerProfile;
import com.geoloqi.android.sdk.service.LQService;
import com.geoloqi.android.sdk.service.LQService.LQBinder;

/**
 * <p>This activity class is used to expose location tracking
 * preferences to a user.</p>
 * 
 * @author Tristan Waddington
 */
public class SettingsActivity extends PreferenceActivity implements OnPreferenceChangeListener,
        OnPreferenceClickListener {
    private static final String TAG = "SettingsActivity";
    private static final String URL_PRIVACY_POLICY = "https://geoloqi.com/privacy?utm_source=preferences&utm_medium=app&utm_campaign=android";
    
    private static String sAppVersion;
    
    /** An instance of the default SharedPreferences. */
    private SharedPreferences mPreferences;
    private LQService mService;
    private boolean mBound;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        
        // Get a shared preferences instance
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Set any preference listeners
        Preference preference = findPreference(
                getString(R.string.pref_key_tracker_status));
        if (preference != null) {
            preference.setOnPreferenceChangeListener(this);
        }
        
        preference = findPreference(
                getString(R.string.pref_key_account_username));
        if (preference != null) {
            preference.setOnPreferenceClickListener(this);
        }
        
        preference = findPreference(
                getString(R.string.pref_key_privacy_policy));
        if (preference != null) {
            preference.setOnPreferenceClickListener(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        
        if (mPreferences != null) {
            Preference preference = null;
            
            // Display the account username
            preference = findPreference(getString(R.string.pref_key_account_username));
            if (preference != null) {
                preference.setSummary(LQSharedPreferences.getSessionUsername(this));
            }
            
            // Display the app version
            preference = findPreference(getString(R.string.pref_key_app_version));
            if (preference != null) {
                preference.setSummary(getAppVersion(this));
            }
            
            // Display the app build
            preference = findPreference(getString(R.string.pref_key_app_build));
            if (preference != null) {
                preference.setSummary(Build.APP_BUILD);
            }
            
            // Display the SDK version
            preference = findPreference(getString(R.string.pref_key_sdk_version));
            if (preference != null) {
                preference.setSummary(LQBuild.LQ_SDK_VERSION);
            }
            
            // Display the SDK build
            preference = findPreference(getString(R.string.pref_key_sdk_build));
            if (preference != null) {
                preference.setSummary(LQBuild.LQ_SDK_BUILD);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // Bind to the tracking service so we can call public methods on it
        Intent intent = new Intent(this, LQService.class);
        bindService(intent, mConnection, 0);
    }

    @Override
    public void onPause() {
        super.onPause();
        
        // Unbind from LQService
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (key.equals(getString(R.string.pref_key_tracker_status))) {
            boolean enableLocation = newValue.equals(true);
            
            if (enableLocation) {
                startTracker(this);
            } else {
                // Stop the tracker
                if (mBound && mService != null) {
                    mService.getTracker().setProfile(LQTrackerProfile.OFF);
                }
                
                // Stop the service
                stopService(new Intent(this, LQService.class));
            }
        }
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        boolean consumed = false;
        String key = preference.getKey();
        if (key.equals(getString(R.string.pref_key_account_username))) {
            LQSession session = mService.getSession();
            if (session != null) {
                if (session.isAnonymous()) {
                    // Start log-in Activity
                    startActivity(new Intent(this, AuthActivity.class));
                } else {
                    // TODO: Sign-out!
                }
            }
            consumed = true;
        } else if (key.equals(getString(R.string.pref_key_privacy_policy))) {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(URL_PRIVACY_POLICY));
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            consumed = true;
        }
        return consumed;
    }

    /** Get the human-readable application version. */
    public static String getAppVersion(Context context) {
        if (TextUtils.isEmpty(sAppVersion)) {
            PackageManager pm = context.getPackageManager();
            try {
                sAppVersion = pm.getPackageInfo(
                        context.getPackageName(), 0).versionName;
            } catch (NameNotFoundException e) {
                // Pass
            }
        }
        return sAppVersion;
    }
    
    /** Start the background location service. */
    public static void startTracker(Context c) {
        Intent intent = new Intent(c, LQService.class);
        intent.setAction(LQService.ACTION_FOREGROUND);
        intent.putExtra(LQService.EXTRA_NOTIFICATION, getNotification(c));
        c.startService(intent);
    }
    
    /** Get the {@link PendingIntent} used by the service Notification. */
    public static PendingIntent getPendingIntent(Context c) {
        Intent intent = new Intent(c, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.setAction(Intent.ACTION_DEFAULT);
        return PendingIntent.getActivity(c, 0, intent, 0);
    }
    
    /** Get the {@link Notification} used by the foreground service. */
    public static Notification getNotification(Context c) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(c);
        builder.setOnlyAlertOnce(true);
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        builder.setTicker(c.getString(R.string.foreground_notification_ticker));
        builder.setContentTitle(c.getString(R.string.app_name));
        builder.setContentText(c.getString(R.string.foreground_notification_text));
        builder.setContentIntent(getPendingIntent(c));
        return builder.getNotification();
    }
    
    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                // We've bound to LocalService, cast the IBinder and get LocalService instance.
                LQBinder binder = (LQBinder) service;
                mService = binder.getService();
                mBound = true;
            } catch (ClassCastException e) {
                // Pass
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };
}