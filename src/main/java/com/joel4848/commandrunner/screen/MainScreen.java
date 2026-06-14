package com.joel4848.commandrunner.screen;

import com.joel4848.commandrunner.CommandExecutor;
import com.joel4848.commandrunner.config.PresetManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// Why

public class MainScreen extends Screen {

    // Layout
    private static final int PADDING          = 8;
    private static final int TITLE_BOX_HEIGHT = 20;
    private static final int BUTTON_HEIGHT    = 20;
    private static final int CLOSE_BTN_SIZE   = 20;
    private static final String PLACEHOLDER   = "(Enter New Preset Name)";
    private static final int LINE_H           = 12;

    // Dropdown appearance
    private static final int DROPDOWN_ROW_H    = 12;
    private static final int DROPDOWN_PAD      = 3;
    private static final int DROPDOWN_MAX_ROWS = 10;
    private static final int COL_BG            = 0xC0101010;
    private static final int COL_SELECTED_BG   = 0xFF1A3A6A;
    private static final int COL_TEXT          = 0xFCFC00;
    private static final int COL_SUFFIX        = 0xFCFC00;
    private static final int COL_BORDER        = 0xFF555555;

    private static final int COL_GHOST_TEXT    = -8355712; // Vanilla Dark Gray
    private static final int COL_SYNTAX_ERROR  = 0xFFFF5555; // Vanilla Error Red

    // Persistent state
    private String loadedPresetName = null;
    private String pendingContent   = null;

    // Widgety bois
    private TextFieldWidget titleBox;
    private EditBoxWidget   teb;
    private ButtonWidget    clearBtn, loadBtn, saveBtn, scheduleBtn, runBtn, closeBtn;

    // TEB geometry bits ('TEB' = the Text Entry Box; we're best buds now)
    private int tebLeft, tebTop, tebBottom, tebWidth;

    // Active line tracking
    private int activeLine = 0;

    private CompletableFuture<Suggestions> pendingSuggestions = null;
    private List<Suggestion> baseSuggestions        = Collections.emptyList();
    private List<Suggestion> suggestions            = Collections.emptyList();
    private String           typedToken             = "";
    private int              suggestionRangeStart   = 0;
    private int              selectedSuggestion     = -1;
    private boolean          dropdownOpen           = false;
    private boolean          cycling                = false;
    private boolean          suppressChangeListener = false;

    // Is there a mistake
    private boolean          currentTokenHasError   = false;

    // Do we warn
    private Text warningMessage = null;
    private int  warningTimer   = 0;

    public MainScreen() {
        super(Text.literal("Command Runner"));
    }

    public void loadPreset(String name, String content) {
        this.loadedPresetName = name;
        this.pendingContent   = content;
    }

    // Init bits
    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;

        closeBtn = ButtonWidget.builder(Text.literal("✕"), btn -> close())
                .dimensions(w - PADDING - CLOSE_BTN_SIZE, PADDING, CLOSE_BTN_SIZE, CLOSE_BTN_SIZE)
                .build();
        addDrawableChild(closeBtn);

        int titleW = w - PADDING * 3 - CLOSE_BTN_SIZE;
        titleBox = new TextFieldWidget(textRenderer, PADDING, PADDING, titleW, TITLE_BOX_HEIGHT,
                Text.literal("Preset Name"));
        titleBox.setMaxLength(128);
        titleBox.setPlaceholder(
                Text.literal(PLACEHOLDER).formatted(net.minecraft.util.Formatting.DARK_GRAY));
        if (loadedPresetName != null) titleBox.setText(loadedPresetName);
        addDrawableChild(titleBox);

        tebLeft   = PADDING;
        tebTop    = PADDING + TITLE_BOX_HEIGHT + PADDING;
        tebBottom = h - PADDING - BUTTON_HEIGHT - PADDING;
        tebWidth  = w - PADDING * 2;

