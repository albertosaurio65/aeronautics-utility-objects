package com.enxv.aerouniversaljoint.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolgunCompatibilityNotice {
    private static final String TOOLGUN_MOD_ID = "create_aeronautics_toolgun";
    private static final String MIN_TOOLGUN_VERSION = "0.1.9";
    private static final int VERSION_COMPONENTS = 3;
    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("\\d+");

    private static boolean shownForConnection;

    private ToolgunCompatibilityNotice() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ToolgunCompatibilityNotice::onClientLogin);
        NeoForge.EVENT_BUS.addListener(ToolgunCompatibilityNotice::onClientLogout);
    }

    private static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (shownForConnection) {
            return;
        }

        LocalPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }

        shownForConnection = true;
        ModList.get().getModContainerById(TOOLGUN_MOD_ID).ifPresentOrElse(
                container -> {
                    String installedVersion = container.getModInfo().getVersion().toString();
                    if (isOlderThan(installedVersion, MIN_TOOLGUN_VERSION)) {
                        player.displayClientMessage(Component.translatable(
                                "message.aeronautics_utility_objects.toolgun_outdated",
                                installedVersion,
                                MIN_TOOLGUN_VERSION
                        ), false);
                    }
                },
                () -> player.displayClientMessage(Component.translatable(
                        "message.aeronautics_utility_objects.toolgun_missing",
                        MIN_TOOLGUN_VERSION
                ), false)
        );
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        shownForConnection = false;
    }

    private static boolean isOlderThan(String version, String minimumVersion) {
        int[] actual = parseVersion(version);
        int[] minimum = parseVersion(minimumVersion);
        for (int i = 0; i < VERSION_COMPONENTS; i++) {
            if (actual[i] < minimum[i]) {
                return true;
            }
            if (actual[i] > minimum[i]) {
                return false;
            }
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        int[] components = new int[VERSION_COMPONENTS];
        if (version == null || version.isBlank()) {
            return components;
        }

        Matcher matcher = VERSION_NUMBER_PATTERN.matcher(version);
        int index = 0;
        while (matcher.find() && index < VERSION_COMPONENTS) {
            try {
                components[index] = Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
                components[index] = 0;
            }
            index++;
        }
        return components;
    }
}
