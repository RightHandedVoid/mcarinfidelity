package net.righthandedvoid.infidelity.client; // change to your package

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Accept/Decline prompt. Opened deliberately with the review key, never popped up on its own,
 * so nobody can click "Accept" by accident mid-game. Closing it (Esc) leaves the request pending.
 */
public class ConsentPromptScreen extends Screen {

    private final UUID requestId;
    private final String fromName;

    public ConsentPromptScreen(UUID requestId, String fromName) {
        super(Component.literal("Request"));
        this.requestId = requestId;
        this.fromName = fromName;
    }

    public UUID requestId() {
        return requestId;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = height / 2;

        addRenderableWidget(Button.builder(Component.literal("Accept"), b -> answer(true))
            .bounds(centerX - 105, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Decline"), b -> answer(false))
            .bounds(centerX + 5, y, 100, 20).build());
    }

    private void answer(boolean accept) {
        ClientConsent.sendResponse(requestId, accept);
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, fromName + " is asking to be intimate with you.",
            width / 2, height / 2 - 30, 0xFFFFFF);
        graphics.drawCenteredString(font, "You can say no, and either of you can stop at any time.",
            width / 2, height / 2 - 16, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}