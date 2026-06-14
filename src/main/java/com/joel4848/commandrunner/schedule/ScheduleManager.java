package com.joel4848.commandrunner.schedule;

import com.joel4848.commandrunner.CommandExecutor;
import com.joel4848.commandrunner.CommandRunner;
import com.joel4848.commandrunner.config.PresetManager;
import com.joel4848.commandrunner.config.ScheduleConfig;
import com.joel4848.commandrunner.config.ScheduleConfig.TriggerType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Central manager that:
 *  - loads all preset schedule configs at startup / world-join
 *  - tracks tick counters for EVERY_X triggers
 *  - exposes hooks that mixins call for damage / death / respawn / sleep / wake / weather
 *  - checks key-press each tick
 *  - checks low-health each tick
 */
public class ScheduleManager {

    // Each entry: (presetName, scheduleData)
    private static final List<Map.Entry<String, ScheduleConfig.ScheduleData>> schedules = new ArrayList<>();
    // Per-preset tick counter for EVERY_X triggers
    private static final Map<String, Integer> tickCounters = new HashMap<>();

    // State tracking for one-shot events we're checking by polling
    private static float prevHealthFraction = 1.0f;
    private static boolean prevSleeping = false;
    private static boolean prevRaining = false;
    private static boolean deadLastTick = false;
    private static boolean wasDeadLastTick = false; // for respawn detection

    public static void init() {
        // Reload schedules when connecting to a world
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reloadSchedules());
        // Do any loading triggers when joining
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> fireTrigger(TriggerType.ON_LOAD))
        );
    }

    // Refresh the cached list
    public static void reloadSchedules() {
        schedules.clear();
        tickCounters.clear();
        for (PresetManager.PresetInfo info : PresetManager.listPresets()) {
            ScheduleConfig.ScheduleData data = ScheduleConfig.load(info.name());
            if (!data.triggers.isEmpty()) {
                schedules.add(Map.entry(info.name(), data));
                tickCounters.put(info.name(), 0);
            }
        }
    }

    // Ticky bit
    public static void clientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        // Every x
        for (Map.Entry<String, ScheduleConfig.ScheduleData> entry : schedules) {
            String name = entry.getKey();
            ScheduleConfig.ScheduleData data = entry.getValue();
            if (data.has(TriggerType.EVERY_X)) {
                int period;
                try { period = Integer.parseInt(data.get(TriggerType.EVERY_X)); }
                catch (NumberFormatException e) { continue; }
                if (period <= 0) continue;
                int count = tickCounters.merge(name, 1, Integer::sum);
                if (count >= period) {
                    tickCounters.put(name, 0);
                    runPreset(name);
                }
            }
        }

        // When pressy key
        long windowHandle = client.getWindow().getHandle();
        for (Map.Entry<String, ScheduleConfig.ScheduleData> entry : schedules) {
            ScheduleConfig.ScheduleData data = entry.getValue();
            if (data.has(TriggerType.ON_KEY)) {
                String keyId = data.get(TriggerType.ON_KEY);
                try {
                    InputUtil.Key key = InputUtil.fromTranslationKey(keyId);
                    if (InputUtil.isKeyPressed(windowHandle, key.getCode())) {
                        runPreset(entry.getKey());
                    }
                } catch (Exception ignored) {}
            }
        }

        // When low on health
        float healthFraction = client.player.getHealth() / client.player.getMaxHealth();
        for (Map.Entry<String, ScheduleConfig.ScheduleData> entry : schedules) {
            ScheduleConfig.ScheduleData data = entry.getValue();
            if (data.has(TriggerType.ON_LOW_HEALTH)) {
                try {
                    float threshold = Integer.parseInt(data.get(TriggerType.ON_LOW_HEALTH)) / 100.0f;
                    // Fire when crossing the threshold downward
                    if (prevHealthFraction > threshold && healthFraction <= threshold) {
                        runPreset(entry.getKey());
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        prevHealthFraction = healthFraction;

        // When player goes from alive to not-so-alive)
        boolean isDead = client.player.isDead();
        if (isDead && !deadLastTick) {
            fireTrigger(TriggerType.ON_DEATH);
        }
        // When player goes from not-so-alive to somewhat-alive)
        if (!isDead && deadLastTick) {
            fireTrigger(TriggerType.ON_RESPAWN);
        }
        deadLastTick = isDead;

        // On sleepy
        boolean sleeping = client.player.isSleeping();
        if (sleeping && !prevSleeping) {
            fireTrigger(TriggerType.ON_SLEEP);
        }
        // On unsleepy
        if (!sleeping && prevSleeping) {
            fireTrigger(TriggerType.ON_WAKE);
        }
        prevSleeping = sleeping;

        // When weather changes
        boolean raining = client.world.isRaining();
        if (raining != prevRaining) {
            fireTrigger(TriggerType.ON_WEATHER);
        }
        prevRaining = raining;
    }

    // When get ouched
    public static void onDamageTaken() {
        fireTrigger(TriggerType.ON_DAMAGE);
    }

    private static void fireTrigger(TriggerType type) {
        for (Map.Entry<String, ScheduleConfig.ScheduleData> entry : schedules) {
            if (entry.getValue().has(type)) {
                runPreset(entry.getKey());
            }
        }
    }

    private static void runPreset(String presetName) {
        String content = PresetManager.load(presetName);
        if (content != null && !content.isBlank()) {
            CommandExecutor.runAll(content);
        }
    }
}