        teb = new EditBoxWidget(textRenderer,
                tebLeft, tebTop, tebWidth, tebBottom - tebTop,
                Text.literal("Enter commands here - one per line"),
                Text.literal(""));
        teb.setMaxLength(Integer.MAX_VALUE / 2);
        if (pendingContent != null) {
            teb.setText(pendingContent);
            pendingContent = null;
        }
        teb.setChangeListener(newText -> {
            if (suppressChangeListener) return;

            cycling = false;

            int n = 0;
            for (int i = 0; i < newText.length(); i++) if (newText.charAt(i) == '\n') n++;
            activeLine = n;

            requestCompletions();
        });
        addDrawableChild(teb);

        int btnY   = h - PADDING - BUTTON_HEIGHT;
        int totalW = w - PADDING * 2;
        int btnCount = 5, gap = 4;
        int btnW  = (totalW - gap * (btnCount - 1)) / btnCount;
        int[] btnX = new int[btnCount];
        btnX[0] = PADDING;
        for (int i = 1; i < btnCount; i++) btnX[i] = btnX[i - 1] + btnW + gap;

        clearBtn = ButtonWidget.builder(Text.literal("Clear"), btn -> {
            suppressChangeListener = true;
            teb.setText("");
            suppressChangeListener = false;
            titleBox.setText("");
            warningMessage = null;
            closeDropdown();
        }).dimensions(btnX[0], btnY, btnW, BUTTON_HEIGHT).build();

        loadBtn = ButtonWidget.builder(Text.literal("Load"),
                        btn -> MinecraftClient.getInstance().setScreen(new LoadPresetScreen(this)))
                .dimensions(btnX[1], btnY, btnW, BUTTON_HEIGHT).build();

        saveBtn = ButtonWidget.builder(Text.literal("Save"), btn -> handleSave())
                .dimensions(btnX[2], btnY, btnW, BUTTON_HEIGHT).build();

        scheduleBtn = ButtonWidget.builder(Text.literal("Schedule"), btn -> {
            String name = getPresetName();
            if (name == null) showWarning("You must enter a name for the preset first!");
            else MinecraftClient.getInstance().setScreen(new ScheduleScreen(this, name));
        }).dimensions(btnX[3], btnY, btnW, BUTTON_HEIGHT).build();

        runBtn = ButtonWidget.builder(Text.literal("Run"), btn -> {
            String text = teb.getText();
            if (!text.isBlank()) { close(); CommandExecutor.runAll(text); }
        }).dimensions(btnX[4], btnY, btnW, BUTTON_HEIGHT).build();

        addDrawableChild(clearBtn);
        addDrawableChild(loadBtn);
        addDrawableChild(saveBtn);
        addDrawableChild(scheduleBtn);
        addDrawableChild(runBtn);

