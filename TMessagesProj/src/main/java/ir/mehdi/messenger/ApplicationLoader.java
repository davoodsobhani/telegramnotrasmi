/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package ir.mehdi.messenger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.multidex.MultiDexApplication;
import android.util.Base64;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.onesignal.OneSignal;

import java.io.File;
import java.io.RandomAccessFile;

import ir.mehdi.tgnet.ConnectionsManager;
import ir.mehdi.tgnet.SerializedData;
import ir.mehdi.tgnet.TLRPC;
import ir.mehdi.ui.Components.ForegroundDetector;

public class ApplicationLoader extends MultiDexApplication
{
	private static final Object sync = new Object();
	@SuppressLint("StaticFieldLeak")
	public static volatile Context applicationContext;
	public static volatile Handler applicationHandler;
	public static volatile boolean isScreenOn                    = false;
	public static volatile boolean mainInterfacePaused           = true;
	public static volatile boolean mainInterfacePausedStageQueue = true;
	public static volatile long mainInterfacePausedStageQueueTime;
	private static volatile boolean applicationInited = false;
	private static Drawable cachedWallpaper;
	private static boolean  isCustomTheme;
	private static int      serviceMessageColor;
	private static int      serviceSelectedMessageColor;

	public static void SetHiddenMode(Context context, boolean hiddenMode)
	{
		SharedPreferences        prefs  = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean("HiddenMode", hiddenMode);
		editor.commit();
	}

