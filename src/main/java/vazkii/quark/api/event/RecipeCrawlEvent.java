package vazkii.quark.api.event;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.eventbus.api.Event;

public abstract class RecipeCrawlEvent extends Event {

	public static class CrawlStarting extends RecipeCrawlEvent {}
	public static class CrawlEnded extends RecipeCrawlEvent {}
	public static class Reset extends RecipeCrawlEvent {}
	
	public static abstract class Visit<T extends Recipe<?>> extends RecipeCrawlEvent {
		
		public final T recipe;
		public final ResourceLocation recipeID;
		public final ItemStack output;
		public final NonNullList<Ingredient> ingredients;
		
		public Visit(T recipe) {
			this.recipe = recipe;
			this.recipeID = recipe.getId();
			this.output = recipe.getResultItem();
			this.ingredients = recipe.getIngredients();
		}
		
		public static class Shaped extends Visit<ShapedRecipe> {

			public Shaped(ShapedRecipe recipe) {
				super(recipe);
			}
			
		}
		
		public static class Shapeless extends Visit<ShapelessRecipe> {

			public Shapeless(ShapelessRecipe recipe) {
				super(recipe);
			}
			
		}
		
		public static class Custom extends Visit<CustomRecipe> {

			public Custom(CustomRecipe recipe) {
				super(recipe);
			}
			
		}
		
		public static class Cooking extends Visit<AbstractCookingRecipe> {

			public Cooking(AbstractCookingRecipe recipe) {
				super(recipe);
			}
			
		}
		
		public static class Misc extends Visit<Recipe<?>> {

			public Misc(Recipe<?> recipe) {
				super(recipe);
			}
			
		}
		
	}
	
}

