package com.secureqr.scanner.importer.model;

import java.io.Serializable;

public final class ImportedOtp implements Serializable {
    private static final long serialVersionUID = 1L;
    public String issuer;
    public String account;
    public String secret;
    public String algorithm;
    public int digits;
    public int period;
    public String sourceFormat;
}
