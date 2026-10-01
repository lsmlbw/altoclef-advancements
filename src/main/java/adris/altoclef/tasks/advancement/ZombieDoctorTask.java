package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
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
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ZombieDoctorTask extends Task {
    private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(true);
    private ZombieVillagerEntity target;
    private BlockPos curePosition;
    private List<BlockPos> shelterBlocks;
    private int shelterIndex;
    private Task shelterTask;
    private Task fillBottleTask;
    private Task openStandTask;
    private boolean potionThrown;
    private boolean appleUsed;
    private boolean finished;
    private final TimerGame potionEffectTimer = new TimerGame(10);

    @Override
    protected void onStart() {
        target = null;
        curePosition = null;
        shelterBlocks = null;
        shelterIndex = 0;
        shelterTask = null;
        fillBottleTask = null;
        openStandTask = null;
        potionThrown = false;
        appleUsed = false;
        finished = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            setDebugState("Returning to the Overworld");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (curePosition != null && target != null && !target.isAlive()) {
            boolean curedVillagerNearby = mod.getEntityTracker().getTrackedEntities(VillagerEntity.class).stream()
                    .anyMatch(villager -> villager.isAlive()
                            && villager.squaredDistanceTo(curePosition.getX(), curePosition.getY(),
                            curePosition.getZ()) < 25);
            if (curedVillagerNearby) {
                finished = true;
                return null;
            }
        }
        if (target == null || !target.isAlive()) {
            target = mod.getEntityTracker().getClosestEntity(mod.getPlayer().getPos(),
                            entity -> entity instanceof ZombieVillagerEntity zombie
                                    && zombie.isAlive() && !zombie.isConverting(),
                            ZombieVillagerEntity.class)
                    .map(entity -> (ZombieVillagerEntity) entity).orElse(null);
            if (target == null) {
                setDebugState("Searching for a zombie villager");
                return wanderTask;
            }
            curePosition = target.getBlockPos();
            shelterBlocks = createShelter(curePosition);
            shelterIndex = 0;
            shelterTask = null;
            potionThrown = false;
            appleUsed = false;
        }

        if (target.isConverting()) {
            setDebugState("Waiting for the zombie villager to finish curing");
            return null;
        }
        if (target.squaredDistanceTo(mod.getPlayer()) > 8 * 8) {
            setDebugState("Approaching the zombie villager");
            return new GetToEntityTask(target, 5);
        }

        if (!isSheltered(mod, curePosition)) {
            Task shelter = buildShelter(mod);
            if (shelter != null) {
                setDebugState("Building a shelter around the zombie villager");
                return shelter;
            }
        }

        if (!target.hasStatusEffect(StatusEffects.WEAKNESS)) {
            if (potionThrown && !potionEffectTimer.elapsed()) {
                setDebugState("Waiting for the Weakness potion to take effect");
                return null;
            }
            if (potionThrown) potionThrown = false;
            Optional<Slot> weaknessPotion = findPotionSlot(mod, Potions.WEAKNESS, true);
            if (weaknessPotion.isEmpty()) {
                Task brewing = brewWeakness(mod);
                if (brewing != null) {
                    setDebugState("Brewing a splash potion of Weakness");
                    return brewing;
                }
                return null;
            }
            mod.getSlotHandler().forceEquipSlot(weaknessPotion.get());
            LookHelper.lookAt(mod, target.getEyePos(), false);
            if (mod.getController().interactItem(mod.getPlayer(), Hand.MAIN_HAND).isAccepted()) {
                potionThrown = true;
                potionEffectTimer.reset();
            }
            setDebugState("Applying Weakness to the zombie villager");
            return null;
        }

        if (!appleUsed) {
            if (!mod.getItemStorage().hasItem(Items.GOLDEN_APPLE)) {
                return getItemTask(Items.GOLDEN_APPLE);
            }
            if (!mod.getSlotHandler().forceEquipItem(Items.GOLDEN_APPLE)) return null;
            LookHelper.lookAt(mod, target.getEyePos(), false);
            if (mod.getController().interactEntity(mod.getPlayer(), target, Hand.MAIN_HAND).isAccepted()) {
                appleUsed = true;
                setDebugState("Giving the weakened zombie villager a golden apple");
            }
            return null;
        }

        setDebugState("Keeping the zombie villager loaded while it cures");
        return null;
    }

    private Task buildShelter(AltoClef mod) {
        if (shelterBlocks == null) shelterBlocks = createShelter(curePosition);
        if (shelterIndex >= shelterBlocks.size()) return null;
        if (!mod.getItemStorage().hasItem(Items.COBBLESTONE)) {
            return getItemTask(Items.COBBLESTONE, 25);
        }
        BlockPos pos = shelterBlocks.get(shelterIndex);
        if (!mod.getWorld().getBlockState(pos).isReplaceable()) {
            shelterIndex++;
            shelterTask = null;
            return null;
        }
        if (shelterTask == null) shelterTask = new PlaceBlockTask(pos, Blocks.COBBLESTONE);
        if (shelterTask.isFinished()) {
            shelterIndex++;
            shelterTask = null;
            return null;
        }
        return shelterTask;
    }

    private List<BlockPos> createShelter(BlockPos center) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int y = 0; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (Math.abs(x) == 1 || Math.abs(z) == 1) {
                        blocks.add(center.add(x, y, z));
                    }
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                blocks.add(center.add(x, 2, z));
            }
        }
        return blocks;
    }

    private Task brewWeakness(AltoClef mod) {
        if (!hasPotion(mod, Potions.WATER, false)) {
            if (!mod.getItemStorage().hasItem(Items.GLASS_BOTTLE)) {
                return getItemTask(Items.GLASS_BOTTLE);
            }
            Optional<BlockPos> water = mod.getBlockScanner().getNearestBlock(Blocks.WATER);
            if (water.isEmpty()) return wanderTask;
            if (fillBottleTask == null) {
                fillBottleTask = new InteractWithBlockTask(new ItemTarget(Items.GLASS_BOTTLE, 1), water.get());
            }
            return fillBottleTask;
        }

        if (!mod.getBlockScanner().anyFound(Blocks.BREWING_STAND)) {
            if (!mod.getItemStorage().hasItem(Items.BREWING_STAND)) {
                return getItemTask(Items.BREWING_STAND);
            }
            return new PlaceBlockNearbyTask(Blocks.BREWING_STAND);
        }
        if (!(MinecraftClient.getInstance().player.currentScreenHandler
                instanceof BrewingStandScreenHandler handler)) {
            BlockPos stand = mod.getBlockScanner().getNearestBlock(Blocks.BREWING_STAND)
                    .orElseThrow(() -> new IllegalStateException("Brewing stand disappeared from the block scanner."));
            if (openStandTask == null) openStandTask = new InteractWithBlockTask(stand);
            return openStandTask;
        }

        ItemStack potion = StorageHelper.getItemStackInSlot(BrewingStandSlot.LEFT_POTION);
        ItemStack ingredient = StorageHelper.getItemStackInSlot(BrewingStandSlot.INGREDIENT);
        if (potionMatches(potion, Potions.WEAKNESS, true)) {
            mod.getSlotHandler().clickSlot(BrewingStandSlot.LEFT_POTION, 0, SlotActionType.QUICK_MOVE);
            StorageHelper.closeScreen();
            return null;
        }
        if (handler.getFuel() <= 0) {
            if (!mod.getItemStorage().hasItem(Items.BLAZE_POWDER)) return getItemTask(Items.BLAZE_POWDER);
            return new MoveItemToSlotFromInventoryTask(new ItemTarget(Items.BLAZE_POWDER, 1), BrewingStandSlot.FUEL);
        }
        if (potion.isEmpty()) {
            if (!putPotionInStand(mod, Potions.WATER, false)) return null;
            return null;
        }
        if (potionMatches(potion, Potions.WATER, false)) {
            if (ingredient.isEmpty()) {
                if (!mod.getItemStorage().hasItem(Items.FERMENTED_SPIDER_EYE)) {
                    return getItemTask(Items.FERMENTED_SPIDER_EYE);
                }
                return moveItemToSlot(Items.FERMENTED_SPIDER_EYE, BrewingStandSlot.INGREDIENT);
            }
            return null;
        }
        if (potionMatches(potion, Potions.WEAKNESS, false)) {
            if (handler.getBrewTime() > 0) return null;
            if (ingredient.isEmpty()) {
                if (!mod.getItemStorage().hasItem(Items.GUNPOWDER)) return getItemTask(Items.GUNPOWDER);
                return moveItemToSlot(Items.GUNPOWDER, BrewingStandSlot.INGREDIENT);
            }
        }
        if (handler.getBrewTime() > 0) return null;
        if (ingredient.isEmpty() && !potion.isEmpty()) {
            mod.getSlotHandler().clickSlot(BrewingStandSlot.LEFT_POTION, 0, SlotActionType.QUICK_MOVE);
        }
        return null;
    }

    private boolean putPotionInStand(AltoClef mod, RegistryEntry<Potion> potion, boolean splash) {
        Optional<Slot> slot = findPotionSlot(mod, potion, splash);
        if (slot.isEmpty()) return false;
        mod.getSlotHandler().clickSlot(slot.get(), 0, SlotActionType.PICKUP);
        mod.getSlotHandler().clickSlot(BrewingStandSlot.LEFT_POTION, 0, SlotActionType.PICKUP);
        return true;
    }

    private Task moveItemToSlot(Item item, Slot slot) {
        return new MoveItemToSlotFromInventoryTask(new ItemTarget(item, 1), slot);
    }

    private boolean hasPotion(AltoClef mod, RegistryEntry<Potion> potion, boolean splash) {
        return findPotionSlot(mod, potion, splash).isPresent();
    }

    private Optional<Slot> findPotionSlot(AltoClef mod, RegistryEntry<Potion> potion, boolean splash) {
        Item item = splash ? Items.SPLASH_POTION : Items.POTION;
        return mod.getItemStorage().getSlotsWithItemPlayerInventory(false, item).stream()
                .filter(slot -> potionMatches(StorageHelper.getItemStackInSlot(slot), potion, splash))
                .findFirst();
    }

    private boolean potionMatches(ItemStack stack, RegistryEntry<Potion> potion, boolean splash) {
        if (stack.isEmpty() || stack.getItem() != (splash ? Items.SPLASH_POTION : Items.POTION)) return false;
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        return contents != null && contents.matches(potion);
    }

    private Task getItemTask(Item item) {
        return getItemTask(item, 1);
    }

    private Task getItemTask(Item item, int count) {
        if (!TaskCatalogue.taskExists(item)) {
            AltoClef.getInstance().logWarning("Cannot complete Zombie Doctor: no resource task is available for "
                    + item.getName().getString() + ".");
            return wanderTask;
        }
        return TaskCatalogue.getItemTask(item, count);
    }

    private boolean isSheltered(AltoClef mod, BlockPos pos) {
        return !mod.getWorld().isSkyVisible(pos.up());
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    protected void onStop(Task interruptTask) {
        StorageHelper.closeScreen();
        AltoClef.getInstance().getInputControls().release(Input.CLICK_RIGHT);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ZombieDoctorTask;
    }

    @Override
    protected String toDebugString() {
        return "Curing a zombie villager";
    }
}
