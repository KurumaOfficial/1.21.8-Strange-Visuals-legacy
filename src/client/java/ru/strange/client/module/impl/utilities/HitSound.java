package ru.strange.client.module.impl.utilities;

import net.minecraft.entity.LivingEntity;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventAttack;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.ButtonSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.utils.other.SoundPlayer;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

@IModule(
        name = "Хит Саунд",
        description = "воспроизводит звук при подтвержденном ударе",
        category = Category.Utilities,
        bind = -1
)
public class HitSound extends Module {
    private static final String HITSOUNDS_DIRECTORY = "hitsounds";
    private static final String CUSTOM_MODE = "Кастом";
    private static final String CRITICAL_ONLY_MODE = "Только крит";
    private static final String SELECT_SOUND_LABEL = "Выбрать звук";
    private static final String EMPTY_SOUNDS_PLACEHOLDER = "(нет файлов)";

    private final ModeSetting sound = new ModeSetting(
            "Звук",
            "Случайный",
            "Случайный",
            CUSTOM_MODE,
            "Hit 1",
            "Hit 2",
            "Hit 3",
            "Hit 4",
            "Hit 5"
    );

    private final ModeSetting triggerMode = new ModeSetting(
            "Триггер",
            "Любой удар",
            "Любой удар",
            CRITICAL_ONLY_MODE
    );

    private final ButtonSetting openFolder = new ButtonSetting("Папка звуков", 0, "Открыть", this::openHitSoundsFolder);
    private final ButtonSetting refreshSounds = new ButtonSetting("Обновить список", 1, "Обновить", this::refreshSoundList);
    private ModeSetting customSound;
    private final SliderSetting volume = new SliderSetting("Громкость", 35, 0, 100, 1, false);

    private int customSoundIndex = -1;

    public HitSound() {
        String[] available = getAvailableSounds();
        customSound = createCustomSoundSetting(available, available[0]);

        openFolder.hidden(() -> !isCustomMode());
        refreshSounds.hidden(() -> !isCustomMode());
        addSettings(sound, triggerMode, openFolder, refreshSounds, customSound, volume);

        List<Setting> settingsList = getSettings();
        for (int i = 0; i < settingsList.size(); i++) {
            if (settingsList.get(i) == customSound) {
                customSoundIndex = i;
                break;
            }
        }
    }

    private void openHitSoundsFolder() {
        try {
            Path hitSoundsPath = ensureHitSoundsDirectory();

            if (tryOpenWithSystemLauncher(hitSoundsPath)) {
                return;
            }

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(hitSoundsPath.toFile());
            }
        } catch (IOException ignored) {
        }
    }

    private boolean tryOpenWithSystemLauncher(Path folder) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command;
        if (os.contains("win")) {
            command = List.of("explorer.exe", folder.toString());
        } else if (os.contains("mac")) {
            command = List.of("open", folder.toString());
        } else if (os.contains("nix") || os.contains("nux")) {
            command = List.of("xdg-open", folder.toString());
        } else {
            return false;
        }

        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void refreshSoundList() {
        String currentSelection = customSound.get();
        String[] availableSounds = getAvailableSounds();
        String defaultSound = availableSounds[0];
        String selectedSound = defaultSound;
        for (String s : availableSounds) {
            if (s.equals(currentSelection)) {
                selectedSound = currentSelection;
                break;
            }
        }

        ModeSetting newCustomSound = createCustomSoundSetting(availableSounds, selectedSound);

        List<Setting> settingsList = getSettings();
        if (customSoundIndex >= 0 && customSoundIndex < settingsList.size()) {
            settingsList.set(customSoundIndex, newCustomSound);
            customSound = newCustomSound;
        }
    }

    private String[] getAvailableSounds() {
        List<String> sounds = new ArrayList<>();

        try {
            Path hitSoundsPath = ensureHitSoundsDirectory();

            if (Files.isDirectory(hitSoundsPath)) {
                try (Stream<Path> files = Files.list(hitSoundsPath)) {
                    files.filter(p -> {
                                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                                return name.endsWith(".wav") && Files.isRegularFile(p);
                            })
                            .sorted((a, b) -> a.getFileName().toString()
                                    .compareToIgnoreCase(b.getFileName().toString()))
                            .forEach(p -> sounds.add(p.getFileName().toString()));
                }
            }
        } catch (IOException e) {
            return new String[]{EMPTY_SOUNDS_PLACEHOLDER};
        }

        if (sounds.isEmpty()) {
            sounds.add(EMPTY_SOUNDS_PLACEHOLDER);
        }

        return sounds.toArray(new String[0]);
    }

    @EventInit
    public void onAttack(EventAttack event) {
        if (!enable || mc.player == null || mc.world == null) return;
        if (!event.isConfirmed() || event.isCancelled()) return;
        if (!(event.getTarget() instanceof LivingEntity)) return;
        if (triggerMode.is(CRITICAL_ONLY_MODE) && (!event.isCritical() || !event.isDamaging())) return;

        if (isCustomMode()) {
            String selected = customSound.get();
            if (EMPTY_SOUNDS_PLACEHOLDER.equals(selected)) return;
            SoundPlayer.playCustomSound(selected, volume.get(), true);
            return;
        }

        SoundPlayer.playSound(resolveSoundName(), volume.get(), true);
    }

    private String resolveSoundName() {
        return switch (sound.get()) {
            case "Hit 1" -> "hit1";
            case "Hit 2" -> "hit2";
            case "Hit 3" -> "hit3";
            case "Hit 4" -> "hit4";
            case "Hit 5" -> "hit5";
            default -> randomSound();
        };
    }

    private String randomSound() {
        return switch (ThreadLocalRandom.current().nextInt(5)) {
            case 0 -> "hit1";
            case 1 -> "hit2";
            case 2 -> "hit3";
            case 3 -> "hit4";
            default -> "hit5";
        };
    }

    private boolean isCustomMode() {
        return sound.is(CUSTOM_MODE);
    }

    private Path ensureHitSoundsDirectory() throws IOException {
        Path hitSoundsPath = Strange.root.toPath().resolve(HITSOUNDS_DIRECTORY).toAbsolutePath().normalize();
        Files.createDirectories(hitSoundsPath);
        return hitSoundsPath;
    }

    private ModeSetting createCustomSoundSetting(String[] availableSounds, String selectedSound) {
        ModeSetting setting = new ModeSetting(SELECT_SOUND_LABEL, selectedSound, availableSounds);
        setting.hidden(() -> !isCustomMode());
        return setting;
    }
}
