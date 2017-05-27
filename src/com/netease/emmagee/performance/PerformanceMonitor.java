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
package com.netease.emmagee.performance;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Locale;

import com.netease.emmagee.performance.utils.Constants;
import com.netease.emmagee.performance.utils.CpuInfo;
import com.netease.emmagee.performance.utils.CurrentInfo;
import com.netease.emmagee.performance.utils.MemoryInfo;
import com.netease.emmagee.performance.utils.TrafficInfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

/**
 * Service running in background
 * 
 * @author hz_liuxiao
 */
public class PerformanceMonitor {

	private final static String LOG_TAG = "Emmagee-" + PerformanceMonitor.class.getSimpleName();

	private int delaytime = 1000;
	private Handler handler = new Handler();

	public BufferedWriter bw;
	public FileOutputStream out;
	public OutputStreamWriter osw;
	public String resultFilePath;

	private int pid, uid;
	private CpuInfo cpuInfo;
	private MemoryInfo memoryInfo;
	private TrafficInfo networkInfo;
	private SimpleDateFormat formatterTime;
	private DecimalFormat fomart;
	private boolean isInitialStatic = true;
	private long processCpu1, processCpu2, totalCpu1, totalCpu2, idleCpu1, idleCpu2;
	private long startTraff, endTraff;
	private String currentBatt, temperature, voltage, intervalTraff;
	private boolean isRunnableStop = false;
	private BatteryInfoBroadcastReceiver receiver;
	private Context context;
	private CurrentInfo currentInfo;

	private String toolName;
	private static final String PERF_CSV = "PerformanceMonitor.csv";

	public PerformanceMonitor(Context context, String packageName, String toolName, String mDateTime) {
		this.context = context;

		fomart = new DecimalFormat();
		fomart.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
		fomart.setGroupingUsed(false);
		fomart.setMaximumFractionDigits(2);
		fomart.setMinimumFractionDigits(0);

		// 注册广播监听电量
		receiver = new BatteryInfoBroadcastReceiver();
		IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		context.registerReceiver(receiver, filter);
		getAppInfo(packageName);
		// 不在初始化的时候创建报告，而是在真正做记录的时候创建
		// creatReport(toolName, mDateTime);
		this.toolName = toolName;
		cpuInfo = new CpuInfo();
		memoryInfo = new MemoryInfo();
		networkInfo = new TrafficInfo(String.valueOf(uid));
		currentInfo = new CurrentInfo();
	}

	/**
	 * get pid and uid
	 * 
	 * @param packageName
	 */
	private void getAppInfo(String packageName) {
		uid = android.os.Process.myUid();
		pid = android.os.Process.myPid();
		Log.d(LOG_TAG, "pid = " + pid);
		Log.d(LOG_TAG, "uid = " + uid);
	}

