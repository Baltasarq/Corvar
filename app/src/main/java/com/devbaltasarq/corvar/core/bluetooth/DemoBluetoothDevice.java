package com.devbaltasarq.corvar.core.bluetooth;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.util.Log;

import com.devbaltasarq.corvar.BuildConfig;

import java.util.Random;
import java.util.UUID;


public final class DemoBluetoothDevice {
    private static final String LogTag = DemoBluetoothDevice.class.getSimpleName();
    private static final String DemoDeviceName = "demo BT device";
    private static final String DemoDeviceAddr = "00:00:00:00:00:00";

    private static final int MIN_HR = 60;
    private static final int MAX_HR = 90;

    /** Creates the bluetooth demo device. */
    private DemoBluetoothDevice()
    {
        this.handler = new Handler();
    }

    /** @return the name of the demo device. */
    public String getName()
    {
        return DemoDeviceName;
    }

    /** @return the address of the demo device. */
    public String getAddress()
    {
        return DemoDeviceAddr;
    }

    private BluetoothGattCharacteristic createHrGattCharacteristic(int newHR, int newRR)
    {
        // Flags: 16bit heart rate && rr presence
        final byte PROPERTIES_FLAGS = (byte) ( 1 << 4 | 1 );
        final UUID UUID_HR_MEASUREMENT_CHR = BluetoothUtils.UUID_HR_MEASUREMENT_CHR;
        final BluetoothGattCharacteristic GATT_HR_CHR =
                new BluetoothGattCharacteristic( UUID_HR_MEASUREMENT_CHR,
                        PROPERTIES_FLAGS,
                        BluetoothGattCharacteristic.PERMISSION_READ );

        final int adaptedRR = (int) ( ( (double) newRR / 1000 ) * 1024 );
        final byte[] bValue = new byte[ 5 ];
        bValue[ 0 ] = PROPERTIES_FLAGS;

        GATT_HR_CHR.setValue( bValue );
        GATT_HR_CHR.setValue( newHR, BluetoothGattCharacteristic.FORMAT_UINT16, 1 );
        GATT_HR_CHR.setValue( adaptedRR, BluetoothGattCharacteristic.FORMAT_UINT16, 3 );

        // Some valuable debug info
        if ( BuildConfig.DEBUG ) {
            final byte[] value = GATT_HR_CHR.getValue();
            final StringBuilder bytes = new StringBuilder( value.length * 3 );
            Log.d( LogTag, "Flags: " + GATT_HR_CHR.getProperties() );

            for (byte bt: value) {
                bytes.append( Integer.toString( (int) ( (char) bt ) ) );
                bytes.append( ' ' );
            }

            Log.d( LogTag, "HR: " + newHR + ", RR: " + newRR );
            Log.d( LogTag, "Adapted RR: " + adaptedRR  );
            Log.d( LogTag, "To send: { " + bytes.toString() + "}" );
        }

        return GATT_HR_CHR;
    }

    /** Connects to the demo device. */
    public void connect(final BluetoothGattCallback callBack)
    {
        this.handler = new Handler();
        this.lastRR = 800;

        this.sendHR = () -> {
            final int HR = (int) ( 60 / ( (double) this.lastRR / 1000 ) );

            BluetoothGattCharacteristic GATT_HR_CHR = this.createHrGattCharacteristic( HR, this.lastRR );
            callBack.onCharacteristicRead( null, GATT_HR_CHR, BluetoothGatt.GATT_SUCCESS );

            final int NEW_HR = MIN_HR + ( rnd.nextInt( MAX_HR - MIN_HR ) );
            this.lastRR =  (int) ( ( (double) 60 / NEW_HR ) * 1000 );
            this.handler.postDelayed( this.sendHR, this.lastRR );
        };

        this.handler.post( this.sendHR );
    }

    /** Eliminates the daemon sending fake HR data. */
    public void disconnect()
    {
        this.handler.removeCallbacksAndMessages( null );
    }

    /** @return gets the only copy of the demo device. */
    public static DemoBluetoothDevice get()
    {
        if ( rnd == null ) {
            rnd = new Random();
        }

        if ( device == null ) {
            device = new DemoBluetoothDevice();
        }

        return device;
    }

    private Handler handler;
    private Runnable sendHR;
    private int lastRR;

    private static DemoBluetoothDevice device;
    private static Random rnd;
}
