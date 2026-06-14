package com.joel4848.commandrunner;

import net.minecraft.client.MinecraftClient;

// The meat and potatoes of the mod (actually run the commands)
public class CommandExecutor {

    // Run 'em
    public static void runAll(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String[] lines = text.split("\n", -1);
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;

            // Strip leading /
            if (line.startsWith("/")) {
                line = line.substring(1);
            }

            // Send 'em
            client.player.networkHandler.sendCommand(line);
        }
    }
}
