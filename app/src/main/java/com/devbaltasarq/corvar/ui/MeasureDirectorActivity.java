package com.devbaltasarq.corvar.ui;

import androidx.appcompat.app.AlertDialog;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.devbaltasarq.corvar.R;
import com.devbaltasarq.corvar.core.Duration;
import com.devbaltasarq.corvar.core.Orm;
import com.devbaltasarq.corvar.core.Result;
import com.devbaltasarq.corvar.core.Tag;
import com.devbaltasarq.corvar.core.bluetooth.BleService;
import com.devbaltasarq.corvar.core.bluetooth.BluetoothDeviceWrapper;
import com.devbaltasarq.corvar.core.bluetooth.BluetoothUtils;
import com.devbaltasarq.corvar.core.bluetooth.HRListenerActivity;
import com.devbaltasarq.corvar.core.bluetooth.ServiceConnectionWithStatus;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;

public class MeasureDirectorActivity extends BasicActivity implements HRListenerActivity {
    public static final String LOG_TAG = MeasureDirectorActivity.class.getSimpleName();

    /** Interface for listeners. */
    private interface Listener<T> {
        void handle(T sender);
    }

    /** Represents a chronometer. */
    private static class Chronometer {
        /** Creates a new chronometer with an event handler. */
        public Chronometer(Listener<Chronometer> eventHandler)
        {
            this.handler = new Handler();
            this.eventHandler = eventHandler;
            this.startTime = 0;
            this.stopped = false;
        }

        /** @return the starting time. */
        public long getBase()
        {
            return this.startTime;
        }

        /** @return the elapsed duration, in milliseconds. */
        public long getMillis()
        {
            return SystemClock.elapsedRealtime() - this.startTime;
        }

        /** Resets the current elapsed time with the current real time. */
        public void reset()
        {
            this.reset( SystemClock.elapsedRealtime() );
        }

        /** Resets the current elapsed time with the given time. */
        public void reset(long time)
        {
            this.startTime = time;
        }

        /** Starts the chronometer */
        public void start()
        {
            this.stopped = false;

            this.sendHR = () -> {
                if ( ! this.stopped ) {
                    this.eventHandler.handle( this );
                    this.handler.postDelayed( this.sendHR,1000);
                }
            };

            this.handler.post( this.sendHR );
        }

        /** Eliminates the daemon so the crono is stopped. */
        public void stop()
        {
            this.stopped = true;
            this.handler.removeCallbacks( this.sendHR );
            this.handler.removeCallbacksAndMessages( null );
        }

        private boolean stopped;
        private long startTime;
        private Handler handler;
        private Runnable sendHR;
        private Listener<Chronometer> eventHandler;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        this.setContentView( R.layout.activity_measure_director );

        // Widgets
        final FloatingActionButton FB_LAUNCH_NOW = this.findViewById( R.id.fbLaunchNow );
        final FloatingActionButton FB_STOP = this.findViewById( R.id.fbStop );
        final TextView LBL_CHOSEN_DEVICE = this.findViewById( R.id.lblChosenDevice );
        final EditText ED_TAG = this.findViewById( R.id.edTag );

        // Assign values
        this.orm = Orm.get();
        this.onMeasurement = false;
        this.btDevice = MainActivity.chosenBtDevice;
        ED_TAG.setText( Tag.NO_TAG.toString() );

        // Listeners
        this.chrono = new Chronometer( this::onChronoUpdate );
        FB_LAUNCH_NOW.setOnClickListener( (v) -> this.launchMeasurement() );
        FB_STOP.setOnClickListener( (v) -> this.stopMeasurement() );
        LBL_CHOSEN_DEVICE.setText( this.btDevice.getName() );
    }

    @Override
    public void onResume()
    {
        super.onResume();

        this.getWindow().addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );

        BluetoothUtils.openBluetoothConnections( this,
                this.getString( R.string.lbl_connected ),
                this.getString( R.string.lbl_disconnected ) );

