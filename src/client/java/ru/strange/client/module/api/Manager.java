package ru.strange.client.module.api;

import ru.strange.client.Strange;
import ru.strange.client.module.impl.interfaces.SwingAnimation;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.module.impl.interfaces.CustomCrosshair;
import ru.strange.client.module.impl.other.*;
import ru.strange.client.module.impl.player.*;
import ru.strange.client.module.impl.utilities.*;
import ru.strange.client.module.impl.world.BlockOutline;
import ru.strange.client.module.impl.world.DashCubes;
import ru.strange.client.module.impl.world.Svetych;
import ru.strange.client.module.impl.world.WorldParticles;
import ru.strange.client.utils.other.KeyBindPolicy;

import java.util.ArrayList;
import java.util.Comparator;

public class Manager {

    public ArrayList<Module> module = new ArrayList<>();

    public Manager() {
        //Other
        module.add(new TimeSet());
        module.add(new AspectRation());
        module.add(new NoRender());
        module.add(new AucHelper());
        module.add(new FullBright());
        module.add(new Optimization());
        module.add(new CustomFog());
        module.add(new ScreenFilters());
        module.add(new ItemPhysics());
        module.add(new ModuleSounds());

        //Utilities
        module.add(new AutoRun());
        module.add(new MiddleClick());
        module.add(new TapeMouse());
        module.add(new ItemScroller());
        module.add(new FTHelper());
        module.add(new FreeLook());
        module.add(new AutoSwap());
        module.add(new ShiftTap());
        module.add(new PvPHelper());
        module.add(new AutoRespawn());
        module.add(new ChatHelper());
        module.add(new FakePlayer());
        module.add(new HitSound());
        module.add(new SpJoiner());

        //Player
        module.add(new PlayerParticles());
        module.add(new Hat());
        module.add(new Box());
        module.add(new FakeHitboxes());
        module.add(new Trails());
        module.add(new TargetESP());
        module.add(new HitBubble());
        module.add(new KillEffect());
        module.add(new JumpCircle());
        module.add(new ShaderHand());

        //World
        module.add(new WorldParticles());
        module.add(new Svetych());
        module.add(new BlockOutline());
        module.add(new DashCubes());

        //Interface
        module.add(new CustomCrosshair());
        module.add(new WaterMark());
        module.add(new SwingAnimation());

        module.sort(Comparator.comparing(f -> f.getDisplayName().toLowerCase()));
    }

    public ArrayList<Module> getModules(){
        return this.module;
    }

    public <T extends Module> T get(final Class<T> clazz) {
        return this.module.stream()
                .filter(module -> clazz.isAssignableFrom(module.getClass()))
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }
    public Module getModule(Class<?> class1) {
        for (Module module1 : this.module){
            if(module1.getClass() == class1) {
                return module1;
            }
        }
        return null;
    }

    public ArrayList<Module> getType(Category category) {
        ArrayList<Module> modules = new ArrayList<>();
        for (Module module1 : this.module) {
            if (module1.category == category) {
                modules.add(module1);
            }
        }
        return modules;
    }

    public Module[] getBind(int bind) {
        if (KeyBindPolicy.isProtectedFunctionKey(bind)) {
            return new Module[0];
        }

        return Strange.get.manager.module.stream().filter(module -> module.bind == bind).toArray(Module[]::new);
    }
}
