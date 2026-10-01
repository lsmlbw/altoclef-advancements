package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.misc.PlaceBedAndSetSpawnTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;

public final class SweetDreamsTask extends Task {
    private final TimeoutWanderTask waitForNightTask = new TimeoutWanderTask(true);
    private PlaceBedAndSetSpawnTask sleepTask;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        sleepTask = null;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            setDebugState("Returning to the Overworld before using a bed");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        BlockPos bed = findBed(mod);
        if (bed == null) {
            if (!mod.getItemStorage().hasItem(ItemHelper.BED)) {
                if (!TaskCatalogue.taskExists("bed")) {
                    mod.logWarning("Cannot complete Sweet Dreams: no bed resource task is available.");
                    finished = true;
                    return null;
                }
                setDebugState("Obtaining a bed");
                return TaskCatalogue.getItemTask("bed", 1);
            }
            setDebugState("Placing a bed");
            return new PlaceBlockNearbyTask(ItemHelper.itemsToBlocks(ItemHelper.BED));
        }

        int time = (int) (mod.getWorld().getTimeOfDay() % 24000);
        if (!mod.getWorld().isThundering() && (time < 12542 || time > 23992)) {
            setDebugState("Waiting until night to sleep safely");
            return waitForNightTask;
        }

        if (sleepTask == null) sleepTask = new PlaceBedAndSetSpawnTask();
        setDebugState("Sleeping in a bed in the Overworld");
        return sleepTask;
    }

    private BlockPos findBed(AltoClef mod) {
        return mod.getBlockScanner().getKnownLocations(Blocks.WHITE_BED, Blocks.ORANGE_BED,
                        Blocks.MAGENTA_BED, Blocks.LIGHT_BLUE_BED, Blocks.YELLOW_BED, Blocks.LIME_BED,
                        Blocks.PINK_BED, Blocks.GRAY_BED, Blocks.LIGHT_GRAY_BED, Blocks.CYAN_BED,
                        Blocks.PURPLE_BED, Blocks.BLUE_BED, Blocks.BROWN_BED, Blocks.GREEN_BED,
                        Blocks.RED_BED, Blocks.BLACK_BED)
                .stream()
                .filter(pos -> pos.isWithinDistance(mod.getPlayer().getPos(), 40))
                .filter(pos -> mod.getWorld().getBlockState(pos).getBlock() instanceof BedBlock)
                .filter(pos -> mod.getWorld().getBlockState(pos.up()).isAir()
                        && mod.getWorld().getBlockState(pos.up(2)).isAir())
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())))
                .orElse(null);
    }

    public boolean wasSuccessful() {
        return successful;
    }

    @Override
    public boolean isFinished() {
        if (!finished && sleepTask != null && sleepTask.isFinished()) {
            successful = true;
            finished = true;
        }
        return finished;
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SweetDreamsTask;
    }

    @Override
    protected String toDebugString() {
        return "Sleeping in an Overworld bed";
    }
}
