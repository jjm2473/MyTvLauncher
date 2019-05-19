package com.tan.mytvlauncher.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by t1569 on 2017/9/23.
 */

public class AppDataManager {
    private final Context mContext;
    private final PackageManager mPackageManager;
    public AppDataManager(Context context) {
        mContext = context;
        mPackageManager = mContext.getPackageManager();
    }

    public static Intent getLauncherIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        return intent;
    }

    public ArrayList<AppModel> getLauncherAppList() {
        PackageManager packageManager = mPackageManager;
        Intent intent = getLauncherIntent();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent, 0);
        ArrayList<AppModel> appModels = new ArrayList<>();
        for (ResolveInfo resolveInfo : list) {
            AppModel appModel = toModel(resolveInfo);
            String pkgName = resolveInfo.activityInfo.packageName;
            PackageInfo mPackageInfo;
            try {
                mPackageInfo = mContext.getPackageManager().getPackageInfo(pkgName, 0);
                if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) > 0) {
                    appModel.setSysApp(true);
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            if (!appModel.getPackageName().equals("com.tan.mytvlauncher")) appModels.add(appModel);
        }
        return appModels;
    }

    public AppModel toModel(ResolveInfo resolveInfo) {
        AppModel appModel = new AppModel();
        appModel.setIcon(resolveInfo.activityInfo.loadIcon(mPackageManager));
        appModel.setName(resolveInfo.activityInfo.loadLabel(mPackageManager).toString());
        appModel.setPackageName(resolveInfo.activityInfo.packageName);
        appModel.setLauncherName(resolveInfo.activityInfo.name);
        appModel.initOpenCount(mContext);
        return appModel;
    }
}
