package com.custommusic.modmenu;

import com.custommusic.ui.MusicScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** ModMenu 入口：给模组列表加一个「设置」按钮。 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return MusicScreen::new;
    }
}
