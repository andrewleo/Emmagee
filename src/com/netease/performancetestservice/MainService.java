/*
 * Copyright (c) 2012-2013 NetEase, Inc. and other contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.netease.performancetestservice;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.netease.performancetestservice.R;
import com.netease.performancetestservice.utils.Constants;
import com.netease.performancetestservice.utils.CpuInfo;
import com.netease.performancetestservice.utils.CurrentInfo;
import com.netease.performancetestservice.utils.EncryptData;
import com.netease.performancetestservice.utils.MemoryInfo;
import com.netease.performancetestservice.utils.MyApplication;
import com.netease.performancetestservice.utils.ProcessInfo;
import com.netease.performancetestservice.utils.Programe;
import com.netease.performancetestservice.utils.Settings;

/**
 * Service running in background
 * 
 * @author andrewleo
 */
public class MainService extends Service {

	private final static String LOG_TAG = "Emmagee-" + MainService.class.getSimpleName();

	private static final String BLANK_STRING = "";

	private WindowManager windowManager = null;
	private WindowManager.LayoutParams wmParams = null;
	private View viFloatingWindow;
	private float mTouchStartX;
	private float mTouchStartY;
	private float x;
	private float y;
	private TextView txtTotalMem;
	private TextView txtUnusedMem;
	private TextView txtTraffic;
	private Button btnStop;
	private Button btnWifi;
	private DecimalFormat fomart;
	private MemoryInfo memoryInfo;
	private WifiManager wifiManager;
	private Handler handler = new Handler();
	private CpuInfo cpuInfo;
	private boolean isFloating;
	private boolean isRoot;
	private boolean isAutoStop = false;
	private String processName, packageName;
	private int pid, uid;
	private boolean isServiceStop = false;
	private String sender, password, recipients, smtp;
	private String[] receivers;
	private EncryptData des;
	private ProcessInfo procInfo;

	public static BufferedWriter bw;
	public static FileOutputStream out;
	public static OutputStreamWriter osw;
	public static String resultFilePath;
	public static boolean isStop = false;

	private String totalBatt;
	private String temperature;
	private String voltage;
	private CurrentInfo currentInfo;
	private BatteryInfoBroadcastReceiver batteryBroadcast = null;

	private static final String START_TIME = "#startTime";
	private String startTime = "";
	public static final String SERVICE_ACTION = "com.netease.action.emmageeService";
	private static final String BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED";
	private static final String PERF_MONITOR_DIR = "/sdcard/grape";
	private static final String PERF_MONITOR_FILE = PERF_MONITOR_DIR+"/PerformanceMonitor.csv";
	private int interval = 5000;
	
	@Override
	public void onCreate() {
		Log.i(LOG_TAG, "service onCreate");
		super.onCreate();
		isServiceStop = false;
		isStop = false;
		memoryInfo = new MemoryInfo();
		procInfo = new ProcessInfo();
		fomart = new DecimalFormat();
		fomart.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
		fomart.setGroupingUsed(false);
		fomart.setMaximumFractionDigits(2);
		fomart.setMinimumFractionDigits(0);
		des = new EncryptData("emmagee");
		currentInfo = new CurrentInfo();
		batteryBroadcast = new BatteryInfoBroadcastReceiver();
		registerReceiver(batteryBroadcast, new IntentFilter(BATTERY_CHANGED));
	}

	/**
	 * 电池信息监控监听器
	 * 
	 * @author andrewleo
	 * 
	 */
	public class BatteryInfoBroadcastReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {

