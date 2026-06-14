package com.joel4848.commandrunner.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

// 'Are you sure? popup
public class DeleteConfirmScreen extends Screen {

    private static final int BOX_W = 240;
    private static final int BOX_H = 80;
    private static final int BUTTON_H = 20;

    private final Screen parent;
    private final Runnable onConfirm;

    public DeleteConfirmScreen(Screen parent, Runnable onConfirm) {
        super(Text.literal("Confirm Delete"));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;
        int boxLeft = cx - BOX_W / 2;
        int boxTop  = cy - BOX_H / 2;

        int btnY = boxTop + BOX_H - BUTTON_H - 8;
        int btnW = (BOX_W - 24) / 2;

        // Cancel
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn ->
                client.setScreen(parent)
        ).dimensions(boxLeft + 8, btnY, btnW, BUTTON_H).build());

        // Delete
        addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), btn -> {
            onConfirm.run();
            client.setScreen(parent);
        }).dimensions(boxLeft + 8 + btnW + 8, btnY, btnW, BUTTON_H).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int cx = width / 2;
        int cy = height / 2;
        int boxTop = cy - BOX_H / 2;

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Are you sure? This action cannot be undone."),
                cx, boxTop + 16, 0xFFFFFFFF);
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
