package com.zjrb.sjzsw;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;

import com.github.yuweiguocn.library.greendao.MigrationHelper;
import com.jzf.net.api.HttpClient;
import com.squareup.leakcanary.LeakCanary;
import com.tencent.bugly.crashreport.CrashReport;
import com.zjrb.sjzsw.greendao.DaoMaster;
import com.zjrb.sjzsw.greendao.DaoSession;
import com.zjrb.sjzsw.greendao.MySQLiteOpenHelper;
import com.zjrb.sjzsw.utils.AppUtil;


/**
 * Created by shiwei on 2017/3/21.
 */

public class App extends Application {

    private MySQLiteOpenHelper mHelper;
    private SQLiteDatabase db;
    private DaoMaster mDaoMaster;
    private DaoSession mDaoSession;
    private static App sAppContext;


    public static App getAppContext() {
        if (sAppContext == null) {
            sAppContext = new App();
        }
        return sAppContext;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sAppContext = this;
        HttpClient.init(this);

        //LeakCanary初始化
        if (LeakCanary.isInAnalyzerProcess(this)) {
            return;
        }
        LeakCanary.install(this);
        //配置tencent.bugly,上报进程控制
        initBugly();
    }

    /**
     * 配置tencent.bugly,上报进程控制
     */
    private void initBugly() {
        // 获取当前包名
        String packageName = sAppContext.getPackageName();
        // 获取当前进程名
        String processName = AppUtil.getProcessName(android.os.Process.myPid());
        // 设置是否为上报进程
        CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(sAppContext);
        strategy.setUploadProcess(processName == null || processName.equals(packageName));
        //tencent.bugly初始化 建议在测试阶段建议设置成true，发布时设置为false。 8f699e3b6c为你申请的应用appid
        CrashReport.initCrashReport(getApplicationContext(), "8f699e3b6c", true);
    }





    /**
     * google官方bug，暂时解决方案
     * 手机切屏后重新设置UI_MODE模式（因为在dayNight主题下，切换横屏后UI_MODE会出错，会导致资源获取出错，需要重新设置回来）
     */
    private void updateConfig(Activity activity, int uiNightMode) {
        Configuration newConfig = new Configuration(activity.getResources().getConfiguration());
        newConfig.uiMode &= ~Configuration.UI_MODE_NIGHT_MASK;
        newConfig.uiMode |= uiNightMode;
        activity.getResources().updateConfiguration(newConfig, null);
    }

    /**
     * 设置greenDao
     */
    private void setDatabase() {
        // 通过 DaoMaster 的内部类 DevOpenHelper，你可以得到一个便利的 SQLiteOpenHelper 对象。
        // 可能你已经注意到了，你并不需要去编写「CREATE TABLE」这样的 SQL 语句，因为 greenDAO 已经帮你做了。
        // 注意：默认的 DaoMaster.DevOpenHelper 会在数据库升级时，删除所有的表，意味着这将导致数据的丢失。
        // 所以，在正式的项目中，你还应该做一层封装，来实现数据库的安全升级。
//        mHelper = new DaoMaster.DevOpenHelper(this, "notes-db", null);
        // 初始化
        MigrationHelper.DEBUG = true; //如果你想查看日志信息，请将 DEBUG 设置为 true
        mHelper = new MySQLiteOpenHelper(this, "notes-db", null);
        //不加密数据库
        db = mHelper.getWritableDatabase();

        // 注意：该数据库连接属于 DaoMaster，所以多个 Session 指的是相同的数据库连接。
        mDaoMaster = new DaoMaster(db);
        mDaoSession = mDaoMaster.newSession();



    }

    public MySQLiteOpenHelper getHelper() {
        return mHelper;
    }

    public DaoSession getDaoSession() {
        return mDaoSession;
    }
    public SQLiteDatabase getDb() {
        return db;
    }
}