			if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
				int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
				int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
				totalBatt = String.valueOf(level * 100 / scale);
				voltage = String.valueOf(intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) * 1.0 / 1000);
				temperature = String.valueOf(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) * 1.0 / 10);
			}

		}

	}
	
	/**
	 * wait for test application started.
	 * 
	 * @param packageName
	 *            package name of test application
	 */
	public void waitForAppStart(String packageName,Context context) {
		Log.d(LOG_TAG, "wait for app start");
		long startTime = System.currentTimeMillis();
		while (System.currentTimeMillis() < startTime + Constants.WAIT_START_TIMEOUT) {
			pid = procInfo.getPidByPackageName(getBaseContext(), packageName);
			if (pid != 0) {
				break;
			}
		}
		try {
			uid = getPackageManager().getApplicationInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES).uid;
		} catch (NameNotFoundException e) {
			Log.e(LOG_TAG, "get uid exception: " + e.getMessage());
			e.printStackTrace();
		}
		Log.d(LOG_TAG, String.format("uid = %s, pid = %s", uid, pid));
	}

	private int getInterval(String intervalParam) {
		try {
			if (intervalParam != null && !"".endsWith(intervalParam)) {
				return Integer.parseInt(intervalParam) * 1000;
			}
		} catch (Exception e) {
			return interval;
		}
		return interval;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(LOG_TAG, "service onStart");
		packageName = intent.getExtras().getString("package");
		interval = getInterval(intent.getExtras().getString("interval"));
		String startApp = intent.getExtras().getString("startApp");
		Log.d(LOG_TAG, "packageName="+packageName);
		Log.d(LOG_TAG, "interval="+interval);
		if (startApp != null && !"".equals(startApp)) {
			Intent startIntent = getPackageManager().getLaunchIntentForPackage(packageName);
			startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(startIntent);
		}
		waitForAppStart(packageName,getBaseContext());
		processName = intent.getExtras().getString("processName");
		
		cpuInfo = new CpuInfo(getBaseContext(), pid, Integer.toString(uid));
		readSettingInfo();
		if (isFloating) {
			viFloatingWindow = LayoutInflater.from(this).inflate(R.layout.floating, null);
			txtUnusedMem = (TextView) viFloatingWindow.findViewById(R.id.memunused);
			txtTotalMem = (TextView) viFloatingWindow.findViewById(R.id.memtotal);
			txtTraffic = (TextView) viFloatingWindow.findViewById(R.id.traffic);
			btnWifi = (Button) viFloatingWindow.findViewById(R.id.wifi);

			wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			if (wifiManager.isWifiEnabled()) {
				btnWifi.setText(R.string.close_wifi);
			} else {
				btnWifi.setText(R.string.open_wifi);
			}
			txtUnusedMem.setText(getString(R.string.calculating));
			txtUnusedMem.setTextColor(android.graphics.Color.RED);
			txtTotalMem.setTextColor(android.graphics.Color.RED);
			txtTraffic.setTextColor(android.graphics.Color.RED);
			btnStop = (Button) viFloatingWindow.findViewById(R.id.stop);
			btnStop.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.putExtra("isServiceStop", true);
					intent.setAction(SERVICE_ACTION);
					sendBroadcast(intent);
					stopSelf();
				}
			});
			createFloatingWindow();
		}
		createResultCsv();
		handler.postDelayed(task, 1000);
		return START_NOT_STICKY;
	}

	/**
	 * read configuration file.
	 * 
	 * @throws IOException
	 */
	private void readSettingInfo() {
		SharedPreferences preferences = Settings.getDefaultSharedPreferences(getApplicationContext());
		int interval = preferences.getInt(Settings.KEY_INTERVAL, 5);
		isFloating = preferences.getBoolean(Settings.KEY_ISFLOAT, false);
		sender = preferences.getString(Settings.KEY_SENDER, BLANK_STRING);
		password = preferences.getString(Settings.KEY_PASSWORD, BLANK_STRING);
		recipients = preferences.getString(Settings.KEY_RECIPIENTS, BLANK_STRING);
		receivers = recipients.split("\\s+");
		smtp = preferences.getString(Settings.KEY_SMTP, BLANK_STRING);
		isRoot = preferences.getBoolean(Settings.KEY_ROOT, false);
		isAutoStop = preferences.getBoolean(Settings.KEY_AUTO_STOP, false);
	}

	/**
	 * write the test result to csv format report.
	 */
	private void createResultCsv() {
		Log.d(LOG_TAG, "start write report");
		// 两个候选目录用于存储性能数据: /sdcard/grape/PerformanceMonitor.csv,  /sdcard/PerformanceMonitor.csv
		String[] dirs = new String[] {"/sdcard/grape", "/sdcard"};
		boolean createCompleted = false;
		File resultFile = null;
		// 这里尝试两个候选目录，如果都不行那暂时报异常出来
		for (int i = 0; i < dirs.length; i ++) {
			String dir = dirs[i];
			resultFilePath = dir + "/PerformanceMonitor.csv"; // 这边的性能文件命名改简单一点
			Log.i(LOG_TAG, "createNewFile in " + resultFilePath);
			resultFile = new File(resultFilePath);
			try {
				// 创建目录
				File fileDir = new File(dir);
				if (!fileDir.exists()) {
					fileDir.mkdirs();
				}
				resultFile.delete();
				// 只有在性能结果文件不存在的情况下才创建文件，并生成头文件，让文件只保持一份就好
				createCompleted = resultFile.createNewFile();
				if (createCompleted) {
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
				Log.w(LOG_TAG, "mkdir and createNewFile exception: " + e.getMessage());
			}
		}
		try {
			out = new FileOutputStream(resultFile, true); // 在文件内容后继续加内容
			osw = new OutputStreamWriter(out, "utf-8");
			bw = new BufferedWriter(osw);
			// 生成头文件
			// 用例，时间，栈顶activity，pss，内存占比，剩余内存，cpu占比，cpu总占比，流量，电量，电流，电池温度，电压
			bw.write("测试用例信息" + Constants.COMMA + getString(R.string.timestamp) + Constants.COMMA + getString(R.string.top_activity) + Constants.COMMA
					+ getString(R.string.used_mem_PSS) + Constants.COMMA + getString(R.string.used_mem_ratio) + Constants.COMMA
					+ getString(R.string.mobile_free_mem) + Constants.COMMA + getString(R.string.app_used_cpu_ratio) + Constants.COMMA
					+ getString(R.string.total_used_cpu_ratio) + Constants.COMMA + getString(R.string.traffic) + Constants.COMMA
					+ getString(R.string.battery) + Constants.COMMA + getString(R.string.current) + Constants.COMMA + getString(R.string.temperature)
					+ Constants.COMMA + getString(R.string.voltage) + Constants.LINE_END);
			bw.flush();
		} catch (IOException e) {
			Log.e(LOG_TAG, "create BufferedWriter exception: " + e.getMessage());
		}
		Log.d(LOG_TAG, "end write report");
	}

	/**
	 * create a floating window to show real-time data.
	 */
	private void createFloatingWindow() {
		SharedPreferences shared = getSharedPreferences("float_flag", Activity.MODE_PRIVATE);
		SharedPreferences.Editor editor = shared.edit();
		editor.putInt("float", 1);
		editor.commit();
		windowManager = (WindowManager) getApplicationContext().getSystemService("window");
		wmParams = ((MyApplication) getApplication()).getMywmParams();
		wmParams.type = 2002;
		wmParams.flags |= 8;
		wmParams.gravity = Gravity.LEFT | Gravity.TOP;
		wmParams.x = 0;
		wmParams.y = 0;
		wmParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
		wmParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		wmParams.format = 1;
		windowManager.addView(viFloatingWindow, wmParams);
		viFloatingWindow.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				x = event.getRawX();
				y = event.getRawY() - 25;
				switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					mTouchStartX = event.getX();
					mTouchStartY = event.getY();
					break;
				case MotionEvent.ACTION_MOVE:
					updateViewPosition();
					break;
				case MotionEvent.ACTION_UP:
					updateViewPosition();
					// showImg();
					mTouchStartX = mTouchStartY = 0;
					break;
				}
				return true;
			}
		});

		btnWifi.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					btnWifi = (Button) viFloatingWindow.findViewById(R.id.wifi);
					String buttonText = (String) btnWifi.getText();
					String wifiText = getResources().getString(R.string.open_wifi);
					if (buttonText.equals(wifiText)) {
						wifiManager.setWifiEnabled(true);
						btnWifi.setText(R.string.close_wifi);
					} else {
						wifiManager.setWifiEnabled(false);
						btnWifi.setText(R.string.open_wifi);
					}
				} catch (Exception e) {
					Toast.makeText(viFloatingWindow.getContext(), getString(R.string.wifi_fail_toast), Toast.LENGTH_LONG).show();
					Log.e(LOG_TAG, e.toString());
				}
			}
		});
	}

	private Runnable task = new Runnable() {

		public void run() {
			if (!isServiceStop) {
				dataRefresh();
				handler.postDelayed(this, interval);
				if (isFloating && viFloatingWindow != null) {
					windowManager.updateViewLayout(viFloatingWindow, wmParams);
				}
			} else {
				Intent intent = new Intent();
				intent.putExtra("isServiceStop", true);
				intent.setAction(SERVICE_ACTION);
				sendBroadcast(intent);
				stopSelf();
			}
		}
	};

	/**
	 * Above JellyBean, we cannot grant READ_LOGS permission...
	 * 
	 * @return
	 */
	private boolean isGrantedReadLogsPermission() {
		int permissionState = getPackageManager().checkPermission(android.Manifest.permission.READ_LOGS, getPackageName());
		return permissionState == PackageManager.PERMISSION_GRANTED;
	}

	/**
	 * refresh the performance data showing in floating window.
	 * 
	 * @throws FileNotFoundException
	 * 
	 * @throws IOException
	 */
	private void dataRefresh() {
		Log.d(LOG_TAG, "*** dataRefresh ***");
		int pidMemory = memoryInfo.getPidMemorySize(pid, getBaseContext());
		long freeMemory = memoryInfo.getFreeMemorySize(getBaseContext());
		String freeMemoryKb = fomart.format((double) freeMemory / 1024);
		String processMemory = fomart.format((double) pidMemory / 1024);
		String currentBatt = String.valueOf(currentInfo.getCurrentValue());
		// 异常数据过滤
		try {
			if (Math.abs(Double.parseDouble(currentBatt)) >= 500) {
				currentBatt = Constants.NA;
			}
		} catch (Exception e) {
			currentBatt = Constants.NA;
		}
		ArrayList<String> processInfo = cpuInfo.getCpuRatioInfo(totalBatt, currentBatt, temperature, voltage, false);
		if (isFloating) {
			String processCpuRatio = "0.00";
			String totalCpuRatio = "0.00";
			String trafficSize = "0";
			long tempTraffic = 0L;
			double trafficMb = 0;
			boolean isMb = false;
			if (!processInfo.isEmpty()) {
				processCpuRatio = processInfo.get(0);
				totalCpuRatio = processInfo.get(1);
				trafficSize = processInfo.get(2);
				if (!(BLANK_STRING.equals(trafficSize)) && !("-1".equals(trafficSize))) {
					tempTraffic = Long.parseLong(trafficSize);
					if (tempTraffic > 1024) {
						isMb = true;
						trafficMb = (double) tempTraffic / 1024;
					}
				}
				// 如果cpu使用率存在且都不小于0，则输出
				if (processCpuRatio != null && totalCpuRatio != null) {
					txtUnusedMem.setText(getString(R.string.process_free_mem) + processMemory + "/" + freeMemoryKb + "MB");
					txtTotalMem.setText(getString(R.string.process_overall_cpu) + processCpuRatio + "%/" + totalCpuRatio + "%");
					String batt = getString(R.string.current) + currentBatt;
					if ("-1".equals(trafficSize)) {
						txtTraffic.setText(batt + Constants.COMMA + getString(R.string.traffic) + Constants.NA);
					} else if (isMb)
						txtTraffic.setText(batt + Constants.COMMA + getString(R.string.traffic) + fomart.format(trafficMb) + "MB");
					else
						txtTraffic.setText(batt + Constants.COMMA + getString(R.string.traffic) + trafficSize + "KB");
				}
				// 当内存为0切cpu使用率为0时则是被测应用退出
				if ("0".equals(processMemory)) {
					closeOpenedStream();
					isServiceStop = true;
					return;
				}
			}

		}
	}

	/**
	 * update the position of floating window.
	 */
	private void updateViewPosition() {
		wmParams.x = (int) (x - mTouchStartX);
		wmParams.y = (int) (y - mTouchStartY);
		if (viFloatingWindow != null) {
			windowManager.updateViewLayout(viFloatingWindow, wmParams);
		}
	}

	/**
	 * close all opened stream.
	 */
	public void closeOpenedStream() {
		try {
			if (bw != null) {
//				bw.write(getString(R.string.comment1) + Constants.LINE_END + getString(R.string.comment2) + Constants.LINE_END
//						+ getString(R.string.comment3) + Constants.LINE_END + getString(R.string.comment4) + Constants.LINE_END);
				bw.close();
			}
			if (osw != null)
				osw.close();
			if (out != null)
				out.close();
		} catch (Exception e) {
			Log.d(LOG_TAG, e.getMessage());
		}
	}

	@Override
	public void onDestroy() {
		Log.i(LOG_TAG, "service onDestroy");
		if (windowManager != null) {
			windowManager.removeView(viFloatingWindow);
			viFloatingWindow = null;
		}
		handler.removeCallbacks(task);
		closeOpenedStream();
		// replace the start time in file
		if (!BLANK_STRING.equals(startTime)) {
			replaceFileString(resultFilePath, START_TIME, getString(R.string.start_time) + startTime + Constants.LINE_END);
		} else {
			replaceFileString(resultFilePath, START_TIME, BLANK_STRING);
		}
		isStop = true;
		unregisterReceiver(batteryBroadcast);
		super.onDestroy();
		stopForeground(true);
	}

	/**
	 * Replaces all matches for replaceType within this replaceString in file on
	 * the filePath
	 * 
	 * @param filePath
	 * @param replaceType
	 * @param replaceString
	 */
	private void replaceFileString(String filePath, String replaceType, String replaceString) {
		try {
			File file = new File(filePath);
			BufferedReader reader = new BufferedReader(new FileReader(file));
			String line = BLANK_STRING;
			String oldtext = BLANK_STRING;
			while ((line = reader.readLine()) != null) {
				oldtext += line + Constants.LINE_END;
			}
			reader.close();
			// replace a word in a file
			String newtext = oldtext.replaceAll(replaceType, replaceString);
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), "utf-8"));
			writer.write(newtext);
			writer.close();
		} catch (IOException e) {
			Log.d(LOG_TAG, e.getMessage());
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}