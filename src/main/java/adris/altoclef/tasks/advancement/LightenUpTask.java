package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.multiversion.versionedfields.Blocks;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.multiversion.versionedfields.VersionedFieldHelper;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.CursorSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import baritone.api.utils.input.Input;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.state.property.Properties;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

public final class LightenUpTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/lighten_up");
    private static final Item[] AXES = {
            Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.IRON_AXE,
            Items.STONE_AXE, Items.GOLDEN_AXE, Items.WOODEN_AXE
    };
    private static final net.minecraft.block.Block[] BULBS = {
            Blocks.COPPER_BULB, Blocks.EXPOSED_COPPER_BULB, Blocks.WEATHERED_COPPER_BULB,
            Blocks.OXIDIZED_COPPER_BULB, Blocks.WAXED_COPPER_BULB,
            Blocks.WAXED_EXPOSED_COPPER_BULB, Blocks.WAXED_WEATHERED_COPPER_BULB,
            Blocks.WAXED_OXIDIZED_COPPER_BULB
    };
    private static final net.minecraft.block.Block[] AGED_BULBS = {
            Blocks.EXPOSED_COPPER_BULB, Blocks.WEATHERED_COPPER_BULB, Blocks.OXIDIZED_COPPER_BULB,
            Blocks.WAXED_EXPOSED_COPPER_BULB, Blocks.WAXED_WEATHERED_COPPER_BULB,
            Blocks.WAXED_OXIDIZED_COPPER_BULB
    };

    private final Task searchAgedBulbsTask = new SearchChunkForBlockTask(AGED_BULBS);
    private PlaceBlockNearbyTask placeBulbTask;
    private PlaceBlockTask placeButtonTask;
    private InteractWithBlockTask powerBulbTask;
    private InteractWithBlockTask scrapeBulbTask;
    private BlockPos bulbPos;
    private BlockPos placedBulbPos;
    private BlockPos buttonPos;
    private Item axe;
    private Slot shieldStorageSlot;
    private boolean stowingShield;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        placeBulbTask = null;
        placeButtonTask = null;
        powerBulbTask = null;
        scrapeBulbTask = null;
        bulbPos = null;
        placedBulbPos = null;
        buttonPos = null;
        axe = null;
        shieldStorageSlot = null;
        stowingShield = false;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (!VersionedFieldHelper.isSupported(Items.COPPER_BULB)) {
            return fail(mod, "copper bulbs are not available in this Minecraft version");
        }

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Lighten Up advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            setDebugState("Returning to the Overworld for Lighten Up");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (axe == null || !mod.getItemStorage().hasItem(axe)) {
            axe = findOwnedAxe(mod);
            if (axe == null) {
                axe = findObtainableAxe();
                if (axe == null) return fail(mod, "no axe resource task is available");
                setDebugState("Obtaining an axe to scrape the copper bulb");
                return TaskCatalogue.getItemTask(axe, 1);
            }
        }

        if (bulbPos == null || !isAgedBulb(mod, bulbPos)) {
            bulbPos = findAgedBulb(mod);
            if (bulbPos != null) {
                buttonPos = null;
                placeButtonTask = null;
                powerBulbTask = null;
                scrapeBulbTask = null;
            }
        }

        if (bulbPos == null) {
            if (placedBulbPos == null || !isBulb(mod, placedBulbPos)) {
                if (!mod.getItemStorage().hasItem(Items.COPPER_BULB)) {
                    if (!TaskCatalogue.taskExists(Items.COPPER_BULB)) {
                        return fail(mod, "the copper bulb recipe is unavailable");
                    }
                    setDebugState("Crafting a copper bulb to oxidize for Lighten Up");
                    return TaskCatalogue.getItemTask(Items.COPPER_BULB, 1);
                }
                if (placeBulbTask == null) {
                    placeBulbTask = new PlaceBlockNearbyTask(Blocks.COPPER_BULB);
                }
                if (!placeBulbTask.isFinished()) {
                    setDebugState("Placing a copper bulb where it can oxidize");
                    return placeBulbTask;
                }
                placedBulbPos = placeBulbTask.getPlaced();
                placeBulbTask = null;
                if (placedBulbPos == null || !isBulb(mod, placedBulbPos)) {
                    return fail(mod, "the placed copper bulb could not be located");
                }
            }

            if (!isLit(mod, placedBulbPos)) {
                if (buttonPos == null || !mod.getWorld().getBlockState(buttonPos).isOf(Blocks.STONE_BUTTON)) {
                    buttonPos = findButtonPosition(mod, placedBulbPos);
                    if (buttonPos == null) return fail(mod, "no position is available to attach a button");
                    if (!mod.getItemStorage().hasItem(Items.STONE_BUTTON)) {
                        if (!TaskCatalogue.taskExists(Items.STONE_BUTTON)) {
                            return fail(mod, "the stone button resource task is unavailable");
                        }
                        setDebugState("Obtaining a button to power the copper bulb");
                        return TaskCatalogue.getItemTask(Items.STONE_BUTTON, 1);
                    }
                    if (placeButtonTask == null) {
                        placeButtonTask = new PlaceBlockTask(buttonPos, Blocks.STONE_BUTTON);
                    }
                    if (!mod.getWorld().getBlockState(buttonPos).isOf(Blocks.STONE_BUTTON)) {
                        setDebugState("Placing a button to light the copper bulb");
                        return placeButtonTask;
                    }
                }
                if (powerBulbTask == null) powerBulbTask = new InteractWithBlockTask(buttonPos);
                setDebugState("Powering the copper bulb");
                return powerBulbTask;
            }

            setDebugState("Waiting for the copper bulb to oxidize; searching for naturally aged bulbs");
            return searchAgedBulbsTask;
        }

        if (!isLit(mod, bulbPos)) {
            if (buttonPos == null || !mod.getWorld().getBlockState(buttonPos).isOf(Blocks.STONE_BUTTON)) {
                buttonPos = findButtonPosition(mod, bulbPos);
                if (buttonPos == null) return fail(mod, "no position is available to attach a button");
                if (!mod.getItemStorage().hasItem(Items.STONE_BUTTON)) {
                    if (!TaskCatalogue.taskExists(Items.STONE_BUTTON)) {
                        return fail(mod, "the stone button resource task is unavailable");
                    }
                    setDebugState("Obtaining a button to light the aged copper bulb");
                    return TaskCatalogue.getItemTask(Items.STONE_BUTTON, 1);
                }
                if (placeButtonTask == null) {
                    placeButtonTask = new PlaceBlockTask(buttonPos, Blocks.STONE_BUTTON);
                }
                if (!mod.getWorld().getBlockState(buttonPos).isOf(Blocks.STONE_BUTTON)) {
                    setDebugState("Placing a button beside the aged copper bulb");
                    return placeButtonTask;
                }
            }
            if (powerBulbTask == null) powerBulbTask = new InteractWithBlockTask(buttonPos);
            setDebugState("Lighting the aged copper bulb");
            return powerBulbTask;
        }

        Task stowShieldTask = stowOffhandShield(mod);
        if (stowShieldTask != null) return stowShieldTask;
        if (finished) return null;

        if (!mod.getSlotHandler().forceEquipItem(axe)) {
            setDebugState("Equipping an axe to scrape the copper bulb");
            return null;
        }
        if (scrapeBulbTask == null || scrapeBulbTask.isFinished()) {
            scrapeBulbTask = new InteractWithBlockTask(
                    new ItemTarget(axe, 1), null, bulbPos, Input.CLICK_RIGHT, false, true);
        }
        setDebugState("Sneaking and scraping the lit, oxidized copper bulb");
        return scrapeBulbTask;
    }

    private Task stowOffhandShield(AltoClef mod) {
        ItemStack cursor = StorageHelper.getItemStackInSlot(CursorSlot.SLOT);
        if (stowingShield) {
            if (cursor.isOf(Items.SHIELD)) {
                if (shieldStorageSlot == null) {
                    Optional<Slot> storageSlot =
                            mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false);
                    if (storageSlot.isEmpty()) {
                        return fail(mod, "there is no inventory space to stow the offhand shield");
                    }
                    shieldStorageSlot = storageSlot.get();
                }
                mod.getSlotHandler().clickSlot(shieldStorageSlot, 0, SlotActionType.PICKUP);
                stowingShield = false;
                shieldStorageSlot = null;
                return null;
            }
            if (!cursor.isEmpty()) {
                Optional<Slot> storageSlot =
                        mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false);
                if (storageSlot.isEmpty()) {
                    return fail(mod, "there is no inventory space to clear the cursor before scraping");
                }
                mod.getSlotHandler().clickSlot(storageSlot.get(), 0, SlotActionType.PICKUP);
                return null;
            }
            if (StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT).isOf(Items.SHIELD)) {
                Optional<Slot> storageSlot = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(
                        StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT), false);
                if (storageSlot.isEmpty()) {
                    return fail(mod, "there is no inventory space to stow the offhand shield");
                }
                shieldStorageSlot = storageSlot.get();
                mod.getSlotHandler().clickSlot(PlayerSlot.OFFHAND_SLOT, 0, SlotActionType.PICKUP);
                return null;
            }
            stowingShield = false;
            shieldStorageSlot = null;
            return null;
        }

        if (StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT).isOf(Items.SHIELD)) {
            stowingShield = true;
            return stowOffhandShield(mod);
        }
        return null;
    }

    private Item findOwnedAxe(AltoClef mod) {
        for (Item candidate : AXES) {
            if (mod.getItemStorage().hasItem(candidate)) return candidate;
        }
        return null;
    }

    private Item findObtainableAxe() {
        for (int i = AXES.length - 1; i >= 0; i--) {
            Item candidate = AXES[i];
            if (TaskCatalogue.taskExists(candidate)) return candidate;
        }
        return null;
    }

    private BlockPos findAgedBulb(AltoClef mod) {
        return mod.getBlockScanner().getKnownLocations(AGED_BULBS).stream()
                .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                .filter(pos -> isAgedBulb(mod, pos))
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())))
                .orElse(null);
    }

    private boolean isAgedBulb(AltoClef mod, BlockPos pos) {
        return mod.getWorld().getBlockState(pos).isOf(Blocks.EXPOSED_COPPER_BULB)
                || mod.getWorld().getBlockState(pos).isOf(Blocks.WEATHERED_COPPER_BULB)
                || mod.getWorld().getBlockState(pos).isOf(Blocks.OXIDIZED_COPPER_BULB)
                || mod.getWorld().getBlockState(pos).isOf(Blocks.WAXED_EXPOSED_COPPER_BULB)
                || mod.getWorld().getBlockState(pos).isOf(Blocks.WAXED_WEATHERED_COPPER_BULB)
                || mod.getWorld().getBlockState(pos).isOf(Blocks.WAXED_OXIDIZED_COPPER_BULB);
    }

    private boolean isBulb(AltoClef mod, BlockPos pos) {
        return Arrays.stream(BULBS)
                .anyMatch(block -> mod.getWorld().getBlockState(pos).isOf(block));
    }

    private boolean isLit(AltoClef mod, BlockPos pos) {
        return isBulb(mod, pos) && mod.getWorld().getBlockState(pos).get(Properties.LIT);
    }

    private BlockPos findButtonPosition(AltoClef mod, BlockPos bulb) {
        for (Direction direction : new Direction[]{
                Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
            BlockPos candidate = bulb.offset(direction);
            if (mod.getWorld().getBlockState(candidate).isReplaceable()) return candidate;
        }
        return null;
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Lighten Up: " + reason + ".");
        finished = true;
        return null;
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

    public boolean wasSuccessful() {
        return successful;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() != null
                && !StorageHelper.getItemStackInSlot(CursorSlot.SLOT).isEmpty()
                && shieldStorageSlot != null) {
            mod.getSlotHandler().clickSlot(shieldStorageSlot, 0, SlotActionType.PICKUP);
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof LightenUpTask;
    }

    @Override
    protected String toDebugString() {
        return "Scraping a lit, oxidized copper bulb for Lighten Up";
    }
}
