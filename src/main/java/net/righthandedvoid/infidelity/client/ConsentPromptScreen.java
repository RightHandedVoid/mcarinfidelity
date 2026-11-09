package net.righthandedvoid.infidelity.client; // change to your package

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.righthandedvoid.infidelity.consent.ConsentResponse;

import java.util.UUID;

/**
 * Accept/Decline prompt. Opened deliberately with the review key, never popped up on its own,
 * so nobody can click "Accept" by accident mid-game. Closing it (Esc) leaves the request pending.
 *
 * If the request is unprotected, the responder can accept only with protection. A protected
 * request can't be downgraded here.
 */
public class ConsentPromptScreen extends Screen {

    private final UUID requestId;
    private final String fromName;
    private final boolean protection;

    public ConsentPromptScreen(UUID requestId, String fromName, boolean protection) {
        super(Component.literal("Request"));
        this.requestId = requestId;
        this.fromName = fromName;
        this.protection = protection;
    }

    public UUID requestId() {
        return requestId;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = height / 2;

        if (protection) {
            addRenderableWidget(Button.builder(Component.literal("Accept"), b -> answer(ConsentResponse.ACCEPT))
                .bounds(centerX - 105, y, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Decline"), b -> answer(ConsentResponse.DECLINE))
                .bounds(centerX + 5, y, 100, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Accept"), b -> answer(ConsentResponse.ACCEPT))
                .bounds(centerX - 172, y, 112, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Only with protection"),
                    b -> answer(ConsentResponse.ACCEPT_WITH_PROTECTION))
                .bounds(centerX - 56, y, 112, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Decline"), b -> answer(ConsentResponse.DECLINE))
                .bounds(centerX + 60, y, 112, 20).build());
        }
    }

    private void answer(ConsentResponse response) {
        ClientConsent.sendResponse(requestId, response);
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, fromName + " is asking to be intimate with you, "
                + (protection ? "with" : "without") + " protection.",
            width / 2, height / 2 - 30, 0xFFFFFF);
        graphics.drawCenteredString(font, "You can say no, and either of you can stop at any time.",
            width / 2, height / 2 - 16, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}