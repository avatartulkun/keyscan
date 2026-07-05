package com.secureqr.scanner;

import android.app.Application;

public class BaseApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            System.loadLibrary("sqlcipher");
        } catch (UnsatisfiedLinkError ignored) {
            // SQLCipher may already be loaded by the support factory.
        }
    }
}

