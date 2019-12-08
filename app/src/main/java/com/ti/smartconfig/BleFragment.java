

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

package com.ti.smartconfig;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.ti.smartconfig.gatt.GattCharacteristicReadCallback;
import com.ti.smartconfig.gatt.GattManager;
import com.ti.smartconfig.utils.BlePickPopUpView;
import com.ti.smartconfig.utils.BlePickPopUpView_;
import com.ti.smartconfig.utils.BluetoothLeService;
import com.ti.smartconfig.utils.BroadcastToBleCallbackInterface;
import com.ti.smartconfig.utils.Constants;
import com.ti.smartconfig.utils.Popup;
import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import java.util.List;
import java.util.UUID;
import com.ti.smartconfig.gatt.GattCharacteristicWriteCallback;
import com.ti.smartconfig.gatt.CharacteristicChangeListener;
import com.ti.smartconfig.gatt.operations.GattCharacteristicReadOperation;
import com.ti.smartconfig.gatt.operations.GattCharacteristicWriteOperation;
import com.ti.smartconfig.gatt.operations.GattDescriptorReadOperation;
import com.ti.smartconfig.gatt.operations.GattOperation;

@EFragment(R.layout.ble_provisioning_layout)
public class BleFragment extends Fragment {
    private static final String TAG = "BleFragment";
    public SharedPreferences sharedpreferences;
    public static final String Name = "deviceIP";
    public final static short START_PROV = 0x05;
    private Handler mHandler;
    private Runnable mRunnable;
    private int wifiRssiLevel = 0;
    public BlePickPopUpView blePickPopUpView;
    private BroadcastToBleCallbackInterface callback;
    boolean comingBackFromPopUpFlag = false;
    private static final int SCAN_PERIOD = 2000;
    private static final int PROV_SCAN_PERIOD = 20000;
    private boolean returnFromPopUp = false;
    AlertDialog alertDialog;
    boolean deviceFound = false;
    public MainActivity mainActivity;
    BlePickPopUpView blePickUpView;

    @ViewById
    RelativeLayout tab_ble_loader_layout;
    @ViewById
    TextView tab_ble_loader_label;
    @ViewById
    EditText tab_ble_configuration_devicename_editText;
    @ViewById
    EditText tab_ble_configuration_ssid_name_editText;
    @ViewById
    EditText tab_ble_wifi_password_check_editText;
    @ViewById
    ImageView tab_ble_configuration_device_to_configure_device_pick_image;
    @ViewById
    ImageView tab_ble_configuration_start_button;
    @ViewById
    Button tab_ble_configuration_device_name_question_button;
    @ViewById
    Button tab_ble_configuration_ssid_question_button;
    @ViewById
    Button tab_ble_password_question_button;
    @ViewById
    Button tab_ble_device_to_configure_question_button;
    @ViewById
    CheckBox tab_ble_configuration_password_checkbox;
    @ViewById
    TextView tab_device_configuration_device_to_configure_device_pick_label;

