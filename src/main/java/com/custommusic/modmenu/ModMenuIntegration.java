package com.custommusic.modmenu;

import com.custommusic.CustomMusicClient;
import com.custommusic.ui.MusicScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** ModMenu 入口：给模组列表加一个「设置」按钮。 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        CustomMusicClient.LOG.info("ModMenu 请求配置界面，返回歌单/情境主界面");
        return MusicScreen::new;
    }
}
