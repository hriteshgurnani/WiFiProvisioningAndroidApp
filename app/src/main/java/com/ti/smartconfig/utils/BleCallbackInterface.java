package com.ti.smartconfig.utils;


public interface BleCallbackInterface {
    void broadcastBle(final String action , final String address,final int status);
    void broadcastBle(final String action , final byte[] address,final int status);
}
