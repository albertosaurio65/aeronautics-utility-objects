package com.enxv.aerouniversaljoint;

import com.enxv.aerouniversaljoint.content.DampingStressBearingMenu;
import com.enxv.aerouniversaljoint.content.HydraulicConnectionHeadMenu;
import com.enxv.aerouniversaljoint.content.UniversalJointMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, AeroUniversalJointMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<DampingStressBearingMenu>> DAMPING_STRESS_BEARING = MENU_TYPES.register(
            "damping_stress_bearing",
            () -> IMenuTypeExtension.create(DampingStressBearingMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<UniversalJointMenu>> UNIVERSAL_JOINT = MENU_TYPES.register(
            "universal_joint",
            () -> IMenuTypeExtension.create(UniversalJointMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<HydraulicConnectionHeadMenu>> HYDRAULIC_CONNECTION_HEAD = MENU_TYPES.register(
            "hydraulic_connection_head",
            () -> IMenuTypeExtension.create(HydraulicConnectionHeadMenu::new));

    private ModMenuTypes() {
    }

    public static void register(IEventBus modBus) {
        MENU_TYPES.register(modBus);
    }
}
