package com.joel4848.commandrunner.screen;

import com.joel4848.commandrunner.config.PresetManager;
import com.joel4848.commandrunner.config.ScheduleConfig;
import com.joel4848.commandrunner.schedule.ScheduleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Screen with presets to load and buttons to do other things with
public class LoadPresetScreen extends Screen {

    private static final int PADDING = 8;
    private static final int HEADER_H = 16;
    private static final int ROW_H = 18;
    private static final int BUTTON_H = 20;
    private static final int SCROLLBAR_W = 6;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final MainScreen parent;

    // Sorted preset list
    private List<PresetManager.PresetInfo> presets = new ArrayList<>();
    private int selectedIndex = -1;

    // Sort state
    private enum SortColumn { NAME, DATE }
    private SortColumn sortColumn = SortColumn.DATE;
    private boolean sortAscending = false; // dates: most recent first by default

    // Scroll
    private int scrollOffset = 0; // in pixels
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private int nameColW;
    private int dateColW;

    // Column header buttons
    private int headerY;

    // Buttons
    private ButtonWidget loadBtn;
    private ButtonWidget deleteBtn;

    public LoadPresetScreen(MainScreen parent) {
        super(Text.literal("Load Preset"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshPresets();

        listLeft  = PADDING;
        listRight = width - PADDING - SCROLLBAR_W - 2;
        headerY   = PADDING;
        listTop   = headerY + HEADER_H + 2;
        listBottom = height - PADDING - BUTTON_H - PADDING;

        nameColW = (listRight - listLeft) * 55 / 100;
        dateColW = (listRight - listLeft) - nameColW;

        // Bottom buttons
        int btnY = height - PADDING - BUTTON_H;
        int totalW = width - PADDING * 2;
        int btnW = (totalW - 8) / 3;

        loadBtn = ButtonWidget.builder(Text.literal("Load"), btn -> handleLoad())
                .dimensions(PADDING, btnY, btnW, BUTTON_H).build();
        deleteBtn = ButtonWidget.builder(Text.literal("Delete"), btn -> handleDelete())
                .dimensions(PADDING + btnW + 4, btnY, btnW, BUTTON_H).build();
        ButtonWidget folderBtn = ButtonWidget.builder(Text.literal("Open Presets Folder"), btn ->
                Util.getOperatingSystem().open(PresetManager.getPresetsDir().toFile())
        ).dimensions(PADDING + (btnW + 4) * 2, btnY, btnW, BUTTON_H).build();

        addDrawableChild(loadBtn);
        addDrawableChild(deleteBtn);
        addDrawableChild(folderBtn);

        updateButtonStates();
    }

    private void refreshPresets() {
        presets = new ArrayList<>(PresetManager.listPresets());
        applySort();
        if (selectedIndex >= presets.size()) selectedIndex = -1;
    }

    private void applySort() {
        Comparator<PresetManager.PresetInfo> cmp;
        if (sortColumn == SortColumn.NAME) {
            cmp = Comparator.comparing(p -> p.name().toLowerCase(Locale.ROOT));
            cmp = Comparator.comparing(p -> p.name(), LoadPresetScreen::naturalCompare);
        } else {
            cmp = Comparator.comparing(PresetManager.PresetInfo::lastModified);
        }
        if (!sortAscending) cmp = cmp.reversed();
        presets.sort(cmp);
    }

    // Make sorting properly alphanumerical (or whatever you call it)
    private static int naturalCompare(String a, String b) {
        int ia = 0, ib = 0;
        while (ia < a.length() && ib < b.length()) {
            char ca = a.charAt(ia), cb = b.charAt(ib);
            boolean aDigit = Character.isDigit(ca), bDigit = Character.isDigit(cb);
            if (aDigit && bDigit) {
                int startA = ia, startB = ib;
                while (ia < a.length() && Character.isDigit(a.charAt(ia))) ia++;
                while (ib < b.length() && Character.isDigit(b.charAt(ib))) ib++;
                long na = Long.parseLong(a.substring(startA, ia));
                long nb = Long.parseLong(b.substring(startB, ib));
                if (na != nb) return Long.compare(na, nb);
            } else {
                int c = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (c != 0) return c;
                ia++; ib++;
            }
        }
        return Integer.compare(a.length() - ia, b.length() - ib);
    }

    private void updateButtonStates() {
        boolean sel = selectedIndex >= 0 && selectedIndex < presets.size();
        loadBtn.active = sel;
        deleteBtn.active = sel;
    }

    private void handleLoad() {
        if (selectedIndex < 0 || selectedIndex >= presets.size()) return;
        PresetManager.PresetInfo info = presets.get(selectedIndex);
        String content = PresetManager.load(info.path());
        if (content != null) {
            parent.loadPreset(info.name(), content);
        }
        client.setScreen(parent);
    }

    private void handleDelete() {
        if (selectedIndex < 0 || selectedIndex >= presets.size()) return;
        PresetManager.PresetInfo info = presets.get(selectedIndex);
        client.setScreen(new DeleteConfirmScreen(this, () -> {
            PresetManager.delete(info.path());
            ScheduleConfig.delete(info.name());
            ScheduleManager.reloadSchedules();
            selectedIndex = -1;
            refreshPresets();
            updateButtonStates();
        }));
    }

    // Input bits-

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Header row click for sorting
        if (mouseY >= headerY && mouseY < headerY + HEADER_H) {
            if (mouseX >= listLeft && mouseX < listLeft + nameColW) {
                if (sortColumn == SortColumn.NAME) sortAscending = !sortAscending;
                else { sortColumn = SortColumn.NAME; sortAscending = true; }
                applySort();
                selectedIndex = -1;
                updateButtonStates();
                return true;
            } else if (mouseX >= listLeft + nameColW && mouseX < listRight) {
                if (sortColumn == SortColumn.DATE) sortAscending = !sortAscending;
                else { sortColumn = SortColumn.DATE; sortAscending = false; }
                applySort();
                selectedIndex = -1;
                updateButtonStates();
                return true;
            }
        }

        // Row click
        if (mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom) {
            int relY = (int) mouseY - listTop + scrollOffset;
            int idx = relY / ROW_H;
            if (idx >= 0 && idx < presets.size()) {
                selectedIndex = idx;
                updateButtonStates();

                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int visibleH = listBottom - listTop;
        int contentH = presets.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - visibleH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(verticalAmount * ROW_H)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(parent);
            return true;
        }
        // Arrow navigationy bits
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            updateButtonStates();
            ensureVisible(selectedIndex);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            selectedIndex = Math.min(presets.size() - 1, selectedIndex + 1);
            updateButtonStates();
            ensureVisible(selectedIndex);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            handleLoad();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void ensureVisible(int idx) {
        if (idx < 0 || idx >= presets.size()) return;
        int visibleH = listBottom - listTop;
        int rowTop = idx * ROW_H;
        if (rowTop < scrollOffset) scrollOffset = rowTop;
        if (rowTop + ROW_H > scrollOffset + visibleH) scrollOffset = rowTop + ROW_H - visibleH;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int visibleH = listBottom - listTop;
        int contentH = presets.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - visibleH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        boolean nameActive = sortColumn == SortColumn.NAME;
        boolean dateActive = sortColumn == SortColumn.DATE;
        String nameLabel = "Name" + (nameActive ? (sortAscending ? " ▲" : " ▼") : "");
        String dateLabel = "Date Modified" + (dateActive ? (sortAscending ? " ▲" : " ▼") : "");

        // Column headers
        context.drawTextWithShadow(textRenderer, Text.literal(nameLabel), listLeft + 3, headerY + 4, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal(dateLabel), listLeft + nameColW + 3, headerY + 4, 0xFFFFFFFF);

        context.enableScissor(listLeft, listTop, listRight, listBottom);

        for (int i = 0; i < presets.size(); i++) {
            int rowY = listTop + i * ROW_H - scrollOffset;
            if (rowY + ROW_H < listTop || rowY > listBottom) continue;

            PresetManager.PresetInfo info = presets.get(i);
            String nameStr = info.name();
            String dateStr = DATE_FMT.format(info.lastModified());

            String displayName = truncateText(nameStr, nameColW - 6);
            context.drawTextWithShadow(textRenderer, Text.literal(displayName), listLeft + 3, rowY + 5, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, Text.literal(dateStr), listLeft + nameColW + 3, rowY + 5, 0xFFCCCCCC);
        }

        context.disableScissor();

        if (contentH > visibleH) {
            int scrollbarX = listRight + 2;
            int scrollbarH = listBottom - listTop;
            int thumbH = Math.max(16, scrollbarH * visibleH / contentH);
            int thumbY = listTop + (scrollbarH - thumbH) * scrollOffset / maxScroll;
            context.fill(scrollbarX, listTop, scrollbarX + SCROLLBAR_W, listBottom, 0xFF333333);
            context.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_W, thumbY + thumbH, 0xFFAAAAAA);
        }

        context.drawCenteredTextWithShadow(textRenderer, this.title, width / 2, 2, 0xFFFFFFFF);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        int visibleH = listBottom - listTop;
        int contentH = presets.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - visibleH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        boolean nameActive = sortColumn == SortColumn.NAME;
        boolean dateActive = sortColumn == SortColumn.DATE;

        context.fill(listLeft, headerY, listLeft + nameColW, headerY + HEADER_H, nameActive ? 0xFF334455 : 0xFF222222);
        context.fill(listLeft + nameColW, headerY, listRight, headerY + HEADER_H, dateActive ? 0xFF334455 : 0xFF222222);
        context.drawBorder(listLeft, headerY, listRight - listLeft, HEADER_H, 0xFF888888);

        context.enableScissor(listLeft, listTop, listRight, listBottom);

        for (int i = 0; i < presets.size(); i++) {
            int rowY = listTop + i * ROW_H - scrollOffset;
            if (rowY + ROW_H < listTop || rowY > listBottom) continue;

            boolean sel = i == selectedIndex;
            int bg = sel ? 0xFF3355AA : (i % 2 == 0 ? 0xFF1A1A1A : 0xFF222222);
            context.fill(listLeft, rowY, listRight, rowY + ROW_H, bg);
        }

        context.disableScissor();

        context.drawBorder(listLeft, listTop, listRight - listLeft, listBottom - listTop, 0xFF888888);
    }

    private String truncateText(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        int ellipsisW = textRenderer.getWidth(ellipsis);
        while (text.length() > 0 && textRenderer.getWidth(text) + ellipsisW > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }

    @Override
    public boolean shouldPause() { return false; }
}