        this.setAbleToLaunch( false );
    }

    @Override
    public void onPause()
    {
        super.onPause();

        this.setAbleToLaunch( false );
        this.chrono.stop();
        this.stopMeasurement();

        this.getWindow().clearFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );

        BluetoothUtils.closeBluetoothConnections( this );
        Log.d( LOG_TAG, "Director finished, stopped chrono, closed connections." );
    }

    @Override
    public void showStatus(String msg)
    {
        this.showStatus( LOG_TAG, msg );
    }

    private void setAbleToLaunch(boolean isAble)
    {
        final FloatingActionButton FB_LAUNCH_NOW = this.findViewById( R.id.fbLaunchNow );
        final TextView LBL_CONN_STATUS = this.findViewById( R.id.lblConnected );
        final TextView LBL_TIME = this.findViewById( R.id.lblTime );
        final TextView LBL_HEARTBEAT = this.findViewById( R.id.lblHeartBeat );
        int visibility;

        if ( isAble ) {
            // "Connected" in "approval" color (e.g green).
            LBL_CONN_STATUS.setText( R.string.lbl_connected );
            LBL_CONN_STATUS.setTextColor( Color.parseColor( "#228B22" ) );
        } else {
            // "Disconnected" in "denial" color (e.g red).
            LBL_CONN_STATUS.setText( R.string.lbl_disconnected );
            LBL_CONN_STATUS.setTextColor( Color.parseColor( "#8B0000" ) );
        }

        // Set the visibility of the launch button
        if ( isAble ) {
            visibility = View.VISIBLE;
        } else {
            visibility = View.GONE;
        }

        this.readyToLaunch = isAble;
        FB_LAUNCH_NOW.setVisibility( visibility );
        LBL_HEARTBEAT.setVisibility( visibility );
        LBL_TIME.setVisibility( visibility );
    }

    /** @return the elapsed time, in seconds, from the start of the experiment. */
    private int getElapsedExperimentSeconds()
    {
        return (int) ( (double) this.chrono.getMillis() / 1000 );
    }

    /** Triggers when the chrono changes. */
    @SuppressWarnings("unused")
    private void onChronoUpdate(Chronometer crono)
    {
        final TextView lblCrono = this.findViewById( R.id.lblTime );
        final int elapsedTimeSeconds = this.getElapsedExperimentSeconds();

        lblCrono.setText( new Duration( elapsedTimeSeconds ).toChronoString() );

        // Stop if the service was disconnected
        if ( !this.serviceConnection.isConnected() ) {
            this.onMeasurement = false;
        }

        return;
    }

    /** Launches the experiment. */
    private void launchMeasurement()
    {
        // Prevent screen rotation
        this.scrOrientationOnMeasurement = this.getRequestedOrientation();
        this.setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_NOSENSOR );
        this.hideKeyboard();

        // Prepare the UI
        final FloatingActionButton FB_LAUNCH = this.findViewById( R.id.fbLaunchNow );
        final FloatingActionButton FB_STOP = this.findViewById( R.id.fbStop );
        final EditText ED_TAG = this.findViewById( R.id.edTag );

        ED_TAG.setEnabled( false );
        FB_LAUNCH.setVisibility( View.INVISIBLE );
        FB_STOP.setVisibility( View.VISIBLE );

        // Create the result object
        this.onMeasurement = true;
        this.resultBuilder = new Result.Builder( System.currentTimeMillis() );

        // Start counting time
        this.chrono.reset();
        this.chrono.start();
        Log.i( LOG_TAG, "Starting..." );

        return;
    }

    /** Stops the experiment. */
    private synchronized void stopMeasurement()
    {
        // Finish for good
        this.chrono.stop();
        this.setRequestedOrientation( this.scrOrientationOnMeasurement );

        if ( this.onMeasurement ) {
            final EditText ED_TAG = this.findViewById( R.id.edTag );

            this.onMeasurement = false;

            // Store results
            if ( this.resultBuilder != null ) {
                final long elapsedMillis = this.chrono.getMillis();
                final Tag TAG = new Tag( ED_TAG.getText().toString() );

                try {
                    this.orm.store( this.resultBuilder.build( TAG, elapsedMillis ) );
                    this.resultBuilder = null;
                    this.showStatus( this.getString( R.string.msg_finished_measurement ) );
                } catch(IOException exc) {
                    this.showStatus( LOG_TAG, "unable to save experiment result" );
                }
            }

            // Warn the experiment has finished
            final AlertDialog.Builder dlg = new AlertDialog.Builder( this );

            dlg.setTitle( R.string.lbl_measurement );
            dlg.setMessage( R.string.msg_finished_measurement );
            dlg.setCancelable( false );
            dlg.setPositiveButton( R.string.lbl_back, (d, i) -> {
                this.finish();
            });

            dlg.create().show();
        }

        return;
    }

    /** Extracts the info received from the HR service.
     * @param intent The key-value extra collection has at least
     *                BleService.HEART_RATE_TAG for heart rate information (as int),
     *                and it can also have BleService.RR_TAG for the rr info (as int).
     */
    @Override
    public void onReceiveBpm(Intent intent)
    {
        final TextView LBL_INSTANT_BPM = this.findViewById( R.id.lblHeartBeat );
        final int HR = intent.getIntExtra( BleService.HEART_RATE_TAG, -1 );
        final int MEAN_RR = intent.getIntExtra( BleService.MEAN_RR_TAG, -1 );
        final int[] RRS = intent.getIntArrayExtra( BleService.RR_TAG );

        LBL_INSTANT_BPM.setText( HR
                                    + this.getString( R.string.lbl_bpm )
                                 + " " + MEAN_RR
                                    + this.getString(  R.string.lbl_rr ) );

        if ( this.inDebugMode()  ) {
            if ( HR >= 0 ) {
                Log.d( LOG_TAG, "HR received: " + HR + "bpm" );
            }

            if ( MEAN_RR >= 0 ) {
                Log.d( LOG_TAG, "Mean RR received: " + MEAN_RR + "millisecs" );
            }

            if ( RRS != null ) {
                final StringBuilder STR_RR = new StringBuilder();

                Log.d( LOG_TAG, "RR's received: " + RRS.length );

                for(int rr: RRS) {
                    STR_RR.append( rr );
                    STR_RR.append( ' ' );
                }

                Log.d( LOG_TAG, "RR's: { " + STR_RR.toString() + "}" );
            } else {
                Log.d( LOG_TAG, "No RR's received." );
            }
        }

        if ( RRS != null ) {
            if ( !this.readyToLaunch ) {
                this.setAbleToLaunch( true );
            } else {
                long time = this.chrono.getMillis();

                for(int rr: RRS) {
                    this.addToResult( new Result.BeatEvent( time, rr ) );

                    time += rr;
                }
            }
        }

        return;
    }

    /** Adds a new event to the result.
     * Since the bpm information comes from one thread and the time from another,
     * this centralized consumer is synchronized.
     * @param beatEvent the time, rr pair to store.
     */
    private synchronized void addToResult(Result.BeatEvent beatEvent)
    {
        if ( this.resultBuilder != null
          && this.onMeasurement )
        {
            this.resultBuilder.add( beatEvent );
        }

        return;
    }

    /** @return the BleService object used by this activity. */
    @Override
    public BleService getService()
    {
        return this.bleService;
    }

    @Override
    public void setService(BleService service)
    {
        this.bleService = service;
    }

    /** @return the BroadcastReceiver used by this activivty. */
    @Override
    public BroadcastReceiver getBroadcastReceiver()
    {
        return this.broadcastReceiver;
    }

    /** @return the device this activity will connect to. */
    @Override
    public BluetoothDeviceWrapper getBtDevice()
    {
        return this.btDevice;
    }

    /** @return the service connection for this activity. */
    @Override
    public ServiceConnectionWithStatus getServiceConnection()
    {
        return this.serviceConnection;
    }

    @Override
    public void setServiceConnection(ServiceConnectionWithStatus serviceConnection)
    {
        this.serviceConnection = serviceConnection;
    }

    @Override
    public void setBroadcastReceiver(BroadcastReceiver broadcastReceiver)
    {
        this.broadcastReceiver = broadcastReceiver;
    }

    public void hideKeyboard()
    {
        InputMethodManager imm = (InputMethodManager) this.getSystemService( Activity.INPUT_METHOD_SERVICE );
        // Find the currently focused view, so we can grab the correct window token from it.
        View view = this.getCurrentFocus();

        // If no view currently has focus, create a new one, just so we can grab a window token from it
        if ( view == null ) {
            view = new View( this );
        }
        imm.hideSoftInputFromWindow( view.getWindowToken(), 0 );
    }

    private int scrOrientationOnMeasurement;
    private boolean readyToLaunch;
    private boolean onMeasurement;

    private Chronometer chrono;
    private Result.Builder resultBuilder;
    private Orm orm;

    private ServiceConnectionWithStatus serviceConnection;
    private BroadcastReceiver broadcastReceiver;
    private BleService bleService;
    private BluetoothDeviceWrapper btDevice;
}
