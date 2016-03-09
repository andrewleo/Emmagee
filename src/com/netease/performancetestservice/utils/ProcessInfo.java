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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Debug;
import android.util.Log;

/**
 * get information of processes
 * 
 * @author andrewleo
 */
public class ProcessInfo {

	private static final String LOG_TAG = "Emmagee-" + ProcessInfo.class.getSimpleName();

	private static final String PACKAGE_NAME = "com.netease.performancetestservice";

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
	

}
