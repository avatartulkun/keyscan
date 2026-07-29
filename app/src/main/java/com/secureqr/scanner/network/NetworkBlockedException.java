package com.secureqr.scanner.network;

import java.io.IOException;

public class NetworkBlockedException extends IOException {
    public NetworkBlockedException(String message) {
        super(message);
    }
}
