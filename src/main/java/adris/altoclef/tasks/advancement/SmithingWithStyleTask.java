package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.BrushableBlockEntityAccessor;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.container.LootContainerTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.slot.MoveItemToSlotFromInventoryTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.SmithingTableSlot;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BrushableBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SmithingWithStyleTask extends Task {
    private static final List<TrimTemplate> REQUIRED_TEMPLATES = List.of(
            new TrimTemplate("spire", Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplate("snout", Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplate("rib", Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplate("ward", Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplate("silence", Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplate("vex", Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplate("tide", Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplate("wayfinder", Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE)
    );
    private static final List<Item> ARMOR_OPTIONS = List.of(
            Items.IRON_CHESTPLATE, Items.IRON_HELMET, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.GOLDEN_CHESTPLATE, Items.GOLDEN_HELMET, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
            Items.LEATHER_CHESTPLATE, Items.LEATHER_HELMET, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS
    );
    private static final Item MATERIAL = Items.IRON_INGOT;
    private static final Set<String> TRAIL_RUINS_LOOT_TABLES = Set.of(
            "archaeology/trail_ruins_rare",
            "archaeology/trail_ruins_common"
    );

    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final Task searchSuspiciousGravelTask = new SearchChunkForBlockTask(Blocks.SUSPICIOUS_GRAVEL);
    private final TimerGame brushTimer = new TimerGame(20);
    private final TimerGame advancementUpdateTimer = new TimerGame(2);
    private final Set<BlockPos> checkedChests = new HashSet<>();
    private final Set<BlockPos> brushedBlocks = new HashSet<>();
    private Task chestLootTask;
    private Task openTableTask;
    private Task guardianKillTask;
    private BlockPos brushTarget;
    private TrimTemplate activeTemplate;
    private ElderGuardianEntity elderGuardian;
    private boolean brushing;
    private boolean waitingForProgress;
    private boolean finished;
    private boolean successful;
    private Item armor;

    @Override
    protected void onStart() {
        checkedChests.clear();
        brushedBlocks.clear();
        chestLootTask = null;
        openTableTask = null;
        guardianKillTask = null;
        brushTarget = null;
        activeTemplate = null;
        elderGuardian = null;
        brushing = false;
        waitingForProgress = false;
        finished = false;
        successful = false;
        armor = null;
        releaseBrush();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        var progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Smithing with Style advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            closeSmithingScreen();
            releaseBrush();
            return null;
        }

        if (waitingForProgress) {
            if (!advancementUpdateTimer.elapsed()) {
                setDebugState("Waiting for the armor trim to register");
                return null;
            }
            waitingForProgress = false;
            activeTemplate = null;
        }

        if (activeTemplate == null || isCriterionMissing(progress, activeTemplate)) {
            activeTemplate = nextMissingTemplate(progress);
        }
        if (activeTemplate == null) {
            setDebugState("Waiting for the eight Smithing with Style criteria to update");
            return null;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player.currentScreenHandler instanceof SmithingScreenHandler handler) {
            if (armor == null) {
                closeSmithingScreen();
                openTableTask = null;
                return null;
            }
            return smithArmor(mod, handler, progress);
        }

        if (chestLootTask != null) {
            if (!chestLootTask.isFinished()) {
                setDebugState("Searching unopened chests for " + activeTemplate.name + " armor trim");
                return chestLootTask;
            }
            checkedChests.add(((LootContainerTask) chestLootTask).chest);
            chestLootTask = null;
        }

        if (mod.getItemStorage().hasItem(activeTemplate.item)) {
            releaseBrush();
            brushTarget = null;
            armor = findAvailableArmor(mod);
            if (armor == null) {
                return fail(mod, "no untrimmed armor item can be obtained");
            }
            if (!hasUntrimmedArmor(mod, armor)) {
                if (!TaskCatalogue.taskExists(armor)) {
                    return fail(mod, "no resource task is available for an untrimmed armor piece");
                }
                setDebugState("Obtaining untrimmed armor for " + activeTemplate.name);
                return TaskCatalogue.getItemTask(armor, 1);
            }
            if (!mod.getItemStorage().hasItem(MATERIAL)) {
                if (!TaskCatalogue.taskExists(MATERIAL)) {
                    return fail(mod, "no iron ingot resource task is available");
                }
                setDebugState("Obtaining iron for the armor trim material");
                return TaskCatalogue.getItemTask(MATERIAL, 1);
            }
            if (!mod.getItemStorage().hasItem(Items.SMITHING_TABLE)) {
                if (!TaskCatalogue.taskExists(Items.SMITHING_TABLE)) {
                    return fail(mod, "no smithing table resource task is available");
                }
                setDebugState("Obtaining a smithing table");
                return TaskCatalogue.getItemTask(Items.SMITHING_TABLE, 1);
            }
            BlockPos table = mod.getBlockScanner().getNearestBlock(Blocks.SMITHING_TABLE).orElse(null);
            if (table == null) {
                setDebugState("Placing a smithing table");
                return new PlaceBlockNearbyTask(Blocks.SMITHING_TABLE);
            }
            if (openTableTask == null) openTableTask = new InteractWithBlockTask(table);
            setDebugState("Opening the smithing table for " + activeTemplate.name);
            return openTableTask;
        }

        releaseBrush();
        closeSmithingScreen();
        if (activeTemplate.name.equals("tide")) {
            if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
                setDebugState("Returning to the Overworld to hunt an elder guardian");
                return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
            }
            return acquireTideTemplate(mod);
        }
        if (activeTemplate.name.equals("wayfinder")) {
            if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
                setDebugState("Returning to the Overworld to search trail ruins");
                return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
            }
            return acquireWayfinderTemplate(mod);
        }
        Dimension templateDimension = dimensionFor(activeTemplate);
        if (WorldHelper.getCurrentDimension() != templateDimension) {
            setDebugState("Traveling to the " + templateDimension + " to search for "
                    + activeTemplate.name + " armor trim");
            return new DefaultGoToDimensionTask(templateDimension);
        }
        return acquireChestTemplate(mod);
    }

    private Task smithArmor(AltoClef mod, SmithingScreenHandler handler,
                            net.minecraft.advancement.AdvancementProgress progress) {
        if (!isCriterionMissing(progress, activeTemplate)) {
            closeSmithingScreen();
            activeTemplate = null;
            return null;
        }

        ItemStack output = handler.getSlot(SmithingTableSlot.OUTPUT_SLOT.getWindowSlot()).getStack();
        if (!output.isEmpty() && output.contains(DataComponentTypes.TRIM)) {
            mod.getSlotHandler().clickSlot(
                    SmithingTableSlot.OUTPUT_SLOT, 0, SlotActionType.QUICK_MOVE);
            waitingForProgress = true;
            advancementUpdateTimer.reset();
            setDebugState("Applying the " + activeTemplate.name + " armor trim");
            return null;
        }

        ItemStack templateStack = StorageHelper.getItemStackInSlot(SmithingTableSlot.INPUT_SLOT_TEMPLATE);
        if (templateStack.isEmpty() || !templateStack.isOf(activeTemplate.item)) {
            return new MoveItemToSlotFromInventoryTask(
                    new ItemTarget(activeTemplate.item, 1), SmithingTableSlot.INPUT_SLOT_TEMPLATE);
        }
        ItemStack armorStack = StorageHelper.getItemStackInSlot(SmithingTableSlot.INPUT_SLOT_TOOL);
        if (armorStack.isEmpty() || !armorStack.isOf(armor)
                || armorStack.contains(DataComponentTypes.TRIM)) {
            return new MoveItemToSlotFromInventoryTask(
                    new ItemTarget(armor, 1), SmithingTableSlot.INPUT_SLOT_TOOL);
        }
        ItemStack materialStack = StorageHelper.getItemStackInSlot(SmithingTableSlot.INPUT_SLOT_MATERIALS);
        if (materialStack.isEmpty() || !materialStack.isOf(MATERIAL)) {
            return new MoveItemToSlotFromInventoryTask(
                    new ItemTarget(MATERIAL, 1), SmithingTableSlot.INPUT_SLOT_MATERIALS);
        }
        setDebugState("Waiting for the " + activeTemplate.name + " trimmed armor output");
        return null;
    }

    private Task acquireChestTemplate(AltoClef mod) {
        BlockPos chest = mod.getBlockScanner().getNearestBlock(
                pos -> !checkedChests.contains(pos) && WorldHelper.isUnopenedChest(pos),
                Blocks.CHEST, Blocks.TRAPPED_CHEST).orElse(null);
        if (chest != null) {
            chestLootTask = new LootContainerTask(chest, List.of(activeTemplate.item));
            setDebugState("Looting unopened chests for " + activeTemplate.name + " armor trim");
            return chestLootTask;
        }
        setDebugState("Exploring for a chest containing the " + activeTemplate.name + " armor trim");
        return exploreTask;
    }

    private Task acquireTideTemplate(AltoClef mod) {
        if (mod.getEntityTracker().itemDropped(Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE)) {
            setDebugState("Collecting a Tide armor trim dropped by an elder guardian");
            return new adris.altoclef.tasks.movement.PickupDroppedItemTask(
                    Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, 1, true);
        }
        elderGuardian = mod.getEntityTracker().getTrackedEntities(ElderGuardianEntity.class).stream()
                .filter(Entity::isAlive)
                .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
        if (elderGuardian != null) {
            if (guardianKillTask == null || guardianKillTask.isFinished()) {
                guardianKillTask = new KillEntityTask(elderGuardian);
            }
            setDebugState("Defeating an elder guardian for the Tide armor trim");
            return guardianKillTask;
        }
        guardianKillTask = null;
        setDebugState("Searching for an ocean monument and elder guardian");
        return exploreTask;
    }

    private Task acquireWayfinderTemplate(AltoClef mod) {
        if (!mod.getItemStorage().hasItem(Items.BRUSH)) {
            if (!TaskCatalogue.taskExists(Items.BRUSH)) {
                return fail(mod, "no brush resource task is available for the Wayfinder template");
            }
            setDebugState("Obtaining a brush for the Wayfinder armor trim");
            return TaskCatalogue.getItemTask(Items.BRUSH, 1);
        }
        if (brushTarget == null || !isTrailRuinsGravel(mod, brushTarget)) {
            brushTarget = findTrailRuinsGravel(mod);
            if (brushTarget == null) {
                setDebugState("Searching trail ruins for suspicious gravel containing Wayfinder");
                return searchSuspiciousGravelTask;
            }
        }
        if (mod.getPlayer().squaredDistanceTo(brushTarget.toCenterPos()) > 4.5 * 4.5) {
            setDebugState("Approaching suspicious gravel in trail ruins");
            return new GetToBlockTask(brushTarget);
        }
        if (!mod.getSlotHandler().forceEquipItem(Items.BRUSH)) return null;
        var rotation = LookHelper.getLookRotation(mod, brushTarget.toCenterPos());
        LookHelper.lookAt(mod, brushTarget.toCenterPos(), false);
        if (!LookHelper.isLookingAt(mod, rotation)
                || !(MinecraftClient.getInstance().crosshairTarget instanceof BlockHitResult hit)
                || !hit.getBlockPos().equals(brushTarget)) {
            setDebugState("Aiming the brush at suspicious gravel in trail ruins");
            return null;
        }
        if (!brushing) {
            brushing = true;
            brushTimer.reset();
        }
        mod.getInputControls().hold(Input.CLICK_RIGHT);
        if (!isTrailRuinsGravel(mod, brushTarget)) {
            brushedBlocks.add(brushTarget);
            brushTarget = null;
            releaseBrush();
            return null;
        }
        if (brushTimer.elapsed()) {
            brushedBlocks.add(brushTarget);
            brushTarget = null;
            releaseBrush();
            setDebugState("Searching for another trail ruins gravel block");
            return null;
        }
        setDebugState("Brushing suspicious gravel for Wayfinder");
        return null;
    }

    private BlockPos findTrailRuinsGravel(AltoClef mod) {
        return mod.getBlockScanner().getKnownLocations(Blocks.SUSPICIOUS_GRAVEL).stream()
                .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                .filter(pos -> !brushedBlocks.contains(pos))
                .filter(pos -> isTrailRuinsGravel(mod, pos))
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())))
                .orElse(null);
    }

    private boolean isTrailRuinsGravel(AltoClef mod, BlockPos pos) {
        if (!mod.getWorld().getBlockState(pos).isOf(Blocks.SUSPICIOUS_GRAVEL)
                || !(mod.getWorld().getBlockEntity(pos) instanceof BrushableBlockEntity blockEntity)) {
            return false;
        }
        RegistryKey<LootTable> lootTable =
                ((BrushableBlockEntityAccessor) blockEntity).altoclef$getLootTable();
        return lootTable != null
                && TRAIL_RUINS_LOOT_TABLES.contains(lootTable.getValue().getPath());
    }

    private TrimTemplate nextMissingTemplate(net.minecraft.advancement.AdvancementProgress progress) {
        for (TrimTemplate template : REQUIRED_TEMPLATES) {
            if (isCriterionMissing(progress, template)) return template;
        }
        return null;
    }

    private boolean isCriterionMissing(net.minecraft.advancement.AdvancementProgress progress,
                                       TrimTemplate template) {
        String criterion = criterionFor(template);
        for (String missing : progress.getUnobtainedCriteria()) {
            if (missing.equals(criterion)) return true;
        }
        return false;
    }

    private Item findAvailableArmor(AltoClef mod) {
        for (Item candidate : ARMOR_OPTIONS) {
            if (hasUntrimmedArmor(mod, candidate)) return candidate;
        }
        for (Item candidate : ARMOR_OPTIONS) {
            if (!mod.getItemStorage().hasItem(candidate) && TaskCatalogue.taskExists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean hasUntrimmedArmor(AltoClef mod, Item item) {
        return mod.getItemStorage().getSlotsWithItemPlayerInventory(false, item).stream()
                .map(StorageHelper::getItemStackInSlot)
                .anyMatch(stack -> !stack.isEmpty() && !stack.contains(DataComponentTypes.TRIM));
    }

    private String criterionFor(TrimTemplate template) {
        return "armor_trimmed_minecraft:" + template.name
                + "_armor_trim_smithing_template_smithing_trim";
    }

    private Dimension dimensionFor(TrimTemplate template) {
        return switch (template.name) {
            case "spire" -> Dimension.END;
            case "snout", "rib" -> Dimension.NETHER;
            default -> Dimension.OVERWORLD;
        };
    }

    private net.minecraft.advancement.AdvancementProgress getAdvancementProgress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return null;
        var manager = client.getNetworkHandler().getAdvancementHandler();
        var entry = manager.getManager().get(
                net.minecraft.util.Identifier.of("minecraft",
                        "adventure/trim_with_all_exclusive_armor_patterns"));
        if (entry == null) return null;
        return ((ClientAdvancementManagerAccessor) manager)
                .altoclef$getAdvancementProgresses().get(entry);
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Smithing with Style: " + reason + ".");
        finished = true;
        releaseBrush();
        closeSmithingScreen();
        return null;
    }

    private void releaseBrush() {
        AltoClef mod = AltoClef.getInstance();
        mod.getInputControls().release(Input.CLICK_RIGHT);
        if (mod.getPlayer() != null) mod.getPlayer().stopUsingItem();
        brushing = false;
    }

    private void closeSmithingScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.currentScreenHandler instanceof SmithingScreenHandler) {
            StorageHelper.closeScreen();
        }
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
        releaseBrush();
        closeSmithingScreen();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SmithingWithStyleTask;
    }

    @Override
    protected String toDebugString() {
        return "Applying the eight exclusive armor trim patterns";
    }

    private record TrimTemplate(String name, Item item) {
    }
}
