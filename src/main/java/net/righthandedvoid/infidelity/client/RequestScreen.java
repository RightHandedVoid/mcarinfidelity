package net.righthandedvoid.infidelity.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.righthandedvoid.infidelity.intimacy.IntimacyNetwork;

/**
 * Opened by the request key. The initiator picks protected or unprotected; this only names the
 * target and the choice. The server re-checks everything, including whether the target is still there.
 */
public class RequestScreen extends Screen {

    private final int entityId;
    private final String targetName;

    public RequestScreen(int entityId, String targetName) {
        super(Component.literal("Ask"));
        this.entityId = entityId;
        this.targetName = targetName;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = height / 2;

        addRenderableWidget(Button.builder(Component.literal("With protection"), b -> send(true))
            .bounds(centerX - 172, y, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Without protection"), b -> send(false))
            .bounds(centerX - 56, y, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(centerX + 60, y, 112, 20).build());
    }

    private void send(boolean protection) {
        PacketDistributor.sendToServer(new IntimacyNetwork.RequestIntimacyPayload(entityId, protection));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "Ask " + targetName + "?", width / 2, height / 2 - 30, 0xFFFFFF);
        graphics.drawCenteredString(font, "They can still insist on protection.",
            width / 2, height / 2 - 16, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
