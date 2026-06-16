package com.joel4848.commandrunner;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArrayList;

public class CommandExecutor {

    // Track a running preset
    private static class RunningPreset {
        private final List<String> commands;
        private int waitTicksRemaining = 0;

        public RunningPreset(List<String> commands) {
            this.commands = new ArrayList<>(commands);
        }

        // Run it until it doesn't need running anymore
        public boolean tick(MinecraftClient client) {
            if (client.player == null) {
                return false; // Keep it going
            }

            // Do any wait timers
            if (waitTicksRemaining > 0) {
                waitTicksRemaining--;
                return false;
            }

            // Run the lines
            while (!commands.isEmpty() && waitTicksRemaining == 0) {
                String line = commands.remove(0);

                if (line.startsWith("!wait ")) {
                    waitTicksRemaining = parseWaitTicks(line.substring(6).trim());
                } else {
                    client.player.networkHandler.sendCommand(line);
                }
            }

            // Stop running things when we run out of things to do
            return commands.isEmpty();
        }
    }

    private static final List<RunningPreset> runningTasks = new CopyOnWriteArrayList<>();

    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || runningTasks.isEmpty()) {
                return;
            }

            List<RunningPreset> finishedTasks = new ArrayList<>();
            for (RunningPreset task : runningTasks) {
                if (task.tick(client)) {
                    finishedTasks.add(task);
                }
            }

            if (!finishedTasks.isEmpty()) {
                runningTasks.removeAll(finishedTasks);
            }
        });
    }

    public static void runAll(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String[] rawLines = text.split("\n", -1);
        List<String> cleanedLines = new ArrayList<>();

        for (String raw : rawLines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

            if (line.startsWith("/") && !line.startsWith("//")) {
                line = line.substring(1);
            }
            cleanedLines.add(line);
        }

        try {
            List<String> flattened = flattenRepeats(cleanedLines);

            runningTasks.add(new RunningPreset(flattened));

        } catch (IllegalArgumentException e) {
            client.player.sendMessage(
                    net.minecraft.text.Text.literal("§c[Macro Error] " + e.getMessage()),
                    false
            );
        }
    }

    // Flatten out any repeats
    private static List<String> flattenRepeats(List<String> sourceLines) {
        List<String> output = new ArrayList<>();
        Stack<int[]> loopStack = new Stack<>();

        for (String line : sourceLines) {
            if (line.startsWith("!repeat ")) {
                int count;
                try {
                    count = Integer.parseInt(line.substring(8).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid repeat loop count at line: " + line);
                }
                loopStack.push(new int[]{output.size(), count});

            } else if (line.equals("!endrepeat")) {
                if (loopStack.isEmpty()) {
                    throw new IllegalArgumentException("Found '!endrepeat' without a matching '!repeat'");
                }

                int[] loopMetaData = loopStack.pop();
                int startIndex = loopMetaData[0];
                int totalIterations = loopMetaData[1];

                List<String> loopContents = new ArrayList<>(output.subList(startIndex, output.size()));

                for (int iteration = 1; iteration < totalIterations; iteration++) {
                    output.addAll(loopContents);
                }

            } else {
                output.add(line);
            }
        }

        if (!loopStack.isEmpty()) {
            throw new IllegalArgumentException("Missing closed '!endrepeat' for an active loop block.");
        }

        return output;
    }

    // Turn wait times into ticks
    private static int parseWaitTicks(String input) {
        if (input.isEmpty()) return 0;

        try {
            char lastChar = input.charAt(input.length() - 1);
            if (Character.isDigit(lastChar)) {
                return Math.max(0, Integer.parseInt(input));
            }

            int value = Integer.parseInt(input.substring(0, input.length() - 1));

            return switch (lastChar) {
                case 't' -> Math.max(0, value);
                case 's' -> Math.max(0, value * 20);
                case 'd' -> Math.max(0, value * 24000);
                default  -> Math.max(0, value);
            };
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}