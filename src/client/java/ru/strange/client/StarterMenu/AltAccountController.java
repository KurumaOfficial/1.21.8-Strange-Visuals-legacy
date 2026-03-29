package ru.strange.client.StarterMenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

public final class AltAccountController {
    private final AltAccountStore accountStore;
    private final List<AltAccount> activeAccounts = new ArrayList<>();
    private final List<AltAccount> deletedAccounts = new ArrayList<>();
    private final List<AltAccount> activeAccountsView = Collections.unmodifiableList(activeAccounts);
    private final List<AltAccount> deletedAccountsView = Collections.unmodifiableList(deletedAccounts);
    private String selectedActiveName;

    public AltAccountController(AltAccountStore accountStore) {
        this.accountStore = accountStore;
    }

    public List<AltAccount> activeAccounts() {
        return activeAccountsView;
    }

    public List<AltAccount> deletedAccounts() {
        return deletedAccountsView;
    }

    public String selectedActiveName() {
        return selectedActiveName;
    }

    public void setSelectedActiveName(String selectedActiveName) {
        this.selectedActiveName = sanitizeSelectedActiveName(selectedActiveName);
    }

    public AltSortMode load(AltSortMode fallbackSortMode) {
        AltAccountStore.LoadedAccounts loaded = accountStore.load();
        activeAccounts.clear();
        activeAccounts.addAll(loaded.active());
        deletedAccounts.clear();
        deletedAccounts.addAll(loaded.deleted());
        selectedActiveName = loaded.selectedActiveName();
        AltSortMode resolvedSortMode = loaded.sortMode() != null ? loaded.sortMode() : fallbackSortMode;
        sortActive(resolvedSortMode);
        reconcileSelectedActiveName();
        return resolvedSortMode;
    }

    public boolean save(AltSortMode sortMode) {
        reconcileSelectedActiveName();
        return accountStore.save(activeAccounts, deletedAccounts, selectedActiveName, sortMode);
    }

    public Snapshot snapshot() {
        return new Snapshot(copyAccounts(activeAccounts), copyAccounts(deletedAccounts), selectedActiveName);
    }

    public void restore(Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        activeAccounts.clear();
        activeAccounts.addAll(copyAccounts(snapshot.activeAccounts()));
        deletedAccounts.clear();
        deletedAccounts.addAll(copyAccounts(snapshot.deletedAccounts()));
        selectedActiveName = snapshot.selectedActiveName();
        reconcileSelectedActiveName();
    }

    public void sortActive(AltSortMode sortMode) {
        Comparator<AltAccount> comparator = Comparator
                .comparing((AltAccount account) -> !account.pinned)
                .thenComparing(sortMode.comparator());
        activeAccounts.sort(comparator);
    }

    public boolean containsName(String name) {
        return findIndex(activeAccounts, name) != -1 || findIndex(deletedAccounts, name) != -1;
    }

    public AltAccount createAccount(String name, AltSortMode sortMode) {
        AltAccount account = AltAccount.create(name);
        activeAccounts.add(account);
        sortActive(sortMode);
        return account;
    }

    public AltAccount moveToDeleted(int index) {
        if (index < 0 || index >= activeAccounts.size()) {
            return null;
        }

        AltAccount removedAccount = activeAccounts.remove(index);
        deletedAccounts.add(0, removedAccount);
        reconcileSelectedActiveName();
        return removedAccount;
    }

    public AltAccount restoreDeleted(int deletedIndex, AltSortMode sortMode) {
        if (deletedIndex < 0 || deletedIndex >= deletedAccounts.size()) {
            return null;
        }

        AltAccount restored = deletedAccounts.remove(deletedIndex);
        activeAccounts.add(restored);
        sortActive(sortMode);
        return restored;
    }

    public String deleteDeletedPermanently(int deletedIndex) {
        if (deletedIndex < 0 || deletedIndex >= deletedAccounts.size()) {
            return null;
        }

        return deletedAccounts.remove(deletedIndex).name;
    }

    public boolean clearActive() {
        if (activeAccounts.isEmpty()) {
            return false;
        }

        deletedAccounts.addAll(0, new ArrayList<>(activeAccounts));
        activeAccounts.clear();
        reconcileSelectedActiveName();
        return true;
    }

    public AltAccount togglePinned(int index, AltSortMode sortMode) {
        if (index < 0 || index >= activeAccounts.size()) {
            return null;
        }

        AltAccount account = activeAccounts.get(index);
        account.pinned = !account.pinned;
        sortActive(sortMode);
        return account;
    }

    public int findActiveIndex(String name) {
        return findIndex(activeAccounts, name);
    }

    private static int findIndex(List<AltAccount> entries, String name) {
        if (name == null) {
            return -1;
        }

        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name.equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private String sanitizeSelectedActiveName(String name) {
        int index = findIndex(activeAccounts, name);
        return index == -1 ? null : activeAccounts.get(index).name;
    }

    private void reconcileSelectedActiveName() {
        selectedActiveName = sanitizeSelectedActiveName(selectedActiveName);
    }

    private static List<AltAccount> copyAccounts(List<AltAccount> accounts) {
        List<AltAccount> copy = new ArrayList<>(accounts.size());
        for (AltAccount account : accounts) {
            copy.add(account.copy());
        }
        return copy;
    }

    public record Snapshot(List<AltAccount> activeAccounts, List<AltAccount> deletedAccounts, String selectedActiveName) {}
}
