package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.slot.MoveItemToSlotFromInventoryTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.BrewingStandSlot;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.effect.StatusEffect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class FuriousCocktailTask extends Task {
    private static final BrewingStandSlot POTION_SLOT = BrewingStandSlot.LEFT_POTION;
    private static final List<RegistryEntry<StatusEffect>> REQUIRED_EFFECTS = List.of(
            StatusEffects.FIRE_RESISTANCE, StatusEffects.INFESTED, StatusEffects.INVISIBILITY,
            StatusEffects.JUMP_BOOST, StatusEffects.NIGHT_VISION, StatusEffects.OOZING,
            StatusEffects.POISON, StatusEffects.REGENERATION, StatusEffects.SLOW_FALLING,
            StatusEffects.SLOWNESS, StatusEffects.RESISTANCE, StatusEffects.SPEED,
            StatusEffects.STRENGTH, StatusEffects.WATER_BREATHING, StatusEffects.WEAKNESS,
            StatusEffects.WEAVING, StatusEffects.WIND_CHARGED);
    private final List<BrewStage> stages = createStages();
    private final TimerGame useCooldown = new TimerGame(1);
    private Task interactStandTask;
    private Task fillBottleTask;
    private int stageIndex;
    private boolean brewStarted;
    private boolean applyingPotions;
    private boolean finished;
    private boolean warningReported;
    private final Set<Item> unavailableItemsReported = new HashSet<>();

    @Override
    protected void onStart() {
        AltoClef mod = AltoClef.getInstance();
        mod.getBehaviour().push();
        mod.getBehaviour().addProtectedItems(Items.POTION, Items.SPLASH_POTION,
                Items.GLASS_BOTTLE, Items.BLAZE_POWDER);
        interactStandTask = null;
        fillBottleTask = null;
        stageIndex = 0;
        brewStarted = false;
        applyingPotions = false;
        finished = false;
        warningReported = false;
        unavailableItemsReported.clear();
        useCooldown.reset();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) {
            return null;
        }
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            return new adris.altoclef.tasks.movement.DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (applyingPotions || stageIndex >= stages.size()) {
            applyingPotions = true;
            if (hasAllEffects(mod)) {
                finished = true;
                mod.getInputControls().release(Input.CLICK_RIGHT);
                return null;
            }
            if (!useCooldown.elapsed()) {
                return null;
            }
            if (!mod.getSlotHandler().forceEquipItem(Items.SPLASH_POTION)) {
                stageIndex = 0;
                applyingPotions = false;
                if (!warningReported) {
                    mod.logWarning("Some Furious Cocktail effects expired before all effects could be applied; preparing the splash potions again.");
                    warningReported = true;
                }
                return null;
            }
            LookHelper.lookAt(mod, mod.getPlayer().getPos().add(0, -2, 0), false);
            if (mod.getController().interactItem(mod.getPlayer(), Hand.MAIN_HAND).isAccepted()) {
                useCooldown.reset();
            }
            setDebugState("Applying splash potions (" + countActiveEffects(mod) + "/17 effects)");
            return null;
        }

        BrewStage stage = stages.get(stageIndex);
        if (hasPotion(mod, stage.outputPotion, stage.splash)) {
            stageIndex++;
            brewStarted = false;
            return null;
        }

        if (stage.inputPotion == Potions.WATER && !hasPotion(mod, Potions.WATER, false)) {
            if (!mod.getItemStorage().hasItem(Items.GLASS_BOTTLE)) {
                return getItemTask(Items.GLASS_BOTTLE, 1);
            }
            Optional<BlockPos> water = mod.getBlockScanner().getNearestBlock(Blocks.WATER);
            if (water.isEmpty()) {
                return new adris.altoclef.tasks.movement.TimeoutWanderTask(true);
            }
            if (fillBottleTask == null) {
                fillBottleTask = new InteractWithBlockTask(
                        new ItemTarget(Items.GLASS_BOTTLE, 1), water.get());
            }
            setDebugState("Filling a glass bottle with water");
            return fillBottleTask;
        }

        if (MinecraftClient.getInstance().player.currentScreenHandler
                instanceof BrewingStandScreenHandler handler) {
            return brewStage(mod, handler, stage);
        }

        if (!mod.getBlockScanner().anyFound(Blocks.BREWING_STAND)) {
            setDebugState("Placing a brewing stand");
            return new PlaceBlockNearbyTask(Blocks.BREWING_STAND);
        }
        BlockPos stand = mod.getBlockScanner().getNearestBlock(Blocks.BREWING_STAND)
                .orElseThrow(() -> new IllegalStateException("Brewing stand disappeared from the block scanner."));
        if (interactStandTask == null) {
            interactStandTask = new InteractWithBlockTask(stand);
        }
        setDebugState("Opening brewing stand");
        return interactStandTask;
    }

    private Task brewStage(AltoClef mod, BrewingStandScreenHandler handler, BrewStage stage) {
        ItemStack potionInStand = StorageHelper.getItemStackInSlot(POTION_SLOT);
        ItemStack ingredientInStand = StorageHelper.getItemStackInSlot(BrewingStandSlot.INGREDIENT);
        if (stage.outputPotion != null && potionMatches(potionInStand, stage.outputPotion, stage.splash)
                && ingredientInStand.isEmpty() && brewStarted) {
            mod.getSlotHandler().clickSlot(POTION_SLOT, 0, SlotActionType.QUICK_MOVE);
            stageIndex++;
            brewStarted = false;
            return null;
        }

        if (handler.getFuel() <= 0) {
            if (!mod.getItemStorage().hasItem(Items.BLAZE_POWDER)) {
                return getItemTask(Items.BLAZE_POWDER, 1);
            }
            if (StorageHelper.getItemStackInSlot(BrewingStandSlot.FUEL).isEmpty()) {
                return new MoveItemToSlotFromInventoryTask(
                        new ItemTarget(Items.BLAZE_POWDER, 1), BrewingStandSlot.FUEL);
            }
        }

        if (potionInStand.isEmpty()) {
            if (!putPotionInStand(mod, stage.inputPotion, stage.inputSplash)) {
                if (!hasPotion(mod, stage.inputPotion, stage.inputSplash)) {
                    stageIndex = 0;
                    return null;
                }
                return null;
            }
            return null;
        }
        if (!potionMatches(potionInStand, stage.inputPotion, stage.inputSplash)) {
            mod.getSlotHandler().clickSlot(POTION_SLOT, 0, SlotActionType.QUICK_MOVE);
            return null;
        }

        if (ingredientInStand.isEmpty()) {
            if (!mod.getItemStorage().hasItem(stage.ingredient)) {
                return getItemTask(stage.ingredient, 1);
            }
            return new MoveItemToSlotFromInventoryTask(
                    new ItemTarget(stage.ingredient, 1), BrewingStandSlot.INGREDIENT);
        }

        if (handler.getBrewTime() > 0) {
            brewStarted = true;
            setDebugState("Brewing " + stage.description);
            return null;
        }

        if (brewStarted) {
            if (ingredientInStand.isEmpty() && potionMatches(potionInStand, stage.outputPotion, stage.splash)) {
                mod.getSlotHandler().clickSlot(POTION_SLOT, 0, SlotActionType.QUICK_MOVE);
                stageIndex++;
                brewStarted = false;
                return null;
            }
            brewStarted = false;
        }
        return null;
    }

    private boolean putPotionInStand(AltoClef mod, RegistryEntry<net.minecraft.potion.Potion> potion,
                                     boolean splash) {
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        if (!cursor.isEmpty()) {
            if (potionMatches(cursor, potion, splash)) {
                mod.getSlotHandler().clickSlot(POTION_SLOT, 0, SlotActionType.PICKUP);
                return true;
            }
            Optional<Slot> safeSlot = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false);
            safeSlot.ifPresent(slot -> mod.getSlotHandler().clickSlot(slot, 0, SlotActionType.PICKUP));
            return false;
        }
        Optional<Slot> slot = findPotionSlot(mod, potion, splash);
        if (slot.isEmpty()) {
            return false;
        }
        mod.getSlotHandler().clickSlot(slot.get(), 0, SlotActionType.PICKUP);
        return false;
    }

    private Optional<Slot> findPotionSlot(AltoClef mod, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return findPotionSlot(mod, stack.get(DataComponentTypes.POTION_CONTENTS).potion().orElse(null),
                stack.getItem() == Items.SPLASH_POTION);
    }

    private Optional<Slot> findPotionSlot(AltoClef mod, RegistryEntry<net.minecraft.potion.Potion> potion,
                                          boolean splash) {
        Item item = splash ? Items.SPLASH_POTION : Items.POTION;
        for (Slot slot : mod.getItemStorage().getSlotsWithItemPlayerInventory(false, item)) {
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            if (potionMatches(stack, potion, splash)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    private boolean hasPotion(AltoClef mod, RegistryEntry<net.minecraft.potion.Potion> potion, boolean splash) {
        return findPotionSlot(mod, potion, splash).isPresent();
    }

    private boolean potionMatches(ItemStack stack, RegistryEntry<net.minecraft.potion.Potion> potion,
                                  boolean splash) {
        if (stack.isEmpty() || potion == null
                || stack.getItem() != (splash ? Items.SPLASH_POTION : Items.POTION)) {
            return false;
        }
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        return contents != null && contents.matches(potion);
    }

    private Task getItemTask(Item item, int count) {
        if (!TaskCatalogue.taskExists(item)) {
            if (unavailableItemsReported.add(item)) {
                AltoClef.getInstance().logWarning("Cannot brew Furious Cocktail potion: no resource task is available for "
                        + item.getName().getString() + ".");
            }
            return null;
        }
        return TaskCatalogue.getItemTask(item, count);
    }

    private boolean hasAllEffects(AltoClef mod) {
        return REQUIRED_EFFECTS.stream().allMatch(mod.getPlayer()::hasStatusEffect);
    }

    private int countActiveEffects(AltoClef mod) {
        return (int) REQUIRED_EFFECTS.stream().filter(mod.getPlayer()::hasStatusEffect).count();
    }

    private static List<BrewStage> createStages() {
        List<BrewStage> result = new ArrayList<>();
        addPotion(result, Potions.AWKWARD, Items.MAGMA_CREAM, Potions.FIRE_RESISTANCE, Potions.LONG_FIRE_RESISTANCE, "fire resistance");
        addPotion(result, Potions.AWKWARD, Items.STONE, Potions.INFESTED, null, "infestation");
        addPotion(result, Potions.AWKWARD, Items.RABBIT_FOOT, Potions.LEAPING, Potions.LONG_LEAPING, "jump boost");
        result.add(new BrewStage(Potions.WATER, false, Items.NETHER_WART, Potions.AWKWARD, false, "awkward potion"));
        result.add(new BrewStage(Potions.AWKWARD, false, Items.GOLDEN_CARROT, Potions.NIGHT_VISION, false, "night vision"));
        result.add(new BrewStage(Potions.NIGHT_VISION, false, Items.FERMENTED_SPIDER_EYE,
                Potions.INVISIBILITY, false, "invisibility"));
        result.add(new BrewStage(Potions.INVISIBILITY, false, Items.REDSTONE,
                Potions.LONG_INVISIBILITY, false, "extended invisibility"));
        result.add(new BrewStage(Potions.LONG_INVISIBILITY, false, Items.GUNPOWDER,
                Potions.LONG_INVISIBILITY, true, "splash invisibility"));
        result.add(new BrewStage(Potions.WATER, false, Items.NETHER_WART, Potions.AWKWARD, false, "awkward potion"));
        result.add(new BrewStage(Potions.AWKWARD, false, Items.GOLDEN_CARROT, Potions.NIGHT_VISION, false, "night vision"));
        result.add(new BrewStage(Potions.NIGHT_VISION, false, Items.REDSTONE,
                Potions.LONG_NIGHT_VISION, false, "extended night vision"));
        result.add(new BrewStage(Potions.LONG_NIGHT_VISION, false, Items.GUNPOWDER,
                Potions.LONG_NIGHT_VISION, true, "splash night vision"));
        addPotion(result, Potions.AWKWARD, Items.SLIME_BLOCK, Potions.OOZING, null, "oozing");
        addPotion(result, Potions.AWKWARD, Items.SPIDER_EYE, Potions.POISON, Potions.LONG_POISON, "poison");
        addPotion(result, Potions.AWKWARD, Items.GHAST_TEAR, Potions.REGENERATION, Potions.LONG_REGENERATION, "regeneration");
        addPotion(result, Potions.AWKWARD, Items.PHANTOM_MEMBRANE, Potions.SLOW_FALLING, Potions.LONG_SLOW_FALLING, "slow falling");
        addPotion(result, Potions.AWKWARD, Items.TURTLE_HELMET, Potions.TURTLE_MASTER, Potions.LONG_TURTLE_MASTER, "slowness and resistance");
        addPotion(result, Potions.AWKWARD, Items.SUGAR, Potions.SWIFTNESS, Potions.LONG_SWIFTNESS, "speed");
        addPotion(result, Potions.AWKWARD, Items.BLAZE_POWDER, Potions.STRENGTH, Potions.LONG_STRENGTH, "strength");
        addPotion(result, Potions.AWKWARD, Items.PUFFERFISH, Potions.WATER_BREATHING, Potions.LONG_WATER_BREATHING, "water breathing");
        addPotion(result, Potions.WATER, Items.FERMENTED_SPIDER_EYE, Potions.WEAKNESS, Potions.LONG_WEAKNESS, "weakness");
        addPotion(result, Potions.AWKWARD, Items.COBWEB, Potions.WEAVING, null, "weaving");
        addPotion(result, Potions.AWKWARD, Items.BREEZE_ROD, Potions.WIND_CHARGED, null, "wind charged");
        return result;
    }

    private static void addPotion(List<BrewStage> stages, RegistryEntry<net.minecraft.potion.Potion> base,
                                  Item ingredient, RegistryEntry<net.minecraft.potion.Potion> potion,
                                  RegistryEntry<net.minecraft.potion.Potion> extended, String name) {
        if (base == Potions.AWKWARD) {
            stages.add(new BrewStage(Potions.WATER, false, Items.NETHER_WART,
                    Potions.AWKWARD, false, "awkward potion"));
        }
        stages.add(new BrewStage(base, false, ingredient, potion, false, name));
        RegistryEntry<net.minecraft.potion.Potion> longPotion = extended == null ? potion : extended;
        if (extended != null) {
            stages.add(new BrewStage(potion, false, Items.REDSTONE, extended, false, "extended " + name));
        }
        stages.add(new BrewStage(longPotion, false, Items.GUNPOWDER, longPotion, true, "splash " + name));
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    protected void onStop(Task interruptTask) {
        StorageHelper.closeScreen();
        AltoClef mod = AltoClef.getInstance();
        mod.getInputControls().release(Input.CLICK_RIGHT);
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FuriousCocktailTask;
    }

    @Override
    protected String toDebugString() {
        return "Applying all potion effects for A Furious Cocktail";
    }

    private record BrewStage(RegistryEntry<net.minecraft.potion.Potion> inputPotion, boolean inputSplash,
                             Item ingredient, RegistryEntry<net.minecraft.potion.Potion> outputPotion,
                             boolean splash, String description) {
    }
}
