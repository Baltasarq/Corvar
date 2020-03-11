package com.devbaltasarq.corvar.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.devbaltasarq.corvar.R;
import com.devbaltasarq.corvar.core.Result;
import com.devbaltasarq.corvar.core.Tag;
import com.devbaltasarq.corvar.core.bluetooth.BluetoothDeviceWrapper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Adapter for holding hrDevices found through deviceSearch.
public class ResultListAdapter extends ArrayAdapter<File> {
    public ResultListAdapter(@NonNull Context cntxt, @NonNull File[] entries)
    {
        super( cntxt, 0, entries );
    }

    @Override
    public @NonNull View getView(int i, View view, @NonNull ViewGroup viewGroup)
    {
        final LayoutInflater inflater = LayoutInflater.from( this.getContext() );

        if ( view == null ) {
            view = inflater.inflate( R.layout.listview_result_entry, null );
        }

        final String FILE_NAME = this.getItem( i ).getName();
        final TextView LBL_TAG = view.findViewById( R.id.lblTag );
        final TextView LBL_TIME = view.findViewById( R.id.lblTime );
        final SimpleDateFormat FORMATTER = new SimpleDateFormat( "dd/MM/yyyy HH:mm:ss",
                                                                  Locale.getDefault() );

        long timeInMillis = Result.parseTimeFromName( FILE_NAME );
        Calendar time = Calendar.getInstance();

        time.setTimeInMillis( timeInMillis );

        LBL_TAG.setText( Result.parseTagFromName( FILE_NAME ) );
        LBL_TIME.setText( FORMATTER.format( time.getTime() ) );
        return view;
    }
}
