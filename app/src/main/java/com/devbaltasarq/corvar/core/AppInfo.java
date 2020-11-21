// Corvar (c) 2020 Baltasar MIT License <jbgarcia@uvigo.es>


package com.devbaltasarq.corvar.core;


/** The version information for this app. */
public class AppInfo {
    public static final String NAME = "Corvar";
    public static final String VERSION = "v0.0.4 20201121";
    public static final String AUTHOR = "MILE Group";
    public static final String EDITION = "NextGen";

    public static String asShortString()
    {
        return NAME + ' ' + VERSION;
    }

    public static String asString()
    {
        return NAME + ' ' + VERSION
                + " \"" + EDITION + "\" - " + AUTHOR;
    }
}
