package com.javiersantos.mlmanager.activities;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.batch.android.Batch;
import com.batch.android.BatchCodeListener;
import com.batch.android.BatchRestoreListener;
import com.batch.android.BatchURLListener;
import com.batch.android.BatchUnlockListener;
import com.batch.android.CodeErrorInfo;
import com.batch.android.FailReason;
import com.batch.android.Feature;
import com.batch.android.Offer;
import com.javiersantos.mlmanager.AppInfo;
import com.javiersantos.mlmanager.MLManagerApplication;
import com.javiersantos.mlmanager.R;
import com.javiersantos.mlmanager.adapters.AppAdapter;
import com.javiersantos.mlmanager.utils.AppPreferences;
import com.javiersantos.mlmanager.utils.UtilsApp;
import com.javiersantos.mlmanager.utils.UtilsDialog;
import com.javiersantos.mlmanager.utils.UtilsUI;
import com.mikepenz.materialdrawer.Drawer;
import com.pnikosis.materialishprogress.ProgressWheel;
import com.yalantis.phoenix.PullToRefreshView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import xyz.danoz.recyclerviewfastscroller.vertical.VerticalRecyclerViewFastScroller;


public class MainActivity extends AppCompatActivity implements SearchView.OnQueryTextListener, BatchUnlockListener, BatchURLListener {
    // Load Settings
    private AppPreferences appPreferences;

    // General variables
    private List<AppInfo> appList;
    private List<AppInfo> appSystemList;
    private List<AppInfo> appHiddenList;

    private AppAdapter appAdapter;
    private AppAdapter appSystemAdapter;
    private AppAdapter appFavoriteAdapter;
    private AppAdapter appHiddenAdapter;

    // Configuration variables
    private Boolean doubleBackToExitPressedOnce = false;
    private Toolbar toolbar;
    private Context context;
    private RecyclerView recyclerView;
    private PullToRefreshView pullToRefreshView;
    private ProgressWheel progressWheel;
    private Drawer drawer;
    private MenuItem searchItem;
    private SearchView searchView;
    private static VerticalRecyclerViewFastScroller fastScroller;
    private static LinearLayout noResults;

    // BATCH.COM
    private MaterialDialog batchIndeterminateDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.appPreferences = MLManagerApplication.getAppPreferences();
        this.context = this;

        setInitialConfiguration();
        setAppDir();

        recyclerView = (RecyclerView) findViewById(R.id.appList);
        pullToRefreshView = (PullToRefreshView) findViewById(R.id.pull_to_refresh);
        fastScroller = (VerticalRecyclerViewFastScroller) findViewById(R.id.fast_scroller);
        progressWheel = (ProgressWheel) findViewById(R.id.progress);
        noResults = (LinearLayout) findViewById(R.id.noResults);

        fastScroller.setRecyclerView(recyclerView);
        recyclerView.setOnScrollListener(fastScroller.getOnScrollListener());
        pullToRefreshView.setEnabled(false);

        recyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(linearLayoutManager);

        drawer = UtilsUI.setNavigationDrawer((Activity) context, context, toolbar, appAdapter, appSystemAdapter, appFavoriteAdapter, appHiddenAdapter, recyclerView);

