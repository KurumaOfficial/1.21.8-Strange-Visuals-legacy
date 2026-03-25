package ru.strange.client.StarterMenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AltAccountController {
    private final AltAccountStore accountStore;
    private final List<AltAccount> activeAccounts = new ArrayList<>();
    private final List<AltAccount> deletedAccounts = new ArrayList<>();
    private String selectedActiveName;

    public AltAccountController(AltAccountStore accountStore) {
        this.accountStore = accountStore;
    }

    public List<AltAccount> activeAccounts() {
        return activeAccounts;
    }

    public List<AltAccount> deletedAccounts() {
        return deletedAccounts;
    }

    public String selectedActiveName() {
        return selectedActiveName;
    }

    public void setSelectedActiveName(String selectedActiveName) {
        this.selectedActiveName = sanitizeSelectedActiveName(selectedActiveName);
    }

    public void load(AltSortMode sortMode) {
        AltAccountStore.LoadedAccounts loaded = accountStore.load();
        activeAccounts.clear();
        activeAccounts.addAll(loaded.active());
        deletedAccounts.clear();
        deletedAccounts.addAll(loaded.deleted());
        selectedActiveName = sanitizeSelectedActiveName(loaded.selectedActiveName());
        sortActive(sortMode);
    }

    public boolean save() {
        selectedActiveName = sanitizeSelectedActiveName(selectedActiveName);
        return accountStore.save(activeAccounts, deletedAccounts, selectedActiveName);
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
}
