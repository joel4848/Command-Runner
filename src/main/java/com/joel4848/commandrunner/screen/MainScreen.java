package com.joel4848.commandrunner.screen;

import com.joel4848.commandrunner.CommandExecutor;
import com.joel4848.commandrunner.config.PresetManager;
import com.joel4848.commandrunner.ui.CommandSuggestor;
import com.joel4848.commandrunner.ui.CommandTextField;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.List;

public class MainScreen extends Screen {

    private static final int PADDING     = 8;
    private static final int TITLE_BOX_H = 20;
    private static final int BUTTON_H    = 20;
    private static final int CLOSE_BTN_W = 20;

    public String loadedPresetName = null;
    public String pendingContent   = null;

    private CommandTextField tef;
    private CommandSuggestor suggestor;

    private ButtonWidget newBtn, loadBtn, saveBtn, scheduleBtn, runBtn, closeBtn;

    private Text warningMessage = null;
    private int  warningTimer   = 0;

    private int tefLeft, tefTop, tefBottom, tefWidth, tefHeight;

    private boolean suppressSuggestorRefresh = false;

    public MainScreen() {
        super(Text.literal("Command Runner"));
    }

    public void loadPreset(String name, String content) {
        this.loadedPresetName = name;
        this.pendingContent   = content;
    }

    @Override
    protected void init() {
        int w = width;
        int h = height;
        int controlGap = 4;
        int HELP_BTN_W = 20;

        addDrawableChild(ButtonWidget.builder(Text.literal("?"),
                        btn -> MinecraftClient.getInstance().setScreen(new HelpScreen(this)))
                .dimensions(w - PADDING - CLOSE_BTN_W - controlGap - HELP_BTN_W, PADDING, HELP_BTN_W, BUTTON_H)
                .build());

        closeBtn = ButtonWidget.builder(Text.literal("✕"), btn -> close())
                .dimensions(w - PADDING - CLOSE_BTN_W, PADDING, CLOSE_BTN_W, BUTTON_H)
                .build();
        addDrawableChild(closeBtn);

        tefLeft   = PADDING;
        tefTop    = PADDING + TITLE_BOX_H + PADDING;
        tefBottom = h - PADDING - BUTTON_H - PADDING;
        tefWidth  = w - PADDING * 2;
        tefHeight = tefBottom - tefTop;

        String preservedText = (tef != null) ? tef.getText() : "";

        tef = new CommandTextField(textRenderer, tefLeft, tefTop, tefWidth, tefHeight,
                Text.literal("Enter commands here - one per line"));
        tef.setMaxLength(Integer.MAX_VALUE);
        tef.setDrawsBackground(true);

        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler != null) {
            net.minecraft.client.gui.widget.TextFieldWidget stub =
                    new net.minecraft.client.gui.widget.TextFieldWidget(
                            textRenderer, 0, 0, 1, 1, Text.empty());

            stub.setMaxLength(Integer.MAX_VALUE);

            suggestor = new CommandSuggestor(
                    MinecraftClient.getInstance(), this, stub,
                    textRenderer, true, true, 0, 10, false,
                    Integer.MIN_VALUE
            );
            suggestor.setWindowActive(true);
            tef.setCommandSuggestor(suggestor);
        }

        if (pendingContent != null) {
            tef.setText(pendingContent);
            pendingContent = null;
        } else if (!preservedText.isEmpty()) {
            tef.setText(preservedText);
        } else {
            tef.setText("");
        }

        tef.setChangedListener(newText -> {
            if (!suppressSuggestorRefresh) refreshSuggestor();
        });

        addDrawableChild(tef);

        // Bottom buttons
        int btnY     = h - PADDING - BUTTON_H;
        int totalW   = w - PADDING * 2;
        int btnCount = 5, gap = 4;
        int btnW     = (totalW - gap * (btnCount - 1)) / btnCount;
        int[] btnX   = new int[btnCount];
        btnX[0] = PADDING;
        for (int i = 1; i < btnCount; i++) btnX[i] = btnX[i - 1] + btnW + gap;

