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
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.CursorSlot;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.block.ButtonBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.CrafterScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class CraftersCraftingCraftersTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/crafters_crafting_crafters");
    private static final Item[] REQUIRED_ITEMS = {
            Items.IRON_INGOT, Items.CRAFTING_TABLE, Items.REDSTONE, Items.DROPPER, Items.STONE_BUTTON
    };
    private static final int[] REQUIRED_COUNTS = {5, 1, 2, 1, 1};
    private static final Item[] CRAFTER_RECIPE = {
            Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT,
            Items.IRON_INGOT, Items.CRAFTING_TABLE, Items.IRON_INGOT,
            Items.REDSTONE, Items.DROPPER, Items.REDSTONE
    };

    private final TimerGame retryTimer = new TimerGame(3);
    private boolean finished;
    private boolean successful;
    private int requiredItemIndex;
    private int recipeSlot;
    private BlockPos crafterPos;
    private BlockPos buttonPos;
    private Slot cursorSource;
    private PlaceBlockNearbyTask placeCrafterTask;
    private PlaceBlockTask placeButtonTask;
    private InteractWithBlockTask openCrafterTask;
    private InteractWithBlockTask activateButtonTask;
    private boolean waitingForProgress;

    @Override
    protected void onStart() {
        finished = false;
        successful = false;
        requiredItemIndex = 0;
        recipeSlot = 0;
        crafterPos = null;
        buttonPos = null;
        cursorSource = null;
        placeCrafterTask = new PlaceBlockNearbyTask(Blocks.CRAFTER);
        placeButtonTask = null;
        openCrafterTask = null;
        activateButtonTask = null;
        waitingForProgress = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (!VersionedFieldHelper.isSupported(Items.CRAFTER)) {
            fail(mod, "Crafter blocks are not available in this Minecraft version");
            return null;
        }

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Crafters Crafting Crafters progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            return null;
        }

        int crafterCount = mod.getItemStorage().getItemCount(Items.CRAFTER);
        if (crafterPos == null && crafterCount < 2) {
            if (!TaskCatalogue.taskExists(Items.CRAFTER)) {
                fail(mod, "no resource task is available for crafting two crafters");
                return null;
            }
            setDebugState("Crafting two crafters for Crafters Crafting Crafters");
            return TaskCatalogue.getItemTask(Items.CRAFTER, 2);
        }

        if (requiredItemIndex < REQUIRED_ITEMS.length) {
            Item item = REQUIRED_ITEMS[requiredItemIndex];
            int count = mod.getItemStorage().getItemCount(item);
            if (count >= REQUIRED_COUNTS[requiredItemIndex]) {
                requiredItemIndex++;
                return null;
            }
            if (!TaskCatalogue.taskExists(item)) {
                fail(mod, "no resource task is available for " + item.getName().getString());
                return null;
            }
            setDebugState("Collecting " + item.getName().getString()
                    + " for the Crafter recipe");
            return TaskCatalogue.getItemTask(item, REQUIRED_COUNTS[requiredItemIndex]);
        }

        if (crafterPos == null) {
            if (!placeCrafterTask.isFinished()) {
                setDebugState("Placing the first Crafter");
                return placeCrafterTask;
            }
            crafterPos = placeCrafterTask.getPlaced();
            if (crafterPos == null || !mod.getWorld().getBlockState(crafterPos).isOf(Blocks.CRAFTER)) {
                fail(mod, "the placed Crafter could not be located");
                return null;
            }
        }

        if (recipeSlot < CRAFTER_RECIPE.length) {
            if (!(mod.getPlayer().currentScreenHandler instanceof CrafterScreenHandler handler)) {
                if (openCrafterTask == null) openCrafterTask = new InteractWithBlockTask(crafterPos);
                setDebugState("Opening the Crafter to load the second Crafter recipe");
                return openCrafterTask;
            }
            if (!loadCrafterRecipe(mod, handler)) return null;
            if (recipeSlot < CRAFTER_RECIPE.length) return null;
        }

        if (!StorageHelper.getItemStackInSlot(CursorSlot.SLOT).isEmpty()) {
            if (cursorSource != null) {
                mod.getSlotHandler().clickSlot(cursorSource, 0, SlotActionType.PICKUP);
                cursorSource = null;
            }
            return null;
        }

        if (buttonPos == null) {
            StorageHelper.closeScreen();
            buttonPos = crafterPos.up();
            if (placeButtonTask == null) {
                placeButtonTask = new PlaceBlockTask(buttonPos, Blocks.STONE_BUTTON);
            }
            if (!mod.getWorld().getBlockState(buttonPos).isOf(Blocks.STONE_BUTTON)) {
                setDebugState("Placing a button to activate the Crafter");
                return placeButtonTask;
            }
        }

        if (!waitingForProgress) {
            if (mod.getWorld().getBlockState(buttonPos).isOf(Blocks.STONE_BUTTON)
                    && mod.getWorld().getBlockState(buttonPos).get(ButtonBlock.POWERED)) {
                waitingForProgress = true;
                retryTimer.reset();
                setDebugState("Waiting for the Crafter to craft and server to confirm the advancement");
                return null;
            }
            if (activateButtonTask == null) activateButtonTask = new InteractWithBlockTask(buttonPos);
            setDebugState("Pressing the button to craft a Crafter");
            return activateButtonTask;
        }

        if (retryTimer.elapsed()) {
            waitingForProgress = false;
            activateButtonTask = null;
        } else {
            setDebugState("Waiting for the Crafter crafting advancement to register");
        }
        return null;
    }

    private boolean loadCrafterRecipe(AltoClef mod, CrafterScreenHandler handler) {
        while (recipeSlot < CRAFTER_RECIPE.length) {
            Item wanted = CRAFTER_RECIPE[recipeSlot];
            ItemStack input = handler.getSlot(recipeSlot).getStack();
            if (!input.isEmpty()) {
                if (!input.isOf(wanted)) {
                    fail(mod, "the Crafter input grid contains an unexpected item");
                    return false;
                }
                recipeSlot++;
                continue;
            }

            ItemStack cursor = StorageHelper.getItemStackInSlot(CursorSlot.SLOT);
            if (!cursor.isEmpty()) {
                if (cursor.isOf(wanted)) {
                    MinecraftClient.getInstance().interactionManager.clickSlot(
                            handler.syncId, recipeSlot, 1, SlotActionType.PICKUP, mod.getPlayer());
                    setDebugState("Loading the Crafter recipe");
                    return false;
                }
                if (cursorSource == null) {
                    fail(mod, "an unexpected item is held on the inventory cursor");
                    return false;
                }
                mod.getSlotHandler().clickSlot(cursorSource, 0, SlotActionType.PICKUP);
                cursorSource = null;
                return false;
            }

            Slot source = mod.getItemStorage().getSlotsWithItemPlayerInventory(false, wanted).stream()
                    .filter(slot -> !StorageHelper.getItemStackInSlot(slot).isEmpty())
                    .findFirst().orElse(null);
            if (source == null) {
                fail(mod, "a Crafter recipe ingredient disappeared from the inventory");
                return false;
            }
            cursorSource = source;
            mod.getSlotHandler().clickSlot(source, 0, SlotActionType.PICKUP);
            return false;
        }
        if (!StorageHelper.getItemStackInSlot(CursorSlot.SLOT).isEmpty() && cursorSource != null) {
            mod.getSlotHandler().clickSlot(cursorSource, 0, SlotActionType.PICKUP);
            cursorSource = null;
            return false;
        }
        return true;
    }

    private void fail(AltoClef mod, String reason) {
        mod.logWarning("Could not complete Crafters Crafting Crafters: " + reason + ".");
        finished = true;
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
        if (!StorageHelper.getItemStackInSlot(CursorSlot.SLOT).isEmpty() && cursorSource != null) {
            mod.getSlotHandler().clickSlot(cursorSource, 0, SlotActionType.PICKUP);
            cursorSource = null;
        }
        if (mod.getPlayer() != null
                && mod.getPlayer().currentScreenHandler instanceof CrafterScreenHandler) {
            StorageHelper.closeScreen();
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CraftersCraftingCraftersTask;
    }

    @Override
    protected String toDebugString() {
        return "Crafting a Crafter in a Crafter";
    }
}
