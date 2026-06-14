package com.joel4848.commandrunner;

import com.joel4848.commandrunner.schedule.ScheduleManager;
import com.joel4848.commandrunner.screen.MainScreen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandRunner implements ModInitializer {
    public static final String MOD_ID = "command-runner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyBinding openScreenKey;

    @Override
    public void onInitialize() {
        LOGGER.info("Command Runner initialised.");

        // Register the keybinding (Defaulted to UNKNOWN so the user must set it in controls)
        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.command-runner.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.command-runner"
        ));

        // Register the client tick event to poll key presses and run schedule tasks
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreenKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new MainScreen());
                }
            }
            ScheduleManager.clientTick(client);
        });

        // Start the schedule manager
        ScheduleManager.init();
    }
}