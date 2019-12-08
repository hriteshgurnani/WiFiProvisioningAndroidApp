package com.ti.smartconfig.utils;

public interface BroadcastToBleCallbackInterface {
        void broadcastToBleFragment(final String action , final String address,final int status);
        void broadcastToBleFragment(final String action , final byte[] address,final int status);
    }

