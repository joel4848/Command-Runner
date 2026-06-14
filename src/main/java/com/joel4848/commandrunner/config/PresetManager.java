package com.joel4848.commandrunner.config;

import com.joel4848.commandrunner.CommandRunner;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;

// Saves/loads preset .txt files from: <config dir>/Command Runner/Presets/
public class PresetManager {

    private static final Path PRESETS_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("Command Runner")
            .resolve("Presets");

    public record PresetInfo(String name, Path path, Instant lastModified) {}

    // Awkward if the presets directory doesn't/can't exist
    public static void ensureDir() {
        try {
            Files.createDirectories(PRESETS_DIR);
        } catch (IOException e) {
            CommandRunner.LOGGER.error("Could not create presets directory", e);
        }
    }

    // Get the presets directory path (for opening in file browser)
    public static Path getPresetsDir() {
        ensureDir();
        return PRESETS_DIR;
    }

    // List all saved presets sorted by last modified descending
    public static List<PresetInfo> listPresets() {
        ensureDir();
        List<PresetInfo> list = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(PRESETS_DIR, "*.txt")) {
            for (Path path : stream) {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                String name = path.getFileName().toString();
                name = name.substring(0, name.length() - 4); // strip .txt
                list.add(new PresetInfo(name, path, attrs.lastModifiedTime().toInstant()));
            }
        } catch (IOException e) {
            CommandRunner.LOGGER.error("Could not list presets", e);
        }
        list.sort(Comparator.comparing(PresetInfo::lastModified).reversed());
        return list;
    }

    // Check whether preset name is available
    public static boolean exists(String name) {
        return Files.exists(PRESETS_DIR.resolve(sanitise(name) + ".txt"));
    }

    // Save the preset
    public static boolean save(String name, String content) {
        ensureDir();
        try {
            Path file = PRESETS_DIR.resolve(sanitise(name) + ".txt");
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            CommandRunner.LOGGER.error("Could not save preset '{}'", name, e);
            return false;
        }
    }

    // Load a preset
    public static String load(String name) {
        ensureDir();
        try {
            Path file = PRESETS_DIR.resolve(sanitise(name) + ".txt");
            return Files.readString(file);
        } catch (IOException e) {
            CommandRunner.LOGGER.error("Could not load preset '{}'", name, e);
            return null;
        }
    }

    public static String load(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            CommandRunner.LOGGER.error("Could not load preset at '{}'", path, e);
            return null;
        }
    }

    // Delete a preset
    public static boolean delete(String name) {
        try {
            return Files.deleteIfExists(PRESETS_DIR.resolve(sanitise(name) + ".txt"));
        } catch (IOException e) {
            CommandRunner.LOGGER.error("Could not delete preset '{}'", name, e);
            return false;
        }
    }

    public static boolean delete(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            CommandRunner.LOGGER.error("Could not delete preset at '{}'", path, e);
            return false;
        }
    }

    // Make sure the preset name is okay for file names
    private static String sanitise(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
