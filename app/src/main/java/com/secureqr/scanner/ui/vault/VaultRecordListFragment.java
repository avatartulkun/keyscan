package com.secureqr.scanner.ui.vault;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.data.repository.VaultRepository;
import com.secureqr.scanner.vault.VaultTypes;

import java.util.Collections;
import java.util.List;

public final class VaultRecordListFragment extends Fragment {
    private static final String ARG = "type";
    private VaultRecordAdapter adapter;
    private List<VaultItem> items = Collections.emptyList();
    private String query = "";

    public static VaultRecordListFragment newInstance(String type) {
        VaultRecordListFragment fragment = new VaultRecordListFragment();
        Bundle args = new Bundle();
        args.putString(ARG, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_vault_level, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        // Use the localized fragment context; ApplicationContext may retain the system language.
        VaultAdapter.VaultHost.context = requireContext();
        String key = requireArguments().getString(ARG);
        VaultTypes.Type type = VaultTypes.find(key);
        ((TextView) view.findViewById(R.id.tv_title)).setText(type.labelRes);
        view.findViewById(R.id.btn_back).setOnClickListener(v -> getParentFragmentManager().popBackStack());
        view.findViewById(R.id.btn_add).setOnClickListener(v -> openEdit(key));

        RecyclerView recycler = view.findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new VaultRecordAdapter(new VaultRecordAdapter.Listener() {
            @Override public void onClick(VaultItem item) {
                openDetail(item);
            }

            @Override public void onFavorite(VaultItem item, boolean favorite) {
                VaultUiState.setFavorite(requireContext(), item.id, favorite);
                Toast.makeText(requireContext(), favorite ? R.string.vault_favorite_added : R.string.vault_favorite_removed, Toast.LENGTH_SHORT).show();
                adapter.submit(items, key, query);
            }
        });
        recycler.setAdapter(adapter);

        SearchView search = view.findViewById(R.id.search);
        search.setQueryHint(getString(R.string.vault_search_type_hint, getString(type.labelRes)));
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextSubmit(String q) { filter(q); return true; }
            public boolean onQueryTextChange(String q) { filter(q); return true; }
        });
        new VaultRepository(requireContext()).observe("").observe(getViewLifecycleOwner(), loaded -> {
            items = loaded;
            adapter.submit(items, key, query);
        });
    }

    private void filter(String q) {
        query = q == null ? "" : q;
        adapter.submit(items, requireArguments().getString(ARG), query);
    }

    private void openEdit(String type) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, VaultEditFragment.newItem(type))
                .addToBackStack(null)
                .commit();
    }

    private void openDetail(VaultItem item) {
        VaultUiState.markRecent(requireContext(), item.id);
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, VaultDetailFragment.newInstance(item.id))
                .addToBackStack(null)
                .commit();
    }
}
