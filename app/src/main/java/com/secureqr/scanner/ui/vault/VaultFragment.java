package com.secureqr.scanner.ui.vault;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.data.repository.VaultRepository;
import com.secureqr.scanner.exporter.ExportVaultItem;
import com.secureqr.scanner.exporter.VaultJsonExporter;
import com.secureqr.scanner.security.OperationModeGuard;
import com.secureqr.scanner.security.ExportSecurityGuard;
import com.secureqr.scanner.utils.GlobalWebDavSyncUi;
import com.secureqr.scanner.utils.FragmentUi;
import com.secureqr.scanner.utils.NavigationHelper;
import com.secureqr.scanner.vault.VaultTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONObject;

public final class VaultFragment extends Fragment {
    private static final String ARG_QUERY = "query";
    private static final String ARG_OPEN_CREATE = "open_create";
    private VaultRepository repository;
    private VaultAdapter adapter;
    private LiveData<List<VaultItem>> observed;
    private List<VaultItem> currentItems = new ArrayList<>();
    private Map<String, List<String>> attachmentNames = new HashMap<>();
    private String query = "";
    private String pendingExport = "";
    private final ActivityResultLauncher<String> exportFile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), this::writeExport);
    private final ActivityResultLauncher<String[]> importFile = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::readImport);

    public static VaultFragment search(String query) {
        VaultFragment fragment = new VaultFragment();
        Bundle args = new Bundle();
        args.putString(ARG_QUERY, query);
        fragment.setArguments(args);
        return fragment;
    }

    public static VaultFragment createNew() {
        VaultFragment fragment = new VaultFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_OPEN_CREATE, true);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_vault, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repository = new VaultRepository(requireContext());
        // Keep the activity's localized configuration so adapter strings follow the selected app language.
        VaultAdapter.VaultHost.context = requireContext();
        view.findViewById(R.id.btn_vault_home).setOnClickListener(v -> NavigationHelper.openHome(this));
        view.findViewById(R.id.btn_vault_menu).setOnClickListener(this::showMoreMenu);
        view.findViewById(R.id.btn_vault_add).setVisibility(View.GONE);
        if (state == null && getArguments() != null
                && getArguments().getBoolean(ARG_OPEN_CREATE, false)) {
            view.post(() -> VaultNavigation.showTypePicker(this));
        }

        RecyclerView list = view.findViewById(R.id.recycler_vault);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new VaultAdapter(new VaultAdapter.Listener() {
            public void onOpenCategory(VaultTypes.Category category) { openCategory(category); }
            public void onOpenItem(VaultItem item) { openDetail(item); }
            public void onReorderCategories(List<VaultTypes.Category> categories) {
                VaultUiState.saveCategoryOrder(requireContext(), categories);
            }
            public void onFavoriteChanged(VaultItem item, boolean favorite) {
                VaultUiState.setFavorite(requireContext(), item.id, favorite);
                Toast.makeText(requireContext(), favorite ? R.string.vault_favorite_added : R.string.vault_favorite_removed, Toast.LENGTH_SHORT).show();
                refresh();
            }
        });
        list.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                return adapter.moveCategory(from.getBindingAdapterPosition(), to.getBindingAdapterPosition());
            }
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {}
            public boolean isLongPressDragEnabled() { return true; }
        }).attachToRecyclerView(list);

        SearchView search = view.findViewById(R.id.search_vault);
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextSubmit(String q) { setQuery(q); return true; }
            public boolean onQueryTextChange(String q) { setQuery(q); return true; }
        });
        if (getArguments() != null) {
            query = getArguments().getString(ARG_QUERY, "");
            if (!query.isEmpty()) search.setQuery(query, false);
        }
        bind();
    }

    private void bind() {
        if (observed != null) observed.removeObservers(getViewLifecycleOwner());
        observed = repository.observe("");
        observed.observe(getViewLifecycleOwner(), items -> {
            currentItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
            refresh();
            repository.getAttachmentNames(map -> FragmentUi.run(this, () -> {
                attachmentNames = map;
                refresh();
            }));
        });
    }

    private void setQuery(String q) {
        query = q == null ? "" : q;
        refresh();
    }

    private void refresh() {
        if (adapter != null) adapter.submit(currentItems, attachmentNames, query);
    }

    private void openCategory(VaultTypes.Category category) {
        VaultTypeBottomSheetDialog.show(this, category, currentItems);
    }

    private void openDetail(VaultItem item) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, VaultDetailFragment.newInstance(item.id))
                .addToBackStack(null)
                .commit();
    }

    private void showMoreMenu(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.vault_menu_create);
        menu.getMenu().add(0, 2, 1, R.string.vault_menu_import);
        menu.getMenu().add(0, 3, 2, R.string.vault_menu_export);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                OperationModeGuard.requireEdit(this, () -> VaultNavigation.showTypePicker(this));
            } else if (item.getItemId() == 2) {
                OperationModeGuard.requireEdit(this, () -> importFile.launch(new String[]{"application/json", "text/json"}));
            } else {
                ExportSecurityGuard.require(requireActivity(), getString(R.string.export_auth_prompt), this::exportVault);
            }
            return true;
        });
        menu.show();
    }

    private void exportVault() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.vault_export_title)
                .setMessage(R.string.vault_export_archive_note)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.export_backup, (dialog, which) -> repository.getAllNow(items -> {
                    try {
                        List<ExportVaultItem> exported = new ArrayList<>();
                        for (VaultItem item : items) {
                            exported.add(new ExportVaultItem(item.title, item.type, item.category, item.fieldsJson,
                                    item.notes, item.createdTime, item.updatedTime));
                        }
                        pendingExport = VaultJsonExporter.export(exported);
                        FragmentUi.run(this, () -> exportFile.launch("keyscan_vault_export.zip"));
                    } catch (Exception error) {
                        FragmentUi.run(this, () -> Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }))
                .show();
    }

    private void writeExport(Uri uri) {
        if (uri == null || pendingExport.isEmpty()) return;
        try {
            OutputStream output = requireContext().getContentResolver().openOutputStream(uri);
            if (output == null) throw new IllegalStateException("Unable to open export file");
            String metadata = pendingExport;
            pendingExport = "";
            repository.exportArchive(metadata, output, error -> FragmentUi.run(this, () ->
                    Toast.makeText(requireContext(), error == null
                            ? R.string.vault_export_archive_done
                            : R.string.vault_export_archive_failed, Toast.LENGTH_SHORT).show()));
        } catch (Exception error) {
            Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
            pendingExport = "";
        }
    }

    private void readImport(Uri uri) {
        if (uri == null) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                requireContext().getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
            StringBuilder raw = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) raw.append(line);
            JSONArray values = new JSONArray(raw.toString());
            if (values.length() == 0) throw new IllegalArgumentException(getString(R.string.vault_import_invalid));
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.vault_import_title)
                    .setMessage(getString(R.string.vault_import_attachment_note) + "\n\n" + values.length() + " 条资料")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm, (dialog, which) -> importVaultItems(values))
                    .show();
        } catch (Exception error) {
            Toast.makeText(requireContext(), R.string.vault_import_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    private void importVaultItems(JSONArray values) {
        int count = 0;
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value == null) continue;
            VaultItem item = new VaultItem();
            item.title = value.optString("title", "");
            item.type = value.optString("type", "CUSTOM");
            item.category = value.optString("category", "CUSTOM");
            Object fields = value.opt("fields");
            item.fieldsJson = fields instanceof JSONObject ? fields.toString() : "{}";
            item.notes = value.optString("notes", "");
            item.createdTime = value.optLong("createdTime", 0L);
            item.updatedTime = value.optLong("updatedTime", 0L);
            repository.save(item, null);
            count++;
        }
        Toast.makeText(requireContext(), getString(R.string.vault_import_done, count), Toast.LENGTH_SHORT).show();
    }
}
