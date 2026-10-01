package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.container.LootContainerTask;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.slot.MoveItemToSlotFromInventoryTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.EnchantingTableSlot;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.fluid.Fluids;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VeryVeryFrighteningTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/very_very_frightening");
    private static final int TRIDENT_BATCH_SIZE = 1;
    private static final int LAPIS_PER_ENCHANT = 3;
    private static final double THROW_RANGE = 24;

    private final Task experienceTask = new KillEntitiesTask(Monster.class);
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final TimerGame chargeTimer = new TimerGame(2);
    private final TimerGame hitTimer = new TimerGame(6);
    private final Set<BlockPos> checkedChests = new HashSet<>();
    private Task openTableTask;
    private LootContainerTask chestLootTask;
    private VillagerEntity target;
    private Task approachTask;
    private int tridentBatch = TRIDENT_BATCH_SIZE;
    private int enchantmentsAttempted;
    private boolean waitingForEnchant;
    private boolean charging;
    private boolean waitingForHit;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        checkedChests.clear();
        openTableTask = null;
        chestLootTask = null;
        target = null;
        approachTask = null;
        tridentBatch = TRIDENT_BATCH_SIZE;
        enchantmentsAttempted = 0;
        waitingForEnchant = false;
        charging = false;
        waitingForHit = false;
        finished = false;
        successful = false;
        releaseUse();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Very Very Frightening advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            releaseUse();
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            releaseUse();
            setDebugState("Returning to the Overworld to strike a villager with lightning");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (MinecraftClient.getInstance().player.currentScreenHandler
                instanceof EnchantmentScreenHandler handler) {
            releaseUse();
            return enchantTrident(mod, handler);
        }

        if (waitingForEnchant) {
            waitingForEnchant = false;
            enchantmentsAttempted++;
            if (hasChannelingTrident(mod)) return null;
        }

        if (waitingForHit) {
            if (!hitTimer.elapsed()) {
                setDebugState("Waiting for Channeling lightning to strike the villager");
                return null;
            }
            waitingForHit = false;
            target = null;
            approachTask = null;
        }

        if (!hasChannelingTrident(mod)) {
            releaseUse();
            if (mod.getEntityTracker().itemDropped(Items.TRIDENT)) {
                setDebugState("Recovering a dropped trident");
                return new PickupDroppedItemTask(Items.TRIDENT, 1, true);
            }
            TridentEntity thrownTrident = findThrownTrident(mod);
            if (thrownTrident != null) {
                setDebugState("Recovering the thrown Channeling trident");
                return new GetToEntityTask(thrownTrident, 1.5);
            }
            if (chestLootTask != null) {
                if (!chestLootTask.isFinished()) {
                    setDebugState("Searching chests for a Channeling trident");
                    return chestLootTask;
                }
                checkedChests.add(chestLootTask.chest);
                chestLootTask = null;
            }
            Slot channelingTrident = findChannelingTrident(mod);
            if (channelingTrident != null) {
                return null;
            }
            if (findUnenchantedTrident(mod) == null) {
                BlockPos chest = mod.getBlockScanner().getNearestBlock(
                        pos -> !checkedChests.contains(pos) && WorldHelper.isUnopenedChest(pos),
                        Blocks.CHEST).orElse(null);
                if (chest != null) {
                    chestLootTask = new LootContainerTask(chest, List.of(Items.TRIDENT),
                            this::hasChannelingEnchantment);
                    setDebugState("Searching chests for a Channeling trident");
                    return chestLootTask;
                }
                int tridentCount = mod.getItemStorage().getItemCountInventoryOnly(Items.TRIDENT);
                if (tridentCount == 0) {
                    if (!TaskCatalogue.taskExists(Items.TRIDENT)) {
                        return fail(mod, "no trident resource task is available");
                    }
                    setDebugState("Obtaining a trident before enchanting it with Channeling");
                    return TaskCatalogue.getItemTask(Items.TRIDENT, 1);
                }
                if (tridentCount >= tridentBatch) tridentBatch += TRIDENT_BATCH_SIZE;
                return getResource(mod, Items.TRIDENT, tridentBatch,
                        "Obtaining another trident for Channeling enchantment attempts");
            }
            return prepareEnchanting(mod);
        }

        if (!mod.getWorld().isThundering()) {
            releaseUse();
            target = findValidVillager(mod);
            if (target == null) {
                setDebugState("Searching for a dry villager with direct sky access");
                return exploreTask;
            }
            setDebugState("Waiting for a thunderstorm before throwing the Channeling trident");
            return null;
        }

        if (target == null || !isValidVillager(mod.getWorld(), target)) {
            target = findValidVillager(mod);
            approachTask = null;
        }
        if (target == null) {
            releaseUse();
            setDebugState("Searching for a dry, sky-exposed villager during the thunderstorm");
            return exploreTask;
        }

        if (mod.getPlayer().squaredDistanceTo(target) > THROW_RANGE * THROW_RANGE) {
            releaseUse();
            if (approachTask == null) approachTask = new GetToEntityTask(target, THROW_RANGE - 2);
            setDebugState("Approaching a sky-exposed villager within trident range");
            return approachTask;
        }

        Vec3d aimPoint = target.getBoundingBox().getCenter();
        baritone.api.utils.Rotation rotation = LookHelper.getLookRotation(mod, aimPoint);
        LookHelper.lookAt(mod, aimPoint, false);
        if (!LookHelper.isLookingAt(mod, rotation)
                || !(MinecraftClient.getInstance().crosshairTarget instanceof EntityHitResult hit)
                || hit.getEntity() != target) {
            releaseUse();
            charging = false;
            setDebugState("Aiming the Channeling trident at the villager");
            return null;
        }

        Slot tridentSlot = findChannelingTrident(mod);
        if (tridentSlot == null) {
            releaseUse();
            return null;
        }
        mod.getSlotHandler().forceEquipSlot(tridentSlot);
        if (!mod.getPlayer().getMainHandStack().isOf(Items.TRIDENT)
                || !hasChannelingEnchantment(mod.getPlayer().getMainHandStack())) return null;

        if (!mod.getWorld().isThundering() || !isValidVillager(mod.getWorld(), target)) {
            releaseUse();
            charging = false;
            return null;
        }
        if (!charging) {
            charging = true;
            chargeTimer.reset();
        }
        if (!chargeTimer.elapsed()) {
            mod.getInputControls().hold(Input.CLICK_RIGHT);
            setDebugState("Charging the Channeling trident during the thunderstorm");
            return null;
        }

        releaseUse();
        charging = false;
        waitingForHit = true;
        hitTimer.reset();
        setDebugState("Throwing the Channeling trident at the villager");
        return null;
    }

    private Task enchantTrident(AltoClef mod, EnchantmentScreenHandler handler) {
        ItemStack input = StorageHelper.getItemStackInSlot(EnchantingTableSlot.ITEM);
        if (waitingForEnchant) {
            if (!input.isEmpty() && input.isOf(Items.TRIDENT) && input.hasEnchantments()) {
                mod.getSlotHandler().clickSlot(EnchantingTableSlot.ITEM, 0, SlotActionType.QUICK_MOVE);
                waitingForEnchant = false;
                enchantmentsAttempted++;
                return null;
            }
            if (hasChannelingTrident(mod)) {
                waitingForEnchant = false;
                return null;
            }
            setDebugState("Waiting for the Channeling enchantment attempt to resolve");
            return null;
        }

        if (hasChannelingTrident(mod)) {
            StorageHelper.closeScreen();
            return null;
        }
        if (!input.isEmpty() && input.isOf(Items.TRIDENT) && input.hasEnchantments()) {
            mod.getSlotHandler().clickSlot(EnchantingTableSlot.ITEM, 0, SlotActionType.QUICK_MOVE);
            enchantmentsAttempted++;
            return null;
        }
        if (input.isEmpty()) {
            Slot unenchanted = findUnenchantedTrident(mod);
            if (unenchanted == null) {
                StorageHelper.closeScreen();
                openTableTask = null;
                return prepareEnchanting(mod);
            }
            mod.getSlotHandler().clickSlot(unenchanted, 0, SlotActionType.PICKUP);
            setDebugState("Putting an unenchanted trident in the enchanting table");
            return null;
        }

        ItemStack lapis = StorageHelper.getItemStackInSlot(EnchantingTableSlot.LAPIS);
        if (lapis.isEmpty() || lapis.getCount() < LAPIS_PER_ENCHANT) {
            int lapisNeeded = Math.max(LAPIS_PER_ENCHANT,
                    (tridentBatch - enchantmentsAttempted) * LAPIS_PER_ENCHANT);
            if (mod.getItemStorage().getItemCountInventoryOnly(Items.LAPIS_LAZULI) < LAPIS_PER_ENCHANT) {
                StorageHelper.closeScreen();
                openTableTask = null;
                return getResource(mod, Items.LAPIS_LAZULI, lapisNeeded,
                        "Obtaining lapis lazuli to enchant tridents");
            }
            return new MoveItemToSlotFromInventoryTask(
                    new ItemTarget(Items.LAPIS_LAZULI, LAPIS_PER_ENCHANT), EnchantingTableSlot.LAPIS);
        }

        int option = -1;
        for (int i = 0; i < handler.enchantmentPower.length; i++) {
            int cost = i + 1;
            if (handler.enchantmentPower[i] > 0 && mod.getPlayer().experienceLevel >= cost
                    && handler.getLapisCount() >= cost) {
                option = i;
            }
        }
        if (option < 0) {
            if (mod.getPlayer().experienceLevel < 3) {
                StorageHelper.closeScreen();
                openTableTask = null;
                return experienceTask;
            }
            setDebugState("Waiting for an available trident enchantment offer");
            return null;
        }
        MinecraftClient.getInstance().interactionManager.clickButton(handler.syncId, option);
        waitingForEnchant = true;
        setDebugState("Trying an enchanting-table offer for Channeling");
        return null;
    }

    private Task prepareEnchanting(AltoClef mod) {
        if (findUnenchantedTrident(mod) == null) {
            int tridentCount = mod.getItemStorage().getItemCountInventoryOnly(Items.TRIDENT);
            if (tridentCount >= tridentBatch) tridentBatch += TRIDENT_BATCH_SIZE;
            return getResource(mod, Items.TRIDENT, tridentBatch,
                    "Obtaining more tridents for Channeling enchantment attempts");
        }
        int lapisNeeded = Math.max(LAPIS_PER_ENCHANT,
                (tridentBatch - enchantmentsAttempted) * LAPIS_PER_ENCHANT);
        if (mod.getItemStorage().getItemCountInventoryOnly(Items.LAPIS_LAZULI) < LAPIS_PER_ENCHANT) {
            return getResource(mod, Items.LAPIS_LAZULI, lapisNeeded,
                    "Obtaining lapis lazuli to enchant tridents");
        }
        if (!mod.getItemStorage().hasItem(Items.ENCHANTING_TABLE)) {
            return getResource(mod, Items.ENCHANTING_TABLE, 1, "Obtaining an enchanting table");
        }
        if (mod.getPlayer().experienceLevel < 3) {
            setDebugState("Gaining experience to enchant a trident");
            return experienceTask;
        }
        var table = mod.getBlockScanner().getNearestBlock(Blocks.ENCHANTING_TABLE);
        if (table.isEmpty()) {
            setDebugState("Placing an enchanting table for the Channeling trident");
            return new PlaceBlockNearbyTask(Blocks.ENCHANTING_TABLE);
        }
        if (openTableTask == null) openTableTask = new InteractWithBlockTask(table.get());
        setDebugState("Opening an enchanting table to seek Channeling");
        return openTableTask;
    }

    private VillagerEntity findValidVillager(AltoClef mod) {
        return mod.getEntityTracker().getTrackedEntities(VillagerEntity.class).stream()
                .filter(villager -> isValidVillager(mod.getWorld(), villager))
                .min(Comparator.comparingDouble(villager -> villager.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
    }

    private boolean isValidVillager(World world, VillagerEntity villager) {
        if (!villager.isAlive()) return false;
        BlockPos feet = villager.getBlockPos();
        if (!world.isSkyVisible(feet.up())) return false;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos pos = feet.add(x, 0, z);
                if (world.getBlockState(pos).getFluidState().isOf(Fluids.WATER)
                        || world.getBlockState(pos.up()).getFluidState().isOf(Fluids.WATER)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasChannelingTrident(AltoClef mod) {
        return mod.getItemStorage().getItemStacksPlayerInventory(false).stream()
                .anyMatch(this::hasChannelingEnchantment);
    }

    private boolean hasChannelingEnchantment(ItemStack stack) {
        if (!stack.isOf(Items.TRIDENT) || !stack.hasEnchantments()) return false;
        var channeling = AltoClef.getInstance().getWorld().getRegistryManager()
                .get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.CHANNELING).orElseThrow();
        return EnchantmentHelper.getLevel(channeling, stack) > 0;
    }

    private Slot findChannelingTrident(AltoClef mod) {
        return mod.getItemStorage().getSlotsWithItemPlayerInventory(false, Items.TRIDENT).stream()
                .filter(slot -> hasChannelingEnchantment(StorageHelper.getItemStackInSlot(slot)))
                .findFirst().orElse(null);
    }

    private Slot findUnenchantedTrident(AltoClef mod) {
        return mod.getItemStorage().getSlotsWithItemPlayerInventory(false, Items.TRIDENT).stream()
                .filter(slot -> {
                    ItemStack stack = StorageHelper.getItemStackInSlot(slot);
                    return stack.isOf(Items.TRIDENT) && !stack.hasEnchantments();
                })
                .findFirst().orElse(null);
    }

    private TridentEntity findThrownTrident(AltoClef mod) {
        TridentEntity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Entity entity : mod.getWorld().getEntities()) {
            if (!(entity instanceof TridentEntity trident) || trident.getOwner() != mod.getPlayer()) continue;
            double distance = trident.squaredDistanceTo(mod.getPlayer());
            if (distance < closestDistance) {
                closest = trident;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private Task getResource(AltoClef mod, net.minecraft.item.Item item, int count, String state) {
        if (!TaskCatalogue.taskExists(item)) {
            return fail(mod, "no resource task is available for " + item.getName().getString());
        }
        setDebugState(state);
        return TaskCatalogue.getItemTask(item, count);
    }

    private AdvancementProgress getAdvancementProgress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return null;
        var manager = client.getNetworkHandler().getAdvancementHandler();
        PlacedAdvancement entry = manager.getManager().get(ADVANCEMENT_ID);
        if (entry == null) return null;
        return ((ClientAdvancementManagerAccessor) manager)
                .altoclef$getAdvancementProgresses().get(entry);
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Very Very Frightening: " + reason + ".");
        finished = true;
        releaseUse();
        return null;
    }

    private void releaseUse() {
        AltoClef mod = AltoClef.getInstance();
        mod.getInputControls().release(Input.CLICK_RIGHT);
        if (mod.getPlayer() != null) mod.getPlayer().stopUsingItem();
        charging = false;
    }

    public boolean wasSuccessful() {
        return successful;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    protected void onStop(Task interruptTask) {
        releaseUse();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof VeryVeryFrighteningTask;
    }

    @Override
    protected String toDebugString() {
        return "Striking a villager with Channeling lightning";
    }
}
