/**
 * This class was created by <Vazkii>. It's distributed as
 * part of the Quark Mod. Get the Source Code in github:
 * https://github.com/Vazkii/Quark
 *
 * Quark is Open Source and distributed under the
 * CC-BY-NC-SA 3.0 License: https://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB
 *
 * File Created @ [06/06/2016, 01:40:29 (GMT)]
 */
package vazkii.quark.content.management.module;

import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ScreenEvent.KeyPressed;
import net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import vazkii.quark.base.module.LoadModule;
import vazkii.quark.base.module.ModuleCategory;
import vazkii.quark.base.module.ModuleLoader;
import vazkii.quark.base.module.QuarkModule;
import vazkii.quark.base.module.config.Config;
import vazkii.quark.base.network.QuarkNetwork;
import vazkii.quark.base.network.message.ShareItemMessage;
import vazkii.quark.mixin.accessor.AccessorServerGamePacketListenerImpl;
import vazkii.quark.mixin.client.accessor.AccessorLocalPlayer;

import java.time.Instant;
import java.util.List;

@LoadModule(category = ModuleCategory.MANAGEMENT, hasSubscriptions = true, subscribeOn = Dist.CLIENT)
public class ItemSharingModule extends QuarkModule {

	@Config
	public static boolean renderItemsInChat = true;

	//global variable to apply 5 sec cooldown
	private static long lastShareTimestamp = -1;

	@OnlyIn(Dist.CLIENT)
	public static void renderItemForMessage(PoseStack poseStack, FormattedCharSequence sequence, float x, float y, int color) {
		if (!ModuleLoader.INSTANCE.isModuleEnabled(ItemSharingModule.class) || !renderItemsInChat)
			return;

		Minecraft mc = Minecraft.getInstance();

		StringBuilder before = new StringBuilder();

		sequence.accept((counter_, style, character) -> {
			String sofar = before.toString();
			if (sofar.endsWith("    ")) {
				render(mc, poseStack, sofar.substring(0, sofar.length() - 3), x, y, style, color);
				return false;
			}
			before.append((char) character);
			return true;
		});
	}

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public void onKeyInput(KeyPressed.Pre event) {
		KeyMapping key = getChatKey();
		if(key.getKey().getType() == Type.KEYSYM && event.getKeyCode() == key.getKey().getValue())
			event.setCanceled(clicc());

	}

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public void onMouseInput(MouseButtonPressed.Post event) {
		KeyMapping key = getChatKey();
		int btn = event.getButton();
		if(key.getKey().getType() == Type.MOUSE && btn != GLFW.GLFW_MOUSE_BUTTON_LEFT && btn == key.getKey().getValue())
			event.setCanceled(clicc());
	}

	private KeyMapping getChatKey() {
		return Minecraft.getInstance().options.keyChat;
	}

	public boolean clicc() {
		Minecraft mc = Minecraft.getInstance();
		Screen screen = mc.screen;

		if(screen instanceof AbstractContainerScreen<?> gui && Screen.hasShiftDown()) {
			List<? extends GuiEventListener> children = gui.children();
			for(GuiEventListener c : children)
				if(c instanceof EditBox tf) {
					if(tf.isFocused())
						return false;
				}

			Slot slot = gui.getSlotUnderMouse();
			if(slot != null) {
				ItemStack stack = slot.getItem();

				if(!stack.isEmpty()) {
					if(mc.level != null && mc.level.getGameTime() - lastShareTimestamp > 100) {
						lastShareTimestamp = mc.level.getGameTime();
					} else return false;

					if (mc.player instanceof AccessorLocalPlayer accessorLocalPlayer) {
						Component itemComp = stack.getDisplayName();
						String rawMessage = SharedConstants.filterText(itemComp.getString());

						@SuppressWarnings("UnstableApiUsage")
						String transformedMessage = ForgeHooksClient.onClientSendMessage(rawMessage);

						if (transformedMessage.equals(rawMessage)) {
							ChatMessageContent content = accessorLocalPlayer.quark$buildSignedContent(rawMessage, itemComp);
							MessageSigner sign = accessorLocalPlayer.quark$createMessageSigner();
							LastSeenMessages.Update update = mc.player.connection.generateMessageAcknowledgements();
							MessageSignature signature = accessorLocalPlayer.quark$signMessage(sign, content, update.lastSeen());

							ShareItemMessage message = new ShareItemMessage(stack, content.plain(), content.decorated(), sign.timeStamp(), sign.salt(), signature, content.isDecorated(), update);
							QuarkNetwork.sendToServer(message);

							return true;
						}
					}
				}
			}
		}

		return false;
	}

	public static void shareItem(ServerPlayer player, String message, Component component, ItemStack stack, Instant timeStamp, long salt, MessageSignature signature, boolean signedPreview, LastSeenMessages.Update lastSeenMessages) {
		if (!ModuleLoader.INSTANCE.isModuleEnabled(ItemSharingModule.class))
			return;

		Component itemComp = stack.getDisplayName();

		// This is done to ensure that arbitrary components can't be sent - only the stack's component.
		// Component is checked on this side to ensure the signing was of the correct component.
		if (itemComp.equals(component)) {
			((AccessorServerGamePacketListenerImpl) player.connection).quark$chatPreviewCache().set(message, itemComp);

			player.connection.handleChat(new ServerboundChatPacket(message, timeStamp, salt, signature, signedPreview, lastSeenMessages));
		}
	}

	public static MutableComponent createStackComponent(ItemStack stack, MutableComponent component) {
		if (!ModuleLoader.INSTANCE.isModuleEnabled(ItemSharingModule.class) || !renderItemsInChat)
			return component;

		Style style = component.getStyle();
		if (stack.getCount() > 64) {
			ItemStack copyStack = stack.copy();
			copyStack.setCount(64);
			style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(copyStack)));
			component.withStyle(style);
		}

		MutableComponent out = Component.literal("   ");
		out.setStyle(style);
		return out.append(component);
	}

	@OnlyIn(Dist.CLIENT)
	private static void render(Minecraft mc, PoseStack pose, String before, float x, float y, Style style, int color) {
		float a = (color >> 24 & 255) / 255.0F;

		HoverEvent hoverEvent = style.getHoverEvent();
		if (hoverEvent != null && hoverEvent.getAction() == HoverEvent.Action.SHOW_ITEM) {
			HoverEvent.ItemStackInfo contents = hoverEvent.getValue(HoverEvent.Action.SHOW_ITEM);

			ItemStack stack = contents != null ? contents.getItemStack() : ItemStack.EMPTY;

			if (stack.isEmpty())
				stack = new ItemStack(Blocks.BARRIER); // for invalid icon

			int shift = mc.font.width(before);

			if (a > 0) {
				alphaValue = a;

				PoseStack poseStack = RenderSystem.getModelViewStack();

				poseStack.pushPose();

				poseStack.mulPoseMatrix(pose.last().pose());

				poseStack.translate(shift + x, y, 0);
				poseStack.scale(0.5f, 0.5f, 0.5f);
				mc.getItemRenderer().renderGuiItem(stack, 0, 0);
				poseStack.popPose();

				RenderSystem.applyModelViewMatrix();

				alphaValue = 1F;
			}
		}
	}


	// used in a mixin because rendering overrides are cursed by necessity hahayes
	public static float alphaValue = 1F;
}