        newBtn = ButtonWidget.builder(Text.literal("New"), btn -> {
            tef.setText("");
            loadedPresetName = null;
            warningMessage = null;
        }).dimensions(btnX[0], btnY, btnW, BUTTON_H).build();

        loadBtn = ButtonWidget.builder(Text.literal("Load"),
                        btn -> MinecraftClient.getInstance().setScreen(new LoadPresetScreen(this)))
                .dimensions(btnX[1], btnY, btnW, BUTTON_H).build();

        saveBtn = ButtonWidget.builder(Text.literal("Save"), btn -> handleSave())
                .dimensions(btnX[2], btnY, btnW, BUTTON_H).build();

        scheduleBtn = ButtonWidget.builder(Text.literal("Schedule"), btn -> {
            if (loadedPresetName == null) {
                showWarning("You must save the preset before scheduling it!");
            } else {
                MinecraftClient.getInstance().setScreen(new ScheduleScreen(this, loadedPresetName));
            }
        }).dimensions(btnX[3], btnY, btnW, BUTTON_H).build();

        runBtn = ButtonWidget.builder(Text.literal("Run"), btn -> {
            String text = tef.getText();
            if (!text.isBlank()) { close(); CommandExecutor.runAll(text); }
        }).dimensions(btnX[4], btnY, btnW, BUTTON_H).build();

        addDrawableChild(newBtn);
        addDrawableChild(loadBtn);
        addDrawableChild(saveBtn);
        addDrawableChild(scheduleBtn);
        addDrawableChild(runBtn);

