package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Optional;

public final class PowerOfBooksTask extends Task {
    private Task shelfTask;
    private Task comparatorTask;
    private BlockPos bookshelfPos;
    private BlockPos comparatorPos;
    private Direction comparatorFacing;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        shelfTask = null;
        comparatorTask = null;
        bookshelfPos = null;
        comparatorPos = null;
        comparatorFacing = null;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (comparatorPos != null && mod.getWorld().getBlockState(comparatorPos).isOf(Blocks.COMPARATOR)
                && mod.getWorld().getBlockState(comparatorPos).get(ComparatorBlock.FACING) == comparatorFacing) {
            successful = true;
            finished = true;
            return null;
        }

        if (bookshelfPos == null || !mod.getWorld().getBlockState(bookshelfPos).isOf(Blocks.CHISELED_BOOKSHELF)) {
            bookshelfPos = mod.getBlockScanner().getNearestBlock(Blocks.CHISELED_BOOKSHELF).orElse(null);
            if (bookshelfPos == null) {
                Task craftShelf = getItemTask(Items.CHISELED_BOOKSHELF);
                if (craftShelf != null) {
                    setDebugState("Crafting a chiseled bookshelf");
                    return craftShelf;
                }
                if (shelfTask == null || shelfTask.isFinished()) {
                    shelfTask = new PlaceBlockNearbyTask(Blocks.CHISELED_BOOKSHELF);
                }
                setDebugState("Placing a chiseled bookshelf");
                return shelfTask;
            }
        }

        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos adjacent = bookshelfPos.offset(side);
            if (mod.getWorld().getBlockState(adjacent).isOf(Blocks.COMPARATOR)
                    && mod.getWorld().getBlockState(adjacent).get(ComparatorBlock.FACING) == side) {
                successful = true;
                finished = true;
                return null;
            }
        }

        if (comparatorPos != null && !mod.getWorld().getBlockState(comparatorPos).isOf(Blocks.COMPARATOR)) {
            comparatorPos = null;
            comparatorTask = null;
            comparatorFacing = null;
        }

        if (comparatorPos == null) {
            Optional<Direction> side = findComparatorSide(mod, bookshelfPos);
            if (side.isEmpty()) {
                setDebugState("Finding a clear side of the chiseled bookshelf");
                return null;
            }
            comparatorFacing = side.get();
            comparatorPos = bookshelfPos.offset(comparatorFacing);
            if (!mod.getItemStorage().hasItem(Items.COMPARATOR)) {
                Task craftComparator = getItemTask(Items.COMPARATOR);
                if (craftComparator != null) {
                    setDebugState("Crafting a redstone comparator");
                    return craftComparator;
                }
                return null;
            }
            comparatorTask = new InteractWithBlockTask(
                    Items.COMPARATOR, comparatorFacing, bookshelfPos, false);
            setDebugState("Placing a comparator facing away from the bookshelf");
            return comparatorTask;
        }

        setDebugState("Verifying the bookshelf comparator signal arrangement");
        return null;
    }

    private Optional<Direction> findComparatorSide(AltoClef mod, BlockPos bookshelf) {
        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos comparator = bookshelf.offset(side);
            BlockPos support = comparator.down();
            if (mod.getWorld().getBlockState(comparator).isAir()
                    && WorldHelper.isSolidBlock(support)
                    && mod.getWorld().getBlockState(bookshelf).isOf(Blocks.CHISELED_BOOKSHELF)) {
                return Optional.of(side);
            }
        }
        return Optional.empty();
    }

    private Task getItemTask(Item item) {
        if (!TaskCatalogue.taskExists(item)) {
            AltoClef.getInstance().logWarning("Cannot complete The Power of Books: no resource task is available for "
                    + item.getName().getString() + ".");
            return null;
        }
        return TaskCatalogue.getItemTask(item, 1);
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
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof PowerOfBooksTask;
    }

    @Override
    protected String toDebugString() {
        return "Reading a chiseled bookshelf with a comparator";
    }
}
