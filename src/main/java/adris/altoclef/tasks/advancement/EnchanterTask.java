package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.slot.MoveItemToSlotFromInventoryTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.EnchantingTableSlot;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public final class EnchanterTask extends Task {
    private Task openTableTask;
    private Task itemSlotTask;
    private Task lapisSlotTask;
    private Task xpTask;
    private boolean enchantClicked;
    private boolean finished;

    @Override
    protected void onStart() {
        openTableTask = null;
        itemSlotTask = null;
        lapisSlotTask = null;
        xpTask = new KillEntitiesTask(Monster.class);
        enchantClicked = false;
        finished = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) {
            return null;
        }
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (!mod.getItemStorage().hasItem(Items.ENCHANTING_TABLE)) {
            return getItemTask(Items.ENCHANTING_TABLE);
        }
        if (!mod.getItemStorage().hasItem(Items.BOOK)) {
            return getItemTask(Items.BOOK);
        }
        if (!mod.getItemStorage().hasItem(Items.LAPIS_LAZULI)) {
            return getItemTask(Items.LAPIS_LAZULI);
        }

        if (MinecraftClient.getInstance().player.currentScreenHandler
                instanceof EnchantmentScreenHandler handler) {
            ItemStack item = StorageHelper.getItemStackInSlot(EnchantingTableSlot.ITEM);
            if (enchantClicked) {
                if (!item.isEmpty() && (item.isOf(Items.ENCHANTED_BOOK)
                        || item.get(DataComponentTypes.ENCHANTMENTS) != null
                        && !item.get(DataComponentTypes.ENCHANTMENTS).isEmpty())) {
                    mod.getSlotHandler().clickSlot(EnchantingTableSlot.ITEM, 0, SlotActionType.QUICK_MOVE);
                    finished = true;
                }
                return null;
            }

            if (item.isEmpty()) {
                itemSlotTask = new MoveItemToSlotFromInventoryTask(
                        new ItemTarget(Items.BOOK, 1), EnchantingTableSlot.ITEM);
                return itemSlotTask;
            }
            if (StorageHelper.getItemStackInSlot(EnchantingTableSlot.LAPIS).isEmpty()) {
                lapisSlotTask = new MoveItemToSlotFromInventoryTask(
                        new ItemTarget(Items.LAPIS_LAZULI, 1), EnchantingTableSlot.LAPIS);
                return lapisSlotTask;
            }

            int option = findAvailableOption(mod, handler);
            if (option < 0) {
                if (mod.getPlayer().experienceLevel < 1) {
                    setDebugState("Gaining experience for enchanting");
                    return xpTask;
                }
                return null;
            }
            MinecraftClient.getInstance().interactionManager.clickButton(handler.syncId, option);
            enchantClicked = true;
            setDebugState("Enchanting a book");
            return null;
        }

        Optional<BlockPos> table = mod.getBlockScanner().getNearestBlock(Blocks.ENCHANTING_TABLE);
        if (table.isEmpty()) {
            setDebugState("Placing an enchanting table");
            return new PlaceBlockNearbyTask(Blocks.ENCHANTING_TABLE);
        }
        if (openTableTask == null) {
            openTableTask = new InteractWithBlockTask(table.get());
        }
        setDebugState("Opening the enchanting table");
        return openTableTask;
    }

    private int findAvailableOption(AltoClef mod, EnchantmentScreenHandler handler) {
        if (mod.getPlayer().experienceLevel < 1 || handler.getLapisCount() < 1) {
            return -1;
        }
        for (int option = 0; option < handler.enchantmentPower.length; option++) {
            int levelCost = option + 1;
            if (handler.enchantmentPower[option] > 0
                    && mod.getPlayer().experienceLevel >= levelCost
                    && handler.getLapisCount() >= levelCost) {
                return option;
            }
        }
        return -1;
    }

    private Task getItemTask(net.minecraft.item.Item item) {
        if (!TaskCatalogue.taskExists(item)) {
            AltoClef.getInstance().logWarning("Cannot complete Enchanter: no resource task is available for "
                    + item.getName().getString() + ".");
            return new TimeoutWanderTask(true);
        }
        return TaskCatalogue.getItemTask(item, 1);
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    protected void onStop(Task interruptTask) {
        StorageHelper.closeScreen();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EnchanterTask;
    }

    @Override
    protected String toDebugString() {
        return "Enchanting an item";
    }

}
