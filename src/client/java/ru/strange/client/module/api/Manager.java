package ru.strange.client.module.api;

import ru.strange.client.module.impl.interfaces.*;
import ru.strange.client.module.impl.other.*;
import ru.strange.client.module.impl.player.*;
import ru.strange.client.module.impl.utilities.*;
import ru.strange.client.module.impl.world.*;
import ru.strange.client.utils.other.KeyBindPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Manager {

    private final ArrayList<Module> modules = new ArrayList<>();
    private final List<Module> modulesView;

    public Manager() {
        //Other
        modules.add(new TimeSet());
        modules.add(new AspectRation());
        modules.add(new NoRender());
        modules.add(new AucHelper());
        modules.add(new FullBright());
        modules.add(new Optimization());
        modules.add(new CustomFog());
        modules.add(new ScreenFilters());
        modules.add(new ItemPhysics());
        modules.add(new ModuleSounds());
        modules.add(new NameProtect());
        modules.add(new SmoothCamera());
        modules.add(new ColorReducer());
        modules.add(new BlockRemover());
        modules.add(new SkyFog());
        modules.add(new ContrastBooster());

        //Utilities
        modules.add(new AutoRun());
        modules.add(new MiddleClick());
        modules.add(new TapeMouse());
        modules.add(new ItemScroller());
        modules.add(new FTHelper());
        modules.add(new FreeLook());
        modules.add(new AutoSwap());
        modules.add(new ShiftTap());
        modules.add(new PvPHelper());
        modules.add(new AutoRespawn());
        modules.add(new ChatHelper());
        modules.add(new FakePlayer());
        modules.add(new HitSound());
        modules.add(new SpJoiner());
        modules.add(new FastExp());
        modules.add(new AutoEat());
        modules.add(new ShulkerPreview());

        //Player
        modules.add(new PlayerParticles());
        modules.add(new Hat());
        modules.add(new Box());
        modules.add(new FakeHitboxes());
        modules.add(new Trails());
        modules.add(new TargetESP());
        modules.add(new HitBubble());
        modules.add(new KillEffect());
        modules.add(new JumpCircle());
        modules.add(new SkeletonESP());
        modules.add(new ShaderHand());
        modules.add(new TotemPopCounter());

        //World
        modules.add(new WorldParticles());
        modules.add(new Svetych());
        modules.add(new FireFly());
        modules.add(new BlockOutline());
        modules.add(new DashCubes());
        modules.add(new GPS());
        modules.add(new Ghost());
        modules.add(new JumpHit());

        //Interface
        modules.add(new CustomCrosshair());
        modules.add(new WaterMark());
        modules.add(new SwingAnimation());
        modules.add(new BetterMinecraft());
        modules.add(new CursorParticles());
        modules.add(new ClickGui());
        modules.add(new SaturationHud());

        modules.sort(Comparator.comparing(f -> f.getDisplayName().toLowerCase()));
        for (Module module : modules) {
            module.captureDefaultState();
        }
        modulesView = List.copyOf(modules);
    }

    public List<Module> getModules(){
        return modulesView;
    }

    public <T extends Module> T get(final Class<T> clazz) {
        for (Module module : modules) {
            if (clazz.isAssignableFrom(module.getClass())) {
                return clazz.cast(module);
            }
        }
        return null;
    }
    public Module getModule(Class<?> class1) {
        for (Module module1 : modules){
            if(module1.getClass() == class1) {
                return module1;
            }
        }
        return null;
    }

    public ArrayList<Module> getType(Category category) {
        ArrayList<Module> modules = new ArrayList<>();
        for (Module module1 : this.modules) {
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

        ArrayList<Module> matches = new ArrayList<>();
        for (Module module : modules) {
            if (module.bind == bind) {
                matches.add(module);
            }
        }
        return matches.toArray(new Module[0]);
    }
}