	/**
	 * write the test result to csv format report.
	 */
	private void creatReport(String toolName, String dateTime) {
		Log.d(LOG_TAG, "start write report");
		// 两个候选目录用于存储性能数据: /sdcard/grape/PerformanceMonitor.csv,  /sdcard/PerformanceMonitor.csv
		String[] dirs = new String[] {"/sdcard/grape", "/sdcard"};
		boolean createCompleted = false;
		File resultFile = null;
		// 这里尝试两个候选目录，如果都不行那暂时报异常出来
		for (int i = 0; i < dirs.length; i ++) {
			String dir = dirs[i];
			resultFilePath = dir + "/" + PERF_CSV; // 这边的性能文件命名改简单一点
			Log.i(LOG_TAG, "createNewFile in" + resultFilePath);
			resultFile = new File(resultFilePath);
			try {
				// 创建目录
				File fileDir = new File(dir);
				if (!fileDir.exists()) {
					boolean createDir = fileDir.mkdirs();
					Log.d(LOG_TAG, "create dir: " + createDir);
				} else if (resultFile.exists()) {
					Log.d(LOG_TAG, "perf file existed, delete it");
					resultFile.delete();
				}
				// 只有在性能结果文件不存在的情况下才创建文件，并生成头文件，让文件只保持一份就好
				createCompleted = resultFile.createNewFile();
				if (createCompleted) {
					break;
				} else {
					Log.d(LOG_TAG, "createNewFile failed");
				}
			} catch (Exception e) {
				e.printStackTrace();
				Log.w(LOG_TAG, "mkdir and createNewFile exception: " + e.getMessage());
			}
		}
		try {
			if (resultFile.exists()) {
				out = new FileOutputStream(resultFile, true); // 在文件内容后继续加内容
			} else {
				if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
					// 在android nougat版本以后，MODE_WORLD_READABLE和MODE_WORLD_WRITEABLE废弃，使用时会出现SecurityException
					out = context.openFileOutput(PERF_CSV, Context.MODE_WORLD_READABLE + Context.MODE_WORLD_WRITEABLE);
				} else {
					resultFile = new File(Environment.getExternalStorageDirectory(), PERF_CSV);
				}
			}
			Log.d(LOG_TAG, "perf file path: " + resultFile.getAbsolutePath());
			osw = new OutputStreamWriter(out, "utf-8");
			bw = new BufferedWriter(osw);
			// 生成头文件
			bw.write(HEADER_TEMPLATE + Constants.LINE_END);
			bw.flush();
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(LOG_TAG, "create BufferedWriter exception: " + e.getMessage());
		}
		Log.d(LOG_TAG, "end write report");
	}
	
	private static final String HEADER_TEMPLATE = "用例步骤描述,时间,栈顶Activity名称,应用占用内存PSS(MB),应用占用内存比(%),机器剩余内存(MB),应用占用CPU率(%),CPU总使用率(%),流量(KB),电量(%),电流(mA),温度(C),电压(V),截图";
	
	/**
	 * write data into certain file
	 */
	public void writePerformanceData(String mDateTime, String screenshot, String desc, boolean writePerf) {
		if (isInitialStatic) {
			// 创建相应的性能数据报告
			creatReport(toolName, mDateTime);
			startTraff = networkInfo.getTrafficInfo();
			isInitialStatic = false;
		}
		
		String content = "";
		String topActivity, pss,percent,freeMem,processCpuRatio,totalCpuRatio, current;
		topActivity=pss=percent=freeMem=processCpuRatio=totalCpuRatio=current ="";

		// 如果是在操作前记录，则性能数据全部写0
		if (writePerf) {
			// Network
			endTraff = networkInfo.getTrafficInfo();
			if (startTraff == -1)
				intervalTraff = "-1";
			else
				intervalTraff = String.valueOf((endTraff - startTraff + 1023) / 1024);
	
			// CPU
			processCpu1 = cpuInfo.readCpuStat(pid)[0];
			idleCpu1 = cpuInfo.readCpuStat(pid)[1];
			totalCpu1 = cpuInfo.readCpuStat(pid)[2];
			if (processCpu1 - processCpu2 <= 0 || totalCpu1-totalCpu2 <=0) {
				processCpuRatio = "0";
			} else {
				processCpuRatio = fomart.format(100 * ((double) (processCpu1 - processCpu2) / ((double) (totalCpu1 - totalCpu2))));
				totalCpuRatio = fomart.format(100 * ((double) ((totalCpu1 - idleCpu1) - (totalCpu2 - idleCpu2)) / (double) (totalCpu1 - totalCpu2)));
			}
			// Memory
			long pidMemory = memoryInfo.getPidMemorySize(pid, context);
			pss = fomart.format((double) pidMemory / 1024);
			long freeMemory = memoryInfo.getFreeMemorySize(context);
			freeMem = fomart.format((double) freeMemory / 1024);
			long totalMemorySize = memoryInfo.getTotalMemory();
			 percent = "-1";
			if (totalMemorySize != 0) {
				percent = fomart.format(((double) pidMemory / (double) totalMemorySize) * 100);
			}
			
			// TopActivity
//			topActivity = ProcessInfo.getTopActivity(context);
			
			// 电流
			current = String.valueOf(currentInfo.getCurrentValue());
			// 异常数据过滤
			try {
				if (Math.abs(Double.parseDouble(currentBatt)) >= 500) {
					current = Constants.NA;
				}
			} catch (Exception e) {
				current = Constants.NA;
			}
	
			if (null == desc || "".equals(desc.trim())){
				desc = "";
			}
			
			content = "," + mDateTime + "," + topActivity + "," + pss + "," + percent + "," + freeMem + ","
							+ processCpuRatio + "," + totalCpuRatio + "," + intervalTraff + "," + currentBatt + "," + current + ","+ temperature + "," + voltage
							+ "," + replaceNull(screenshot) + "\r\n";
		} else {
			content = desc;
		}
		
		try {
			bw.write(content);
			bw.flush();
			Log.i(LOG_TAG, "*** writePerformanceData on " + mDateTime + " *** ");
		} catch (Exception e) {
			Log.e(LOG_TAG, "writePerformanceData exception: " + e.getMessage());
			e.printStackTrace();
		}

		processCpu2 = processCpu1;
		idleCpu2 = idleCpu1;
		totalCpu2 = totalCpu1;
	}
	
	/**
	 * write data into certain file
	 */
	public void writePerformanceData(String mDateTime) {
		writePerformanceData(mDateTime, null, null, true);
	}
	
	/**
	 * 替换所有的null
	 * @param input
	 * @return
	 */
	public String replaceNull(String input) {
		  return input == null ? "" : input;
	}


	private Runnable task = new Runnable() {

		public void run() {
			if (!isRunnableStop) {
				handler.postDelayed(this, delaytime);
				// writePerformanceData();
			}
		};
	};

	/**
	 * 电量广播类
	 * 
	 * @author hz_liuxiao@corp.netease.com
	 * 
	 */
	public class BatteryInfoBroadcastReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {

			if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
				int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);

				int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
				currentBatt = String.valueOf(level * 100 / scale) + "%";

				voltage = String.valueOf(intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) * 1.0 / 1000);

				temperature = String.valueOf(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) * 1.0 / 10);

				int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
			}

		}

	}

	/**
	 * close all opened stream.
	 */
	public void closeOpenedStream() {
		try {
			if (bw != null)
				bw.close();
			if (osw != null)
				osw.close();
			if (out != null)
				out.close();
		} catch (Exception e) {
			Log.d(LOG_TAG, e.getMessage());
		}
	}

	public void onDestroy() {
		context.unregisterReceiver(receiver);
		isRunnableStop = true;
		closeOpenedStream();
	}

	/**
	 * 从栈中获取类名和测试方法名
	 * 
	 * @return 类名+测试方法名
	 */
	private String getTestCaseInfo() {
		String testCaseInfo = "";
		String testName, className;
		StackTraceElement[] stack = (new Throwable()).getStackTrace();
		int i = 0;
		while (i < stack.length) {
			// 在setUp和testXXX中会有orange的操作方法
			if (stack[i].getMethodName().toString().startsWith("test") || stack[i].getMethodName().toString().startsWith("setUp")) {
				break;
			}
			i++;
		}

		if (i >= stack.length) {
			testCaseInfo = "No TestCase Info";
		} else {
			// “.”在正则中代码任意字符，不能用来分割，如需使用则通过“//.”转义
			String[] packageName = stack[i].getClassName().toString().split("\\.");
			className = packageName[packageName.length - 1];
			// className = stack[i].getClassName().toString();
			testName = stack[i].getMethodName().toString();
			testCaseInfo = className + "." + testName;
		}
		// Log.i(LOG_TAG, "*** getTestCaseInfo =" + testCaseInfo + " *** ");
		return testCaseInfo;
	}

	/**
	 * 从栈中获取相应操作方法名，有clickXXX，enterXXX，scrollXXX，typeXXX
	 * 
	 * @return 类名+测试方法名
	 */
	private String getActionInfo() {
		String actionInfo = "";
		String testName, className;
		StackTraceElement[] stack = (new Throwable()).getStackTrace();
		int i = 0;
		while (i < stack.length) {
			// 类对应的是OrangeSolo，
			if (stack[i].getMethodName().toString().startsWith("click") || stack[i].getMethodName().toString().startsWith("enter")
					|| stack[i].getMethodName().toString().startsWith("scroll") || stack[i].getMethodName().toString().startsWith("type")) {
				break;
			}
			i++;
		}

		if (i >= stack.length) {
			actionInfo = "No Action Info";
		} else {
			// className暂时不用展现
			testName = stack[i].getMethodName().toString();
			actionInfo = testName;
		}
		return actionInfo;
	}
	
}