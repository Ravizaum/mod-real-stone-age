package com.realstoneage.realstoneagemod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// This mod's first client-only registration: binds the Forge menu type to ForgeScreen.
// Dist.CLIENT-gated so a dedicated server never touches Screen/AbstractContainerScreen.
@EventBusSubscriber(modid = RealStoneAge.MODID, value = Dist.CLIENT)
public class RealStoneAgeClient {
    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(RealStoneAge.FORGE_MENU.get(), ForgeScreen::new);
    }
}
