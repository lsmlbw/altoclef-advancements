package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.container.LootContainerTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.slot.MoveItemToSlotFromInventoryTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.SmithingTableSlot;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CraftingANewLookTask extends Task {
    private static final Item[] TRIM_TEMPLATES = {
            Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE
    };
    private static final List<Item> TEMPLATE_LIST = List.of(TRIM_TEMPLATES);
    private static final Item[] ARMOR_OPTIONS = {
            Items.IRON_CHESTPLATE, Items.IRON_HELMET, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.GOLDEN_CHESTPLATE, Items.GOLDEN_HELMET, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
            Items.LEATHER_CHESTPLATE, Items.LEATHER_HELMET, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS
    };

    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final Set<BlockPos> checkedChests = new HashSet<>();
    private Task chestLootTask;
    private Task openTableTask;
    private Item template;
    private Item armor;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        checkedChests.clear();
        chestLootTask = null;
        openTableTask = null;
        template = null;
        armor = null;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (mod.getPlayer().currentScreenHandler instanceof SmithingScreenHandler handler) {
            return smithArmor(mod, handler);
        }

        if (chestLootTask != null) {
            if (!chestLootTask.isFinished()) {
                setDebugState("Searching chests for an armor trim smithing template");
                return chestLootTask;
            }
            checkedChests.add(((LootContainerTask) chestLootTask).chest);
            chestLootTask = null;
        }

        if (template == null || !mod.getItemStorage().hasItem(template)) {
            template = findOwnedTemplate(mod);
            if (template == null) {
                BlockPos chest = mod.getBlockScanner().getNearestBlock(
                        pos -> !checkedChests.contains(pos) && WorldHelper.isUnopenedChest(pos),
                        Blocks.CHEST).orElse(null);
                if (chest != null) {
                    chestLootTask = new LootContainerTask(chest, TEMPLATE_LIST);
                    setDebugState("Searching chests for an armor trim smithing template");
                    return chestLootTask;
                }
                setDebugState("Exploring for chests containing an armor trim template");
                return exploreTask;
            }
        }

        if (armor == null || !mod.getItemStorage().hasItem(armor)) {
            armor = findOwnedArmor(mod);
            if (armor == null) {
                armor = findCraftableArmor();
                if (armor == null) {
                    mod.logWarning("Cannot complete Crafting a New Look: no armor recipe is available.");
                    finished = true;
                    return null;
                }
                setDebugState("Obtaining armor to trim");
                return TaskCatalogue.getItemTask(armor, 1);
            }
        }

        if (!mod.getItemStorage().hasItem(Items.IRON_INGOT)) {
            if (!TaskCatalogue.taskExists(Items.IRON_INGOT)) {
                mod.logWarning("Cannot complete Crafting a New Look: no iron ingot resource task is available.");
                finished = true;
                return null;
            }
            setDebugState("Obtaining iron as the armor trim material");
            return TaskCatalogue.getItemTask(Items.IRON_INGOT, 1);
        }

        if (!mod.getItemStorage().hasItem(Items.SMITHING_TABLE)) {
            if (!TaskCatalogue.taskExists(Items.SMITHING_TABLE)) {
                mod.logWarning("Cannot complete Crafting a New Look: no smithing table resource task is available.");
                finished = true;
                return null;
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
        setDebugState("Opening the smithing table");
        return openTableTask;
    }

    private Task smithArmor(AltoClef mod, SmithingScreenHandler handler) {
        ItemStack output = handler.getSlot(SmithingTableSlot.OUTPUT_SLOT.getWindowSlot()).getStack();
        if (!output.isEmpty()) {
            if (output.contains(DataComponentTypes.TRIM)) {
                mod.getSlotHandler().clickSlot(SmithingTableSlot.OUTPUT_SLOT, 0, SlotActionType.QUICK_MOVE);
                successful = true;
                finished = true;
                return null;
            }
        }

        ItemStack templateStack = StorageHelper.getItemStackInSlot(SmithingTableSlot.INPUT_SLOT_TEMPLATE);
        if (templateStack.isEmpty() || !templateStack.isOf(template)) {
            return new MoveItemToSlotFromInventoryTask(
                    new ItemTarget(template, 1), SmithingTableSlot.INPUT_SLOT_TEMPLATE);
        }
        ItemStack armorStack = StorageHelper.getItemStackInSlot(SmithingTableSlot.INPUT_SLOT_TOOL);
        if (armorStack.isEmpty() || !armorStack.isOf(armor)) {
            return new MoveItemToSlotFromInventoryTask(
                    new ItemTarget(armor, 1), SmithingTableSlot.INPUT_SLOT_TOOL);
        }
        ItemStack materialStack = StorageHelper.getItemStackInSlot(SmithingTableSlot.INPUT_SLOT_MATERIALS);
        if (materialStack.isEmpty() || !materialStack.isOf(Items.IRON_INGOT)) {
            return new MoveItemToSlotFromInventoryTask(
                    new ItemTarget(Items.IRON_INGOT, 1), SmithingTableSlot.INPUT_SLOT_MATERIALS);
        }
        setDebugState("Waiting for the trimmed armor result");
        return null;
    }

    private Item findOwnedTemplate(AltoClef mod) {
        for (Item candidate : TRIM_TEMPLATES) {
            if (mod.getItemStorage().hasItem(candidate)) return candidate;
        }
        return null;
    }

    private Item findOwnedArmor(AltoClef mod) {
        for (Item candidate : ARMOR_OPTIONS) {
            if (mod.getItemStorage().hasItem(candidate)
                    && !StorageHelper.isArmorEquipped(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Item findCraftableArmor() {
        for (Item candidate : ARMOR_OPTIONS) {
            if (TaskCatalogue.taskExists(candidate)) return candidate;
        }
        return null;
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
        if (MinecraftClient.getInstance().player != null
                && MinecraftClient.getInstance().player.currentScreenHandler instanceof SmithingScreenHandler) {
            StorageHelper.closeScreen();
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CraftingANewLookTask;
    }

    @Override
    protected String toDebugString() {
        return "Trimming armor at a smithing table";
    }
}
