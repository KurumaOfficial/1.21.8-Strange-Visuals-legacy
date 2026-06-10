package ru.strange.client.module.impl.utilities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import ru.strange.client.entity.pet.AxolotlPetEntity;
import ru.strange.client.event.Event;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.utils.other.KeyUtil;

/**
 * Модуль для создания питомцев (тюлень и аксолотль)
 */
@IModule(
        name = "Питомцы",
        description = "Создает питомцев-тюленя и аксолотля",
        category = Category.Utilities,
        bind = -1
)
public class PetModule extends Module {
    private AxolotlPetEntity axolotlPet;
    private final BindSettings spawnBind = new BindSettings("Создать питомцев", 80); // GLFW_KEY_P

    public PetModule() {
        addSettings(spawnBind);
    }

    @Override
    public void onEnable() {
        // Spawn pets when module is enabled
        spawnPets();
    }

    @Override
    public void onDisable() {
        // Despawn pets when module is disabled
        despawnPets();
    }

    public void onEvent(Event event) {
        if (event instanceof EventUpdate) {
            updatePets();
        }
    }

    private void spawnPets() {
        PlayerEntity player = mc.player;
        World world = mc.world;
        if (player == null || world == null) return;

        // Despawn any existing pets first
        despawnPets();

        // Create axolotl pet (only visible to player)
        axolotlPet = new AxolotlPetEntity(world, player);
        world.spawnEntity(axolotlPet);
    }

    private void despawnPets() {
        if (axolotlPet != null && axolotlPet.isAlive()) {
            axolotlPet.discard();
            axolotlPet = null;
        }
    }

    private void updatePets() {
        // Check if spawn bind is pressed
        if (spawnBind.get() != 0 && 
            org.lwjgl.glfw.GLFW.glfwGetKey(
                    mc.getWindow().getHandle(),
                    spawnBind.get()) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            spawnPets();
        }

        // Update existing pets
        if (axolotlPet != null && !axolotlPet.isAlive()) {
            axolotlPet = null;
        }
    }
}