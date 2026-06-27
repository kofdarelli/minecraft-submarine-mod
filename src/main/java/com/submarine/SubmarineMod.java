package com.submarine;

import com.submarine.command.SubmarineCommands;
import com.submarine.entity.ModEntities;
import com.submarine.item.ModItems;
import com.submarine.net.SubmarineNetworking;
import com.submarine.protection.SubmarineProtection;
import com.submarine.seat.SubmarineSeatManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SubmarineMod implements ModInitializer {
    public static final String MOD_ID = "submarine";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.register();
        ModEntities.register();
        SubmarineNetworking.registerServerReceivers();
        SubmarineProtection.registerEvents();
        SubmarineSeatManager.registerServerTicks();
        SubmarineCommands.register();
    }
}
