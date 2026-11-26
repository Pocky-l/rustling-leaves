package com.pockyl.rustling_leaves.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import com.pockyl.rustling_leaves.client.TreeEditorScreen;

/**
 * Mod Menu's config button. Forge Config API Port 8 has no screen of config options, so it opens the settings of single
 * trees; the options stay in {@code config/rustling_leaves-client.toml}. Loaded by Mod Menu only, when it is installed.
 */
public final class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new TreeEditorScreen(parent, null);
    }
}
