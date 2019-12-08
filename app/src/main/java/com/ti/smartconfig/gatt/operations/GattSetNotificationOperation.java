package com.ti.smartconfig.gatt.operations;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import com.ti.smartconfig.gatt.GattManager;

import java.util.UUID;

public class GattSetNotificationOperation extends GattOperation {
    GattManager mGattManager;
    private final UUID mServiceUuid;
    private final UUID mCharacteristicUuid;
    private final UUID mDescriptorUuid;
    private final boolean mEnable;
    public GattSetNotificationOperation(BluetoothDevice device, UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid, boolean enable) {
        super(device, OperationType.OPERATION_SET_NOTIFICATION);
//        Injector.inject(Injector.getApplicationContext(), this);
        mServiceUuid = serviceUuid;
        mCharacteristicUuid = characteristicUuid;
        mDescriptorUuid = descriptorUuid;
        mEnable = enable;
    }
    @Override
    public void execute(BluetoothGatt gatt) {
        BluetoothGattCharacteristic characteristic = gatt.getService(mServiceUuid).getCharacteristic(mCharacteristicUuid);
        gatt.setCharacteristicNotification(characteristic, mEnable);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(mDescriptorUuid);
        if (mEnable)
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        else
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }
    @Override
    public boolean hasAvailableCompletionCallback() {
        return true;
    }
}