        setInitialFocus(teb);
    }

    // Autocomplete stuff
    private String getLine(int index) {
        String[] lines = teb.getText().split("\n", -1);
        return (index >= 0 && index < lines.length) ? lines[index] : "";
    }

    private String toCmd(String line) {
        return line.startsWith("/") ? line.substring(1) : line;
    }

    private void requestCompletions() {
        String line = getLine(activeLine);
        String cmd  = toCmd(line);

        int lastSpace = cmd.lastIndexOf(' ');
        if (lastSpace >= 0) {
            typedToken           = cmd.substring(lastSpace + 1);
            suggestionRangeStart = lastSpace + 1;
            fetchFor(cmd.substring(0, lastSpace + 1), cmd);
        } else {
            typedToken           = cmd;
            suggestionRangeStart = 0;
            fetchFor("", cmd);
        }
    }

    private void fetchFor(String base, String fullLineCmd) {
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) { closeDropdown(); return; }

        CommandDispatcher<CommandSource> dispatcher = handler.getCommandDispatcher();
        CommandSource source = handler.getCommandSource();

        pendingSuggestions = null;

        ParseResults<CommandSource> parse = dispatcher.parse(new StringReader(fullLineCmd), source);

        currentTokenHasError = false;
        for (Map.Entry<?, CommandSyntaxException> entry : parse.getExceptions().entrySet()) {
            int cursorErrorIdx = entry.getValue().getCursor();
            if (cursorErrorIdx >= suggestionRangeStart) {
                currentTokenHasError = true;
                break;
            }
        }

        CompletableFuture<Suggestions> future =
                dispatcher.getCompletionSuggestions(parse, base.length());

        pendingSuggestions = future;
        future.thenAccept(s -> {
            if (future != pendingSuggestions) return;
            baseSuggestions = s.getList().stream()
                    .filter(sg -> !sg.getText().isEmpty())
                    .toList();
            applyTokenFilter();
        });
    }

    private void applyTokenFilter() {
        String lower = typedToken.toLowerCase();
        suggestions = baseSuggestions.stream()
                .filter(sg -> sg.getText().toLowerCase().startsWith(lower))
                .toList();
        if (suggestions.isEmpty()) {
            dropdownOpen       = false;
            selectedSuggestion = -1;
        } else {
            dropdownOpen = true;
            if (!cycling) {
                selectedSuggestion = -1;
            } else {
                selectedSuggestion = Math.min(selectedSuggestion, suggestions.size() - 1);
            }
        }
    }

    private void closeDropdown() {
        dropdownOpen         = false;
        baseSuggestions      = Collections.emptyList();
        suggestions          = Collections.emptyList();
        typedToken           = "";
        selectedSuggestion   = -1;
        pendingSuggestions   = null;
        cycling              = false;
        currentTokenHasError = false;
    }

    private void previewSuggestion(int index) {
        if (index < 0 || index >= suggestions.size()) return;
        Suggestion s      = suggestions.get(index);
        String[] lines    = teb.getText().split("\n", -1);
        int      idx      = Math.min(activeLine, lines.length - 1);
        String   line     = lines[idx];
        boolean  hadSlash = line.startsWith("/");
        String   cmd      = toCmd(line);

        String newCmd = cmd.substring(0, suggestionRangeStart) + s.getText();
        lines[idx]    = (hadSlash ? "/" : "") + newCmd;

        suppressChangeListener = true;
        teb.setText(String.join("\n", lines));
        suppressChangeListener = false;
    }

    private void acceptSuggestion(int index) {
        if (index < 0 || index >= suggestions.size()) return;
        Suggestion s      = suggestions.get(index);
        String[] lines    = teb.getText().split("\n", -1);
        int      idx      = Math.min(activeLine, lines.length - 1);
        String   line     = lines[idx];
        boolean  hadSlash = line.startsWith("/");
        String   cmd      = toCmd(line);

        String newCmd = cmd.substring(0, suggestionRangeStart) + s.getText();
        lines[idx]    = (hadSlash ? "/" : "") + newCmd;

        cycling = false;
        teb.setText(String.join("\n", lines));
    }

    // Suggestion dropdown box misery
    private int activeLineY() {
        return tebTop + activeLine * LINE_H;
    }

    private int tokenX() {
        String line          = getLine(activeLine);
        boolean hadSlash     = line.startsWith("/");
        String  cmd          = toCmd(line);
        String  beforeToken  = cmd.substring(0, Math.min(suggestionRangeStart, cmd.length()));
        String  displayBefore = (hadSlash ? "/" : "") + beforeToken;
        return tebLeft + DROPDOWN_PAD + textRenderer.getWidth(displayBefore);
    }

    private int dropdownWidth() {
        int maxW = 0;
        for (Suggestion s : suggestions) maxW = Math.max(maxW, textRenderer.getWidth(s.getText()));
        int w      = maxW + DROPDOWN_PAD * 2 + 4;
        int startX = tokenX();
        return Math.min(w, tebLeft + tebWidth - startX);
    }

    private int[] dropdownBounds() {
        int rows  = Math.min(suggestions.size(), DROPDOWN_MAX_ROWS);
        int ddH   = rows * DROPDOWN_ROW_H + DROPDOWN_PAD * 2;
        int ddW   = dropdownWidth();
        int ddX   = tokenX();

        int maxVisibleLines = (tebBottom - tebTop) / (LINE_H - 3);
        int scrollCeiling   = maxVisibleLines - 2;
        int effectiveLine   = Math.min(activeLine, scrollCeiling);

        int lineY = tebTop + effectiveLine * LINE_H;
        lineY -= (effectiveLine - 1) * 3;

        int ddY = (lineY - ddH >= tebTop) ? lineY - ddH : lineY + LINE_H;

        if (lineY - ddH >= tebTop) {
            ddY += 1;
        } else {
            ddY -= 2;
        }

        if (activeLine > scrollCeiling) {
            ddY += 2;
        }

        return new int[]{ddX, ddY, ddW, ddH};
    }

    // Helper bois
    private void handleSave() {
        String name = getPresetName();
        if (name == null) { showWarning("You must enter a name for the preset!"); return; }
        if (PresetManager.exists(name) && !name.equalsIgnoreCase(loadedPresetName)) {
            showWarning("This name is already in use!"); return;
        }
        if (PresetManager.save(name, teb.getText())) {
            loadedPresetName = name;
            showWarning("Saved!");
            com.joel4848.commandrunner.schedule.ScheduleManager.reloadSchedules();
        } else {
            showWarning("Error saving preset.");
        }
    }

    private String getPresetName() {
        String t = titleBox.getText().trim();
        return (t.isEmpty() || t.equals(PLACEHOLDER)) ? null : t;
    }

    private void showWarning(String msg) {
        warningMessage = Text.literal(msg);
        warningTimer   = 80;
    }

    @Override
    public void tick() {
        super.tick();
        if (warningTimer > 0 && --warningTimer == 0) warningMessage = null;
    }

    // Rendering misery
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // I hated this
        if (dropdownOpen && !suggestions.isEmpty() && !cycling) {
            int activeIndex = Math.max(0, selectedSuggestion);
            Suggestion topSuggestion = suggestions.get(activeIndex);
            String fullWord = topSuggestion.getText();

            if (fullWord.toLowerCase().startsWith(typedToken.toLowerCase())) {
                String ghostSuffix = fullWord.substring(typedToken.length());

                int currentTokenX = tokenX() + textRenderer.getWidth(typedToken) + 1;

                int maxVisibleLines = (tebBottom - tebTop) / (LINE_H - 3);
                int scrollCeiling   = maxVisibleLines - 2;
                int effectiveLine   = Math.min(activeLine, scrollCeiling);

                int currentLineY = tebTop + effectiveLine * LINE_H + (LINE_H - textRenderer.fontHeight) / 2;
                currentLineY -= (effectiveLine - 1) * 3;

                // Also this
                if (activeLine > scrollCeiling) {
                    currentLineY += 2;
                }

                context.drawTextWithShadow(textRenderer, ghostSuffix, currentTokenX, currentLineY, COL_GHOST_TEXT);
            }
        }

        if (dropdownOpen && !suggestions.isEmpty()) {
            context.enableScissor(tebLeft, tebTop, tebLeft + tebWidth, tebBottom);
            renderDropdown(context, mouseX, mouseY);
            context.disableScissor();
        }

        if (warningMessage != null) {
            int msgY = height - PADDING - BUTTON_HEIGHT - PADDING - textRenderer.fontHeight - 2;
            context.drawCenteredTextWithShadow(textRenderer, warningMessage, width / 2, msgY,
                    warningTimer > 0 ? 0xFFFF5555 : 0xFF55FF55);
        }
    }

    private void renderDropdown(DrawContext context, int mouseX, int mouseY) {
        int[] b  = dropdownBounds();
        int ddX  = b[0], ddY = b[1], ddW = b[2], ddH = b[3];
        int rows = Math.min(suggestions.size(), DROPDOWN_MAX_ROWS);

        context.fill(ddX, ddY, ddX + ddW, ddY + ddH, COL_BG);
        context.drawBorder(ddX, ddY, ddW, ddH, COL_BORDER);

        int dynamicTextPrefixColor = currentTokenHasError ? COL_SYNTAX_ERROR : COL_TEXT;

        for (int i = 0; i < rows; i++) {
            int rowY    = ddY + DROPDOWN_PAD + i * DROPDOWN_ROW_H;
            boolean sel = i == selectedSuggestion;
            boolean hov = mouseX >= ddX && mouseX < ddX + ddW
                    && mouseY >= rowY && mouseY < rowY + DROPDOWN_ROW_H;

            if (sel || hov) {
                context.fill(ddX + 1, rowY, ddX + ddW - 1, rowY + DROPDOWN_ROW_H, COL_SELECTED_BG);
            }

            Suggestion s    = suggestions.get(i);
            String     full = s.getText();
            int tokenLen    = Math.min(typedToken.length(), full.length());
            String typed    = full.substring(0, tokenLen);
            String suffix   = full.substring(tokenLen);

            int textX = ddX + DROPDOWN_PAD + 2;
            int textY = rowY + (DROPDOWN_ROW_H - textRenderer.fontHeight) / 2;

            context.drawTextWithShadow(textRenderer, Text.literal(typed), textX, textY, dynamicTextPrefixColor);
            context.drawTextWithShadow(textRenderer, Text.literal(suffix),
                    textX + textRenderer.getWidth(typed), textY, COL_SUFFIX);
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    // Inputy bits
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (dropdownOpen) { closeDropdown(); return true; }
            close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_TAB) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

            if (!dropdownOpen || suggestions.isEmpty()) {
                fetchForTab();
                return true;
            }

            if (!cycling) {
                cycling = true;
                selectedSuggestion = shift ? suggestions.size() - 1 : 0;
            } else {
                if (shift) {
                    selectedSuggestion = (selectedSuggestion <= 0)
                            ? suggestions.size() - 1 : selectedSuggestion - 1;
                } else {
                    selectedSuggestion = (selectedSuggestion + 1) % suggestions.size();
                }
            }
            previewSuggestion(selectedSuggestion);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_SPACE && cycling) {
            cycling = false;
        }

        if (dropdownOpen) {
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                selectedSuggestion = (selectedSuggestion + 1) % suggestions.size();
                if (cycling) previewSuggestion(selectedSuggestion);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                selectedSuggestion = (selectedSuggestion <= 0)
                        ? suggestions.size() - 1 : selectedSuggestion - 1;
                if (cycling) previewSuggestion(selectedSuggestion);
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void fetchForTab() {
        String line = getLine(activeLine);
        String cmd  = toCmd(line);

        int lastSpace = cmd.lastIndexOf(' ');
        String base;
        if (lastSpace >= 0) {
            typedToken           = cmd.substring(lastSpace + 1);
            suggestionRangeStart = lastSpace + 1;
            base                 = cmd.substring(0, lastSpace + 1);
        } else {
            typedToken           = cmd;
            suggestionRangeStart = 0;
            base                 = "";
        }

        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) return;

        CommandDispatcher<CommandSource> dispatcher = handler.getCommandDispatcher();
        CommandSource source = handler.getCommandSource();

        pendingSuggestions = null;
        ParseResults<CommandSource> parse = dispatcher.parse(new StringReader(base), source);
        CompletableFuture<Suggestions> future =
                dispatcher.getCompletionSuggestions(parse, base.length());

        pendingSuggestions = future;
        future.thenAccept(s -> {
            if (future != pendingSuggestions) return;
            baseSuggestions = s.getList().stream()
                    .filter(sg -> !sg.getText().isEmpty())
                    .toList();
            String lower = typedToken.toLowerCase();
            suggestions = baseSuggestions.stream()
                    .filter(sg -> sg.getText().toLowerCase().startsWith(lower))
                    .toList();
            if (!suggestions.isEmpty()) {
                dropdownOpen       = true;
                selectedSuggestion = -1;
                cycling            = false;
            }
        });
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dropdownOpen && !suggestions.isEmpty()) {
            int[] b  = dropdownBounds();
            int ddX  = b[0], ddY = b[1], ddW = b[2];
            int rows = Math.min(suggestions.size(), DROPDOWN_MAX_ROWS);
            if (mouseX >= ddX && mouseX < ddX + ddW) {
                for (int i = 0; i < rows; i++) {
                    int rowY = ddY + DROPDOWN_PAD + i * DROPDOWN_ROW_H;
                    if (mouseY >= rowY && mouseY < rowY + DROPDOWN_ROW_H) {
                        acceptSuggestion(i);
                        return true;
                    }
                }
            }
            closeDropdown();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}