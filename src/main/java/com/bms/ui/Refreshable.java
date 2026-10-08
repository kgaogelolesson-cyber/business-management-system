package com.bms.ui;

/** A screen that can reload its data from the database. The main window calls this when its tab is opened. */
public interface Refreshable {
    void refresh();
}