        setInitialFocus(tef);
        refreshSuggestor();
        tef.forceRefreshSuggestorPos();
    }

    // Suggester (NOT 'suggestor' cmon Mojang)
    private void refreshSuggestor() {
        if (suggestor == null || MinecraftClient.getInstance().getNetworkHandler() == null) return;
        String activeLine = tef.getActiveLineRaw();

        if (activeLine.startsWith("#")) {
            primeSuggestorTextField("");
            tef.onSuggestorRefreshed();
            return;
        }

        if (activeLine.startsWith("!")) {
            primeSuggestorTextField("");

            com.joel4848.commandrunner.mixin.CommandSuggestorAccessor suggestorAccessor =
                    (com.joel4848.commandrunner.mixin.CommandSuggestorAccessor) suggestor;

            List<net.minecraft.text.OrderedText> messages = suggestorAccessor.getMessages();
            messages.clear();

            suggestorAccessor.setWindow(null);

            String content = activeLine.substring(1).trim().toLowerCase();

            if (content.startsWith("w")) {
                messages.add(Text.literal("§6!wait §e<time>[t|s|d]§7 (e.g. !wait 20s)").asOrderedText());
                messages.add(Text.literal("§7Units: t=ticks, s=seconds, d=minecraft days").asOrderedText());
            } else if (content.startsWith("r")) {
                messages.add(Text.literal("§6!repeat §e<count>§7 (e.g. !repeat 5)").asOrderedText());
                messages.add(Text.literal("§7Repeats a block of commands <count> times").asOrderedText());
            } else if (content.startsWith("e")) {
                messages.add(Text.literal("§6!endrepeat").asOrderedText());
                messages.add(Text.literal("§7Closes the nearest active repeat block").asOrderedText());
            } else {
                // messages.add(Text.literal("§6Handy-Dandy Internal Syntax:").asOrderedText());
                messages.add(Text.literal(" §e!wait <time>[t|s|d]").asOrderedText());
                messages.add(Text.literal(" §e!repeat <count>").asOrderedText());
                messages.add(Text.literal(" §e!endrepeat").asOrderedText());
            }

            suggestor.setWindowActive(true);
            tef.onSuggestorRefreshed();
            return;
        }

        primeSuggestorTextField(activeLine);
        suggestor.refresh();
        tef.onSuggestorRefreshed();
    }

    private void primeSuggestorTextField(String text) {
        try {
            com.joel4848.commandrunner.mixin.TextFieldWidgetAccessor tfAccessor =
                    (com.joel4848.commandrunner.mixin.TextFieldWidgetAccessor)
                            ((com.joel4848.commandrunner.mixin.CommandSuggestorAccessor) suggestor).getTextField();
            tfAccessor.setTextVariable(text);
            tfAccessor.setSelectionStart(text.length());
            tfAccessor.setSelectionEnd(text.length());
        } catch (Exception ignored) {}
    }

    // Inputy bits
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_TAB) {
            if (suggestor != null) {
                primeSuggestorTextField(tef.getActiveLineRaw());

                suppressSuggestorRefresh = true;
                boolean handled = suggestor.keyPressed(keyCode, scanCode, modifiers);
                suppressSuggestorRefresh = false;
                if (handled) {
                    syncSuggestorCompletionToTef();
                }
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            if (suggestor != null && suggestor.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void syncSuggestorCompletionToTef() {
        if (suggestor == null) return;
        try {
            com.joel4848.commandrunner.mixin.TextFieldWidgetAccessor tfAccessor =
                    (com.joel4848.commandrunner.mixin.TextFieldWidgetAccessor)
                            ((com.joel4848.commandrunner.mixin.CommandSuggestorAccessor) suggestor).getTextField();
            String completed = tfAccessor.getText();
            String current = tef.getActiveLineRaw();
            if (!completed.equals(current)) {
                replaceActiveLine(completed);
            }
        } catch (Exception ignored) {}
    }

    private void replaceActiveLine(String newLineText) {
        String fullText = tef.getText();
        String[] lines = fullText.split("\n", -1);
        int active = tef.getActiveLine();
        if (active < 0 || active >= lines.length) return;
        lines[active] = newLineText;
        String newText = String.join("\n", lines);

        int newCursor = 0;
        for (int i = 0; i < active; i++) newCursor += lines[i].length() + 1;
        newCursor += newLineText.length();

        suppressSuggestorRefresh = true;
        tef.setText(newText);
        tef.setCursor(newCursor, false);
        suppressSuggestorRefresh = false;

        tef.onSuggestorRefreshed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (suggestor != null && suggestor.mouseClicked(mouseX, mouseY, button)) {
            syncSuggestorCompletionToTef();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // Savey bits
    private void handleSave() {
        String textToSave = tef.getText();
        if (loadedPresetName == null) {
            MinecraftClient.getInstance().setScreen(new SaveAsScreen(this, textToSave, savedName -> {
                this.loadedPresetName = savedName;
                com.joel4848.commandrunner.schedule.ScheduleManager.reloadSchedules();
            }));
            return;
        }
        if (PresetManager.save(loadedPresetName, textToSave)) {
            showWarning("Saved!");
            com.joel4848.commandrunner.schedule.ScheduleManager.reloadSchedules();
        } else {
            showWarning("Error saving preset.");
        }
    }

    public void showWarning(String msg) {
        warningMessage = Text.literal(msg);
        warningTimer   = 80;
    }

    @Override
    public void tick() {
        super.tick();
        if (warningTimer > 0 && --warningTimer == 0) warningMessage = null;
    }

    // Rendery bits
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        String statusText = (loadedPresetName != null)
                ? "Editing: " + loadedPresetName : "Editing: (Unsaved preset)";
        context.drawTextWithShadow(textRenderer, Text.literal(statusText),
                PADDING + 2, PADDING + 4, 0xFFFFFFFF);

        if (warningMessage != null) {
            int msgY = height - PADDING - BUTTON_H - PADDING - textRenderer.fontHeight - 2;
            context.drawCenteredTextWithShadow(textRenderer, warningMessage, width / 2, msgY,
                    warningTimer > 0 ? 0xFFFF5555 : 0xFF55FF55);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}