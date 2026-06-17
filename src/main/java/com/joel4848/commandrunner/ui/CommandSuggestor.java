package com.joel4848.commandrunner.ui;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.command.CommandSource;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;

import com.joel4848.commandrunner.mixin.CommandSuggestorAccessor;
import com.joel4848.commandrunner.mixin.SuggestionWindowAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CommandSuggestor extends ChatInputSuggestor {

    private static final List<Style> HIGHLIGHT_STYLES = Stream.of(
            Formatting.AQUA, Formatting.YELLOW, Formatting.GREEN,
            Formatting.LIGHT_PURPLE, Formatting.GOLD
    ).map(Style.EMPTY::withColor).collect(ImmutableList.toImmutableList());

    private final CommandSuggestorAccessor accessor;
    private int posX, posY;
    private boolean active = false;

    public CommandSuggestor(MinecraftClient client, Screen owner, TextFieldWidget textField,
                            TextRenderer textRenderer, boolean slashOptional,
                            boolean suggestingWhenEmpty, int inWindowIndexOffset,
                            int maxSuggestionSize, boolean chatScreenSized, int color) {
        super(client, owner, textField, textRenderer, slashOptional, suggestingWhenEmpty,
                inWindowIndexOffset, maxSuggestionSize, chatScreenSized, color);
        this.accessor = (CommandSuggestorAccessor) this;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY) {
        if (!active) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        if (accessor.getWindow() == null) {
            List<OrderedText> messages = accessor.getMessages();
            int i = 0;
            for (OrderedText msg : messages) {
                int lineY = posY + i * 10;
                context.fill(posX - 1, lineY, posX + accessor.getWidth() + 1, lineY + 12, accessor.getColor());
                context.drawTextWithShadow(tr, msg, posX, lineY + 2, -1);
                i++;
            }
        } else {
            accessor.getWindow().render(context, mouseX, mouseY);
        }
    }

    @Override
    public void refresh() {
        com.joel4848.commandrunner.mixin.CommandSuggestorAccessor accessor =
                (com.joel4848.commandrunner.mixin.CommandSuggestorAccessor) this;

        if (accessor.getTextField() instanceof CommandTextField multilineField) {
            String activeLine = multilineField.getActiveLineRaw();

            if (activeLine.startsWith("!")) {
                accessor.setWindow(null);
                this.setWindowActive(true);
                return;
            }
        }
        super.refresh();
    }

    public void refreshRenderPos() {
        try {
            SuggestionWindowAccessor window = (SuggestionWindowAccessor) accessor.getWindow();
            if (window != null) {
                Rect2i area = new Rect2i(posX, posY, window.getArea().getWidth(), window.getArea().getHeight());
                window.setArea(area);
            }
        } catch (Exception ignored) {}
    }

    public void setPos(int x, int y) {
        this.posX = x;
        this.posY = y;
    }

    public int getPosX() { return posX; }
    public int getPosY() { return posY; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active) return false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public List<Pair<Integer, Integer>> getColorsForLine(String lineCmd, int firstCharacterIndex) {
        if (accessor.getParse() == null) {
            return new ArrayList<>();
        }
        ParseResults<CommandSource> parse = accessor.getParse();

        ArrayList<Pair<Integer, Integer>> list = new ArrayList<>();
        list.add(new Pair<>(1, 0));
        int colorIndex = -1;

        CommandContextBuilder<CommandSource> ctx = parse.getContext();
        do {
            for (ParsedArgument<CommandSource, ?> arg : ctx.getArguments().values()) {
                colorIndex = bumpColorIndex(colorIndex);
                int k = Math.max(arg.getRange().getStart() - firstCharacterIndex, 0);
                if (k >= lineCmd.length()) break;
                int l = Math.min(arg.getRange().getEnd() - firstCharacterIndex, lineCmd.length());
                if (l <= 0) continue;
                list.add(new Pair<>(colorIndex + 2, k));
                list.add(new Pair<>(1, l));
            }
            ctx = ctx.getChild();
        } while (ctx != null);

        if (parse.getReader().canRead()) {
            int m = Math.max(parse.getReader().getCursor() - firstCharacterIndex, 0);
            if (m < lineCmd.length()) {
                list.add(new Pair<>(0, m));
            }
        }

        return list;
    }

    public List<Pair<Integer, Integer>> getIsolatedColorsForLine(String lineCmd) {
        List<Pair<Integer, Integer>> list = new ArrayList<>();
        list.add(new Pair<>(1, 0));

        MinecraftClient clientInstance = MinecraftClient.getInstance();
        if (lineCmd.isEmpty() || clientInstance.player == null) {
            return list;
        }

        com.mojang.brigadier.StringReader reader = new com.mojang.brigadier.StringReader(lineCmd);
        if (reader.canRead() && reader.peek() == '/') {
            reader.skip();
        }

        net.minecraft.client.network.ClientPlayNetworkHandler networkHandler = clientInstance.player.networkHandler;
        if (networkHandler == null) return list;

        com.mojang.brigadier.CommandDispatcher<net.minecraft.command.CommandSource> dispatcher = networkHandler.getCommandDispatcher();
        com.mojang.brigadier.ParseResults<net.minecraft.command.CommandSource> isolatedParse = dispatcher.parse(reader, networkHandler.getCommandSource());

        int colorIndex = -1;
        com.mojang.brigadier.context.CommandContextBuilder<net.minecraft.command.CommandSource> ctx = isolatedParse.getContext();

        do {
            for (com.mojang.brigadier.context.ParsedArgument<net.minecraft.command.CommandSource, ?> arg : ctx.getArguments().values()) {
                colorIndex = bumpColorIndex(colorIndex);
                int k = Math.max(arg.getRange().getStart(), 0);
                if (k >= lineCmd.length()) break;
                int l = Math.min(arg.getRange().getEnd(), lineCmd.length());
                if (l <= 0) continue;
                list.add(new Pair<>(colorIndex + 2, k));
                list.add(new Pair<>(1, l));
            }
            ctx = ctx.getChild();
        } while (ctx != null);

        if (isolatedParse.getReader().canRead()) {
            int m = Math.max(isolatedParse.getReader().getCursor(), 0);
            if (m < lineCmd.length()) {
                list.add(new Pair<>(0, m));
            }
        }

        return list;
    }

    public Style getHighlightStyle(int colorIndex) {
        if (colorIndex == 0) return Style.EMPTY.withColor(Formatting.RED);
        if (colorIndex == 1) return Style.EMPTY.withColor(Formatting.GRAY);
        int idx = (colorIndex - 2) % HIGHLIGHT_STYLES.size();
        return HIGHLIGHT_STYLES.get(idx);
    }

    private int bumpColorIndex(int colorIndex) {
        return (colorIndex + 1) % HIGHLIGHT_STYLES.size();
    }
}