    public String bleDeviceAddress = "";
    public String bleDeviceName = "";
    String deviceName;
    String ssidName;
    String password = "";
    TextWatcher deviceNameWatcher;
    TextWatcher ssidWatcher;
    boolean provStarted = false;
    public static BluetoothDevice mBtdevice;
    public static GattManager mGattManager;
    private boolean hasService;
    BluetoothGattCallback bleGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == 133) {
                Log.e("GattManager", "Got the status 133 bug, closing gatt");
                gatt.close();
                mGattManager.getGatts().remove(mBtdevice.getAddress());
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("GattManager", "Gatt connected to device " + mBtdevice.getAddress());
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(tab_device_configuration_device_to_configure_device_pick_label!=null) {
                            tab_device_configuration_device_to_configure_device_pick_label.setText(bleDeviceName);
                            tab_device_configuration_device_to_configure_device_pick_label.setTextColor(Color.BLACK);
                            check_able_start_button();
                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    // Do something after 1000ms
                                    gatt.discoverServices();
                                }
                            }, 1000);
                        }
                    }
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("GattManager", "Disconnected from gatt server " + mBtdevice.getAddress() + ", newState: " + newState);
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if(tab_device_configuration_device_to_configure_device_pick_label!=null) {
                            tab_device_configuration_device_to_configure_device_pick_label.setText("Search for your device");
                            tab_device_configuration_device_to_configure_device_pick_label.setTextColor(Color.GRAY);
                            check_able_start_button();
                        }
                    }
                });
                mGattManager.getGatts().remove(mBtdevice.getAddress());
                mGattManager.setCurrentOperation(null);
                gatt.close();
                mGattManager.drive();
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            ((GattDescriptorReadOperation) mGattManager.getCurrentOperation()).onRead(descriptor);
            mGattManager.setCurrentOperation(null);
            mGattManager.drive();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            mGattManager.setCurrentOperation(null);
            mGattManager.drive();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            ((GattCharacteristicReadOperation) mGattManager.getCurrentOperation()).onRead(characteristic);
            mGattManager.setCurrentOperation(null);
            mGattManager.drive();
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d("GattManager", "services discovered, status: " + status);
            if(!provStarted) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mHandler.removeCallbacksAndMessages(null);
                    }
                });

                List<BluetoothGattService> services = gatt.getServices();

                for (BluetoothGattService s : services) {
                    if (s.getUuid().toString().equals(Constants.BLE_WIFI_SERVICE_UUID)) {
                        hasService = true;
                    }
                }
                if (!hasService) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            gatt.close();
                           }
                    });
                } else {
                    mGattManager.getGatts().put(mBtdevice.getAddress(), gatt);
                }
            }else{
                provStarted=false;
                List<BluetoothGattService> services = gatt.getServices();
                boolean hasService = false;
                for (BluetoothGattService s : services) {
                    if (s.getUuid().toString().equals(Constants.BLE_WIFI_SERVICE_UUID)) {
                        hasService = true;
                    }
                }
                if (!hasService) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            gatt.close();
                        }
                    });
                } else {
                    mGattManager.getGatts().put(mBtdevice.getAddress(), gatt);
                    // Device reconnected.
                    // Reading confirmation status value.
                    mGattManager.queue(new GattCharacteristicReadOperation(
                            mBtdevice,
                            UUID.fromString(Constants.BLE_WIFI_SERVICE_UUID),
                            UUID.fromString(Constants.BLE_WIFI_STATUS_CHAR_UUID),
                            new GattCharacteristicReadCallback() {
                                @Override
                                public void call(byte[] characteristic) {
                                    if (characteristic[0] == Constants.BLE_PROVISIOINING_SUCCESS) {
                                        //IP Acquired and provisioning succeeded. Sending confirmation
                                        final GattCharacteristicWriteCallback startWriteCallback = new GattCharacteristicWriteCallback() {
                                            @Override
                                            public void call(BluetoothGattCharacteristic characteristic, int status) {
                                                if (characteristic.getUuid().equals(UUID.fromString(Constants.BLE_WIFI_START_CHAR_UUID))) {
                                                    // Start characteristic written successfully
                                                    //   Provisioning successfully Completed.
                                                    Log.d(TAG, "Success");
                                                    getActivity().runOnUiThread(new Runnable() {
                                                        public void run() {
                                                            mainActivity.showSuccessDialog(null, getString(R.string.pop_up_close), null, Popup.PopupType.Success, null, "Provisioning successful");
                                                            removeLoaderAndClear();
                                                            tab_ble_configuration_start_button.setImageResource(R.drawable.start_configuration_button_off);
                                                            tab_ble_configuration_start_button.setEnabled(false);
                                                            mHandler.removeCallbacksAndMessages(null);
                                                        }
                                                    });
                                                }
                                            }
                                        };
                                        mGattManager.queue(new GattCharacteristicWriteOperation(
                                                mBtdevice,
                                                UUID.fromString(Constants.BLE_WIFI_SERVICE_UUID),
                                                UUID.fromString(Constants.BLE_WIFI_START_CHAR_UUID),
                                                new byte[]{0x02},
                                                startWriteCallback
                                        ));
                                        return;
                                    } else {
                                        getActivity().runOnUiThread(new Runnable() {
                                            public void run() {
                                                showLoaderWithText(false, "");
                                                check_able_start_button();
                                            }
                                        });
                                    }
                                    if (characteristic[0] == Constants.FAILED_TO_GET_IP_ADDRESS) {
                                        //ERROR: Connection to AP failed!
                                        getActivity().runOnUiThread(new Runnable() {
                                            public void run() {
                                                mainActivity.showSuccessDialog(Constants.FAILED_IP_ADDRESS, getString(R.string.pop_up_close), null, Popup.PopupType.Failure, null, null);
                                                Log.d(TAG, "Failed to get and ip address from the access point");
                                                removeLoaderAndClear();
                                            }
                                        });
                                    } else if (characteristic[0] == Constants.FAILED_TO_CONNECT_THE_AP) {
                                        //ERROR: Failed to acquire IP!
                                        getActivity().runOnUiThread(new Runnable() {
                                            public void run() {
                                                mainActivity.showSuccessDialog(Constants.FAILED_TO_CONNECT_AP, getString(R.string.pop_up_close), null, Popup.PopupType.Failure, null, null);
                                                Log.d(TAG, "Failed to connect to the access point");
                                                removeLoaderAndClear();
                                            }
                                        });
                                    } else if (characteristic[0] == Constants.FAILED_TO_PING) {
                                        // ERROR: Failed to ping gateway!
                                        getActivity().runOnUiThread(new Runnable() {
                                            public void run() {
                                                mainActivity.showSuccessDialog(Constants.FAILED_PING, getString(R.string.pop_up_close), null, Popup.PopupType.Failure, null, null);
                                                Log.d(TAG, "Failed to ping the access point");
                                                removeLoaderAndClear();
                                            }
                                        });
                                    } else if (characteristic[0] == Constants.FAILED_REASON_TIMEOUT) {
                                        // "ERROR: Provisioning timeout!
                                        getActivity().runOnUiThread(new Runnable() {
                                            public void run() {
                                                mainActivity.showSuccessDialog(Constants.FAILED_TIMEOUT, getString(R.string.pop_up_close), null, Popup.PopupType.Failure, null, null);
                                                Log.d(TAG, "Failed Timeout!");
                                                removeLoaderAndClear();
                                            }
                                        });
                                    }
                                }
                            }
                    ));
                }
            }
        }
    public void removeLoaderAndClear(){
        showLoaderWithText(false, "");
        tab_ble_configuration_devicename_editText.setText("");
        tab_ble_configuration_ssid_name_editText.setText("");
        tab_ble_wifi_password_check_editText.setText("");
        deviceFound = false;
        provStarted = false;
        bleDeviceName = "";
        bleDeviceAddress = "";
    }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d("GattManager", "Characteristic " + characteristic.getUuid() + "written to on device " + mBtdevice.getAddress());
            if (mGattManager.getCurrentOperation() != null && mGattManager.getCurrentOperation().type() == GattOperation.OperationType.OPERATION_CHAR_WRITE)
                ((GattCharacteristicWriteOperation) mGattManager.getCurrentOperation()).onWrite(characteristic, status);
            mGattManager.setCurrentOperation(null);
            mGattManager.drive();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
