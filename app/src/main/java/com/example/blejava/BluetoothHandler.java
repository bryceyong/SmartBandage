package com.example.blejava;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.welie.blessed.BluetoothBytesParser;
import com.welie.blessed.BluetoothCentral;
import com.welie.blessed.BluetoothCentralCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;

import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT8;
import static com.welie.blessed.BluetoothBytesParser.bytes2String;
import static com.welie.blessed.BluetoothPeripheral.GATT_SUCCESS;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import timber.log.Timber;

public class BluetoothHandler {

    //Intent constants
    public static final String MEASUREMENT_HEARTRATE = "blessed.measurement.heartrate";
    public static final String MEASUREMENT_HEARTRATE_EXTRA = "blessed.measurement.heartrate.extra";
    public static final String MEASUREMENT_EXTRA_PERIPHERAL = "blessed.measurement.peripheral";

    //UUID
    //Device Info
    private static final UUID DIS_SERVICE_UUID = UUID.fromString("5C5922DE-273C-1F03-7EEB-98CB1ED16E9B");
    private static final UUID MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    private static final UUID MODEL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb");
    //Battery Info
    private static final UUID BTS_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");
    // UUIDs for the Heart Rate service (HRS)
    private static final UUID HRS_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    private static final UUID HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");

    //Local Variables
    public BluetoothCentral central;
    @SuppressLint("StaticFieldLeak")
    private static BluetoothHandler instance = null;
    private final Context context;
    private final Handler handler = new Handler();

    //Callback functions
    private final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {
        @Override
        public void onServicesDiscovered(@NotNull BluetoothPeripheral peripheral) {
            Timber.i("discovered services");

            //request a higher MTU (Maximum Transmission Unit (MTU) is the maximum length of an ATT packet)
            peripheral.requestMtu(185);

            // Request a new connection priority
            peripheral.requestConnectionPriority(CONNECTION_PRIORITY_HIGH);

            //read manufacturer model num
            if(peripheral.getService(DIS_SERVICE_UUID) != null) {
                BluetoothGattCharacteristic manufacturerCharacteristic = peripheral.getCharacteristic(DIS_SERVICE_UUID, MANUFACTURER_NAME_CHARACTERISTIC_UUID);
                if (manufacturerCharacteristic != null) {
                    peripheral.readCharacteristic(manufacturerCharacteristic);
                }
                BluetoothGattCharacteristic modelCharacteristic = peripheral.getCharacteristic(DIS_SERVICE_UUID, MODEL_NUMBER_CHARACTERISTIC_UUID);
                if (modelCharacteristic != null) {
                    peripheral.readCharacteristic(modelCharacteristic);
                }
            }

            //Read battery status service using Battery Service
            if(peripheral.getService(BTS_SERVICE_UUID) != null) {
                BluetoothGattCharacteristic batteryCharacteristic = peripheral.getCharacteristic(BTS_SERVICE_UUID, BATTERY_LEVEL_CHARACTERISTIC_UUID);
                if ( batteryCharacteristic != null) {
                    peripheral.readCharacteristic(batteryCharacteristic);
                }
            }


            // Turn on notification for Heart Rate  Service
            if(peripheral.getService(HRS_SERVICE_UUID) != null) {
                BluetoothGattCharacteristic heartrateCharacteristic = peripheral.getCharacteristic(HRS_SERVICE_UUID, HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID);
                if (heartrateCharacteristic != null) {
                    peripheral.setNotify(heartrateCharacteristic, true);
                }
            }

        }

        @Override
        public void onNotificationStateUpdate(@NotNull BluetoothPeripheral peripheral, @NotNull BluetoothGattCharacteristic characteristic, int status) {
            if( status == GATT_SUCCESS) {
                if(peripheral.isNotifying(characteristic)) {
                    Timber.i("SUCCESS: Notify set to 'on' for %s", characteristic.getUuid());
                } else {
                    Timber.i("SUCCESS: Notify set to 'off' for %s", characteristic.getUuid());
                }
            } else {
                Timber.e("ERROR: Changing notification state failed for %s", characteristic.getUuid());
            }
        }

        @Override
        public void onCharacteristicWrite(@NotNull BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, int status) {
            if( status == GATT_SUCCESS) {
                Timber.i("SUCCESS: Writing <%s> to <%s>", bytes2String(value), characteristic.getUuid().toString());
            } else {
                Timber.i("ERROR: Failed writing <%s> to <%s>", bytes2String(value), characteristic.getUuid().toString());
            }
        }

        @Override
        public void onCharacteristicUpdate(@NotNull BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, int status) {
            if(status != GATT_SUCCESS) return;
            UUID characteristicUUID = characteristic.getUuid();
            BluetoothBytesParser parser = new BluetoothBytesParser(value);

            if(characteristicUUID.equals(BATTERY_LEVEL_CHARACTERISTIC_UUID)) {
                int batteryLevel = parser.getIntValue(FORMAT_UINT8);
                Timber.i("Received battery level %d%%", batteryLevel);
            } else if(characteristicUUID.equals(HEARTRATE_MEASUREMENT_CHARACTERISTIC_UUID)) {
                HeartRateMeasurement measurement = new HeartRateMeasurement(value);
                Intent intent = new Intent(MEASUREMENT_HEARTRATE);
                intent.putExtra(MEASUREMENT_HEARTRATE_EXTRA, measurement);
                intent.putExtra(MEASUREMENT_EXTRA_PERIPHERAL, peripheral.getAddress());
                context.sendBroadcast(intent);
                Timber.d("%s", measurement);
            }


        }
        @Override
        public void onMtuChanged(@NotNull BluetoothPeripheral peripheral, int mtu, int status) {
            Timber.i("new MTU set: %d", mtu);
        }


    };

    public static synchronized BluetoothHandler getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothHandler(context.getApplicationContext());
        }
        return instance;
    }

    private BluetoothHandler(Context context) {
        this.context = context;

        Timber.plant(new Timber.DebugTree());

        BluetoothCentralCallback bluetoothCentralCallback = new BluetoothCentralCallback() {
            @Override
            public void onDiscoveredPeripheral(@NotNull BluetoothPeripheral peripheral, @NotNull ScanResult scanResult) {
                central.stopScan();
                central.connectPeripheral(peripheral, peripheralCallback);
            }

        };
        central = new BluetoothCentral(context, bluetoothCentralCallback, new Handler());

        //Define device UUID
        String peripheralNames = "Movesense 191230000391";
        central.startPairingPopupHack();
        central.scanForPeripheralsWithNames(new String[]{peripheralNames});

    }


}