        progressWheel.setBarColor(appPreferences.getPrimaryColorPref());
        progressWheel.setVisibility(View.VISIBLE);
        new getInstalledApps().execute();

    }

    private void setInitialConfiguration() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(UtilsUI.darker(appPreferences.getPrimaryColorPref(), 0.8));
            toolbar.setBackgroundColor(appPreferences.getPrimaryColorPref());
            if (!appPreferences.getNavigationBlackPref()) {
                getWindow().setNavigationBarColor(appPreferences.getPrimaryColorPref());
            }
        }
    }

    class getInstalledApps extends AsyncTask<Void, String, Void> {
        private Integer totalApps;
        private Integer actualApps;

        public getInstalledApps() {
            actualApps = 0;

            appList = new ArrayList<>();
            appSystemList = new ArrayList<>();
            appHiddenList = new ArrayList<>();
        }

        @Override
        protected Void doInBackground(Void... params) {
            final PackageManager packageManager = getPackageManager();
            List<PackageInfo> packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA);
            Set<String> hiddenApps = appPreferences.getHiddenApps();
            totalApps = packages.size() + hiddenApps.size();
            // Get Sort Mode
            switch (appPreferences.getSortMode()) {
                default:
                    // Comparator by Name (default)
                    Collections.sort(packages, new Comparator<PackageInfo>() {
                        @Override
                        public int compare(PackageInfo p1, PackageInfo p2) {
                            return packageManager.getApplicationLabel(p1.applicationInfo).toString().toLowerCase().compareTo(packageManager.getApplicationLabel(p2.applicationInfo).toString().toLowerCase());
                        }
                    });
                    break;
                case "2":
                    // Comparator by Size
                    Collections.sort(packages, new Comparator<PackageInfo>() {
                        @Override
                        public int compare(PackageInfo p1, PackageInfo p2) {
                            Long size1 = new File(p1.applicationInfo.sourceDir).length();
                            Long size2 = new File(p2.applicationInfo.sourceDir).length();
                            return size2.compareTo(size1);
                        }
                    });
                    break;
                case "3":
                    // Comparator by Installation Date (default)
                    Collections.sort(packages, new Comparator<PackageInfo>() {
                        @Override
                        public int compare(PackageInfo p1, PackageInfo p2) {
                            return Long.toString(p2.firstInstallTime).compareTo(Long.toString(p1.firstInstallTime));
                        }
                    });
                    break;
                case "4":
                    // Comparator by Last Update
                    Collections.sort(packages, new Comparator<PackageInfo>() {
                        @Override
                        public int compare(PackageInfo p1, PackageInfo p2) {
                            return Long.toString(p2.lastUpdateTime).compareTo(Long.toString(p1.lastUpdateTime));
                        }
                    });
                    break;
            }

            // Installed & System Apps
            for (PackageInfo packageInfo : packages) {
                if (!(packageManager.getApplicationLabel(packageInfo.applicationInfo).equals("") || packageInfo.packageName.equals(""))) {
                    if (packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null) {
                        try {
                            // Non System Apps
                            AppInfo tempApp = new AppInfo(packageManager.getApplicationLabel(packageInfo.applicationInfo).toString(), packageInfo.packageName, packageInfo.versionName, packageInfo.applicationInfo.sourceDir, packageInfo.applicationInfo.dataDir, packageManager.getApplicationIcon(packageInfo.applicationInfo), false);
                            appList.add(tempApp);
                        } catch (OutOfMemoryError e) {
                            //TODO Workaround to avoid FC on some devices (OutOfMemoryError). Drawable should be cached before.
                            AppInfo tempApp = new AppInfo(packageManager.getApplicationLabel(packageInfo.applicationInfo).toString(), packageInfo.packageName, packageInfo.versionName, packageInfo.applicationInfo.sourceDir, packageInfo.applicationInfo.dataDir, getResources().getDrawable(R.drawable.ic_android), false);
                            appList.add(tempApp);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            // System Apps
                            AppInfo tempApp = new AppInfo(packageManager.getApplicationLabel(packageInfo.applicationInfo).toString(), packageInfo.packageName, packageInfo.versionName, packageInfo.applicationInfo.sourceDir, packageInfo.applicationInfo.dataDir, packageManager.getApplicationIcon(packageInfo.applicationInfo), true);
                            appSystemList.add(tempApp);
                        } catch (OutOfMemoryError e) {
                            //TODO Workaround to avoid FC on some devices (OutOfMemoryError). Drawable should be cached before.
                            AppInfo tempApp = new AppInfo(packageManager.getApplicationLabel(packageInfo.applicationInfo).toString(), packageInfo.packageName, packageInfo.versionName, packageInfo.applicationInfo.sourceDir, packageInfo.applicationInfo.dataDir, getResources().getDrawable(R.drawable.ic_android), false);
                            appSystemList.add(tempApp);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                actualApps++;
                publishProgress(Double.toString((actualApps * 100) / totalApps));
            }

            // Hidden Apps
            for (String app : hiddenApps) {
                AppInfo tempApp = new AppInfo(app);
                Drawable tempAppIcon = UtilsApp.getIconFromCache(context, tempApp);
                tempApp.setIcon(tempAppIcon);
                appHiddenList.add(tempApp);

                actualApps++;
                publishProgress(Double.toString((actualApps * 100) / totalApps));
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            super.onProgressUpdate(progress);
            progressWheel.setProgress(Float.parseFloat(progress[0]));
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            appAdapter = new AppAdapter(appList, context);
            appSystemAdapter = new AppAdapter(appSystemList, context);
            appFavoriteAdapter = new AppAdapter(getFavoriteList(appList, appSystemList), context);
            appHiddenAdapter = new AppAdapter(appHiddenList, context);

            fastScroller.setVisibility(View.VISIBLE);
            recyclerView.setAdapter(appAdapter);
            pullToRefreshView.setEnabled(true);
            progressWheel.setVisibility(View.GONE);
            searchItem.setVisible(true);

            setPullToRefreshView(pullToRefreshView);
            drawer.closeDrawer();
            updateDrawer();
        }

    }

    private void setPullToRefreshView(final PullToRefreshView pullToRefreshView) {
        pullToRefreshView.setOnRefreshListener(new PullToRefreshView.OnRefreshListener() {
            @Override
            public void onRefresh() {
                appAdapter.clear();
                appSystemAdapter.clear();
                appFavoriteAdapter.clear();
                recyclerView.setAdapter(null);
                new getInstalledApps().execute();

                pullToRefreshView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pullToRefreshView.setRefreshing(false);
                    }
                }, 2000);
            }
        });
    }

    private void updateDrawer() {
        drawer = UtilsUI.setNavigationDrawer((Activity) context, context, toolbar, appAdapter, appSystemAdapter, appFavoriteAdapter, appHiddenAdapter, recyclerView);
    }

    private void setAppDir() {
        File appDir = UtilsApp.getAppFolder();
        if(!appDir.exists()) {
            appDir.mkdir();
        }
    }

    private List<AppInfo> getFavoriteList(List<AppInfo> appList, List<AppInfo> appSystemList) {
        List<AppInfo> res = new ArrayList<>();

        for (AppInfo app : appList) {
            if (UtilsApp.isAppFavorite(app.getAPK(), appPreferences.getFavoriteApps())) {
                res.add(app);
            }
        }
        for (AppInfo app : appSystemList) {
            if (UtilsApp.isAppFavorite(app.getAPK(), appPreferences.getFavoriteApps())) {
                res.add(app);
            }
        }

        return res;
    }

    @Override
    public boolean onQueryTextChange(String search) {
        if (search.isEmpty()) {
            ((AppAdapter) recyclerView.getAdapter()).getFilter().filter("");
        } else {
            ((AppAdapter) recyclerView.getAdapter()).getFilter().filter(search.toLowerCase());
        }

        return false;
    }

    public static void setResultsMessage(Boolean result) {
        if (result) {
            noResults.setVisibility(View.VISIBLE);
            fastScroller.setVisibility(View.GONE);
        } else {
            noResults.setVisibility(View.GONE);
            fastScroller.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        onCodeEntered(query.toUpperCase());
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);

        searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setOnQueryTextListener(this);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else if (searchItem.isVisible() && !searchView.isIconified()) {
            searchView.onActionViewCollapsed();
        } else {
            if (doubleBackToExitPressedOnce) {
                super.onBackPressed();
                return;
            }
            this.doubleBackToExitPressedOnce = true;
            Toast.makeText(this, R.string.tap_exit, Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    doubleBackToExitPressedOnce = false;
                }
            }, 2000);
        }
    }

    // BATCH.COM //
    @Override
    protected void onStart() {
        super.onStart();

        Batch.Unlock.setUnlockListener(this);
        Batch.Unlock.setURLListener(this);
        Batch.onStart(this);
        onRestore();
    }

    @Override
    public void onRedeemAutomaticOffer(Offer offer) {
        for (Feature feature : offer.getFeatures()) {
            String featureRef = feature.getReference();
            String value = feature.getValue();

            if (featureRef.equals("PRO_MODE")) {
                MLManagerApplication.setPro(true);
            }
        }
    }

    @Override
    public void onURLWithCodeFound(String code) {
        batchIndeterminateDialog = UtilsDialog.showBatchIndeterminate(context);
    }

    @Override
    public void onURLCodeSuccess(String code, Offer offer) {
        batchIndeterminateDialog.dismiss();
        MLManagerApplication.setPro(true);
        updateDrawer();
        UtilsDialog.showBatchRedeemed(context, true);
    }

    @Override
    public void onURLCodeFailed(String code, FailReason reason, CodeErrorInfo info) {
        batchIndeterminateDialog.dismiss();
        UtilsDialog.showBatchRedeemed(context, false);
    }

    @Override
    protected void onStop() {
        Batch.onStop(this);

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Batch.onDestroy(this);

        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Batch.onNewIntent(this, intent);

        super.onNewIntent(intent);
    }

    private void onCodeEntered(String code) {
        Batch.Unlock.redeemCode(code, new BatchCodeListener() {
            @Override
            public void onRedeemCodeSuccess(String code, Offer offer) {
                MLManagerApplication.setPro(true);
                updateDrawer();
                UtilsDialog.showBatchRedeemed(context, true);
            }


            @Override
            public void onRedeemCodeFailed(String code, FailReason reason, CodeErrorInfo info) {}
        });
    }

    private void onRestore() {
        Batch.Unlock.restore(new BatchRestoreListener() {

            @Override
            public void onRestoreSucceed(List<Feature> features) {
                if (!features.isEmpty()) {
                    MLManagerApplication.setPro(true);
                } else {
                    if (!context.getPackageName().equals(MLManagerApplication.getProPackage())) {
                        MLManagerApplication.setPro(false);
                    }
                }

            }

            @Override
            public void onRestoreFailed(FailReason reason) {}
        });
    }

}
