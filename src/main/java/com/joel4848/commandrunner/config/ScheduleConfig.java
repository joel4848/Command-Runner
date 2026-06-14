package com.joel4848.commandrunner.config;

import com.joel4848.commandrunner.CommandRunner;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

// Format:
//   EVERY_X=60 (in ticks)
//   ON_LOAD=true
//   ON_DEATH=true
//   ON_RESPAWN=true
//   ON_DAMAGE=true
//   ON_KEY=key.keyboard.f6
//   ON_LOW_HEALTH=30 (percentage, also a whole number)
//   ON_SLEEP=true
//   ON_WAKE=true
//   ON_WEATHER=true

public class ScheduleConfig {

    private static final Path SCHEDULES_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("Command Runner")
            .resolve("Schedules");

    public enum TriggerType {
        EVERY_X,
        ON_LOAD,
        ON_DEATH,
        ON_RESPAWN,
        ON_DAMAGE,
        ON_KEY,
        ON_LOW_HEALTH,
        ON_SLEEP,
        ON_WAKE,
        ON_WEATHER
    }

    public static class ScheduleData {
        public final Map<TriggerType, String> triggers = new EnumMap<>(TriggerType.class);

        public boolean has(TriggerType t) { return triggers.containsKey(t); }
        public String get(TriggerType t) { return triggers.getOrDefault(t, ""); }
        public void set(TriggerType t, String value) { triggers.put(t, value); }
        public void clear(TriggerType t) { triggers.remove(t); }
    }

    private static void ensureDir() {
        try {
            Files.createDirectories(SCHEDULES_DIR);
        } catch (IOException e) {
            CommandRunner.LOGGER.error("Could not create schedules directory", e);
        }
    }

    private static Path schedulePath(String presetName) {
        return SCHEDULES_DIR.resolve(sanitise(presetName) + ".schedule");
    }

    public static ScheduleData load(String presetName) {
        ensureDir();
        ScheduleData data = new ScheduleData();
        Path path = schedulePath(presetName);
        if (!Files.exists(path)) return data;
        try {
            Properties props = new Properties();
            try (Reader r = Files.newBufferedReader(path)) {
                props.load(r);
            }
            for (TriggerType t : TriggerType.values()) {
                String val = props.getProperty(t.name());
                if (val != null && !val.isEmpty()) {
                    data.set(t, val);
                }
            }
        } catch (IOException e) {
            CommandRunner.LOGGER.error("Could not load schedule for '{}'", presetName, e);
        }
        return data;
    }

    public static void save(String presetName, ScheduleData data) {
        ensureDir();
        Properties props = new Properties();
        for (Map.Entry<TriggerType, String> entry : data.triggers.entrySet()) {
            props.setProperty(entry.getKey().name(), entry.getValue());
        }
        try (Writer w = Files.newBufferedWriter(schedulePath(presetName))) {
            props.store(w, "Command Runner schedule for: " + presetName);
        } catch (IOException e) {
            CommandRunner.LOGGER.error("Could not save schedule for '{}'", presetName, e);
        }
    }

    public static void delete(String presetName) {
        try {
            Files.deleteIfExists(schedulePath(presetName));
        } catch (IOException e) {
            CommandRunner.LOGGER.error("Could not delete schedule for '{}'", presetName, e);
        }
    }

    private static String sanitise(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
