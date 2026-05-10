package ru.strange.client.module.impl.other;

import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.MultiBooleanSetting;

@IModule(
        name = "Р‘РµР· СЂРµРЅРґРµСЂР°",
        description = "РћС‚РєР»СЋС‡Р°РµС‚ РЅРµРЅСѓР¶РЅС‹Рµ РІРёР·СѓР°Р»СЊРЅС‹Рµ СЌС„С„РµРєС‚С‹",
        category = Category.Other,
        bind = -1
)
public class NoRender extends Module {

    public static volatile NoRender INSTANCE;

    public static MultiBooleanSetting settings = new MultiBooleanSetting(
            "РќР°СЃС‚СЂРѕР№РєРё",
            new BooleanSetting("РЈР±СЂР°С‚СЊ РѕРіРѕРЅСЊ", true),
            new BooleanSetting("РЈР±СЂР°С‚СЊ Р±Р»РѕРє-РѕРІРµСЂР»РµР№", true),
            new BooleanSetting("РЈР±СЂР°С‚СЊ С‚СЂСЏСЃРєСѓ", true),
            new BooleanSetting("РЈР±СЂР°С‚СЊ С‚С‹РєРІСѓ", true),
            new BooleanSetting("РЈР±СЂР°С‚СЊ РїРѕСЂС‚Р°Р»", true),
            new BooleanSetting("РЈР±СЂР°С‚СЊ СЃРєРѕСЂР±РѕСЂРґ", false)
    );

    /**
     * Singleton РёРЅРёС†РёР°Р»РёР·РёСЂСѓРµС‚СЃСЏ С‡РµСЂРµР· РєРѕРЅСЃС‚СЂСѓРєС‚РѕСЂ, РІС‹Р·С‹РІР°РµРјС‹Р№ Manager
     * РѕРґРёРЅ СЂР°Р· РїСЂРё Р·Р°РіСЂСѓР·РєРµ РјРѕРґСѓР»РµР№.
     */
    public NoRender() {
        if (INSTANCE != null) {
            throw new IllegalStateException("NoRender module already initialized");
        }
        INSTANCE = this;
        addSettings(settings);
    }

    public static boolean enabled(String name) {
        return INSTANCE != null && INSTANCE.enable && settings.get(name);
    }
}
