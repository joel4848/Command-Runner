package com.joel4848.commandrunner.screen;

import com.joel4848.commandrunner.config.ScheduleConfig;
import com.joel4848.commandrunner.config.ScheduleConfig.TriggerType;
import com.joel4848.commandrunner.schedule.ScheduleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.util.*;

public class ScheduleScreen extends Screen {

    private static final int PADDING = 8;
    private static final int ROW_H = 22;
    private static final int BUTTON_H = 20;
    private static final int DETAIL_PANEL_FRACTION = 3; // right 1/3

    private final MainScreen parent;
    private final String presetName;

    private ScheduleConfig.ScheduleData scheduleData;

    // Which row is selected (to show the deets on the side)
    private int selectedTrigger = -1;

    // List of all the triggers
    private static final List<TriggerDef> TRIGGERS = List.of(
            new TriggerDef(TriggerType.EVERY_X,      "Every X ticks/seconds/minutes/hours",    true),
            new TriggerDef(TriggerType.ON_LOAD,       "When the game loads",                    false),
            new TriggerDef(TriggerType.ON_DEATH,      "When you die",                           false),
            new TriggerDef(TriggerType.ON_RESPAWN,    "When you respawn",                       false),
            new TriggerDef(TriggerType.ON_DAMAGE,     "When you take damage",                   false),
            new TriggerDef(TriggerType.ON_KEY,        "When <key> is pressed",                  true),
            new TriggerDef(TriggerType.ON_LOW_HEALTH, "When your health drops below X%",        true),
            new TriggerDef(TriggerType.ON_SLEEP,      "When you sleep",                         false),
            new TriggerDef(TriggerType.ON_WAKE,       "When you wake up",                       false),
            new TriggerDef(TriggerType.ON_WEATHER,    "When the weather changes",               false)
    );

    private record TriggerDef(TriggerType type, String label, boolean hasDetail) {}

    // List positioning
    private int listLeft, listRight, listTop, listBottom;
    private int detailLeft, detailRight;
    private int scrollOffset = 0;

    // Details
    private final List<net.minecraft.client.gui.Drawable> detailDrawables = new ArrayList<>();
    private final List<net.minecraft.client.gui.Element>  detailElements  = new ArrayList<>();

    // Detail fields
    private TextFieldWidget everyXValueField;   // numeric value
    private CyclingButtonWidget<TimeUnit> everyXUnitBtn; // ticks/seconds/minutes/hours
    private TextFieldWidget healthPctField;
    private boolean listeningForKey = false;

    private enum TimeUnit {
        TICKS("ticks", 1),
        SECONDS("seconds", 20),
        MINUTES("minutes", 1200),
        HOURS("hours", 72000);

        final String label;
        final int ticksPerUnit;
        TimeUnit(String label, int ticksPerUnit) {
            this.label = label;
            this.ticksPerUnit = ticksPerUnit;
        }
        @Override public String toString() { return label; }
    }

    // Buttons
    private ButtonWidget saveBtn, cancelBtn;

    public ScheduleScreen(MainScreen parent, String presetName) {
        super(Text.literal("Schedule: " + presetName));
        this.parent = parent;
        this.presetName = presetName;
    }

    @Override
    protected void init() {
        scheduleData = ScheduleConfig.load(presetName);

        int listW = width * 2 / DETAIL_PANEL_FRACTION;  // 2/3
        listLeft  = PADDING;
        listRight = listLeft + listW - PADDING;
        listTop   = PADDING + textRenderer.fontHeight + 4;
        listBottom = height - PADDING - BUTTON_H - PADDING;

        detailLeft  = listRight + PADDING;
        detailRight = width - PADDING;

        int btnY = height - PADDING - BUTTON_H;
        int btnW = 60;
        saveBtn = ButtonWidget.builder(Text.literal("Save"), btn -> handleSave())
                .dimensions(width / 2 - btnW - 4, btnY, btnW, BUTTON_H).build();
        cancelBtn = ButtonWidget.builder(Text.literal("Cancel"), btn -> client.setScreen(parent))
                .dimensions(width / 2 + 4, btnY, btnW, BUTTON_H).build();
        addDrawableChild(saveBtn);
        addDrawableChild(cancelBtn);

        if (selectedTrigger >= 0) {
            buildDetailPanel();
        }
    }

