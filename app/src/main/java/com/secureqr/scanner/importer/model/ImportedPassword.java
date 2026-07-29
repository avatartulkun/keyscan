package com.secureqr.scanner.importer.model;

import java.io.Serializable;

public final class ImportedPassword implements Serializable {
    private static final long serialVersionUID = 1L;
    public String title;
    public String websiteDomain;
    public String appPackageName;
    public String username;
    public String account;
    public String password;
    public String notes;
    public String folderName;
    public String sourceFormat;
}
