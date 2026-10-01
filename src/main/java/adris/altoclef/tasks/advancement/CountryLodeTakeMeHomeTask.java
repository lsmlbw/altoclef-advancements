package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.multiversion.versionedfields.Blocks;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;

public final class CountryLodeTakeMeHomeTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/at_lodestone");

    private final Task searchLodestoneTask = new SearchChunkForBlockTask(Blocks.LODESTONE);
    private PlaceBlockNearbyTask placeLodestoneTask;
    private InteractWithBlockTask useCompassTask;
    private BlockPos target;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        placeLodestoneTask = new PlaceBlockNearbyTask(Blocks.LODESTONE);
        useCompassTask = null;
        target = null;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Country Lode, Take Me Home advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            return null;
        }

        if (!mod.getItemStorage().hasItem(Items.COMPASS)) {
            if (!TaskCatalogue.taskExists(Items.COMPASS)) {
                return fail(mod, "the compass resource task is unavailable");
            }
            setDebugState("Obtaining a compass for Country Lode, Take Me Home");
            return TaskCatalogue.getItemTask(Items.COMPASS, 1);
        }

        if (target == null || !mod.getWorld().getBlockState(target).isOf(Blocks.LODESTONE)) {
            target = mod.getBlockScanner().getKnownLocations(Blocks.LODESTONE).stream()
                    .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                    .filter(pos -> mod.getWorld().getBlockState(pos).isOf(Blocks.LODESTONE))
                    .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())))
                    .orElse(null);
            useCompassTask = null;
        }

        if (target == null) {
            if (mod.getItemStorage().hasItem(Items.LODESTONE)) {
                if (!placeLodestoneTask.isFinished()) {
                    setDebugState("Placing a lodestone for Country Lode, Take Me Home");
                    return placeLodestoneTask;
                }
                target = placeLodestoneTask.getPlaced();
                if (target == null || !mod.getWorld().getBlockState(target).isOf(Blocks.LODESTONE)) {
                    placeLodestoneTask = new PlaceBlockNearbyTask(Blocks.LODESTONE);
                    return null;
                }
            } else if (TaskCatalogue.taskExists(Items.LODESTONE)) {
                setDebugState("Obtaining a lodestone for Country Lode, Take Me Home");
                return TaskCatalogue.getItemTask(Items.LODESTONE, 1);
            } else {
                setDebugState("Searching explored chunks for a lodestone");
                return searchLodestoneTask;
            }
        }

        if (useCompassTask == null) {
            useCompassTask = new InteractWithBlockTask(Items.COMPASS, target);
        }
        setDebugState("Using a compass on the lodestone");
        return useCompassTask;
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Country Lode, Take Me Home: " + reason + ".");
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
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CountryLodeTakeMeHomeTask;
    }

    @Override
    protected String toDebugString() {
        return "Using a compass on a lodestone for Country Lode, Take Me Home";
    }
}
