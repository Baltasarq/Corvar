package com.devbaltasarq.corvar.ui;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.devbaltasarq.corvar.R;
import com.devbaltasarq.corvar.core.FileNameAdapter;
import com.devbaltasarq.corvar.core.Orm;
import com.devbaltasarq.corvar.core.bluetooth.BluetoothDeviceWrapper;
import com.devbaltasarq.corvar.core.bluetooth.BluetoothHRFiltering;
import com.devbaltasarq.corvar.core.bluetooth.BluetoothUtils;
import com.devbaltasarq.corvar.core.bluetooth.DemoBluetoothDevice;
import com.devbaltasarq.corvar.core.bluetooth.ScannerUI;
import com.devbaltasarq.corvar.ui.adapters.BtDeviceListAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends BasicActivity implements ScannerUI {
    private final static String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int RQC_ENABLE_BT = 367;
    private static final int RQC_ASK_CLEARANCE_FOR_BLUETOOTH = 389;
    private static final int MAX_SCAN_PERIOD = 60000;

    @Override
    protected void onStart()
    {
        super.onStart();

        Orm.init( this.getApplicationContext(), FileNameAdapter.get() );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        this.setContentView( R.layout.activity_main );
        final Toolbar TOOLBAR = this.findViewById( R.id.toolbar );
        this.setSupportActionBar( TOOLBAR );

        // Init
        this.bluetoothDefinitelyNotAvailable = false;
        this.handler = new Handler();
        this.deviceSearch = false;
        this.hrDevices = new ArrayList<>();

        // Build view
        this.createHRDeviceList();

        // Listeners
        final FloatingActionButton FB_MEASURE = this.findViewById( R.id.fbMEASURE );
        final ImageButton BT_SCAN = this.findViewById( R.id.btScan );
        final ImageButton BT_RESULTS = this.findViewById( R.id.btResults );
        final ImageButton BT_MEASURE = this.findViewById( R.id.btMeasure );
        final ListView LV_DEVICES = this.findViewById( R.id.lvDevices );

        View.OnClickListener handlerMeasure = (View v) -> {
            MainActivity.this.goTo( R.id.action_measure_heartvar );
        };

        FB_MEASURE.setOnClickListener( handlerMeasure );
        BT_MEASURE.setOnClickListener( handlerMeasure );

        BT_SCAN.setOnClickListener( (v) -> {
            MainActivity.this.goTo( R.id.action_search_devices );
        });

        BT_RESULTS.setOnClickListener( (v) -> {
            MainActivity.this.goTo( R.id.action_chk_results );
        });

        LV_DEVICES.setOnItemClickListener( (v, a, i, l) -> {
            MainActivity.this.setChosenDevice( this.hrDevices.get( i ) );
        });
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        this.cancelAllConnections( false );

        if ( this.actionDeviceDiscovery != null ) {
            try {
                this.unregisterReceiver( this.actionDeviceDiscovery );
            } catch(IllegalArgumentException exc) {
                Log.e( LOG_TAG, "the receiver for device discovery was not registered." );
            }
        }

        this.clearDeviceListView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        this.goTo( item.getItemId() );
        return super.onOptionsItemSelected( item );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult( requestCode, resultCode, data );

        switch ( requestCode ) {
            case RQC_ENABLE_BT:
                this.configBtLaunched = false;

                if ( resultCode != Activity.RESULT_OK ) {
                    this.bluetoothDefinitelyNotAvailable = true;
                    this.showStatus( this.getString( R.string.error_no_bluetooth_supported ) );
                    this.disableFurtherScan();
                }
                break;
            default:
                final String MSG = "unknown request code was not managed: " + requestCode;

                Log.e( LOG_TAG, MSG );
                throw new InternalError( MSG );

        }

        return;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult( requestCode, permissions, grantResults );

        switch( requestCode ) {
            case RQC_ASK_CLEARANCE_FOR_BLUETOOTH:
                int totalGrants = 0;

                for(int result: grantResults) {
                    if ( result == PackageManager.PERMISSION_GRANTED ) {
                        ++totalGrants;
                    }
                }

                if ( totalGrants == grantResults.length ) {
                    this.doStartScanning();
                } else {
                    final AlertDialog.Builder DLG = new AlertDialog.Builder( this );

                    DLG.setMessage( R.string.error_no_bluetooth_permissions );
                    DLG.setPositiveButton( R.string.lbl_back, null );

                    DLG.create().show();
                    this.bluetoothDefinitelyNotAvailable = true;
                }
                break;
            default:
                final String MSG = "unknown permission request code was not managed" + requestCode;

                Log.e( LOG_TAG, MSG );
                throw new InternalError( MSG );
        }

        return;
    }

    private void showStatus(String msg)
    {
        this.showStatus( LOG_TAG, msg );
    }

    private void goTo(int id)
    {
        switch( id ) {
            case R.id.action_search_devices:
                if ( !this.isLookingForDevices()
                  && !this.bluetoothDefinitelyNotAvailable )
                {
                    this.initBluetooth();

                    // Ensures Bluetooth is enabled on the device.
                    // If Bluetooth is not currently enabled,
                    // fire an intent to display a dialog asking the user
                    // to grant permission to enable it.
                    if ( this.bluetoothAdapter == null ) {
                        this.launchBtConfigPage();
                    }
                    else
                    if ( !this.bluetoothAdapter.isEnabled() ) {
                        this.launchBtConfigPage();
                    } else {
                        // Scans health devices
                        this.startScanning();
                    }
                }
                break;
            case R.id.action_measure_heartvar:
                this.startActivity( new Intent( this, MeasureDirectorActivity.class ) );
                break;
            case R.id.action_chk_results:
                this.startActivity( new Intent( this, CheckResultsActivity.class ) );
                break;
        }

        return;
    }

    /** Creates the list of hrDevices. */
    private void createHRDeviceList()
    {
        final ListView lvDevices = this.findViewById( R.id.lvDevices );

        // Create lists, if needed
        if ( this.hrDevices == null ) {
            this.hrDevices = new ArrayList<>();
        }

        if ( this.discoveredDevices == null ) {
            this.discoveredDevices = new ArrayList<>();
        }

        if ( this.addrFound == null ) {
            this.addrFound = new HashSet<>();
        }

        if ( this.bluetoothFiltering == null ) {
            this.bluetoothFiltering = new BluetoothHRFiltering( this );
        }

        // Set chosen device
        if ( demoDevice == null ) {
            demoDevice = new BluetoothDeviceWrapper( DemoBluetoothDevice.get() );
        }

        if ( chosenBtDevice == null ) {
            chosenBtDevice = demoDevice;
        }

        this.setChosenDevice( chosenBtDevice );

        // Clear devices found list
        this.devicesListAdapter = new BtDeviceListAdapter( this, this.hrDevices );
        lvDevices.setAdapter( this.devicesListAdapter );
        this.clearDeviceListView();
    }

    /** Removes all hrDevices in the list. */
    private void clearDeviceListView()
    {
        this.addrFound.clear();
        this.discoveredDevices.clear();
        this.devicesListAdapter.clear();
        this.devicesListAdapter.add( demoDevice );

        if ( chosenBtDevice != null
          && !chosenBtDevice.isDemo() )
        {
            this.addDeviceToListView( chosenBtDevice.getDevice() );
        }

        return;
    }

    /** Takes care of all open Gatt connections. */
    private void closeAllGattConnections()
    {
        if ( this.bluetoothFiltering != null ) {
            this.bluetoothFiltering.closeAllGattConnections();
        }

        return;
    }

    /** Adds a given device to the list.
     * @param btDevice the bluetooth LE device.
     */
    @Override
    public void onDeviceFound(BluetoothDevice btDevice)
    {
        final String addr = btDevice.getAddress();

        if ( btDevice != null
          && btDevice.getName() != null
          && btDevice.getAddress() != null
          && !this.addrFound.contains( addr ) )
        {
            this.addrFound.add( addr );
            this.discoveredDevices.add( btDevice );

            MainActivity.this.runOnUiThread( () -> {
                this.showStatus( btDevice.getName()
                        + " " + this.getString( R.string.msg_device_found ).toLowerCase()
                        + "..." ); });
        }

        return;
    }

    /** Selects the device the user wants to employ.
     * @param newChosenDevice The device the user wants.
     * @see BluetoothDeviceWrapper
     */
    private void setChosenDevice(BluetoothDeviceWrapper newChosenDevice)
    {
        final TextView LBL_CHOSEN_DEVICE = this.findViewById( R.id.lbl_chosen_device );

        assert newChosenDevice != null: "FATAL: newChosenDevice is null!!!";

        chosenBtDevice = newChosenDevice;
        LBL_CHOSEN_DEVICE.setText( newChosenDevice.getName() );
    }

    /** Initializes Bluetooth. */
    private void initBluetooth()
    {
        // Getting the Bluetooth adapter
        this.bluetoothAdapter = BluetoothUtils.getBluetoothAdapter( this );

        if ( this.bluetoothAdapter != null ) {
            // Register the BroadcastReceiver
            IntentFilter discoverer = new IntentFilter( BluetoothDevice.ACTION_FOUND );
            discoverer.addAction( BluetoothAdapter.ACTION_DISCOVERY_STARTED );
            discoverer.addAction( BluetoothAdapter.ACTION_DISCOVERY_FINISHED );

            this.actionDeviceDiscovery = BluetoothUtils.createActionDeviceDiscoveryReceiver( this );
            this.registerReceiver( this.actionDeviceDiscovery, discoverer );
        } else {
            this.disableFurtherScan();
        }

        return;
    }

    /** @return whether the device is looking for (scanning and filtering), hrDevices or not. */
    public boolean isLookingForDevices()
    {
        return this.deviceSearch;
    }

    /** Cancels the discovering. */
    private void cancelDiscovery()
    {
        if ( this.bluetoothAdapter != null
          && this.bluetoothAdapter.isDiscovering() )
        {
            this.bluetoothAdapter.cancelDiscovery();
        }

        return;
    }

    @Override
    public void startScanning()
    {
        if ( !this.bluetoothDefinitelyNotAvailable ) {
            final String[] ALL_PERMISSIONS = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };

            final ArrayList<String> PERMISSIONS_TO_ASK_FOR = new ArrayList<>( ALL_PERMISSIONS.length );

            // Check all permissions
            for(String permissionId: ALL_PERMISSIONS) {
                int askAnswerBluetooth = ContextCompat.checkSelfPermission(
                                                                    this.getApplicationContext(),
                                                                    permissionId );

                if ( askAnswerBluetooth != PackageManager.PERMISSION_GRANTED ) {
                    PERMISSIONS_TO_ASK_FOR.add( permissionId );
                }
            }

            // Launch scanning or ask for permissions
            if ( PERMISSIONS_TO_ASK_FOR.size() > 0 ) {
                ActivityCompat.requestPermissions(
                        this,
                        PERMISSIONS_TO_ASK_FOR.toArray( new String[ 0 ] ),
                        RQC_ASK_CLEARANCE_FOR_BLUETOOTH );
            } else {
                this.doStartScanning();
            }

        }

        return;
    }

    /** Launches deviceSearch for a given period of time */
    public void doStartScanning()
    {
        if ( !this.isLookingForDevices() ) {
            this.deviceSearch = true;
            this.closeAllGattConnections();
            this.clearDeviceListView();

            this.handler.postDelayed( () -> {
                if ( this.bluetoothAdapter.isDiscovering() ) {
                    Log.d( LOG_TAG, "Discovery forced finish." );
                    MainActivity.this.stopScanning();
                }
            }, MAX_SCAN_PERIOD );

            this.bluetoothAdapter.startDiscovery();

            MainActivity.this.runOnUiThread( () -> {
                this.disableScanUI();
                this.showStatus( this.getString( R.string.msg_start_scan ) );
            });
        }

        return;
    }

    /** Launches the bluetooth configuration page */
    private void launchBtConfigPage()
    {
        if ( !this.configBtLaunched
          && !this.bluetoothDefinitelyNotAvailable )
        {
            this.configBtLaunched = true;

            MainActivity.this.runOnUiThread( () -> {
                this.showStatus( this.getString( R.string.msg_activate_bluetooth ) );

                final Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
                this.startActivityForResult( enableBtIntent, RQC_ENABLE_BT );
            });
        }

        return;
    }

    /** Stops deviceSearch, starting the filtering for HR devices. */
    @Override
    public void stopScanning()
    {
        if ( this.isLookingForDevices() ) {
            this.cancelDiscovery();

            MainActivity.this.runOnUiThread( () -> {
                    this.showStatus( this.getString( R.string.msg_filtering_by_service )
                                     + "..." );
            });

            if ( this.discoveredDevices.size() >  0 ) {
                this.bluetoothFiltering.filter( this.discoveredDevices.toArray( new BluetoothDevice[ 0 ] ) );
            } else {
                this.filteringFinished();
            }
        }

        return;
    }

    @Override
    public Context getContext()
    {
        return this;
    }

    /** Stops deviceSearch, removing all pending callbacks (that would stop deviceSearch) */
    private void cancelAllConnections(boolean warn)
    {
        this.handler.removeCallbacksAndMessages( null );
        this.cancelDiscovery();
        this.filteringFinished( warn );
    }

    public void addDeviceToListView(BluetoothDevice btDevice)
    {
        final BluetoothDeviceWrapper BTW_DEVICE = new BluetoothDeviceWrapper( btDevice );

        MainActivity.this.runOnUiThread( () -> {
            if ( !this.hrDevices.contains( BTW_DEVICE ) ) {
                this.devicesListAdapter.add( BTW_DEVICE );
                this.showStatus( this.getString( R.string.msg_device_found )
                                  + ": " + btDevice.getName() );
            }
        });
    }

    public void denyAdditionToList(BluetoothDevice btDevice)
    {
        MainActivity.this.runOnUiThread( () -> {
            this.showStatus( this.getString( R.string.error_no_hr )
                             + ": " + btDevice.getName() );
        });
    }

    public void filteringFinished()
    {
        this.filteringFinished( true );
    }

    public void filteringFinished(final boolean warn)
    {
        this.deviceSearch = false;
        this.closeAllGattConnections();

        MainActivity.this.runOnUiThread( () -> {
            if ( warn ) {
                this.showStatus( this.getString( R.string.msg_stop_scan ) );
            }

            this.enableScanUI();
        });
    }

    private void enableScanUI()
    {
        this.setScanControls( true );
    }

    private void disableScanUI()
    {
        this.setScanControls( false );
    }

    private void setScanControls(boolean enabled)
    {
        final ImageButton BT_SCAN = this.findViewById( R.id.btScan );

        BT_SCAN.setVisibility( enabled ? View.VISIBLE : View.INVISIBLE );
    }

    /** Enables the launch button or not. */
    private void disableFurtherScan()
    {
        final ImageButton btScan = this.findViewById( R.id.btScan );

        if ( btScan.getVisibility() != View.INVISIBLE ) {
            this.disableScanUI();

            MainActivity.this.runOnUiThread( () -> {
                btScan.setEnabled( false );
                btScan.setVisibility( View.INVISIBLE );
            });
        }

        return;
    }

    private Set<String> addrFound;
    private BroadcastReceiver actionDeviceDiscovery;
    private BluetoothAdapter bluetoothAdapter;
    private ArrayList<BluetoothDeviceWrapper> hrDevices;
    private ArrayList<BluetoothDevice> discoveredDevices;
    private BtDeviceListAdapter devicesListAdapter;
    private BluetoothHRFiltering bluetoothFiltering;
    private Handler handler;
    private boolean bluetoothDefinitelyNotAvailable;
    private boolean configBtLaunched;
    private boolean deviceSearch;

    public static BluetoothDeviceWrapper chosenBtDevice;
    public static BluetoothDeviceWrapper demoDevice;
}