	public static boolean GetHiddenMode(Context context)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		return prefs.getBoolean("HiddenMode", false);
	}

	public static int GetLastZangoolehId(Context context)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		return prefs.getInt("LastZangoolehId", 0);
	}

	public static void SetLastZangoolehId(Context context, int Id)
	{
		SharedPreferences        prefs  = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt("LastZangoolehId", Id);
		editor.commit();
	}

	public static boolean IsLanguageSelected(Context context)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		return prefs.getBoolean("IsLanguageSelected", false);
	}

	public static void SetLanguageSelected(Context context, boolean status)
	{
		SharedPreferences        prefs  = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean("IsLanguageSelected", status);
		editor.commit();
	}

	private static void calcBackgroundColor()
	{
		int result[] = AndroidUtilities.calcDrawableColor(cachedWallpaper);
		serviceMessageColor = result[0];
		serviceSelectedMessageColor = result[1];
		SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
		preferences.edit().putInt("serviceMessageColor", serviceMessageColor).putInt("serviceSelectedMessageColor", serviceSelectedMessageColor).commit();
	}

	public static void loadWallpaper()
	{
		if (cachedWallpaper != null)
		{
			return;
		}
		Utilities.searchQueue.postRunnable(new Runnable()
		{
			@Override
			public void run()
			{
				synchronized (sync)
				{
					int selectedColor = 0;
					try
					{
						SharedPreferences preferences        = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
						int               selectedBackground = preferences.getInt("selectedBackground", 1000001);
						selectedColor = preferences.getInt("selectedColor", 0);
						serviceMessageColor = preferences.getInt("serviceMessageColor", 0);
						serviceSelectedMessageColor = preferences.getInt("serviceSelectedMessageColor", 0);
						if (selectedColor == 0)
						{
							if (selectedBackground == 1000001)
							{
								cachedWallpaper = applicationContext.getResources().getDrawable(R.drawable.background_hd);
								isCustomTheme = false;
							}
							else
							{
								File toFile = new File(getFilesDirFixed(), "wallpaper.jpg");
								if (toFile.exists())
								{
									cachedWallpaper = Drawable.createFromPath(toFile.getAbsolutePath());
									isCustomTheme = true;
								}
								else
								{
									cachedWallpaper = applicationContext.getResources().getDrawable(R.drawable.background_hd);
									isCustomTheme = false;
								}
							}
						}
					} catch (Throwable throwable)
					{
						//ignore
					}
					if (cachedWallpaper == null)
					{
						if (selectedColor == 0)
						{
							selectedColor = -2693905;
						}
						cachedWallpaper = new ColorDrawable(selectedColor);
					}
					if (serviceMessageColor == 0)
					{
						calcBackgroundColor();
					}
				}
			}
		});
	}

	public static Drawable getCachedWallpaper()
	{
		synchronized (sync)
		{
			return cachedWallpaper;
		}
	}

	private static void convertConfig()
	{
		SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("dataconfig", Context.MODE_PRIVATE);
		if (preferences.contains("currentDatacenterId"))
		{
			SerializedData buffer = new SerializedData(32 * 1024);
			buffer.writeInt32(2);
			buffer.writeBool(preferences.getInt("datacenterSetId", 0) != 0);
			buffer.writeBool(true);
			buffer.writeInt32(preferences.getInt("currentDatacenterId", 0));
			buffer.writeInt32(preferences.getInt("timeDifference", 0));
			buffer.writeInt32(preferences.getInt("lastDcUpdateTime", 0));
			buffer.writeInt64(preferences.getLong("pushSessionId", 0));
			buffer.writeBool(false);
			buffer.writeInt32(0);
			try
			{
				String datacentersString = preferences.getString("datacenters", null);
				if (datacentersString != null)
				{
					byte[] datacentersBytes = Base64.decode(datacentersString, Base64.DEFAULT);
					if (datacentersBytes != null)
					{
						SerializedData data = new SerializedData(datacentersBytes);
						buffer.writeInt32(data.readInt32(false));
						buffer.writeBytes(datacentersBytes, 4, datacentersBytes.length - 4);
						data.cleanup();
					}
				}
			} catch (Exception e)
			{
				FileLog.e(e);
			}

			try
			{
				File             file             = new File(getFilesDirFixed(), "tgnet.dat");
				RandomAccessFile fileOutputStream = new RandomAccessFile(file, "rws");
				byte[]           bytes            = buffer.toByteArray();
				fileOutputStream.writeInt(Integer.reverseBytes(bytes.length));
				fileOutputStream.write(bytes);
				fileOutputStream.close();
			} catch (Exception e)
			{
				FileLog.e(e);
			}
			buffer.cleanup();
			preferences.edit().clear().commit();
		}
	}

	public static File getFilesDirFixed()
	{
		for (int a = 0; a < 10; a++)
		{
			File path = ApplicationLoader.applicationContext.getFilesDir();
			if (path != null)
			{
				return path;
			}
		}
		try
		{
			ApplicationInfo info = applicationContext.getApplicationInfo();
			File            path = new File(info.dataDir, "files");
			path.mkdirs();
			return path;
		} catch (Exception e)
		{
			FileLog.e(e);
		}
		return new File("/data/data/ir.mehdi.messenger/files");
	}

	public static void postInitApplication()
	{
		if (applicationInited)
		{
			return;
		}

		applicationInited = true;
		convertConfig();

		try
		{
			LocaleController.getInstance();
		} catch (Exception e)
		{
			e.printStackTrace();
		}

		try
		{
			final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
			filter.addAction(Intent.ACTION_SCREEN_OFF);
			final BroadcastReceiver mReceiver = new ScreenReceiver();
			applicationContext.registerReceiver(mReceiver, filter);
		} catch (Exception e)
		{
			e.printStackTrace();
		}

		try
		{
			PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
			isScreenOn = pm.isScreenOn();
			FileLog.e("screen state = " + isScreenOn);
		} catch (Exception e)
		{
			FileLog.e(e);
		}

		UserConfig.loadConfig();
		String deviceModel;
		String langCode;
		String appVersion;
		String systemVersion;
		String configPath = getFilesDirFixed().toString();

		try
		{
			langCode = LocaleController.getLocaleStringIso639();
			deviceModel = Build.MANUFACTURER + Build.MODEL;
			PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
			appVersion = pInfo.versionName + " (" + pInfo.versionCode + ")";
			systemVersion = "SDK " + Build.VERSION.SDK_INT;
		} catch (Exception e)
		{
			langCode = "en";
			deviceModel = "Android unknown";
			appVersion = "App version unknown";
			systemVersion = "SDK " + Build.VERSION.SDK_INT;
		}
		if (langCode.trim().length() == 0)
		{
			langCode = "en";
		}
		if (deviceModel.trim().length() == 0)
		{
			deviceModel = "Android unknown";
		}
		if (appVersion.trim().length() == 0)
		{
			appVersion = "App version unknown";
		}
		if (systemVersion.trim().length() == 0)
		{
			systemVersion = "SDK Unknown";
		}

		SharedPreferences preferences          = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
		boolean           enablePushConnection = preferences.getBoolean("pushConnection", true);

		MessagesController.getInstance();
		ConnectionsManager.getInstance().init(BuildVars.BUILD_VERSION, TLRPC.LAYER, BuildVars.APP_ID, deviceModel, systemVersion, appVersion, langCode, configPath, FileLog.getNetworkLogPath(), UserConfig.getClientUserId(), enablePushConnection);
		if (UserConfig.getCurrentUser() != null)
		{
			MessagesController.getInstance().putUser(UserConfig.getCurrentUser(), true);
			ConnectionsManager.getInstance().applyCountryPortNumber(UserConfig.getCurrentUser().phone);
			MessagesController.getInstance().getBlockedUsers(true);
			SendMessagesHelper.getInstance().checkUnsentMessages();
		}

		ApplicationLoader app = (ApplicationLoader) ApplicationLoader.applicationContext;
		app.initPlayServices();
		FileLog.e("app initied");

		ContactsController.getInstance().checkAppAccount();
		MediaController.getInstance();
	}

	public static void startPushService()
	{
		SharedPreferences preferences = applicationContext.getSharedPreferences("Notifications", MODE_PRIVATE);

		if (preferences.getBoolean("pushService", true))
		{
			applicationContext.startService(new Intent(applicationContext, NotificationsService.class));
		}
		else
		{
			stopPushService();
		}
	}

	public static void stopPushService()
	{
		applicationContext.stopService(new Intent(applicationContext, NotificationsService.class));

		PendingIntent pintent = PendingIntent.getService(applicationContext, 0, new Intent(applicationContext, NotificationsService.class), 0);
		AlarmManager  alarm   = (AlarmManager) applicationContext.getSystemService(Context.ALARM_SERVICE);
		alarm.cancel(pintent);
	}

    /*public static void sendRegIdToBackend(final String token) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                UserConfig.pushString = token;
                UserConfig.registeredForPush = false;
                UserConfig.saveConfig(false);
                if (UserConfig.getClientUserId() != 0) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            MessagesController.getInstance().registerForPush(token);
                        }
                    });
                }
            }
        });
    }*/

	@Override
	public void onCreate()
	{
		super.onCreate();

		InitializeAdelPushServices(); //telegraf

		applicationContext = getApplicationContext();
		NativeLoader.initNativeLibs(ApplicationLoader.applicationContext);
		ConnectionsManager.native_setJava(Build.VERSION.SDK_INT == 14 || Build.VERSION.SDK_INT == 15);
		new ForegroundDetector(this);

		applicationHandler = new Handler(applicationContext.getMainLooper());

		startPushService();
	}

	//telegraf
	private void InitializeAdelPushServices()
	{
		// OneSignal
		OneSignal.startInit(this).init();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		try
		{
			LocaleController.getInstance().onDeviceConfigurationChange(newConfig);
			AndroidUtilities.checkDisplaySize(applicationContext, newConfig);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void initPlayServices()
	{
		AndroidUtilities.runOnUIThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (checkPlayServices())
				{
					if (UserConfig.pushString != null && UserConfig.pushString.length() != 0)
					{
						FileLog.d("GCM regId = " + UserConfig.pushString);
					}
					else
					{
						FileLog.d("GCM Registration not found.");
					}

					InitializeAdelPushServices(); //telegraf

					//if (UserConfig.pushString == null || UserConfig.pushString.length() == 0) {
					Intent intent = new Intent(applicationContext, GcmRegistrationIntentService.class);
					startService(intent);
					//} else {
					//    FileLog.d("GCM regId = " + UserConfig.pushString);
					//}
				}
				else
				{
					FileLog.d("No valid Google Play Services APK found.");
				}
			}
		}, 1000);
	}

    /*private void initPlayServices() {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (checkPlayServices()) {
                    if (UserConfig.pushString != null && UserConfig.pushString.length() != 0) {
                        FileLog.d("GCM regId = " + UserConfig.pushString);
                    } else {
                        FileLog.d("GCM Registration not found.");
                    }
                    try {
                        if (!FirebaseApp.getApps(ApplicationLoader.applicationContext).isEmpty()) {
                            String token = FirebaseInstanceId.getInstance().getToken();
                            if (token != null) {
                                sendRegIdToBackend(token);
                            }
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                } else {
                    FileLog.d("No valid Google Play Services APK found.");
                }
            }
        }, 2000);
    }*/

	private boolean checkPlayServices()
	{
		try
		{
			int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
			return resultCode == ConnectionResult.SUCCESS;
		} catch (Exception e)
		{
			FileLog.e(e);
		}
		return true;

        /*if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i("tmessages", "This device is not supported.");
            }
            return false;
        }
        return true;*/
	}
}
