package com.enxv.aerouniversaljoint;

import com.enxv.aerouniversaljoint.client.AeroUniversalJointClient;
import com.enxv.aerouniversaljoint.network.AeroUniversalJointNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(AeroUniversalJointMod.MOD_ID)
public final class AeroUniversalJointMod {
    public static final String MOD_ID = "aeronautics_utility_objects";

    public AeroUniversalJointMod(IEventBus modBus, ModContainer modContainer) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenuTypes.register(modBus);
        ModCreativeTabs.register(modBus);
        AeroUniversalJointNetwork.register(modBus);
        AeroUniversalJointCommonEvents.register();
        modContainer.registerConfig(ModConfig.Type.SERVER, AeroUniversalJointConfig.SERVER_SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            AeroUniversalJointClient.init(modBus);
        }
    }
}

