package com.joel4848.commandrunner.ui;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Util;

import com.joel4848.commandrunner.mixin.TextFieldWidgetAccessor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class CommandTextField extends TextFieldWidget implements Element {

    private static final int LINE_HEIGHT = 12;
    private static final int PADDING = 4;

    private final TextFieldWidgetAccessor accessor;
    private CommandSuggestor suggestor;

    private final List<String> lines = new LinkedList<>();
    private final List<Integer> lineStartIndices = new LinkedList<>();
    private final List<List<Pair<Integer, Integer>>> lineColors = new LinkedList<>();

    private int scrolledLines = 0;
    private int visibleLines = 10;
    private int activeLine = 0;

    private int suggestorAnchorX = -1;

    private String anchorLineSnapshot = null;

    public CommandTextField(TextRenderer textRenderer, int x, int y, int width, int height, Text message) {
        super(textRenderer, x, y, width, height, message);
        this.accessor = (TextFieldWidgetAccessor) this;
        this.visibleLines = Math.max(1, (height - PADDING * 2) / LINE_HEIGHT);
    }

    public void setCommandSuggestor(CommandSuggestor suggestor) {
        this.suggestor = suggestor;
    }

    @Override
    public void setText(String text) {
        if (!accessor.getTextPredicate().test(text)) return;
        String clamped = text.length() > accessor.invokeGetMaxLength()
                ? text.substring(0, accessor.invokeGetMaxLength()) : text;
        accessor.setTextVariable(clamped);
        accessor.setSelectionStart(clamped.length());
        accessor.setSelectionEnd(accessor.getSelectionStart());
        rebuildLines(clamped);
        notifyChanged(clamped);
        resetAnchor();
    }

    @Override
    public void write(String text) {
        int i = Math.min(accessor.getSelectionStart(), accessor.getSelectionEnd());
        int j = Math.max(accessor.getSelectionStart(), accessor.getSelectionEnd());
        int room = accessor.invokeGetMaxLength() - accessor.getText().length() - (i - j);

        StringBuilder filtered = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '\n' || StringHelper.isValidChar(c)) {
                filtered.append(c);
            }
        }
        String stripped = filtered.toString();
        if (stripped.length() > room) stripped = stripped.substring(0, room);
        if (stripped.isEmpty()) return;

        String newText = new StringBuilder(accessor.getText()).replace(i, j, stripped).toString();
        if (!accessor.getTextPredicate().test(newText)) return;

        accessor.setTextVariable(newText);
        accessor.setSelectionStart(i + stripped.length());
        accessor.setSelectionEnd(accessor.getSelectionStart());
        rebuildLines(newText);
        notifyChanged(newText);
        updateSuggestorAnchor();
        updateScrollForCursor();
    }

    private void eraseImpl(int offset) {
        if (Screen.hasControlDown()) {
            eraseWords(offset);
        } else {
            eraseCharacters(offset);
        }
        rebuildLines(accessor.getText());
        notifyChanged(accessor.getText());
        updateSuggestorAnchor();
        updateScrollForCursor();
    }

    @Override
    public void eraseCharacters(int characterOffset) {
        if (accessor.getText().isEmpty()) return;
        if (accessor.getSelectionEnd() != accessor.getSelectionStart()) {
            write("");
            return;
        }
        int i = accessor.invokeGetCursorPosWithOffset(characterOffset);
        int from = Math.min(i, accessor.getSelectionStart());
        int to = Math.max(i, accessor.getSelectionStart());
        if (from == to) return;
        String s = new StringBuilder(accessor.getText()).delete(from, to).toString();
        if (!accessor.getTextPredicate().test(s)) return;
        accessor.setTextVariable(s);
        setCursor(from, false);
    }

    @Override
    public void eraseWords(int wordOffset) {
        if (accessor.getText().isEmpty()) return;
        if (accessor.getSelectionEnd() != accessor.getSelectionStart()) {
            write("");
            return;
        }
        eraseCharacters(getWordSkipOffset(wordOffset));
    }

    private int getWordSkipOffset(int wordOffset) {
        String text = accessor.getText();
        int pos = accessor.getSelectionStart();
        if (wordOffset < 0) {
            while (pos > 0 && text.charAt(pos - 1) == ' ') pos--;
            while (pos > 0 && text.charAt(pos - 1) != ' ') pos--;
            return pos - accessor.getSelectionStart();
        } else {
            int len = text.length();
            while (pos < len && text.charAt(pos) != ' ') pos++;
            while (pos < len && text.charAt(pos) == ' ') pos++;
            return pos - accessor.getSelectionStart();
        }
    }

    private void notifyChanged(String text) {
        if (accessor.getChangedListener() != null) {
            accessor.getChangedListener().accept(text);
        }
    }

    private void updateSuggestorAnchor() {
        if (suggestor == null) return;
        String line = getActiveLineRaw();
        String tokenStart = getTokenStart(line, cursorOffsetInLine(activeLine, accessor.getSelectionStart()));

        if (anchorLineSnapshot == null || !isSameTokenContext(line, tokenStart)) {
            computeAndSetAnchor(line, tokenStart);
        }
    }

    private boolean isSameTokenContext(String line, String tokenStart) {
        return anchorLineSnapshot != null
                && anchorLineSnapshot.startsWith(tokenStart)
                && line.startsWith(tokenStart);
    }

    private void computeAndSetAnchor(String line, String tokenStart) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int visLineIdx = activeLine - scrolledLines;
        int x = getX() + PADDING + tr.getWidth(tokenStart);
        int y = getY() + PADDING + (visLineIdx + 1) * LINE_HEIGHT;
        suggestorAnchorX = x;
        anchorLineSnapshot = tokenStart;
        applySuggestorPos(x, y);
    }

    private String getTokenStart(String line, int cursorOffset) {
        String upToCursor = line.substring(0, Math.min(cursorOffset, line.length()));
        int lastSpace = upToCursor.lastIndexOf(' ');
        return lastSpace >= 0 ? upToCursor.substring(0, lastSpace + 1) : "";
    }

    private void resetAnchor() {
        suggestorAnchorX = -1;
        anchorLineSnapshot = null;
    }

    private void applySuggestorPos(int x, int y) {
        if (suggestor == null) return;
        suggestor.setPos(x, y);
        suggestor.refreshRenderPos();
    }

    private void rebuildLines(String text) {
        lines.clear();
        lineStartIndices.clear();
        lineColors.clear();

        int idx = 0;
        for (String line : text.split("\n", -1)) {
            lines.add(line);
            lineStartIndices.add(idx);
            lineColors.add(new ArrayList<>());
            idx += line.length() + 1;
        }

        int cursor = accessor.getSelectionStart();
        activeLine = lineIndexForTextIndex(cursor);
        rebuildColorsForLine(activeLine);
    }

    private void rebuildColorsForLine(int lineIdx) {
        if (suggestor == null || lineIdx < 0 || lineIdx >= lines.size()) return;
        String line = lines.get(lineIdx);
        String cmd = line.startsWith("/") ? line.substring(1) : line;
        int firstCharIdx = line.startsWith("/") ? 1 : 0;
        List<Pair<Integer, Integer>> colors = suggestor.getColorsForLine(cmd, 0);
        if (firstCharIdx > 0) {
            List<Pair<Integer, Integer>> adjusted = new ArrayList<>();
            for (Pair<Integer, Integer> p : colors) {
                adjusted.add(new Pair<>(p.getLeft(), p.getRight() + firstCharIdx));
            }
            colors = adjusted;
        }
        if (lineIdx < lineColors.size()) {
            lineColors.set(lineIdx, colors);
        }
    }

    private int lineIndexForTextIndex(int textIdx) {
        for (int i = lineStartIndices.size() - 1; i >= 0; i--) {
            if (textIdx >= lineStartIndices.get(i)) return i;
        }
        return 0;
    }

    private int cursorOffsetInLine(int lineIdx, int textIdx) {
        if (lineIdx < 0 || lineIdx >= lineStartIndices.size()) return 0;
        return Math.max(0, textIdx - lineStartIndices.get(lineIdx));
    }

    public int getActiveLine() { return activeLine; }

    @Override
    public void setCursor(int cursor, boolean shiftKeyPressed) {
        accessor.setSelectionStart(Math.max(0, Math.min(cursor, accessor.getText().length())));
        if (!shiftKeyPressed) {
            accessor.setSelectionEnd(accessor.getSelectionStart());
        }
        int prevLine = activeLine;
        activeLine = lineIndexForTextIndex(accessor.getSelectionStart());
        if (activeLine != prevLine) {
            resetAnchor();
        }
        rebuildColorsForLine(activeLine);
        notifyChanged(accessor.getText());
        updateScrollForCursor();
    }

    @Override
    public void moveCursor(int offset, boolean shiftKeyPressed) {
        setCursor(accessor.invokeGetCursorPosWithOffset(offset), shiftKeyPressed);
    }

    private void moveCursorVertical(int delta) {
        int targetLine = Math.max(0, Math.min(lines.size() - 1, activeLine + delta));
        int offsetInLine = cursorOffsetInLine(activeLine, accessor.getSelectionStart());
        String targetLineText = targetLine < lines.size() ? lines.get(targetLine) : "";
        int clampedOffset = Math.min(offsetInLine, targetLineText.length());
        int newCursor = lineStartIndices.get(targetLine) + clampedOffset;
        resetAnchor();
        setCursor(newCursor, Screen.hasShiftDown());
    }

    private void updateScrollForCursor() {
        if (activeLine < scrolledLines) {
            scrolledLines = activeLine;
        } else if (activeLine >= scrolledLines + visibleLines) {
            scrolledLines = activeLine - visibleLines + 1;
        }
        scrolledLines = Math.max(0, scrolledLines);
        refreshSuggestorPos();
    }

    public void scrollBy(int delta) {
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        scrolledLines = Math.max(0, Math.min(maxScroll, scrolledLines - delta));
        refreshSuggestorPos();
    }

    void refreshSuggestorPos() {
        if (suggestor == null) return;

        if (lines.isEmpty()) {
            suggestor.setPos(getX() + PADDING, getY() + PADDING + LINE_HEIGHT);
            suggestor.refreshRenderPos();
            return;
        }

        int lineIdx = activeLine;
        int visLineIdx = lineIdx - scrolledLines;
        if (visLineIdx < 0 || visLineIdx >= visibleLines) {
            suggestor.setPos(getX() + PADDING, getY() - 200);
            suggestor.refreshRenderPos();
            return;
        }

        int x;
        if (suggestorAnchorX >= 0) {
            x = suggestorAnchorX;
        } else {
            int offsetInLine = cursorOffsetInLine(lineIdx, accessor.getSelectionStart());
            String line = lineIdx < lines.size() ? lines.get(lineIdx) : "";
            String textUpToCursor = line.substring(0, Math.min(offsetInLine, line.length()));
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            x = getX() + PADDING + tr.getWidth(textUpToCursor);
        }

        int y = getY() + PADDING + (visLineIdx + 1) * LINE_HEIGHT;
        suggestor.setPos(x, y);
        suggestor.refreshRenderPos();
    }

    public void forceRefreshSuggestorPos() {
        refreshSuggestorPos();
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        if (accessor.getDrawsBackground()) {
            int borderColor = isFocused() ? -1 : -6250336;
            context.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, borderColor);
            context.fill(getX(), getY(), getX() + width, getY() + height, -16777216);
        }

        int selStart = accessor.getSelectionStart();
        int selEnd = accessor.getSelectionEnd();
        if (selStart > selEnd) { int t = selEnd; selEnd = selStart; selStart = t; }

        boolean cursorVisible = isFocused()
                && (Util.getMeasuringTimeMs() - accessor.getLastSwitchFocusTime()) / 300L % 2L == 0L;

        context.enableScissor(getX(), getY(), getX() + width, getY() + height);

        RenderSystem.enableColorLogicOp();
        RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE);

        for (int i = scrolledLines; i < scrolledLines + visibleLines && i < lines.size(); i++) {
            String line = lines.get(i);
            int lineY = getY() + PADDING + (i - scrolledLines) * LINE_HEIGHT;
            int lineStartIdx = lineStartIndices.get(i);
            int lineEndIdx = lineStartIdx + line.length();

            drawHighlightedLine(context, tr, line, i, getX() + PADDING, lineY);

            if (cursorVisible && i == activeLine
                    && accessor.getSelectionStart() >= lineStartIdx
                    && accessor.getSelectionStart() <= lineEndIdx) {
                int cursorOffset = accessor.getSelectionStart() - lineStartIdx;
                int cx = getX() + PADDING + tr.getWidth(line.substring(0, Math.min(cursorOffset, line.length())));
                if (accessor.getSelectionStart() < accessor.getText().length()) {
                    context.fill(cx, lineY - 1, cx + 1, lineY + LINE_HEIGHT, -3092272);
                } else {
                    RenderSystem.disableColorLogicOp();
                    context.drawTextWithShadow(tr, "_", cx, lineY, -3092272);
                    RenderSystem.enableColorLogicOp();
                    RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE);
                }
            }

            if (selStart != selEnd) {
                int hlStart = Math.max(selStart, lineStartIdx);
                int hlEnd = Math.min(selEnd, lineEndIdx);
                if (hlStart < hlEnd) {
                    int x1 = getX() + PADDING + tr.getWidth(line.substring(0, hlStart - lineStartIdx));
                    int x2 = getX() + PADDING + tr.getWidth(line.substring(0, hlEnd - lineStartIdx));
                    accessor.invokeDrawSelectionHighlight(context, x1, lineY, x2, lineY + LINE_HEIGHT);
                }
            }
        }

        RenderSystem.disableColorLogicOp();
        context.disableScissor();

        if (suggestor != null && isFocused()) {
            suggestor.render(context, mouseX, mouseY);
        }
    }

    private void drawHighlightedLine(DrawContext context, TextRenderer tr, String line, int lineIdx, int x, int y) {
        if (lineIdx != activeLine || suggestor == null) {
            int color = (line.isEmpty() || line.startsWith("#")) ? 0xFF888888 : 0xFFFFFFFF;
            context.drawTextWithShadow(tr, line, x, y, color);
            return;
        }

        List<Pair<Integer, Integer>> colors = lineIdx < lineColors.size()
                ? lineColors.get(lineIdx) : new ArrayList<>();

        if (colors.isEmpty()) {
            context.drawTextWithShadow(tr, line, x, y, 0xFFFFFFFF);
            return;
        }

        int renderX = x;
        int currentOffset = 0;

        for (int ci = 0; ci < colors.size(); ci++) {
            if (currentOffset >= line.length()) break;
            int nextOffset = (ci + 1 < colors.size()) ? colors.get(ci + 1).getRight() : line.length();
            nextOffset = Math.min(nextOffset, line.length());
            if (nextOffset <= currentOffset) continue;

            String segment = line.substring(currentOffset, nextOffset);
            Style style = suggestor.getHighlightStyle(colors.get(ci).getLeft());
            int color = style.getColor() != null ? style.getColor().getRgb() | 0xFF000000 : 0xFFFFFFFF;

            context.drawTextWithShadow(tr, segment, renderX, y, color);
            renderX += tr.getWidth(segment);
            currentOffset = nextOffset;
        }

        if (currentOffset < line.length()) {
            context.drawTextWithShadow(tr, line.substring(currentOffset), renderX, y, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isActive()) return false;

        if (Screen.isSelectAll(keyCode)) {
            setCursorToEnd(Screen.hasShiftDown());
            accessor.setSelectionEnd(0);
            return true;
        }
        if (Screen.isCopy(keyCode)) {
            MinecraftClient.getInstance().keyboard.setClipboard(getSelectedText());
            return true;
        }
        if (Screen.isPaste(keyCode)) {
            if (accessor.getEditable()) write(MinecraftClient.getInstance().keyboard.getClipboard());
            return true;
        }
        if (Screen.isCut(keyCode)) {
            MinecraftClient.getInstance().keyboard.setClipboard(getSelectedText());
            if (accessor.getEditable()) write("");
            return true;
        }

        return switch (keyCode) {
            case 263 -> {
                if (Screen.hasControlDown()) setCursor(accessor.invokeGetCursorPosWithOffset(getWordSkipOffset(-1)), Screen.hasShiftDown());
                else moveCursor(-1, Screen.hasShiftDown());
                yield true;
            }
            case 262 -> {
                if (Screen.hasControlDown()) setCursor(accessor.invokeGetCursorPosWithOffset(getWordSkipOffset(1)), Screen.hasShiftDown());
                else moveCursor(1, Screen.hasShiftDown());
                yield true;
            }
            case 264 -> { moveCursorVertical(1); yield true; }
            case 265 -> { moveCursorVertical(-1); yield true; }
            case 259 -> { if (accessor.getEditable()) eraseImpl(-1); yield true; }
            case 261 -> { if (accessor.getEditable()) eraseImpl(1); yield true; }
            case 268 -> { setCursorToStart(Screen.hasShiftDown()); yield true; }
            case 269 -> { setCursorToEnd(Screen.hasShiftDown()); yield true; }
            // Enter / numpad Enter — insert a newline
            case 257, 335 -> {
                if (accessor.getEditable()) write("\n");
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!isActive()) return false;
        if (chr == '\n' || chr == '\r') {
            return false;
        }
        if (StringHelper.isValidChar(chr)) {
            if (accessor.getEditable()) write(Character.toString(chr));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible()) return false;
        boolean hovered = mouseX >= getX() && mouseX < getX() + width
                && mouseY >= getY() && mouseY < getY() + height;
        if (accessor.getFocusUnlocked()) setFocused(hovered);
        if (isFocused() && hovered && button == 0) {
            int clickedLine = scrolledLines + (int) ((mouseY - getY() - PADDING) / LINE_HEIGHT);
            clickedLine = Math.max(0, Math.min(lines.size() - 1, clickedLine));
            String line = clickedLine < lines.size() ? lines.get(clickedLine) : "";
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            String prefix = tr.trimToWidth(line, (int) (mouseX - getX() - PADDING));
            int newCursor = lineStartIndices.get(clickedLine) + prefix.length();
            resetAnchor();
            setCursor(newCursor, Screen.hasShiftDown());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isVisible()) return false;
        scrollBy((int) verticalAmount);
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (suggestor != null) {
            suggestor.setActive(focused);
        }
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
        this.visibleLines = Math.max(1, (height - PADDING * 2) / LINE_HEIGHT);
    }

    public String getActiveLineCmd() {
        if (activeLine < 0 || activeLine >= lines.size()) return "";
        String line = lines.get(activeLine);
        return line.startsWith("/") ? line.substring(1) : line;
    }

    public String getActiveLineRaw() {
        if (activeLine < 0 || activeLine >= lines.size()) return "";
        return lines.get(activeLine);
    }

    public void onSuggestorRefreshed() {
        rebuildColorsForLine(activeLine);
        updateSuggestorAnchor();
    }
}