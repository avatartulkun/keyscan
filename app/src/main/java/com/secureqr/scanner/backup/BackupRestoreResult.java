package com.secureqr.scanner.backup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BackupRestoreResult {
    public int passwordSuccess;
    public int passwordFailed;
    public int otpSuccess;
    public int otpFailed;
    public int vaultSuccess;
    public int vaultFailed;
    public int attachmentCount;
    public int attachmentSuccess;
    public int attachmentFailed;
    public int attachmentSkipped;
    private final List<String> errors = new ArrayList<>();

    public void addError(String error) { if (error != null && !error.trim().isEmpty()) errors.add(error); }
    public List<String> errors() { return Collections.unmodifiableList(errors); }
}
