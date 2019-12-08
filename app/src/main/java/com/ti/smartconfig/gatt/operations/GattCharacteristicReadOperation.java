package com.ti.smartconfig.gatt.operations;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import com.ti.smartconfig.gatt.GattCharacteristicReadCallback;

import java.util.UUID;

public class GattCharacteristicReadOperation extends GattOperation {
    private final UUID mService;
    private final UUID mCharacteristic;
    private final GattCharacteristicReadCallback mCallback;
    public GattCharacteristicReadOperation(BluetoothDevice device, UUID service, UUID characteristic, GattCharacteristicReadCallback callback) {
        super(device, OperationType.OPERATION_CHAR_READ);
        mService = service;
        mCharacteristic = characteristic;
        mCallback = callback;
    }
    @Override
    public void execute(BluetoothGatt gatt) {
        Log.d("GattCharReadCallback", "writing to " + mCharacteristic);
        BluetoothGattCharacteristic characteristic = gatt.getService(mService).getCharacteristic(mCharacteristic);
        gatt.readCharacteristic(characteristic);
    }
    @Override
    public boolean hasAvailableCompletionCallback() {
        return true;
    }
    public void onRead(BluetoothGattCharacteristic characteristic) {
        mCallback.call(characteristic.getValue());
    }
}