
package com.ti.smartconfig.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import com.ti.smartconfig.MainActivity;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.ti.smartconfig.MainActivity.mbleService;


/**
 * Service for managing connection and data communication with a GATT server
 * hosted on a given Bluetooth LE device.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothLeService extends Service {
    static final String TAG = "BluetoothLeService";
    public final static String ACTION_DATA_AVAILABLE =          "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_GATT_CONNECTED =          "com.ti.ble.common.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =       "com.ti.ble.common.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED ="com.ti.ble.common.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_READ =               "com.ti.ble.common.ACTION_DATA_READ";
    public final static String ACTION_DATA_NOTIFY =             "com.ti.ble.common.ACTION_DATA_NOTIFY";
    public final static String ACTION_DATA_WRITE =              "com.ti.ble.common.ACTION_DATA_WRITE";
    public final static String EXTRA_DATA =                     "com.ti.ble.common.EXTRA_DATA";
    public final static String EXTRA_UUID =                     "com.ti.ble.common.EXTRA_UUID";
    public final static String EXTRA_STATUS =                   "com.ti.ble.common.EXTRA_STATUS";
    public final static String EXTRA_ADDRESS =                  "com.ti.ble.common.EXTRA_ADDRESS";
 //   public final static String ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static int GATT_TIMEOUT = 150;
    BleCallbackInterface callback;
    Activity activity;
    // BLE
    private BluetoothManager mBluetoothManager = null;
  //  private BluetoothAdapter mBtAdapter = null;
    private BluetoothGatt mBluetoothGatt = null;
    private int mConnectionState = Constants.STATE_DISCONNECTED;
    private String mBluetoothDeviceAddress;
 //   private PreferenceWR mDevicePrefs = null;
    MainActivity mainActivity = (MainActivity) getBaseContext();
    public Timer disconnectionTimer;
    private final Lock lock = new ReentrantLock();
    private volatile boolean blocking = false;
    private volatile int lastGattStatus = 0; //Success
    private volatile bleRequest curBleRequest = null;
    public int mCurrentConnectionPriority = 0;

    public enum bleRequestOperation {
        wrBlocking,
        wr,
        rdBlocking,
        rd,
        nsBlocking,
    }

    public enum bleRequestStatus {
        not_queued,
        queued,
        processing,
        timeout,
        done,
        no_such_request,
        failed,
    }

    public class bleRequest {
        public int id;
        public BluetoothGattCharacteristic characteristic;
        public bleRequestOperation operation;
        public volatile bleRequestStatus status;
        public int timeout;
        public int curTimeout;
        public boolean notifyenable;
    }

    // Queuing for fast application response.
    private volatile LinkedList<bleRequest> procQueue;
    private volatile LinkedList<bleRequest> nonBlockQueue;

    /**
     * GATT client callbacks
     */
    private BluetoothGattCallback mGattCallbacks = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (mBluetoothGatt == null) {
                 Log.e(TAG, "mBluetoothGatt not created!");
                return;
            }
            BluetoothDevice device = gatt.getDevice();
            String address = device.getAddress();
             Log.d(TAG, "onConnectionStateChange (" + address + ") " + newState +
             " status: " + status);
            try {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        mConnectionState = Constants.STATE_CONNECTED;
                        broadcastUpdate(ACTION_GATT_CONNECTED, address, newState);
                        //causing disconnection
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        mConnectionState = Constants.STATE_DISCONNECTED;
                        broadcastUpdate(ACTION_GATT_DISCONNECTED, address, newState);
                        break;
                    //7.11 need to change 4 to constant
                    case 4 :
                        broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED, address, newState);
                        //add here that we got gatt success / discovered new
                        break;
                    case 5 :
                        broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED, address, newState);
                    default:
                        // Log.e(TAG, "New state not processed: " + newState);
                        break;
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothDevice device = gatt.getDevice();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED,gatt.getDevice().getAddress(),4);
                    Log.d(TAG,"Found device with Connection control service, using it !");
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }

        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_NOTIFY, characteristic,
                    BluetoothGatt.GATT_SUCCESS);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("Gatt chars :",characteristic+" INT : "+ status);
                if (!characteristic.getUuid().toString().contains(Constants.BLE_WIFI_STATUS_CHAR_UUID)) {
                    byte[] val;
                    val = characteristic.getValue();
                    String string = new String(val);
                    Log.d(TAG, "answer : " + string);
                    BluetoothLeService.this.callback.broadcastBle(ACTION_GATT_SERVICES_DISCOVERED, string, 5);
                }
                else{
                    BluetoothLeService.this.callback.broadcastBle(ACTION_GATT_SERVICES_DISCOVERED, characteristic.getValue(), 5);
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            if (blocking)unlockBlockingThread(status);
            if (nonBlockQueue.size() > 0) {
                lock.lock();
                for (int ii = 0; ii < nonBlockQueue.size(); ii++) {
                    bleRequest req = nonBlockQueue.get(ii);
                    if (req.characteristic == characteristic) {
                        req.status = bleRequestStatus.done;
                        nonBlockQueue.remove(ii);
                        break;
                    }
                }
                lock.unlock();
            }
            broadcastUpdate(ACTION_DATA_WRITE, characteristic, status);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt,
                                     BluetoothGattDescriptor descriptor, int status) {
            if (blocking)unlockBlockingThread(status);
            unlockBlockingThread(status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            if (blocking)unlockBlockingThread(status);
             Log.i(TAG, "onDescriptorWrite: " + descriptor.getUuid().toString());
        }
    };

    private void unlockBlockingThread(int status) {
        this.lastGattStatus = status;
        this.blocking = false;
    }

    public void broadcastUpdate(final String action, final String address,
                                final int status) {
        Log.i("Print","Action : "+action + " Address : "+address+" Status :"+status);
        Log.e("BluetoothLeService","INSIDE");
        BluetoothLeService.this.callback.broadcastBle(action,address,status);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic, final int status) {}
    public void sendBroadCast(Context context){
        Intent intent = new Intent(context, BlePickPopUpView.class);
        context.sendBroadcast(intent);
    }

    public boolean checkGatt() {
        if (mainActivity.mBluetoothAdapter == null) {
             Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        if (mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized");
            return false;
        }
        if (this.blocking) {
            Log.d(TAG,"Cannot start operation : Blocked");
            return false;
        }
        return true;

    }

    /**
     * Manage the BLE service
     */
    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that
        // BluetoothGatt.close() is called
        // such that resources are cleaned up properly. In this particular example,
        // close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }
    private final IBinder binder = new LocalBinder();
    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public void initBleCallback(BleCallbackInterface callback , Activity activity){
        this.activity = activity;
        this.callback = callback;
    }
    public boolean initialize(BluetoothLeService bleService , BluetoothManager bluetoothManager , BluetoothAdapter bluetoothAdapter ,Activity activity) {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        mbleService =bleService;
        mBluetoothManager = bluetoothManager;
        mainActivity = (MainActivity) activity;
        mainActivity.bleBroadcast();
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                 Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        bluetoothAdapter = mBluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        procQueue = new LinkedList<bleRequest>();
        nonBlockQueue = new LinkedList<bleRequest>();
        mCurrentConnectionPriority = BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
        return true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Log.i(TAG, "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
      //  this.initialize();
        Log.d("BluetoothLeService","Started BluetoothLeService");
        return START_STICKY;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if ( mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public int writeCharacteristic(
            BluetoothGattCharacteristic characteristic, byte b) {
        byte[] val = new byte[1];
        val[0] = b;
        characteristic.setValue(val);

        bleRequest req = new bleRequest();
        req.status = bleRequestStatus.not_queued;
        req.characteristic = characteristic;
        req.operation = bleRequestOperation.wrBlocking;
        addRequestToQueue(req);
        boolean finished = false;
        while (!finished) {
            bleRequestStatus stat = pollForStatusofRequest(req);
            if (stat == bleRequestStatus.done) {
                finished = true;
                return 0;
            }
            else if (stat == bleRequestStatus.timeout) {
                finished = true;
                return -3;
            }
        }
        return -2;
    }
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic,
                                    String data) {
        Log.i(TAG, "characteristic " + characteristic.toString());
        try {
            Log.i(TAG, "data " + URLEncoder.encode(data, "utf-8"));

            characteristic.setValue(URLEncoder.encode(data, "utf-8"));
            mBluetoothGatt.writeCharacteristic(characteristic);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public boolean writeCharacteristicNonBlock(BluetoothGattCharacteristic characteristic) {
        bleRequest req = new bleRequest();
        req.status = bleRequestStatus.not_queued;
        req.characteristic = characteristic;
        req.operation = bleRequestOperation.wr;
        addRequestToQueue(req);
        return true;
    }

    /**
     * Retrieves the number of GATT services on the connected device. This should
     * be invoked only after {@code BluetoothGatt#discoverServices()} completes
     * successfully.
     *
     * @return A {@code integer} number of supported services.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public int getNumServices() {
        if (mBluetoothGatt == null)
            return 0;

        return mBluetoothGatt.getServices().size();
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This
     * should be invoked only after {@code BluetoothGatt#discoverServices()}
     * completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null)
            return null;

        return mBluetoothGatt.getServices();
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address
     *          The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The
     *         connection result is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public boolean connect(final String address , BluetoothAdapter mBluetoothAdapter , BluetoothManager mBluetoothManager) {
        if (mBluetoothAdapter == null || address == null) {
             Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        int connectionState = mBluetoothManager.getConnectionState(device,BluetoothProfile.GATT);
        if (mBluetoothManager.getConnectionState(device,BluetoothProfile.GATT) == BluetoothProfile.STATE_DISCONNECTED) {
            // Previously connected device. Try to reconnect.
			if (mBluetoothDeviceAddress != null
			    && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
				// Log.d(TAG, "Re-use GATT connection");
				if (mBluetoothGatt.connect()) {
				    mConnectionState = Constants.STATE_CONNECTING;
                    return true;
				} else {
					// Log.w(TAG, "GATT re-connect failed.");
					return false;
				}
			}
            if (device == null) {
                Log.w(TAG, "Device not found.  Unable to connect.");
                return false;
            }
            // We want to directly connect to the device, so we are setting the
            // autoConnect parameter to false.
             Log.d(TAG, "Create a new GATT connection.");

            mBluetoothGatt = device.connectGatt(this, false, mGattCallbacks);
            mBluetoothDeviceAddress = address;
            mConnectionState = Constants.STATE_CONNECTING;
            return true;
         //   getSupportedGattServices();
        } else {
            if(connectionState == BluetoothProfile.STATE_CONNECTED ) {
           //     displayGattServices(getSupportedGattServices());
                broadcastUpdate(ACTION_GATT_CONNECTED, address, connectionState);
                Log.w(TAG, "Attempt to connect in state: " + connectionState);
                return false;
            }
//            if(connectionState == 0 ){
//          //      broadcastUpdate(ACTION_GATT_DISCONNECTED, address, connectionState);
//            }
        }
        return false;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The
     * disconnection result is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect(String address) {
        if (mainActivity.mBluetoothAdapter == null) {
            Log.w(TAG, "disconnect: BluetoothAdapter not initialized");
            return;
        }
        try {
            final BluetoothDevice device = mainActivity.mBluetoothAdapter.getRemoteDevice(address);
            int connectionState = mBluetoothManager.getConnectionState(device,
                    BluetoothProfile.GATT);

            if (mBluetoothGatt != null) {
                if (connectionState != BluetoothProfile.STATE_DISCONNECTED) {
                    mBluetoothGatt.disconnect();
                    Log.d(TAG, "Disconnected " + device.getName() + " (" + device.getAddress() + ")");
                } else {
                    Log.w(TAG, "Attempt to disconnect in state: " + connectionState);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * After using a given BLE device, the app must call this method to ensure
     * resources are released properly.
     */
    public void close() {
        if (mBluetoothGatt != null) {
             Log.i(TAG, "close");
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    public int numConnectedDevices() {
        int n = 0;

        if (mBluetoothGatt != null) {
            List<BluetoothDevice> devList;
            devList = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
            n = devList.size();
        }
        return n;
    }

    //
    // Utility functions
    //
    public static BluetoothGatt getBtGatt() {
        return mbleService.mBluetoothGatt;
    }

    public static BluetoothManager getBtManager() {
        return mbleService.mBluetoothManager;
    }

    public static BluetoothLeService getInstance() {
        return mbleService;
    }

    public void waitIdle(int timeout) {
        while (timeout-- > 0) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean refreshDeviceCache(BluetoothGatt gatt){
        try {
            BluetoothGatt localBluetoothGatt = gatt;
            Method localMethod = localBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
            if (localMethod != null) {
                boolean bool = ((Boolean) localMethod.invoke(localBluetoothGatt, new Object[0])).booleanValue();
                return bool;
            }
        }
        catch (Exception localException) {
            Log.e(TAG, "An exception occured while refreshing device");
        }
        return false;
    }

    public void timedDisconnect() {
        disconnectTimerTask disconnectionTimerTask;
        this.disconnectionTimer = new Timer();
        disconnectionTimerTask = new disconnectTimerTask(this);
        this.disconnectionTimer.schedule(disconnectionTimerTask, 20000);
    }
    public void abortTimedDisconnect() {
        if (this.disconnectionTimer != null) {
            this.disconnectionTimer.cancel();
        }
    }
    class disconnectTimerTask extends TimerTask {
        BluetoothLeService param;

        public disconnectTimerTask(final BluetoothLeService param) {
            this.param = param;
        }

        @Override
        public void run() {
            this.param.disconnect(mBluetoothDeviceAddress);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public boolean requestConnectionPriority(int connectionPriority) {
        mCurrentConnectionPriority = connectionPriority;
        return this.mBluetoothGatt.requestConnectionPriority(connectionPriority);
    }

    public boolean addRequestToQueue(bleRequest req) {
        lock.lock();
        if (procQueue.peekLast() != null) {
            req.id = procQueue.peek().id++;
        }
        else {
            req.id = 0;
            procQueue.add(req);
        }
        lock.unlock();
        return true;
    }

    public bleRequestStatus pollForStatusofRequest(bleRequest req) {
        lock.lock();
        if (req == curBleRequest) {
            bleRequestStatus stat = curBleRequest.status;
            if (stat == bleRequestStatus.done) {
                curBleRequest = null;
            }
            if (stat == bleRequestStatus.timeout) {
                curBleRequest = null;
            }
            lock.unlock();
            return stat;
        }
        else {
            lock.unlock();
            return bleRequestStatus.no_such_request;
        }
    }
    private void executeQueue() {
        // Everything here is done on the queue
        lock.lock();
        if (curBleRequest != null) {
            Log.d(TAG, "executeQueue, curBleRequest running");
            try {
                curBleRequest.curTimeout++;
                if (curBleRequest.curTimeout > GATT_TIMEOUT) {
                    curBleRequest.status = bleRequestStatus.timeout;
                    curBleRequest = null;
                }
                Thread.sleep(10, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
            lock.unlock();
            return;
        }
        if (procQueue == null) {
            lock.unlock();
            return;
        }
        if (procQueue.size() == 0) {
            lock.unlock();
            return;
        }
        bleRequest procReq = procQueue.removeFirst();

        switch (procReq.operation) {
            case rd:
                //Read, do non blocking read
                break;
            case rdBlocking:
                //Normal (blocking) read
                if (procReq.timeout == 0) {
                    procReq.timeout = GATT_TIMEOUT;
                }
                procReq.curTimeout = 0;
                curBleRequest = procReq;
                int stat = sendBlockingReadRequest(procReq);
                if (stat == -2) {
                    Log.d(TAG,"executeQueue rdBlocking: error, BLE was busy or device disconnected");
                    lock.unlock();
                    return;
                }
                break;
            case wr:
                //Write, do non blocking write (Ex: OAD)
                nonBlockQueue.add(procReq);
                sendNonBlockingWriteRequest(procReq);
                break;
            case wrBlocking:
                //Normal (blocking) write
                if (procReq.timeout == 0) {
                    procReq.timeout = GATT_TIMEOUT;
                }
                curBleRequest = procReq;
                stat = sendBlockingWriteRequest(procReq);
                if (stat == -2) {
                    Log.d(TAG,"executeQueue wrBlocking: error, BLE was busy or device disconnected");
                    lock.unlock();
                    return;
                }
                break;
            case nsBlocking:
                if (procReq.timeout == 0) {
                    procReq.timeout = GATT_TIMEOUT;
                }
                curBleRequest = procReq;
                stat = sendBlockingNotifySetting(procReq);
                if (stat == -2) {
                    Log.d(TAG,"executeQueue nsBlocking: error, BLE was busy or device disconnected");
                    lock.unlock();
                    return;
                }
                break;
            default:
                break;

        }
        lock.unlock();
    }

    public int sendNonBlockingReadRequest(bleRequest request) {
        request.status = bleRequestStatus.processing;
        if (!checkGatt()) {
            request.status = bleRequestStatus.failed;
            return -2;
        }
        mBluetoothGatt.readCharacteristic(request.characteristic);
        return 0;
    }

    public int sendNonBlockingWriteRequest(bleRequest request) {
        request.status = bleRequestStatus.processing;
        if (!checkGatt()) {
            request.status = bleRequestStatus.failed;
            return -2;
        }
        mBluetoothGatt.writeCharacteristic(request.characteristic);
        return 0;
    }

    public int sendBlockingReadRequest(bleRequest request) {
        request.status = bleRequestStatus.processing;
        int timeout = 0;
        if (!checkGatt()) {
            request.status = bleRequestStatus.failed;
            return -2;
        }
        if (request.characteristic == null) {

        }
        else {
            mBluetoothGatt.readCharacteristic(request.characteristic);
            this.blocking = true; // Set read to be blocking
            while (this.blocking) {
                timeout++;
                waitIdle(1);
                if (timeout > GATT_TIMEOUT) {
                    this.blocking = false;
                    request.status = bleRequestStatus.timeout;
                    return -1;
                }
            }
        }
        request.status = bleRequestStatus.done;
        return lastGattStatus;
    }

    public int sendBlockingWriteRequest(bleRequest request) {
        request.status = bleRequestStatus.processing;
        int timeout = 0;
        if (!checkGatt()) {
            request.status = bleRequestStatus.failed;
            return -2;
        }
        mBluetoothGatt.writeCharacteristic(request.characteristic);
        this.blocking = true; // Set read to be blocking
        while (this.blocking) {
            timeout ++;
            waitIdle(1);
            if (timeout > GATT_TIMEOUT) {this.blocking = false; request.status = bleRequestStatus.timeout; return -1;}
        }
        request.status = bleRequestStatus.done;
        return lastGattStatus;
    }
    public int sendBlockingNotifySetting(bleRequest request) {
        request.status = bleRequestStatus.processing;
        int timeout = 0;
        if (request.characteristic == null) {
            return -1;
        }
        if (!checkGatt())
            return -2;

        if (mBluetoothGatt.setCharacteristicNotification(request.characteristic, request.notifyenable)) {


        }
        return -3; // Set notification to android was wrong ...
    }
    public String getConnectedDeviceAddress() {
        return this.mBluetoothDeviceAddress;
    }
    /**
     * Request a write on a given {@code BluetoothGattCharacteristic}. The write result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *  @param characteristic The characteristic to write on.
     * @param value
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value) {

        characteristic.setValue(value);
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if ( mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }
}

