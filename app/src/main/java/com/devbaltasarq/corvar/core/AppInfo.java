package com.devbaltasarq.corvar.core;

/** The version information for this app. */
public class AppInfo {
    public static final String NAME = "Corvar";
    public static final String VERSION = "v0.0.1 20200229";
    public static final String AUTHOR = "MILE Group";
    public static final String EDITION = "Offspring";

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
