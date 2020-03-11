package com.devbaltasarq.corvar.ui;

import android.content.Intent;
import android.os.Bundle;

import com.devbaltasarq.corvar.core.Orm;
import com.devbaltasarq.corvar.ui.adapters.ResultListAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.devbaltasarq.corvar.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class CheckResultsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        this.setContentView( R.layout.activity_check_results );
        Toolbar toolbar = findViewById( R.id.toolbar );
        this.setSupportActionBar( toolbar );

        final ListView LV_RESULTS = this.findViewById( R.id.lvResults );

        LV_RESULTS.setOnItemClickListener( (av, v, i, l) -> launchResultViewer( i ) );
    }

    @Override
    public void onResume()
    {
        super.onResume();

        this.prepareResultList();
    }

    private void prepareResultList()
    {
        final ListView LV_RESULTS = this.findViewById( R.id.lvResults );

        this.resultFiles = Orm.get().getAllResults();
        LV_RESULTS.setAdapter( new ResultListAdapter( this, this.resultFiles ) );
    }

    private void launchResultViewer(int pos)
    {
        resultFileName = this.resultFiles[ pos ].getAbsolutePath();
        this.startActivity( new Intent( this, ResultViewer.class ) );
    }

    File[] resultFiles;
    public static String resultFileName;
}
