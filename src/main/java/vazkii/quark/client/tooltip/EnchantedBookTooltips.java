package vazkii.quark.client.tooltip;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.registries.GameData;
import vazkii.quark.client.module.ImprovedTooltipsModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EnchantedBookTooltips {

	private static List<ItemStack> testItems = null;
	private static Multimap<Enchantment, ItemStack> additionalStacks = null;

	public static void reloaded() {
		additionalStacks = null;
		testItems = null;
	}

	@OnlyIn(Dist.CLIENT)
	public static void makeTooltip(ItemTooltipEvent event) {
		if(event.getEntityPlayer() == null)
			return;

		ItemStack stack = event.getItemStack();
		if(stack.getItem() == Items.ENCHANTED_BOOK/* || stack.getItem() == AncientTomes.ancient_tome*/) {
			Minecraft mc = Minecraft.getInstance();
			List<ITextComponent> tooltip = event.getToolTip();
			int tooltipIndex = 0;

			List<EnchantmentData> enchants = getEnchantedBookEnchantments(stack);
			for(EnchantmentData ed : enchants) {
				ITextComponent match = ed.enchantment.getDisplayName(ed.enchantmentLevel);

				for(; tooltipIndex < tooltip.size(); tooltipIndex++)
					if(tooltip.get(tooltipIndex).equals(match)) {
						List<ItemStack> items = getItemsForEnchantment(ed.enchantment);
						if(!items.isEmpty()) {
							int len = 3 + items.size() * 9;
							String spaces = "";
							while(mc.fontRenderer.getStringWidth(spaces) < len)
								spaces += " ";

							tooltip.add(tooltipIndex + 1, new StringTextComponent(spaces));
						}

						break;
					}
			}
		}
	}

	@OnlyIn(Dist.CLIENT)
	public static void renderTooltip(RenderTooltipEvent.PostText event) {
		ItemStack stack = event.getStack();

		if(stack.getItem() == Items.ENCHANTED_BOOK/* || stack.getItem() == AncientTomes.ancient_tome*/) {
			Minecraft mc = Minecraft.getInstance();
			List<String> tooltip = event.getLines();

			GlStateManager.pushMatrix();
			GlStateManager.translatef(event.getX(), event.getY() + 12, 0);
			GlStateManager.scalef(0.5f, 0.5f, 1.0f);

			List<EnchantmentData> enchants = getEnchantedBookEnchantments(stack);
			for(EnchantmentData ed : enchants) {
				String match = ed.enchantment.getDisplayName(ed.enchantmentLevel).getString();
				for(int tooltipIndex = 0; tooltipIndex < tooltip.size(); tooltipIndex++) {
					String line = TextFormatting.getTextWithoutFormattingCodes(tooltip.get(tooltipIndex));
					if(line != null && line.equals(match)) {
						int drawn = 0;

						List<ItemStack> items = getItemsForEnchantment(ed.enchantment);
						for(ItemStack testStack : items) {
							mc.getItemRenderer().renderItemIntoGUI(testStack, 6 + drawn * 18, tooltipIndex * 20 - 2);
							drawn++;
						}

						break;
					}
				}
			}
			GlStateManager.popMatrix();
		}
	}

	public static List<ItemStack> getItemsForEnchantment(Enchantment e) {
		List<ItemStack> list = new ArrayList<>();

		for(ItemStack stack : getTestItems()) {
			if (!stack.isEmpty() && e.canApply(stack))
				list.add(stack);
		}

		if(getAdditionalStacks().containsKey(e))
			list.addAll(getAdditionalStacks().get(e));

		return list;
	}

	public static List<EnchantmentData> getEnchantedBookEnchantments(ItemStack stack) {
		Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);

		List<EnchantmentData> retList = new ArrayList<>(enchantments.size());

		for(Enchantment enchantment : enchantments.keySet()) {
			if (enchantment != null) {
				int level = enchantments.get(enchantment);
				retList.add(new EnchantmentData(enchantment, level));
			}
		}

		return retList;
	}

	private static Multimap<Enchantment, ItemStack> getAdditionalStacks() {
		if (additionalStacks == null)
			computeAdditionalStacks();
		return additionalStacks;
	}

	private static List<ItemStack> getTestItems() {
		if (testItems == null)
			computeTestItems();
		return testItems;
	}

	private static void computeTestItems() {
		testItems = Lists.newArrayList();

		for (String loc : ImprovedTooltipsModule.enchantingStacks) {
			Item item = GameData.getWrapper(Item.class).getOrDefault(new ResourceLocation(loc));
			if (item != null)
				testItems.add(new ItemStack(item));
		}
	}

	private static void computeAdditionalStacks() {
		additionalStacks = HashMultimap.create();

		for(String s : ImprovedTooltipsModule.enchantingAdditionalStacks) {
			if(!s.contains("="))
				continue;

			String[] tokens = s.split("=");
			String left = tokens[0];
			String right = tokens[1];

			Enchantment ench = GameData.getWrapper(Enchantment.class).getOrDefault(new ResourceLocation(left));
			if(ench != null) {
				tokens = right.split(",");

				for(String itemId : tokens) {
					Item item = GameData.getWrapper(Item.class).getOrDefault(new ResourceLocation(itemId));
					if(item != null)
						additionalStacks.put(ench, new ItemStack(item));
				}
			}
		}
	}
}
