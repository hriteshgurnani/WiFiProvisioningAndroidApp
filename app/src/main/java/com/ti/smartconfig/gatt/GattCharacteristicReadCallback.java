package com.ti.smartconfig.gatt;
public interface GattCharacteristicReadCallback {
    void call(byte[] characteristic);
}