package com.secureqr.scanner.utils;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.secureqr.scanner.R;
import com.secureqr.scanner.ui.home.HomeFragment;

public final class NavigationHelper {
    private NavigationHelper() {
    }

    public static void openHome(Fragment fragment) {
        FragmentManager manager = fragment.getParentFragmentManager();
        manager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        manager.beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .commit();
    }
}
