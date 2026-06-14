package com.joel4848.commandrunner.screen;

import com.joel4848.commandrunner.config.PresetManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class SaveAsScreen extends Screen {

    private static final int BOX_W = 240;
    private static final int BOX_H = 100;
    private static final int BUTTON_H = 20;

    private final MainScreen parent;
    private final String commandsToSave;
    private final Consumer<String> onSuccessfulSave;

    private TextFieldWidget nameField;
    private String errorMessage = null;

    public SaveAsScreen(MainScreen parent, String commandsToSave, Consumer<String> onSuccessfulSave) {
        super(Text.literal("Save Preset As"));
        this.parent = parent;
        this.commandsToSave = commandsToSave;
        this.onSuccessfulSave = onSuccessfulSave;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;
        int boxLeft = cx - BOX_W / 2;
        int boxTop  = cy - BOX_H / 2;

        nameField = new TextFieldWidget(textRenderer, boxLeft + 12, boxTop + 32, BOX_W - 24, 16, Text.literal("Preset Name"));
        nameField.setMaxLength(64);
        setInitialFocus(nameField);
        addDrawableChild(nameField);

        int btnY = boxTop + BOX_H - BUTTON_H - 8;
        int btnW = (BOX_W - 24) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn ->
                client.setScreen(parent)
        ).dimensions(boxLeft + 8, btnY, btnW, BUTTON_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), btn ->
                handleAttemptedSave()
        ).dimensions(boxLeft + 8 + btnW + 8, btnY, btnW, BUTTON_H).build());
    }

    private void handleAttemptedSave() {
        String inputName = nameField.getText().trim();

        if (inputName.isEmpty()) {
            errorMessage = "Name cannot be empty!";
            return;
        }

        if (inputName.matches(".*[\\\\/:*?\"<>|].*")) {
            errorMessage = "Invalid characters in name!";
            return;
        }

        if (PresetManager.exists(inputName)) {
            errorMessage = "This name is already in use!";
            return;
        }

        if (PresetManager.save(inputName, commandsToSave)) {
            onSuccessfulSave.accept(inputName);
            client.setScreen(parent);
            parent.showWarning("Saved!");
        } else {
            errorMessage = "Error saving preset file.";
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            handleAttemptedSave();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int cx = width / 2;
        int cy = height / 2;
        int boxTop = cy - BOX_H / 2;

        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Enter new preset name:"), cx, boxTop + 12, 0xFFFFFFFF);

        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(errorMessage).formatted(net.minecraft.util.Formatting.RED), cx, boxTop + 54, 0xFFFF5555);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        int cx = width / 2;
        int cy = height / 2;
        int boxLeft = cx - BOX_W / 2;
        int boxTop  = cy - BOX_H / 2;

        context.fill(boxLeft, boxTop, boxLeft + BOX_W, boxTop + BOX_H, 0xFF222222);
        context.drawBorder(boxLeft, boxTop, BOX_W, BOX_H, 0xFFAAAAAA);
    }

    @Override
    public boolean shouldPause() { return false; }
}