//                    Log.d("GattManager", "Characteristic " + characteristic.getUuid() + "was changed, device: " + device.getAddress());
            if (mGattManager.getCharacteristicChangeListeners().containsKey(characteristic.getUuid())) {
                for (CharacteristicChangeListener listener : mGattManager.getCharacteristicChangeListeners().get(characteristic.getUuid())) {
                    listener.onCharacteristicChanged(mBtdevice.getAddress(), characteristic);
                }
            }
        }
    };
    /**
     * Called after fragment is initialized
     */
    @AfterViews
    void afterViews() {

        tab_ble_wifi_password_check_editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        mHandler = new android.os.Handler();
        mRunnable = new Runnable() {
            public void run() {
                Log.i("tag", "20000 milliseconds timeout");
                mHandler.removeCallbacks(mRunnable);
                mHandler.removeCallbacksAndMessages(null);
                // Reconnect to BLE device to continue provisioning
                getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {mGattManager.connectToDevice(mBtdevice, bleGattCallback);}
                    });

            }
        };

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!mainActivity.getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(mainActivity.getApplicationContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show();
        }
        // Checks if Bluetooth is supported on the device.
        if (mainActivity.mBluetoothAdapter == null) {
            Toast.makeText(mainActivity.getApplicationContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        //device name watcher
        deviceNameWatcher = new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {


                check_able_start_button();

            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };
        //ssid watcher
        ssidWatcher = new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                if (tab_ble_configuration_devicename_editText.length() > 0 && tab_ble_configuration_ssid_name_editText.length() > 0 && !tab_device_configuration_device_to_configure_device_pick_label.getText().equals("Search for your device")) {
                    //name required - got name
                    //check pass field visibility
                    tab_ble_configuration_start_button.setImageResource(R.drawable.start_configuration_button_on);
                    tab_ble_configuration_start_button.setEnabled(true);

                } else {
                    // name required but missing - disable configuration
                    tab_ble_configuration_start_button.setImageResource(R.drawable.start_configuration_button_off);
                    tab_ble_configuration_start_button.setEnabled(false);

                }
            }

            @Override
            public void afterTextChanged(Editable s) {
//                ssidName = tab_ble_configuration_ssid_name_editText.getText().toString();
            }
        };

        tab_ble_configuration_password_checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                tab_ble_wifi_password_check_editText.requestFocus();
                try {
                    InputMethodManager imm = (InputMethodManager) mainActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(tab_ble_wifi_password_check_editText, InputMethodManager.SHOW_IMPLICIT);
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                // checkbox status is changed from unchecked to checked.
                if (!isChecked) {
                    // show password
                    tab_ble_wifi_password_check_editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                } else {
                    // hide password
                    tab_ble_wifi_password_check_editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                }
            }
        });

        //listen to editText focus and hiding keyboard when focus is out
        tab_ble_configuration_devicename_editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    hideKeyboard(v);
                }
            }
        });
        tab_ble_configuration_ssid_name_editText.setOnFocusChangeListener(new View.OnFocusChangeListener(){
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    hideKeyboard(v);
                }
            }
        });
        tab_ble_wifi_password_check_editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    hideKeyboard(v);
                }
            }

        });

        tab_ble_configuration_devicename_editText.addTextChangedListener(deviceNameWatcher);
        tab_ble_configuration_ssid_name_editText.addTextChangedListener(ssidWatcher);
    }

    //start ble - sending profile details...
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Click
    void tab_ble_configuration_start_button() {
        showLoaderWithText(true, "Provisioning in progress...");
        if (mBtdevice.getBondState() != BluetoothDevice.BOND_BONDED) {
            mBtdevice.createBond();
        }
        //we splitting our text we got from the fields to fit the characteristics bounds
        deviceFound = false;
        provStarted = true;
        deviceName = tab_ble_configuration_devicename_editText.getText().toString();
        ssidName = tab_ble_configuration_ssid_name_editText.getText().toString();
        password = tab_ble_wifi_password_check_editText.getText().toString();
        Log.d("BLEProvisionFragment", "BLE Provisioning Started");
        //BLE Provisioning Started!
       // Writing SSID characteristic (" + mSsid + ")"
        mHandler = new android.os.Handler();
        mRunnable = new Runnable() {
            public void run() {
                Log.i("tag", "20000 milliseconds timeout");
                mHandler.removeCallbacks(mRunnable);
                mHandler.removeCallbacksAndMessages(null);
                // Reconnect to BLE device to continue provisioning
                getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(mGattManager!=null) mGattManager.connectToDevice(mBtdevice, bleGattCallback);}
                    });
            }
        };
        mHandler.postDelayed(mRunnable, 20000);
        final GattCharacteristicWriteCallback deviceNameWriteCallback = new GattCharacteristicWriteCallback() {
            @Override
            public void call(BluetoothGattCharacteristic characteristic, int status) {
                if (characteristic.getUuid().equals(UUID.fromString(Constants.BLE_WIFI_DEVNAME_CHAR_UUID))) {
                   // Device name characteristic written successfully
                    Log.i("DeviceInfo", "Device name characteristic written successfully");
                    sendProvisionStart();
                }
            }
        };

        final GattCharacteristicWriteCallback keyWriteCallback = new GattCharacteristicWriteCallback() {
            @Override
            public void call(BluetoothGattCharacteristic characteristic, int status) {
                if (characteristic.getUuid().equals(UUID.fromString(Constants.BLE_WIFI_PASS_CHAR_UUID))) {
                    //Security key characteristic written successfully!
                    Log.i("DeviceInfo", "Security key characteristic written successfully!");

                    if (!deviceName.isEmpty()) {
                        //Writing device name characteristic.
                        Log.i("DeviceInfo", "Writing device name characteristic.");
                        mGattManager.queue(new GattCharacteristicWriteOperation(
                                mBtdevice,
                                UUID.fromString(Constants.BLE_WIFI_SERVICE_UUID),
                                UUID.fromString(Constants.BLE_WIFI_DEVNAME_CHAR_UUID),
                                deviceName.getBytes(),
                                deviceNameWriteCallback
                        ));
                    } else {
                        //No device name provided, skipping to next step...
                        sendProvisionStart();
                    }

                }
            }
        };

        final GattCharacteristicWriteCallback ssidWriteCallback = new GattCharacteristicWriteCallback() {
            @Override
            public void call(BluetoothGattCharacteristic characteristic, int status) {
                if (characteristic.getUuid().equals(UUID.fromString(Constants.BLE_WIFI_SSID_CHAR_UUID))) {
                    //SSID characteristic written successfully!
                    Log.i("DeviceInfo", "SSID characteristic written successfully!");
                    if (!password.isEmpty()) {
                        //Writing security key characteristic.
                        Log.i("DeviceInfo", "Writing security key characteristic.");
                            mGattManager.queue(new GattCharacteristicWriteOperation(mBtdevice,
                                    UUID.fromString(Constants.BLE_WIFI_SERVICE_UUID),
                                    UUID.fromString(Constants.BLE_WIFI_PASS_CHAR_UUID),
                                    password.getBytes(),
                                    keyWriteCallback));
                    } else {
                        //No security key provided, skipping to next step...
                        if (!deviceName.isEmpty()) {
                            // Writing device name characteristic.
                            Log.i("DeviceInfo", "Writing device name characteristic.");
                            mGattManager.queue(new GattCharacteristicWriteOperation(mBtdevice,
                                    UUID.fromString(Constants.BLE_WIFI_SERVICE_UUID),
                                    UUID.fromString(Constants.BLE_WIFI_DEVNAME_CHAR_UUID),
                                    deviceName.getBytes(),
                                    deviceNameWriteCallback
                            ));
                        }
                    }
                }
            }
        };
        mGattManager.queue(new GattCharacteristicWriteOperation(
                mBtdevice,
                UUID.fromString(Constants.BLE_WIFI_SERVICE_UUID),
                UUID.fromString(Constants.BLE_WIFI_SSID_CHAR_UUID),
                ssidName.getBytes(),ssidWriteCallback
            ));
    }

    public void sendProvisionStart() {
        //Initiating provisioning. This will cause the device to disconnect and for the SimpleLink device to attempt to connect to the configured router
        final GattCharacteristicWriteCallback startWriteCallback = new GattCharacteristicWriteCallback() {
            @Override
            public void call(BluetoothGattCharacteristic characteristic, int status) {
                if (characteristic.getUuid().equals(UUID.fromString(Constants.BLE_WIFI_START_CHAR_UUID))) {
                   // Start characteristic written successfully!
                    Log.i("DeviceInfo", "Start characteristic written successfully!");
                }
            }
        };
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Provisioning
                mGattManager.queue(new GattCharacteristicWriteOperation(
                        mBtdevice,
                        UUID.fromString(Constants.BLE_WIFI_SERVICE_UUID),
                        UUID.fromString(Constants.BLE_WIFI_START_CHAR_UUID),
                        new byte[]{0x01},
                        startWriteCallback
                ));
                mHandler.postDelayed(mRunnable, 20000);
            }
        });
    }

    //open device list --> start scan --> click + start pairing
    @Click
    void tab_ble_configuration_device_to_configure_device_pick_image() {
        if(mainActivity.mBluetoothAdapter == null || !mainActivity.mBluetoothAdapter.isEnabled()){
            Intent discoverableIntent = new
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            getContext().startActivity(discoverableIntent);
        }else {
            if (alertDialog != null && alertDialog.isShowing()) {
                alertDialog.cancel();
                alertDialog = null;
            } else {
                alertDialog = null;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(mainActivity);
            blePickUpView = BlePickPopUpView_.build(mainActivity);
            BlePickPopUpView.bleFragment = this;
            blePickUpView.start();
            alertDialog = builder.create();
            alertDialog.setView(blePickUpView, 0, 0, 0, 0);
            alertDialog.show();
            // close ,disconnect from gatt etc..
            if (mGattManager != null && mBtdevice != null) {
                mGattManager.close(mBtdevice);
                mGattManager = null;
            }
            mHandler.removeCallbacks(mRunnable);
            mHandler.removeCallbacksAndMessages(null);
            comingBackFromPopUpFlag = false;
        }
    }
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Click
    void tab_ble_configuration_device_name_question_button() {
        mainActivity.showSuccessDialog(Constants.QUESTION_DEVICE_NAME, getString(R.string.pop_up_close), null, Popup.PopupType.Information, null, null);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Click
    void tab_ble_configuration_ssid_question_button() {
        mainActivity.showSuccessDialog(Constants.QUESTION_NETWORK_NAME, getString(R.string.pop_up_close), null, Popup.PopupType.Information, null, null);
    }

    @Click
    void tab_ble_device_to_configure_question_button() {
        mainActivity.showSuccessDialog(Constants.QUESTION_CHOOSE_BLE_DEVICE, getString(R.string.pop_up_close), null, Popup.PopupType.Information, null, null);
    }

    @Click
    void tab_ble_password_question_button() {
        mainActivity.showSuccessDialog(Constants.QUESTION_PASSWORD, getString(R.string.pop_up_close), null, Popup.PopupType.Information, null, null);
    }

    @Override
    public void onResume() {
        super.onResume();
        //connect back to bleservice!
        mGattManager = new GattManager(getActivity());
        if (mBtdevice != null) {
            //mBtdevice = getArguments().getParcelable(BT_DEVICE);
            hasService = false;
            mRunnable = new Runnable() {
                public void run() {
                    Log.i("tag", "20000 milliseconds timeout");
                    mHandler.removeCallbacks(mRunnable);
                    mHandler.removeCallbacksAndMessages(null);
                    // Reconnect to BLE device to continue provisioning
                    getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {mGattManager.connectToDevice(mBtdevice, bleGattCallback);}
                        });
                }
            };
            if(!bleDeviceName.equals("")) {
                mGattManager.connectToDevice(mBtdevice, bleGattCallback);
                mBtdevice.createBond();
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        check_able_start_button();
                    }
                });
            }
        }else {
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    tab_ble_configuration_start_button.setImageResource(R.drawable.start_configuration_button_off);
                    tab_ble_configuration_start_button.setEnabled(false);
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
            if (mGattManager!= null&&mBtdevice!=null) {
                mGattManager.close(mBtdevice);
                mGattManager = null;
            }
    }
    @Override
    public void onDetach() {
        super.onDetach();
        if (mGattManager!= null&& mBtdevice !=null) {
            mGattManager.close(mBtdevice);
            mGattManager = null;
        }
    }
    @Override
    public void onDestroy() {
        Log.d("BLE Provision Fragment", "OnDestroy");
        super.onDestroy();
        if (mGattManager!= null&& mBtdevice!=null) {
            mGattManager.close(mBtdevice);
            mGattManager = null;
        }
    }
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mainActivity = (MainActivity) activity;
    }
    @UiThread
    void showToastWithMessage(final String msg) {

        try {

            Toast.makeText(mainActivity, msg, Toast.LENGTH_LONG).show();

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        //Toast.makeText(mainActivity, msg, Toast.LENGTH_LONG).show();
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }
                }
            }, SCAN_PERIOD);

        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @UiThread
    public void closeDialog() {
        comingBackFromPopUpFlag = true;
        alertDialog.cancel();
        alertDialog = null;
        blePickPopUpView = null;
        returnFromPopUp = true;
        if (bleDeviceAddress != null && bleDeviceAddress.length() > 0) {
            start();
            Log.d(TAG, "The chosen ble device : " + bleDeviceAddress);
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mGattManager = new GattManager(getActivity());
                    mGattManager.connectToDevice(mBtdevice, bleGattCallback);
                    mBtdevice.createBond();
                }
            }, 500);
			
        }
    }

    public void start() {

//                mainActivity = (MainActivity) getActivity().getApplicationContext();
        final MainActivity mainActivity = (MainActivity) getActivity();
//                mHandler = new Handler();
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getActivity().getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(getActivity().getApplicationContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show();
        }
        mainActivity.initializeBluetooth();

        //need to enable this one
        mainActivity.bleFragmentInit(this, callback);
    }
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    /**
     * Hides the virtual keyboard from the window.
     *
     * @param view View. The view which lost focus (which caused this method to be called)
     */
    public void hideKeyboard(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) mainActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public int convertByteToInt(byte[] b) {
        int value = 0;
        for (int i = 0; i < b.length; i++)
            value = (value << 8) | b[i];
        return value;
    }

    /**
     * Displays or dismisses the loader, depending on the Boolean value passed as parameter.
     *
     * @param show Boolean. True to display loader, false to dismiss.
     * @param msg  String. The text to be displayed if the loader is to be shown.
     */
    @UiThread
    void showLoaderWithText(Boolean show, String msg) {
        if (!show) {
            tab_ble_loader_layout.setVisibility(View.GONE);
            tab_ble_loader_label.setText("");
        } else {
            tab_ble_loader_layout.setVisibility(View.VISIBLE);
            tab_ble_loader_label.setText(msg);
        }
    }

    public String[] splitStringEvery(String s, int interval) {
        int arrayLength = (int) Math.ceil(((s.length() / (double)interval)));
        String[] result = new String[arrayLength];

        int j = 0;
        int lastIndex = result.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            result[i] = s.substring(j, j + interval);
            j += interval;
        } //Add the last bit
        result[lastIndex] = s.substring(j);

        return result;
    }

    public void check_able_start_button(){
        if (tab_ble_configuration_devicename_editText.length() > 0 && tab_ble_configuration_ssid_name_editText.length() > 0 && !tab_device_configuration_device_to_configure_device_pick_label.getText().equals("Search for your device")) {
            //pass required - got pass
            //enable configuration and pull pass
            tab_ble_configuration_start_button.setImageResource(R.drawable.start_configuration_button_on);
            tab_ble_configuration_start_button.setEnabled(true);
        } else {
            //pass required - but missing
            //disable configuration
            tab_ble_configuration_start_button.setImageResource(R.drawable.start_configuration_button_off);
            tab_ble_configuration_start_button.setEnabled(false);
        }
    }


}