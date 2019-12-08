/*
* Copyright (C) 2019 Texas Instruments Incorporated - http://www.ti.com/
*
*
*  Redistribution and use in source and binary forms, with or without
*  modification, are permitted provided that the following conditions
*  are met:
*
*    Redistributions of source code must retain the above copyright
*    notice, this list of conditions and the following disclaimer.
*
*    Redistributions in binary form must reproduce the above copyright
*    notice, this list of conditions and the following disclaimer in the
*    documentation and/or other materials provided with the
*    distribution.
*
*    Neither the name of Texas Instruments Incorporated nor the names of
*    its contributors may be used to endorse or promote products derived
*    from this software without specific prior written permission.
*
*  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
*  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
*  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
*  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
*  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
*  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
*  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
*  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
*  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
*  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
*  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
*/

package com.ti.smartconfig.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EViewGroup;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import com.ti.smartconfig.BleFragment;
import com.ti.smartconfig.MainActivity;
import com.ti.smartconfig.R;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import static com.ti.smartconfig.BleFragment.mBtdevice;
import static com.ti.smartconfig.BleFragment.mGattManager;

@EViewGroup(R.layout.ble_pop_up_view)
public class BlePickPopUpView extends RelativeLayout {
    private Context mContext;
    private BluetoothDeviceListAdapter mLeDeviceListAdapter;
    MainActivity mainActivity;
    private Handler mHandler;
    private int wifiRssiLevel = 0;
    private String bleDeviceAddress;
    private String bleDeviceName;
    public BlePickPopUpView blePickPopUpView;
    private BlePopUpCallbackInterface callback;
    private boolean mScanning;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private static final long SCAN_PERIOD = 2000;
    private boolean returnFromPopUp = false;
    public static BleFragment bleFragment;
    @ViewById
    ListView ble_scan_results_pop_up_list_paired;
    @ViewById
    ImageView ble_scan_results_pop_up_buttons_ok_button;
    @ViewById
    ImageView ble_scan_results_pop_up_buttons_rescan_button;
    @ViewById
    ProgressBar ble_scan_results_pop_up_loader;
    @ViewById
    ImageView ble_rssi_image;

