package com.devbaltasarq.corvar.ui;

import android.os.Bundle;

import com.devbaltasarq.corvar.core.ResultAnalyzer;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.View;
import android.widget.TextView;

import com.devbaltasarq.corvar.R;

public class ResultViewer extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        this.setContentView( R.layout.activity_result_viewer );
        Toolbar toolbar = findViewById( R.id.toolbar );
        this.setSupportActionBar( toolbar );

        final TextView ED_CONTENTS = this.findViewById( R.id.edContents );
        String resultFileName = CheckResultsActivity.resultFileName;

        ED_CONTENTS.setText( this.createReport( resultFileName ) );
    }

    private String createReport(String resultFileName)
    {
        final ResultAnalyzer ANALYZER = new ResultAnalyzer( resultFileName );

        ANALYZER.analyze();
        return ANALYZER.getReport();
    }
}