    // Details panel
    private void clearDetailPanel() {
        for (net.minecraft.client.gui.Element e : detailElements) {
            remove((net.minecraft.client.gui.widget.ClickableWidget) e);
        }
        detailDrawables.clear();
        detailElements.clear();
        everyXValueField = null;
        everyXUnitBtn = null;
        healthPctField = null;
        listeningForKey = false;
    }

    @SuppressWarnings("unchecked")
    private void buildDetailPanel() {
        clearDetailPanel();
        if (selectedTrigger < 0 || selectedTrigger >= TRIGGERS.size()) return;
        TriggerDef def = TRIGGERS.get(selectedTrigger);
        if (!def.hasDetail()) return;

        int panelX = detailLeft;
        int panelY = listTop;
        int panelW = detailRight - detailLeft;

        switch (def.type()) {
            case EVERY_X -> {
                int storedTicks = 20; // default 1 second
                TimeUnit storedUnit = TimeUnit.SECONDS;
                if (scheduleData.has(TriggerType.EVERY_X)) {
                    try {
                        storedTicks = Integer.parseInt(scheduleData.get(TriggerType.EVERY_X));
                        for (TimeUnit u : new TimeUnit[]{TimeUnit.HOURS, TimeUnit.MINUTES, TimeUnit.SECONDS, TimeUnit.TICKS}) {
                            if (storedTicks % u.ticksPerUnit == 0) {
                                storedUnit = u;
                                storedTicks = storedTicks / u.ticksPerUnit;
                                break;
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }

                addDetailLabel(context -> context.drawTextWithShadow(textRenderer, Text.literal("Every:"), panelX, panelY, 0xFFFFFFFF), panelY);

                int fieldW = 50;
                everyXValueField = new TextFieldWidget(textRenderer, panelX, panelY + 14, fieldW, 16, Text.literal("Amount"));
                everyXValueField.setMaxLength(6);
                everyXValueField.setText(String.valueOf(storedTicks));
                everyXValueField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d{0,6}"));
                addDetailWidget(everyXValueField);

                final TimeUnit[] unitRef = {storedUnit};
                everyXUnitBtn = CyclingButtonWidget.builder((TimeUnit u) -> Text.literal(u.label))
                        .values(TimeUnit.values())
                        .initially(storedUnit)
                        .build(panelX + fieldW + 4, panelY + 12, panelW - fieldW - 4, 20, Text.literal("Unit"), (btn, val) -> unitRef[0] = val);
                addDetailWidget(everyXUnitBtn);
            }

            case ON_KEY -> {
                String storedKey = scheduleData.has(TriggerType.ON_KEY) ? scheduleData.get(TriggerType.ON_KEY) : "";
                String keyLabel = storedKey.isEmpty() ? "(none)" : storedKey;

                addDetailLabel(ctx -> ctx.drawTextWithShadow(textRenderer,
                        Text.literal("Key: " + keyLabel), panelX, panelY, 0xFFFFFF55), panelY);

                ButtonWidget listenBtn = ButtonWidget.builder(
                        Text.literal(listeningForKey ? "Press any key…" : "Set Key"),
                        btn -> { listeningForKey = true; btn.setMessage(Text.literal("Press any key…")); }
                ).dimensions(panelX, panelY + 16, panelW, 20).build();
                addDetailWidget(listenBtn);

                ButtonWidget clearKeyBtn = ButtonWidget.builder(Text.literal("Clear Key"), btn -> {
                    scheduleData.clear(TriggerType.ON_KEY);
                    listeningForKey = false;
                    buildDetailPanel();
                }).dimensions(panelX, panelY + 40, panelW, 20).build();
                addDetailWidget(clearKeyBtn);
            }

            case ON_LOW_HEALTH -> {
                int stored = 30;
                if (scheduleData.has(TriggerType.ON_LOW_HEALTH)) {
                    try { stored = Integer.parseInt(scheduleData.get(TriggerType.ON_LOW_HEALTH)); }
                    catch (NumberFormatException ignored) {}
                }

                addDetailLabel(ctx -> ctx.drawTextWithShadow(textRenderer, Text.literal("Health threshold (%):"), panelX, panelY, 0xFFFFFFFF), panelY);
                healthPctField = new TextFieldWidget(textRenderer, panelX, panelY + 14, panelW, 16, Text.literal("%"));
                healthPctField.setMaxLength(3);
                healthPctField.setText(String.valueOf(stored));
                healthPctField.setTextPredicate(s -> s.isEmpty() || (s.matches("\\d{1,3}") && Integer.parseInt(s) <= 100));
                addDetailWidget(healthPctField);
            }
            default -> {}
        }
    }

    @FunctionalInterface interface DetailRenderer { void render(DrawContext ctx); }

    private void addDetailLabel(DetailRenderer r, int y) {
        detailDrawables.add((context, mx, my, d) -> r.render(context));
    }

    @SuppressWarnings("unchecked")
    private <T extends net.minecraft.client.gui.widget.ClickableWidget> void addDetailWidget(T w) {
        addDrawableChild(w);
        detailElements.add(w);
    }

    private void syncDetailToData() {
        if (selectedTrigger < 0 || selectedTrigger >= TRIGGERS.size()) return;
        TriggerDef def = TRIGGERS.get(selectedTrigger);
        if (!def.hasDetail()) return;

        switch (def.type()) {
            case EVERY_X -> {
                if (everyXValueField != null && everyXUnitBtn != null) {
                    try {
                        int amount = Integer.parseInt(everyXValueField.getText().trim());
                        TimeUnit unit = everyXUnitBtn.getValue();
                        int ticks = amount * unit.ticksPerUnit;
                        if (scheduleData.has(TriggerType.EVERY_X)) {
                            scheduleData.set(TriggerType.EVERY_X, String.valueOf(ticks));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            case ON_LOW_HEALTH -> {
                if (healthPctField != null) {
                    try {
                        int pct = Integer.parseInt(healthPctField.getText().trim());
                        pct = Math.max(1, Math.min(100, pct));
                        if (scheduleData.has(TriggerType.ON_LOW_HEALTH)) {
                            scheduleData.set(TriggerType.ON_LOW_HEALTH, String.valueOf(pct));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            default -> {}
        }
    }

    private void toggleTrigger(int idx) {
        if (idx < 0 || idx >= TRIGGERS.size()) return;
        TriggerDef def = TRIGGERS.get(idx);
        TriggerType type = def.type();

        if (scheduleData.has(type)) {
            scheduleData.clear(type);
        } else {
            switch (type) {
                case EVERY_X      -> scheduleData.set(type, "20");
                case ON_LOW_HEALTH -> scheduleData.set(type, "30");
                case ON_KEY       -> scheduleData.set(type, "");
                default           -> scheduleData.set(type, "true");
            }
        }
    }

    private void handleSave() {
        syncDetailToData();
        ScheduleConfig.save(presetName, scheduleData);
        ScheduleManager.reloadSchedules();
        client.setScreen(parent);
    }

    // Input bits
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom) {
            int relY = (int) mouseY - listTop + scrollOffset;
            int idx = relY / ROW_H;

            if (idx >= 0 && idx < TRIGGERS.size()) {
                if (mouseX >= listLeft && mouseX < listLeft + 14) {
                    toggleTrigger(idx);
                }

                selectedTrigger = idx;
                buildDetailPanel();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= listLeft && mouseX < listRight) {
            int visibleH = listBottom - listTop;
            int contentH = TRIGGERS.size() * ROW_H;
            int maxScroll = Math.max(0, contentH - visibleH);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(verticalAmount * ROW_H)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningForKey) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                listeningForKey = false;
                buildDetailPanel();
                return true;
            }
            InputUtil.Key key = InputUtil.fromKeyCode(keyCode, scanCode);
            scheduleData.set(TriggerType.ON_KEY, key.getTranslationKey());
            listeningForKey = false;
            buildDetailPanel();
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

        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Schedule Triggers for: " + presetName),
                width / 2, 2, 0xFFFFFFFF);

        context.drawTextWithShadow(textRenderer, Text.literal("Triggers"), listLeft + 2, listTop - textRenderer.fontHeight - 2, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("Options"), detailLeft, listTop - textRenderer.fontHeight - 2, 0xFFAAAAAA);

        context.enableScissor(listLeft, listTop, listRight, listBottom);

        for (int i = 0; i < TRIGGERS.size(); i++) {
            int rowY = listTop + i * ROW_H - scrollOffset;
            if (rowY + ROW_H < listTop || rowY > listBottom) continue;

            TriggerDef def = TRIGGERS.get(i);
            boolean active = scheduleData.has(def.type());

            String label = def.label();
            if (active && def.type() == TriggerType.EVERY_X) {
                label += " (" + scheduleData.get(TriggerType.EVERY_X) + " ticks)";
            } else if (active && def.type() == TriggerType.ON_KEY) {
                String k = scheduleData.get(TriggerType.ON_KEY);
                label += " [" + (k.isEmpty() ? "none" : k) + "]";
            } else if (active && def.type() == TriggerType.ON_LOW_HEALTH) {
                label += " (" + scheduleData.get(TriggerType.ON_LOW_HEALTH) + "%)";
            }

            int cbX = listLeft + 3;
            context.drawTextWithShadow(textRenderer, Text.literal(label), cbX + 14, rowY + (ROW_H - textRenderer.fontHeight) / 2, active ? 0xFFFFFFFF : 0xFFAAAAAA);
        }

        context.disableScissor();

        for (net.minecraft.client.gui.Drawable d : detailDrawables) {
            d.render(context, mouseX, mouseY, delta);
        }

        if (listeningForKey) {
            context.drawTextWithShadow(textRenderer, Text.literal("Press any key…").formatted(net.minecraft.util.Formatting.YELLOW),
                    detailLeft, listTop + 38, 0xFFFFFF55);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        context.enableScissor(listLeft, listTop, listRight, listBottom);

        for (int i = 0; i < TRIGGERS.size(); i++) {
            int rowY = listTop + i * ROW_H - scrollOffset;
            if (rowY + ROW_H < listTop || rowY > listBottom) continue;

            boolean active = scheduleData.has(TRIGGERS.get(i).type());
            boolean sel = i == selectedTrigger;

            int bg = sel ? 0xFF334455 : (i % 2 == 0 ? 0xFF1A1A1A : 0xFF222222);
            context.fill(listLeft, rowY, listRight, rowY + ROW_H, bg);

            int cbX = listLeft + 3, cbY = rowY + (ROW_H - 10) / 2;
            context.fill(cbX, cbY, cbX + 10, cbY + 10, 0xFF555555);
            context.drawBorder(cbX, cbY, 10, 10, 0xFFAAAAAA);
            if (active) {
                context.fill(cbX + 2, cbY + 2, cbX + 8, cbY + 8, 0xFF55FF55);
            }
        }

        context.disableScissor();

        context.drawBorder(listLeft, listTop, listRight - listLeft, listBottom - listTop, 0xFF888888);
        context.fill(detailLeft - PADDING / 2, listTop, detailLeft - PADDING / 2 + 1, listBottom, 0xFF888888);
    }

    @Override
    public boolean shouldPause() { return false; }
}