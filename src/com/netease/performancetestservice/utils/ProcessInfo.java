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
package com.netease.performancetestservice.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

/**
 * get information of processes
 * 
 * @author andrewleo
 */
public class ProcessInfo {

	private static final String LOG_TAG = "Emmagee-" + ProcessInfo.class.getSimpleName();

	private static final String PACKAGE_NAME = "com.netease.performancetestservice";
	private static final int ANDROID_L = 21;

	/**
	 * 获取被测应用的信息
	 * @param context
	 * @return
	 */
	public Programe getAppInfo(Context context, String packageName){
		Log.i(LOG_TAG, "getAppInfo");
		ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningAppProcessInfo> run = am.getRunningAppProcesses();
		Programe programe = new Programe();
		for (RunningAppProcessInfo runningProcess : run) {
			if ((runningProcess.processName != null) && runningProcess.processName.equals(packageName)) {
				programe.setPid(runningProcess.pid);
				programe.setUid(runningProcess.uid);
				break;
			}
		}
		return programe;
	}

	/**
	 * get information of all applications.
	 * 
	 * @param context
	 *            context of activity
	 * @return packages information of all applications
	 */
	private List<ApplicationInfo> getPackagesInfo(Context context) {
		PackageManager pm = context.getApplicationContext().getPackageManager();
		List<ApplicationInfo> appList = pm.getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES);
		return appList;
	}

	/**
	 * get top activity name
	 * 
	 * @param context
	 *            context of activity
	 * @return top activity name
	 */
	public static String getTopActivity(Context context) {
		try {
			ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
			List<RunningTaskInfo> runningTaskInfos = manager.getRunningTasks(1);
			if (runningTaskInfos != null)
				return (runningTaskInfos.get(0).topActivity).toString();
		} catch(Exception e){
			Log.w(LOG_TAG, "getTopActivity exception: " + e.getMessage());
		}
		return "";
	}
	
	/**
	 * get pid by package name
	 * 
	 * @param context
	 *            context of activity
	 * @return pid
	 */
	public int getPidByPackageName(Context context, String packageName) {
		Log.i(LOG_TAG, "start getLaunchedPid");
		ActivityManager am = (ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE);
		// Note: getRunningAppProcesses return itself in API 22
		if (Build.VERSION.SDK_INT < ANDROID_L) {
			List<RunningAppProcessInfo> run = am.getRunningAppProcesses();
			for (RunningAppProcessInfo runningProcess : run) {
				if ((runningProcess.processName != null)
						&& runningProcess.processName.equals(packageName)) {
					return runningProcess.pid;
				}
			}
		} else {
			Log.i(LOG_TAG, "use top/ps command to get pid");
			int psPid = getPidByPs(packageName);
			return psPid > 0 ? psPid : getPidByTop(packageName); 
		}
		return 0;
	}
	
	private int getPidByTop(String packageName) {
		try {
			Process p = Runtime.getRuntime().exec("top -m 100 -n 1");
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
					p.getInputStream()));
			String line = "";
			while ((line = bufferedReader.readLine()) != null) {
				if (line.contains(packageName)) {
					line = line.trim();
					String[] splitLine = line.split("\\s+");
					if (packageName.equals(splitLine[splitLine.length - 1])) {
						return Integer.parseInt(splitLine[0]);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	private int getPidByPs(String packageName) {
		try {
			Process p = Runtime.getRuntime().exec("ps");
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
					p.getInputStream()));
			String line = "";
			while ((line = bufferedReader.readLine()) != null) {
				if (line.contains(packageName)) {
					line = line.trim();
					String[] splitLine = line.split("\\s+");
					if (packageName.equals(splitLine[splitLine.length - 1])) {
						return Integer.parseInt(splitLine[1]);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}

}
