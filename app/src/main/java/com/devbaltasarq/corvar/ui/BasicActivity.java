package com.devbaltasarq.corvar.ui;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class BasicActivity extends AppCompatActivity {
    protected boolean inDebugMode()
    {
        return ( ( this.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE ) != 0 );
    }

    protected void showStatus(String LOG_TAG, String msg)
    {
        Toast.makeText( this, msg, Toast.LENGTH_SHORT ).show();

        if ( this.inDebugMode() ) {
            Log.d( LOG_TAG, msg );
        }
    }
}
