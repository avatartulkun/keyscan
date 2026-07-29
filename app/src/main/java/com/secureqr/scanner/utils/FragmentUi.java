package com.secureqr.scanner.utils;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

/** Safely delivers asynchronous results only to a Fragment with a live view. */
public final class FragmentUi {
    private FragmentUi() {
    }

    public static void run(Fragment fragment, Runnable action) {
        if (fragment == null || action == null || !fragment.isAdded()) return;
        FragmentActivity activity = fragment.getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(() -> {
            if (!fragment.isAdded() || fragment.getView() == null
                    || activity.isFinishing() || activity.isDestroyed()) return;
            action.run();
        });
    }
}
