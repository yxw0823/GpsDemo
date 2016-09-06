package com.example.gpsdemo;

import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BaiduMap.OnMapClickListener;
import com.baidu.mapapi.map.BaiduMap.OnMarkerDragListener;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationConfiguration.LocationMode;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;

public class MainActivity extends Activity implements LocationListener, OnClickListener,
        BDLocationListener, OnMapClickListener, OnMarkerDragListener, OnGetGeoCoderResultListener {

    private String mMockProviderName = LocationManager.GPS_PROVIDER;;
    private Button bt_Ok;
    private LocationManager locationManager;
    private double latitude = 31.3029742, longitude = 120.6097126;// 默认常州
    private Thread thread;// 需要一个线程一直刷新
    private Boolean RUN = true;
    private TextView tv_location;

    boolean isFirstLoc = true;// 是否首次定位
    // 定位相关
    private LocationClient mLocClient;
    private LocationMode mCurrentMode;// 定位模式
    private BitmapDescriptor mCurrentMarker;// 定位图标
    private MapView mMapView;
    private BaiduMap mBaiduMap;

    // 初始化全局 bitmap 信息，不用时及时 recycle
    private BitmapDescriptor bd = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
    private Marker mMarker;
    private LatLng curLatlng;
    private GeoCoder mSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        iniView();
        iniListner();
        iniData();
    }

    /**
     * iniView 界面初始化
     * 
     */
    private void iniView() {
        bt_Ok = (Button) findViewById(R.id.bt_Ok);
        tv_location = (TextView) findViewById(R.id.tv_location);
        // 地图初始化
        mMapView = (MapView) findViewById(R.id.bmapView);
        mBaiduMap = mMapView.getMap();
        // 开启定位图层
        mBaiduMap.setMyLocationEnabled(true);
        // 定位初始化
        mLocClient = new LocationClient(this);
    }

    /**
     * iniListner 接口初始化
     * 
     */
    private void iniListner() {
        bt_Ok.setOnClickListener(this);
        mLocClient.registerLocationListener(this);
        mBaiduMap.setOnMapClickListener(this);
        mBaiduMap.setOnMarkerDragListener(this);

        // 初始化搜索模块，注册事件监听
        mSearch = GeoCoder.newInstance();
        mSearch.setOnGetGeoCodeResultListener(this);
    }

    /**
     * iniData 数据初始化
     * 
     */
    private void iniData() {
        inilocation();
        iniMap();
    }

    /**
     * iniMap 初始化地图
     * 
     */
    private void iniMap() {
        LocationClientOption option = new LocationClientOption();
        option.setOpenGps(true);// 打开gps
        option.setCoorType("bd09ll"); // 设置坐标类型
        option.setScanSpan(1000);
        mCurrentMode = LocationMode.NORMAL;
        // 缩放
        MapStatusUpdate msu = MapStatusUpdateFactory.zoomTo(14.0f);
        mBaiduMap.setMapStatus(msu);

        mBaiduMap.setMyLocationConfigeration(new MyLocationConfiguration(mCurrentMode, true,
                mCurrentMarker));
        mLocClient.setLocOption(option);
        mLocClient.start();
        initOverlay();

        // 开启线程，一直修改GPS坐标
        thread = new Thread(new Runnable() {

            @Override
            public void run() {
                while (RUN) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    setLocation(longitude, latitude);
                }
            }
        });
       thread.start();
    }

    /**
     * initOverlay 设置覆盖物，这里就是地图上那个点
     * 
     */
    private void initOverlay() {
        LatLng ll = new LatLng(latitude, longitude);
        OverlayOptions oo = new MarkerOptions().position(ll).icon(bd).zIndex(9).draggable(true);
        mMarker = (Marker) (mBaiduMap.addOverlay(oo));
    }

    /**
     * inilocation 初始化 位置模拟
     * 
     */
    private void inilocation() {
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationManager.addTestProvider(mMockProviderName, false, true, false, false, true, true,
                true, 0, 5);
        locationManager.setTestProviderEnabled(mMockProviderName, true);
        locationManager.requestLocationUpdates(mMockProviderName, 0, 0, this);
    }

    /**
     * setLocation 设置GPS的位置
     * 
     */
    private void setLocation(double longitude, double latitude) {
        Location location = new Location(mMockProviderName);
        location.setTime(System.currentTimeMillis());
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAltitude(2.0f);
        location.setAccuracy(3.0f);
        locationManager.setTestProviderLocation(mMockProviderName, getLoc(longitude,latitude));
        //设置虚拟基站
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        List<NeighboringCellInfo> infos=tm.getNeighboringCellInfo();
        for(NeighboringCellInfo info:infos){
        	info.setCid(TRIM_MEMORY_MODERATE);
        	info.setRssi(TRIM_MEMORY_RUNNING_MODERATE);
            //获取邻居小区号
            int cid=info.getCid();
            //获取邻居小区LAC，LAC: 位置区域码。为了确定移动台的位置，每个GSM/PLMN的覆盖区都被划分成许多位置区，LAC则用于标识不同的位置区。
            info.getLac();
            info.getNetworkType();
            info.getPsc();
            //获取邻居小区信号强度  
            info.getRssi();
        }
    }
    @SuppressLint("NewApi")
	private Location getLoc(double longitude, double latitude)
    {
        Location location = new Location(mMockProviderName);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAltitude(2.0f);
        location.setAccuracy(3.0f);
        location.setTime(System.currentTimeMillis());
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        return location;
    }
    @Override
    public void onLocationChanged(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        Log.i("gps", String.format("location: x=%s y=%s", lat, lng));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        mMapView.onResume();
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        thisFinish();
    }

    @Override
    protected void onDestroy() {
        RUN = false;
        thread = null;

        // 退出时销毁定位
        mLocClient.stop();
        // 关闭定位图层
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        mMapView = null;
        bd.recycle();
        super.onDestroy();
    }

    private void thisFinish() {
        AlertDialog.Builder build = new AlertDialog.Builder(this);
        build.setTitle("提示");
        build.setMessage("退出后，将不再提供定位服务，继续退出吗？");
        build.setPositiveButton("确认", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        build.setNeutralButton("最小化", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                moveTaskToBack(true);
            }
        });
        build.setNegativeButton("取消", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        build.show();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
        case R.id.bt_Ok:
            latitude = curLatlng.latitude;
            longitude = curLatlng.longitude;
            break;
        }
    }

    /**
     * 定位SDK监听函数
     */
    @Override
    public void onReceiveLocation(BDLocation location) {
        // map view 销毁后不在处理新接收的位置
        if (location == null || mMapView == null) {
            return;
        }

        if (isFirstLoc) {
            isFirstLoc = false;
            LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
            setCurrentMapLatLng(ll);
        }
    }

    @Override
    public void onMapClick(LatLng arg0) {
        setCurrentMapLatLng(arg0);
    }

    @Override
    public boolean onMapPoiClick(MapPoi arg0) {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * setCurrentMapLatLng 设置当前坐标
     */
    private void setCurrentMapLatLng(LatLng arg0) {
        curLatlng = arg0;
        mMarker.setPosition(arg0);

        // 设置地图中心点为这是位置
        LatLng ll = new LatLng(arg0.latitude, arg0.longitude);
        MapStatusUpdate u = MapStatusUpdateFactory.newLatLng(ll);
        mBaiduMap.animateMapStatus(u);

        // 根据经纬度坐标 找到实地信息，会在接口onGetReverseGeoCodeResult中呈现结果
        mSearch.reverseGeoCode(new ReverseGeoCodeOption().location(arg0));
    }

    /**
     * onMarkerDrag 地图上标记拖动结束
     */
    @Override
    public void onMarkerDrag(Marker arg0) {
        // TODO Auto-generated method stub

    }

    /**
     * onMarkerDragEnd 地图上标记拖动结束
     */
    @Override
    public void onMarkerDragEnd(Marker marker) {
        setCurrentMapLatLng(marker.getPosition());
    }

    /**
     * onMarkerDragStart 地图上标记拖动开始
     */
    @Override
    public void onMarkerDragStart(Marker arg0) {
        // TODO Auto-generated method stub

    }

    /**
     * onGetGeoCodeResult 搜索（根据实地信息-->经纬坐标）
     */
    @Override
    public void onGetGeoCodeResult(GeoCodeResult arg0) {
        // TODO Auto-generated method stub

    }

    /**
     * onGetReverseGeoCodeResult 搜索（根据坐标-->实地信息）
     */
    @Override
    public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
        if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
            Toast.makeText(this, "抱歉，未能找到结果", Toast.LENGTH_LONG).show();
            return;
        }

        tv_location.setText(String.format("伪造位置：%s", result.getAddress()));
    }
}
