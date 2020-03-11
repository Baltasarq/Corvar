package com.devbaltasarq.corvar.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.devbaltasarq.corvar.R;
import com.devbaltasarq.corvar.core.bluetooth.BluetoothDeviceWrapper;

import java.util.List;

// Adapter for holding hrDevices found through deviceSearch.
public class BtDeviceListAdapter extends ArrayAdapter<BluetoothDeviceWrapper> {
    public BtDeviceListAdapter(@NonNull Context cntxt, @NonNull List<BluetoothDeviceWrapper> entries)
    {
        super( cntxt, 0, entries );
    }

    @Override
    public @NonNull View getView(int i, View view, @NonNull ViewGroup viewGroup)
    {
        final LayoutInflater inflater = LayoutInflater.from( this.getContext() );

        if ( view == null ) {
            view = inflater.inflate( R.layout.listview_device_entry, null );
        }

        final BluetoothDeviceWrapper device = this.getItem( i );
        final TextView lblDeviceName = view.findViewById( R.id.lblDeviceName );
        final TextView lblDeviceAddress = view.findViewById( R.id.lblDeviceAddress );
        String deviceName = this.getContext().getString( R.string.error_unknown_device);
        String deviceAddress = "00:00:00:00:00:00";

        // Set device's name, if possible.
        if ( device != null ) {
            deviceName = device.getName();
            deviceAddress = device.getAddress();
        }

        // Check the final name, is it valid?
        if ( deviceName == null
                || deviceName.isEmpty() )
        {
            lblDeviceName.setText( R.string.error_unknown_device);
        } else {
            lblDeviceName.setText( deviceName );
        }

        lblDeviceAddress.setText( deviceAddress );
        return view;
    }
}
