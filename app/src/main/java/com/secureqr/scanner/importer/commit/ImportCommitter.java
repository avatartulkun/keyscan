package com.secureqr.scanner.importer.commit;

import android.content.Context;

import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.data.model.PasswordGroup;
import com.secureqr.scanner.data.repository.PasswordRepository;
import com.secureqr.scanner.importer.model.ImportedPassword;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** Coordinates CSV import through PasswordRepository only. */
public final class ImportCommitter {
    public enum ConflictStrategy { SKIP, OVERWRITE, KEEP_BOTH }

    public interface Callback { void onComplete(ImportCommitResult result); }

    private final PasswordRepository repository;
    private final PasswordDuplicateChecker duplicateChecker = new PasswordDuplicateChecker();

    public ImportCommitter(Context context) {
        repository = PasswordRepository.getInstance(context.getApplicationContext());
    }

    public void commitPasswords(List<ImportedPassword> imported, ConflictStrategy strategy, Callback callback) {
        List<ImportedPassword> source = imported == null ? new ArrayList<>() : new ArrayList<>(imported);
        repository.getGroups(groups -> repository.getAll(existing -> resolveGroups(source, groups, groupIds -> {
            ImportCommitResult result = new ImportCommitResult();
            List<PasswordEntry> inserts = new ArrayList<>();
            List<PasswordEntry> updates = new ArrayList<>();
            for (ImportedPassword item : source) {
                try {
                    PasswordEntry duplicate = duplicateChecker.findDuplicate(item, existing);
                    if (duplicate != null && strategy == ConflictStrategy.SKIP) {
                        result.skipCount++;
                        continue;
                    }
                    PasswordEntry entry = map(item, groupIds);
                    if (duplicate != null && strategy == ConflictStrategy.OVERWRITE) {
                        entry.id = duplicate.id;
                        entry.itemId = duplicate.itemId;
                        updates.add(entry);
                    } else {
                        inserts.add(entry);
                    }
                } catch (Exception ignored) {
                    result.failCount++;
                }
            }
            repository.importEntries(inserts, updates, (success, failed) -> {
                result.successCount = success;
                result.failCount += failed;
                if (callback != null) callback.onComplete(result);
            });
        })));
    }

    private void resolveGroups(List<ImportedPassword> items, List<PasswordGroup> groups, CallbackWithGroups callback) {
        HashMap<String, String> ids = new HashMap<>();
        if (groups != null) {
            for (PasswordGroup group : groups) {
                if (group != null) ids.put(normalize(group.displayName()), group.id);
            }
        }
        List<String> pending = new ArrayList<>();
        for (ImportedPassword item : items) {
            String name = item == null ? "" : trim(item.folderName);
            if (!name.isEmpty() && !ids.containsKey(normalize(name)) && !pending.contains(name)) pending.add(name);
        }
        createNextGroup(pending, 0, ids, callback);
    }

    private void createNextGroup(List<String> names, int index, HashMap<String, String> ids, CallbackWithGroups callback) {
        if (index >= names.size()) {
            callback.onResolved(ids);
            return;
        }
        String name = names.get(index);
        repository.createGroup(name, group -> {
            if (group != null) ids.put(normalize(group.displayName()), group.id);
            createNextGroup(names, index + 1, ids, callback);
        });
    }

    private PasswordEntry map(ImportedPassword source, HashMap<String, String> groupIds) {
        PasswordEntry entry = new PasswordEntry();
        long now = System.currentTimeMillis();
        entry.title = trim(source.title);
        entry.websiteDomain = trim(source.websiteDomain);
        entry.appPackageName = trim(source.appPackageName);
        entry.username = trim(source.username);
        entry.account = trim(source.account);
        entry.password = source.password == null ? "" : source.password;
        entry.notes = trim(source.notes);
        entry.remark = entry.title;
        entry.createdAt = now;
        entry.updatedAt = now;
        String group = trim(source.folderName);
        entry.groupId = group.isEmpty() ? PasswordGroup.DEFAULT_ID : groupIds.getOrDefault(normalize(group), PasswordGroup.DEFAULT_ID);
        return entry;
    }

    private String trim(String value) { return value == null ? "" : value.trim(); }
    private String normalize(String value) { return trim(value).toLowerCase(Locale.ROOT); }

    private interface CallbackWithGroups { void onResolved(HashMap<String, String> groupIds); }
}
