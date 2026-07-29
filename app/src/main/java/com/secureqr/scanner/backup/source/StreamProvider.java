package com.secureqr.scanner.backup.source;

import java.io.InputStream;

/** Functional alias for components that need a fresh stream for each pass. */
public interface StreamProvider {
    InputStream openStream() throws Exception;
}
