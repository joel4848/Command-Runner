package com.joel4848.commandrunner.screen;

import net.minecraft.client.font.MultilineText;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class HelpScreen extends Screen {

    private static final int PADDING = 15;
    private static final int BACK_BTN_W = 80;
    private static final int BACK_BTN_H = 20;

    private final Screen parent;
    private MultilineText guideContent = MultilineText.EMPTY;

    public HelpScreen(Screen parent) {
        super(Text.literal("User Guide").formatted(Formatting.GREEN, Formatting.BOLD, Formatting.UNDERLINE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> client.setScreen(parent))
                .dimensions(this.width / 2 - BACK_BTN_W / 2, this.height - PADDING - BACK_BTN_H, BACK_BTN_W, BACK_BTN_H)
                .build());

        int maxTextWidth = this.width - (PADDING * 2);

        Text fullGuideText = Text.empty()

                // Header
                // .append(Text.literal("What is this?\n").formatted(Formatting.GOLD, Formatting.BOLD, Formatting.UNDERLINE))
                // Body
                // .append(Text.literal("Command Runner lets you bulk-run commands, similar to a function file in a datapack. You can save and edit batches of commands ('presets') and schedule them to run on certain triggers.\n\n").formatted(Formatting.WHITE))
                // .append(Text.literal("Commands are run by the client, so only commands which you have permission to run will work.\n\n").formatted(Formatting.WHITE))

                // Header
                .append(Text.literal("Getting Started\n").formatted(Formatting.GOLD, Formatting.BOLD, Formatting.UNDERLINE))
                // Body
                .append(Text.literal("Write/paste normal Minecraft commands in the main text box - one per line. I've bodged command autocomplete into it so it works kinda like typing a command into the chat bar: use ").formatted(Formatting.WHITE))
                .append(Text.literal("TAB").formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal("/").formatted(Formatting.WHITE))
                .append(Text.literal("SHIFT + TAB").formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal(" or the ").formatted(Formatting.WHITE))
                .append(Text.literal("UP").formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("/").formatted(Formatting.WHITE))
                .append(Text.literal("DOWN").formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" arrows to cycle through autocomplete suggestions.\n\n").formatted(Formatting.WHITE))

                .append(Text.literal("You don't have to start commands with a /, but as far as I'm aware it won't break anything if you do.\n\n").formatted(Formatting.WHITE))

                // Header
                .append(Text.literal("Internal Syntax\n").formatted(Formatting.GOLD, Formatting.BOLD, Formatting.UNDERLINE))
                // Body
                .append(Text.literal("• ").formatted(Formatting.WHITE))
                .append(Text.literal("!wait <time><t/s/d> ").formatted(Formatting.GREEN))
                .append(Text.literal(" - inserts a pause for <time> ticks/seconds/in-game days.\n").formatted(Formatting.WHITE))
                .append(Text.literal("Example: ").formatted(Formatting.BLUE))
                .append(Text.literal("!wait 5s ").formatted(Formatting.GRAY))
                .append(Text.literal("- inserts a 5-second pause\n\n").formatted(Formatting.BLUE))
                .append(Text.literal("• ").formatted(Formatting.WHITE))
                .append(Text.literal("!repeat <count>").formatted(Formatting.GREEN))
                .append(Text.literal(" / ").formatted(Formatting.WHITE))
                .append(Text.literal("!endrepeat").formatted(Formatting.GREEN))
                .append(Text.literal(" - repeats the wrapped commands <count> times (can be nested).\n").formatted(Formatting.WHITE))
                .append(Text.literal("Example:\n").formatted(Formatting.BLUE))
                .append(Text.literal("!repeat 5\n").formatted(Formatting.GRAY))
                .append(Text.literal("<block of commands>\n").formatted(Formatting.GRAY))
                .append(Text.literal("!endrepeat ").formatted(Formatting.GRAY))
                .append(Text.literal("- repeats <block of commands> 5 times\n\n").formatted(Formatting.BLUE))

                // Header
                .append(Text.literal("Scheduling Presets\n").formatted(Formatting.GOLD, Formatting.BOLD, Formatting.UNDERLINE))
                // Body
                .append(Text.literal("Click ").formatted(Formatting.WHITE))
                .append(Text.literal("Schedule").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal(" to open the preset scheduler window. There you can enable/disable/configure various triggers which will automatically run the loaded preset.").formatted(Formatting.WHITE));

        this.guideContent = MultilineText.create(textRenderer, fullGuideText, maxTextWidth);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);

        int textStartY = PADDING +5;
        this.guideContent.drawWithShadow(context, PADDING, textStartY, textRenderer.fontHeight + 3, 0xFFFFFFFF);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}