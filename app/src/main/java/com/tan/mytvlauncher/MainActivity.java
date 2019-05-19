package com.tan.mytvlauncher;

import android.app.Activity;
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.util.Log;
import android.view.KeyEvent;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.gson.Gson;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.tan.mytvlauncher.app.AppCardPresenter;
import com.tan.mytvlauncher.app.AppDataManager;
import com.tan.mytvlauncher.app.AppModel;
import com.tan.mytvlauncher.app.BingImage;
import com.tan.mytvlauncher.card.CardModel;
import com.tan.mytvlauncher.card.CardPresenter;
import com.tan.mytvlauncher.function.FunctionCardPresenter;
import com.tan.mytvlauncher.function.FunctionModel;
import com.tan.mytvlauncher.util.Tools;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import cz.msebera.android.httpclient.Header;

public class MainActivity extends Activity {
    protected BrowseFragment mBrowseFragment;
    private BackgroundManager mBackgroundManager;
    private Context mContext;
    private ArrayList<AppModel> mAppModels;
    private Receiver receiver;
    private NetworkStateReceiver networkStateReceiver;
    private TimeReceiver timeReceiver;
    private String backImgUrl = null;
    private Object selected = null;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int ITEM_LOADER_ID = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        mBrowseFragment = (BrowseFragment) getFragmentManager().findFragmentById(R.id.browse_fragment);
        mBrowseFragment.setHeadersState(BrowseFragment.HEADERS_DISABLED);

        SharedPreferences pref = this.getSharedPreferences(FAV_PREFS_NAME, MODE_PRIVATE);
        fav = new HashSet<>(pref.getStringSet("fav", Collections.<String>emptySet()));

        getLoaderManager().initLoader(ITEM_LOADER_ID, null, new MainFragmentLoaderCallbacks(this));

        //prepareBackgroundManager();

        receiver = new Receiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        this.registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        networkStateReceiver = new NetworkStateReceiver();
        this.registerReceiver(networkStateReceiver, intentFilter);

        intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
        intentFilter.addAction(Intent.ACTION_TIME_TICK);
        intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        timeReceiver = new TimeReceiver();
        this.registerReceiver(timeReceiver, intentFilter);

        timeChange();
    }

    @Override
    protected void onStop() {
        super.onStop();
        for (BroadcastReceiver receiver : new BroadcastReceiver[]{this.networkStateReceiver, this.timeReceiver}) {
            if (receiver != null) {
                this.unregisterReceiver(receiver);
            }
        }
        this.networkStateReceiver = null;
        this.timeReceiver = null;
    }

    //    @Override
