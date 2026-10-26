package com.ddangi.calling;

import android.app.Application;

import com.google.firebase.FirebaseApp;

public class CallingApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
    }
}
