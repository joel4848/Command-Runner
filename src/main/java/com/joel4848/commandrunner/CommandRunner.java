package com.joel4848.commandrunner;

import com.joel4848.commandrunner.config.PresetManager;
import com.joel4848.commandrunner.schedule.ScheduleManager;
import com.joel4848.commandrunner.screen.MainScreen;
import com.joel4848.commandrunner.screen.ScheduleScreen;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class CommandRunner implements ModInitializer {
    public static final String MOD_ID = "command-runner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyBinding openScreenKey;

    private static Runnable pendingScreenAction = null;

    @Override
    public void onInitialize() {
        LOGGER.info("Command Runner initialised.");

        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.command-runner.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.command-runner"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreenKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new MainScreen());
                }
            }

            if (pendingScreenAction != null && client.player != null) {
                pendingScreenAction.run();
                pendingScreenAction = null;
            }

            ScheduleManager.clientTick(client);
        });

        ScheduleManager.init();

        registerCommands();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("commandrunner")
                    // Base command (opens main screen)
                    .executes(ctx -> {
                        queueScreenOpen(() -> MinecraftClient.getInstance().setScreen(new MainScreen()));
                        return 1;
                    })

                    // Open
                    .then(ClientCommandManager.literal("open")
                            .executes(ctx -> {
                                queueScreenOpen(() -> MinecraftClient.getInstance().setScreen(new MainScreen()));
                                return 1;
                            })
                            .then(ClientCommandManager.argument("preset", StringArgumentType.greedyString())
                                    .suggests(CommandRunner::suggestPresets)
                                    .executes(ctx -> {
                                        String preset = StringArgumentType.getString(ctx, "preset");
                                        queueScreenOpen(() -> {
                                            MainScreen screen = new MainScreen();

                                            screen.loadedPresetName = preset;

                                            screen.pendingContent = PresetManager.load(preset);

                                            MinecraftClient.getInstance().setScreen(screen);
                                        });
                                        return 1;
                                    })
                            )
                    )

                    // Run
                    .then(ClientCommandManager.literal("run")
                            .then(ClientCommandManager.argument("preset", StringArgumentType.greedyString())
                                    .suggests(CommandRunner::suggestPresets)
                                    .executes(ctx -> {
                                        String preset = StringArgumentType.getString(ctx, "preset");
                                        String content = PresetManager.load(preset);
                                        if (content != null && !content.isBlank()) {
                                            com.joel4848.commandrunner.CommandExecutor.runAll(content);
                                        } else {
                                            ctx.getSource().sendError(Text.literal("Preset '" + preset + "' is empty or does not exist."));
                                        }
                                        return 1;
                                    })
                            )
                    )

                    // Schedule
                    .then(ClientCommandManager.literal("schedule")
                            .then(ClientCommandManager.argument("preset", StringArgumentType.greedyString())
                                    .suggests(CommandRunner::suggestPresets)
                                    .executes(ctx -> {
                                        String preset = StringArgumentType.getString(ctx, "preset");
                                        if (PresetManager.exists(preset)) {
                                            queueScreenOpen(() -> MinecraftClient.getInstance().setScreen(new ScheduleScreen(new MainScreen(), preset)));
                                        } else {
                                            ctx.getSource().sendError(Text.literal("Preset '" + preset + "' does not exist. Save it first!"));
                                        }
                                        return 1;
                                    })
                            )
                    )

                    // Delete
                    .then(ClientCommandManager.literal("delete")
                            .then(ClientCommandManager.argument("preset", StringArgumentType.greedyString())
                                    .suggests(CommandRunner::suggestPresets)
                                    .executes(ctx -> {
                                        String preset = StringArgumentType.getString(ctx, "preset");

                                        if (preset.startsWith("__confirm_delete_")) {
                                            String cleanName = preset.substring("__confirm_delete_".length());
                                            if (PresetManager.delete(cleanName)) {
                                                ctx.getSource().sendFeedback(Text.literal("Deleted preset: " + cleanName).formatted(Formatting.GREEN));
                                                ScheduleManager.reloadSchedules();
                                            } else {
                                                ctx.getSource().sendError(Text.literal("Failed to delete preset."));
                                            }
                                            return 1;
                                        }

                                        if (!PresetManager.exists(preset)) {
                                            ctx.getSource().sendError(Text.literal("Preset '" + preset + "' does not exist."));
                                            return 0;
                                        }

                                        MutableText message = Text.empty()
                                                .append(Text.literal("\nAre you sure you want to delete '" + preset + "'? This cannot be undone!\n").formatted(Formatting.RED))
                                                .append(Text.literal("[CLICK TO CONFIRM DELETE]").formatted(Formatting.DARK_RED, Formatting.BOLD)
                                                        .styled(style -> style
                                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/commandrunner delete __confirm_delete_" + preset))
                                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Permanently delete file")))));

                                        ctx.getSource().sendFeedback(message);
                                        return 1;
                                    })
                            )
                    )
            );
        });
    }

    private static CompletableFuture<Suggestions> suggestPresets(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        PresetManager.listPresets().stream()
                .map(PresetManager.PresetInfo::name)
                .filter(name -> name.toLowerCase().startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static void queueScreenOpen(Runnable action) {
        pendingScreenAction = action;
    }
}