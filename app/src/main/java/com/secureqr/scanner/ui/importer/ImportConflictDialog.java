package com.secureqr.scanner.ui.importer;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.secureqr.scanner.R;

public class ImportConflictDialog extends DialogFragment {
    public enum ConflictMode { SKIP, OVERWRITE, KEEP_BOTH }

    public interface Listener { void onChosen(ConflictMode mode); }
    private Listener listener;
    public void setListener(Listener listener) { this.listener = listener; }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.import_conflict_handling)
                .setMessage(R.string.import_conflict_message)
                .setNeutralButton(R.string.import_conflict_skip, (dialog, which) -> notifyResult(ConflictMode.SKIP))
                .setNegativeButton(R.string.import_conflict_overwrite, (dialog, which) -> notifyResult(ConflictMode.OVERWRITE))
                .setPositiveButton(R.string.import_conflict_keep_both, (dialog, which) -> notifyResult(ConflictMode.KEEP_BOTH))
                .create();
    }

    private void notifyResult(ConflictMode mode) {
        if (listener != null) listener.onChosen(mode);
    }
}