//    protected void onRestart() {
//        super.onRestart();
//        if (backImgUrl == null) setBingImg();
//        else setBackgroundImage();
//    }

    @Override
    protected void onDestroy() {
        if (receiver != null) {
            this.unregisterReceiver(receiver);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        mBrowseFragment.setSelectedPosition(0);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (selected != null && selected instanceof AppModel) {
                toggleFavorites((AppModel) selected);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private static final String FAV_PREFS_NAME = "MyFavApp";
    private HashSet<String> fav;
    private void toggleFavorites(AppModel appModel) {
        String key = appModel.getPackageName()+"/"+appModel.getLauncherName();
        if (fav.contains(key)) {
            fav.remove(key);
        } else {
            fav.add(key);
        }
        SharedPreferences pref = this.getSharedPreferences(FAV_PREFS_NAME, MODE_PRIVATE);
        pref.edit().putStringSet("fav", fav).apply();
        getLoaderManager().restartLoader(ITEM_LOADER_ID, null, new MainFragmentLoaderCallbacks(this));
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(this);
        mBackgroundManager.attach(this.getWindow());
        //设置背景图
        setBingImg();
    }

    private void setBackgroundImage() {
        Glide.with(mContext)
                .load(backImgUrl)
                .into(new SimpleTarget<Drawable>() {
                    @Override
                    public void onResourceReady(Drawable resource, Transition<? super Drawable> transition) {
                        mBackgroundManager.setDrawable(resource);
                    }
                });
    }

    private void setBingImg() {
        AsyncHttpClient client = new AsyncHttpClient();
        client.get("https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1", new JsonHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                        if (statusCode == 200) {
                            Gson gson = new Gson();
                            BingImage bingImage = gson.fromJson(response.toString(), BingImage.class);
                            List<BingImage.ImagesBean> img = bingImage.getImages();
                            if (img != null && img.size() > 0) {
                                backImgUrl = "https://cn.bing.com" + img.get(0).getUrl();
                                setBackgroundImage();
                            } else Log.d("main", "onSuccess: 没有获取到类型");

                        }
                    }
                }
        );
    }

    private class Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mAppModels == null) {
                return;
            }
            //接收安装广播
            if (intent.getAction().equals("android.intent.action.PACKAGE_ADDED")) {
                try {
                    String packageName = intent.getDataString();
                    packageName = packageName.split(":")[1];
                    List<ResolveInfo> list = Tools.findActivitiesForPackage(context, packageName);
                    if (list.size() > 0) {
                        getLoaderManager().restartLoader(ITEM_LOADER_ID, null, new MainFragmentLoaderCallbacks(MainActivity.this));
                    } else {
                        Log.d(TAG, "onReceive: 找不到安装的app");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            //接收卸载广播
            if (intent.getAction().equals("android.intent.action.PACKAGE_REMOVED")) {
                String receiverName = intent.getDataString();
                Log.d(TAG, "onReceive: " + receiverName);
                receiverName = receiverName.substring(8);
                for (int i = 0; i < mAppModels.size(); i++) {
                    if (mAppModels.get(i).getPackageName().equals(receiverName)) {
                        //mAppModels.get(i).setOpenCount(mContext, 0);
                        getLoaderManager().restartLoader(ITEM_LOADER_ID, null, new MainFragmentLoaderCallbacks(MainActivity.this));
                        break;
                    }
                }
            }
        }
    }

    private class NetworkStateReceiver extends BroadcastReceiver {

        private void updateWifiState(Context context) {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo.getBSSID() != null) {
                    // wifi信号强度
                    int signalLevel = WifiManager.calculateSignalLevel(
                            wifiInfo.getRssi(), 4);
                    if (signalLevel == 0) {
                        mBrowseFragment.setBadgeDrawable(context.getResources()
                                .getDrawable(R.drawable.wifi_1));
                    } else if (signalLevel == 1) {
                        mBrowseFragment.setBadgeDrawable(context.getResources()
                                .getDrawable(R.drawable.wifi_2));

                    } else if (signalLevel == 2) {
                        mBrowseFragment.setBadgeDrawable(context.getResources()
                                .getDrawable(R.drawable.wifi_3));

                    } else if (signalLevel == 3) {
                        mBrowseFragment.setBadgeDrawable(context.getResources()
                                .getDrawable(R.drawable.networkstate_on));
                    }
                }
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.RSSI_CHANGED_ACTION.equals(intent.getAction())) {
                updateWifiState(context);
            } else {
                ConnectivityManager cm =
                        (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

                if (cm != null ) {
                    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

                    if (activeNetwork != null && activeNetwork.isConnected()) {
                        if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                            updateWifiState(context);
                        } else {
                            mBrowseFragment.setBadgeDrawable(context.getResources()
                                    .getDrawable(R.drawable.networkstate_ethernet));
                        }
                    } else {
                        mBrowseFragment.setBadgeDrawable(context.getResources()
                                .getDrawable(R.drawable.networkstate_off));
                    }
                }
            }
        }
    }

    private void timeChange() {
        mBrowseFragment.setTitle(new SimpleDateFormat("HH:mm").format(Calendar.getInstance().getTime()));
    }

    private class TimeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            timeChange();
        }
    }

    private static class MainFragmentLoaderCallbacks extends AsyncTaskLoader<List<ListRow>> implements android.app.LoaderManager.LoaderCallbacks<List<ListRow>> {
        private static int CARD_L_WIDTH = 350;
        private static int CARD_L_HEIGHT = 200;
        private AppDataManager mAppDataManager;
        private MainActivity activity;

        public MainFragmentLoaderCallbacks(MainActivity activity) {
            super(activity.mContext);
            this.mAppDataManager = new AppDataManager(activity.mContext);
            this.activity = activity;
        }

        @Override
        public Loader<List<ListRow>> onCreateLoader(int id, Bundle args) {
            Log.d(TAG, "onCreateLoader: AppItemLoader");
            return this;
        }

        @Override
        public void onLoadFinished(Loader<List<ListRow>> loader, List<ListRow> data) {
            Log.d(TAG, "onLoadFinished: " + data.size());
            switch (loader.getId()) {
                case ITEM_LOADER_ID:
                    Log.d(TAG, "onLoadFinished: UI Update");
                    ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
                    rowsAdapter.addAll(0, data);
                    activity.mBrowseFragment.setAdapter(rowsAdapter);
                    activity.mBrowseFragment.setOnItemViewClickedListener(
                            new OnItemViewClickedListener() {
                                @Override
                                public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                                    if (item instanceof AppModel) {
                                        AppModel appModel = (AppModel) item;
                                        Intent intent = AppDataManager.getLauncherIntent();
                                        intent.setClassName(appModel.getPackageName(), appModel.getLauncherName());
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        try {
                                            activity.startActivity(intent);
                                            appModel.setOpenCount(activity.mContext, appModel.getOpenCount() + 1);
                                        } catch (Exception e) {
                                            Log.d(TAG, "launch failed: "+appModel.getPackageName()+"/"+appModel.getLauncherName(), e);
                                        }
                                    } else if (item instanceof FunctionModel) {
                                        FunctionModel functionModel = (FunctionModel) item;
                                        Intent intent = functionModel.getIntent();
                                        if (null != intent)
                                            activity.startActivity(intent);
                                    }
                                }
                            }

                    );
                    activity.mBrowseFragment.setOnItemViewSelectedListener(new OnItemViewSelectedListener() {
                        @Override
                        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                            activity.selected = item;
                        }
                    });
            }
        }

        @Override
        public void onLoaderReset(Loader<List<ListRow>> loader) {
            activity.mBrowseFragment.setAdapter(null);
        }


        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public List<ListRow> loadInBackground() {
            activity.mAppModels = mAppDataManager.getLauncherAppList();
            List<ListRow> listRows = new ArrayList<>();
            Log.d(TAG, "loadInBackground: " + activity.mAppModels.size());
            listRows.add(getUsedRow());

            listRows.addAll(getAppRow());
            listRows.add(getFunctionRow());
            return listRows;
        }

        private ListRow getUsedRow() {
            ArrayObjectAdapter usedListRowAdapter = new ArrayObjectAdapter(new AppCardPresenter(CARD_L_WIDTH, CARD_L_HEIGHT));
            ArrayList<AppModel> appModels = (ArrayList<AppModel>) activity.mAppModels.clone();
            Collections.sort(appModels, new Comparator<AppModel>() {
                public int compare(AppModel appModel1, AppModel appModel2) {
                    return appModel2.getOpenCount() - appModel1.getOpenCount();
                }
            });
            for (int i = 0; i < 5 && i < appModels.size(); i++) {
                usedListRowAdapter.add(appModels.get(i));
            }
            ListRow listRow = new ListRow(new HeaderItem(0, getContext().getString(R.string.title_used)), usedListRowAdapter);
            return listRow;
        }

        private ListRow getFunctionRow() {
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new FunctionCardPresenter());
            List<FunctionModel> functionModels = FunctionModel.getFunctionList(getContext());
            for (FunctionModel item : functionModels
            ) {
                listRowAdapter.add(item);
            }
            return new ListRow(new HeaderItem(0, getContext().getString(R.string.title_function)), listRowAdapter);
        }

        private List<ListRow> getAppRow() {
            ArrayObjectAdapter listFavAdapter = new ArrayObjectAdapter(new AppCardPresenter());
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new AppCardPresenter());
            ArrayObjectAdapter listSysRowAdapter = new ArrayObjectAdapter(new AppCardPresenter());
            for (AppModel appModel : activity.mAppModels) {
                if (appModel.isSysApp()) listSysRowAdapter.add(appModel);
                else listRowAdapter.add(appModel);
                if (activity.fav.contains(appModel.getPackageName()+"/"+appModel.getLauncherName())) {
                    listFavAdapter.add(appModel);
                }
            }
            List<ListRow> listRows = new ArrayList<>(3);
            if (listFavAdapter.size() > 0) {
                listRows.add(new ListRow(new HeaderItem(0, getContext().getString(R.string.title_favorites)), listFavAdapter));
            }
            listRows.add(new ListRow(new HeaderItem(0, getContext().getString(R.string.title_app)), listRowAdapter));
            listRows.add(new ListRow(new HeaderItem(0, getContext().getString(R.string.title_sysapp)), listSysRowAdapter));
            return listRows;
        }

        private ListRow[] getCardRow() {
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter());

            for (CardModel carModel : CardModel.getCardModels()
            ) {
                listRowAdapter.add(carModel);
            }

            HeaderItem header = new HeaderItem(0, getContext().getString(R.string.title_used));
            return new ListRow[]{new ListRow(header, listRowAdapter)};
        }
    }
}