    public BlePickPopUpView(Context context) {
        super(context);
        mContext = context;
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            final BluetoothDevice btDevice = result.getDevice();
            final int rssi = result.getRssi();

            mLeDeviceListAdapter.addDevice(btDevice, rssi);
            mLeDeviceListAdapter.notifyDataSetChanged();
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    @UiThread
    void print(String msg) {
        System.out.println(msg);
    }

    @AfterViews
    void afterViews() {
        mainActivity = (MainActivity) getContext();
        if (bleFragment != null) {
            mHandler = new Handler();
            // Use this check to determine whether BLE is supported on the device.  Then you can
            // selectively disable BLE-related features.
            if (!getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                Toast.makeText(getContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            }
                        // Checks if Bluetooth is supported on the device.
            if (mainActivity.mBluetoothAdapter == null) {
                Toast.makeText(getContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show();
                return;
            }
            mLeDeviceListAdapter = new BluetoothDeviceListAdapter();
            ble_scan_results_pop_up_list_paired.setAdapter(mLeDeviceListAdapter);
         //   scanLeDevice(true);
        }
        ble_scan_results_pop_up_list_paired.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                final String info = arg1.toString();
                BluetoothDevice device = (BluetoothDevice) ble_scan_results_pop_up_list_paired.getAdapter().getItem(arg2);
                //get the device address when click the device item
                bleDeviceAddress= device.getAddress();
                bleDeviceName = device.getName();
                mHandler.removeCallbacksAndMessages(null);
                mainActivity.callback = null;
                if(bleDeviceAddress != null) {
                    bleFragment.bleDeviceAddress = bleDeviceAddress;
                    bleFragment.bleDeviceName = bleDeviceName;
                    mBtdevice = device;
                    bleFragment.closeDialog();
                }
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // close ,disconnect from gatt etc..
        if (mGattManager!= null&& mBtdevice !=null) {
            mGattManager.close(mBtdevice);
            mGattManager = null;
        }
        mHandler= new Handler();
        mHandler.removeCallbacksAndMessages(null);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Click
    void ble_scan_results_pop_up_buttons_ok_button() {
        if(mGattManager!=null&& mBtdevice!=null&& mBtdevice.getAddress()!=null) {
            mGattManager.close(mBtdevice);
            mHandler.removeCallbacksAndMessages(null);
            mainActivity.callback = null;
            bleDeviceAddress = null;
        }
        bleFragment.closeDialog();
    }

    @UiThread
    void showToastWithMessage(String msg) {
        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Click
    void ble_scan_results_pop_up_buttons_rescan_button() {
      scanLeDevice(true);
        mLeDeviceListAdapter.clear();
    }

    private class BluetoothDeviceListAdapter extends BaseAdapter {

        private ArrayList<BluetoothDevice> device_list;
        private HashMap hm = new HashMap();
        private LayoutInflater mInflator;

        public BluetoothDeviceListAdapter() {
            super();
            this.device_list = new ArrayList<>();
            mInflator = bleFragment.getActivity().getLayoutInflater();
        }

        public class BluetoothDeviceComparator implements Comparator<BluetoothDevice> {
            public int compare(BluetoothDevice left, BluetoothDevice right) {
                return (int) (hm.get(right)) - (int) (hm.get(left));
            }
        }

        public void addDevice(BluetoothDevice device, int rssi) {
            if(device.getName()!=null&&device.getName().length()>0) {
                if (!device_list.contains(device)) {
                    device_list.add(device);
                }
                hm.put(device, rssi);

                Collections.sort(device_list, new BluetoothDeviceComparator());
            }
        }

        public void clear() {
            device_list.clear();
        }

        public BluetoothDevice getDevice(int position) {
            return device_list.get(position);
        }

        @Override
        public Object getItem(int position) {
            return device_list.get(position);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getCount() {
            return device_list.size();
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
                view = inflater.inflate(R.layout.ble_device_name_layout, parent, false);
            }
            TextView cellLabelDeviceName = (TextView) view.findViewById(R.id.ble_device_name);
            TextView cellLabelDeviceAddress = (TextView) view.findViewById(R.id.ble_device_mac);
            ImageView bleRSSI = (ImageView) view.findViewById(R.id.ble_rssi_image);
            BluetoothDevice device = device_list.get(position);
            final String deviceName = device.getName();
            final String deviceAddress = device.getAddress();
            int deviceRssi = (int) hm.get(device);

            if (deviceName != null && deviceName.length() > 0)
                cellLabelDeviceName.setText(deviceName);
            cellLabelDeviceAddress.setText(deviceAddress);
            if (deviceRssi >= 0) {
                switch (deviceRssi) {
                    case SmartConfigConstants.RSSI_LEVEL_HIGH:
                        bleRSSI.setImageResource(R.drawable.new_graphics_wifi_4);
                        break;
                    case SmartConfigConstants.RSSI_LEVEL_MID_HIGH:
                        bleRSSI.setImageResource(R.drawable.new_graphics_wifi_3);
                        break;
                    case SmartConfigConstants.RSSI_LEVEL_MID_LOW:
                        bleRSSI.setImageResource(R.drawable.new_graphics_wifi_2);
                        break;
                    case SmartConfigConstants.RSSI_LEVEL_LOW:
                        bleRSSI.setImageResource(R.drawable.new_graphics_wifi_1);
                        break;
                }
            }
            return view;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void scanLeDevice(final boolean enable) {
        mainActivity.mBluetoothAdapter = mainActivity.bluetoothManager.getAdapter();
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler = new Handler();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    ble_scan_results_pop_up_loader.setVisibility(INVISIBLE);
                    if (mLEScanner != null && mainActivity.mBluetoothAdapter.isEnabled())
                        mLEScanner.stopScan(mScanCallback);
                }
            }, SCAN_PERIOD);
            if (mainActivity.mBluetoothAdapter == null || !mainActivity.mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                getContext().startActivity(enableBtIntent);
                bleFragment.closeDialog();
                return;
            }
            mLEScanner.startScan(null, settings, mScanCallback);
            ble_scan_results_pop_up_loader.setVisibility(VISIBLE);
        } else {
            ble_scan_results_pop_up_loader.setVisibility(INVISIBLE);
            mLEScanner.stopScan(mScanCallback);
        }
    }


    private void startActivityForResult(Intent enableBtIntent, int i) {

    }
    public void start() {
        if (bleFragment != null&&( mainActivity.mBluetoothAdapter != null && mainActivity.mBluetoothAdapter.isEnabled())) {
            if (Build.VERSION.SDK_INT >= Constants.MIN_SDK) {
                mHandler = new Handler();
                mLEScanner = mainActivity.mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                mainActivity = (MainActivity) getContext();
                // Use this check to determine whether BLE is supported on the device.  Then you can
                // selectively disable BLE-related features.
                if (!getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                    Toast.makeText(getContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show();
                }
                mainActivity.initializeBluetooth();
                mLeDeviceListAdapter = new BluetoothDeviceListAdapter();
                ble_scan_results_pop_up_list_paired.setAdapter(mLeDeviceListAdapter);
                scanLeDevice(true);
                mainActivity.blePopInit(this, callback);
            }
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
