package com.ti.smartconfig.gatt;

import android.bluetooth.BluetoothGattCharacteristic;

public interface GattCharacteristicWriteCallback {
    void call(BluetoothGattCharacteristic characteristic, int status);
}