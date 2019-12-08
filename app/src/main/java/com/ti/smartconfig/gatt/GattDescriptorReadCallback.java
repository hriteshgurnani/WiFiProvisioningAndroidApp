package com.ti.smartconfig.gatt;
public interface GattDescriptorReadCallback {
    void call(byte[] value);
}