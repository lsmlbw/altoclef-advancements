package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.multiversion.versionedfields.Blocks;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;

public final class MinecraftTrialsEditionTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/minecraft_trials_edition");

    private final Task searchTask = new SearchChunkForBlockTask(Blocks.TRIAL_SPAWNER, Blocks.VAULT);
    private final TimerGame progressTimer = new TimerGame(2);
    private BlockPos target;
    private Task approachTask;
    private boolean waitingForProgress;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        target = null;
        approachTask = null;
        waitingForProgress = false;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Minecraft: Trial(s) Edition advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            target = null;
            approachTask = null;
            waitingForProgress = false;
            setDebugState("Returning to the Overworld to find a trial chamber");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (waitingForProgress) {
            if (!progressTimer.elapsed()) {
                setDebugState("Waiting for the trial chamber visit to register");
                return null;
            }
            waitingForProgress = false;
            target = null;
            approachTask = null;
        }

        if (target == null || !isTrialChamberMarker(mod.getWorld().getBlockState(target).getBlock())) {
            target = mod.getBlockScanner().getKnownLocations(Blocks.TRIAL_SPAWNER, Blocks.VAULT).stream()
                    .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                    .filter(pos -> isTrialChamberMarker(mod.getWorld().getBlockState(pos).getBlock()))
                    .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())))
                    .orElse(null);
            approachTask = null;
        }

        if (target == null) {
            setDebugState("Searching explored chunks for a trial spawner or vault");
            return searchTask;
        }

        if (mod.getPlayer().getBlockPos().isWithinDistance(target, 4)) {
            waitingForProgress = true;
            progressTimer.reset();
            setDebugState("Inside the trial chamber; waiting for advancement confirmation");
            return null;
        }

        if (approachTask == null) approachTask = new GetToBlockTask(target.up());
        if (approachTask.isFinished()) {
            waitingForProgress = true;
            progressTimer.reset();
            setDebugState("Reached the trial chamber; waiting for advancement confirmation");
            return null;
        }
        setDebugState("Entering the trial chamber");
        return approachTask;
    }

    private boolean isTrialChamberMarker(net.minecraft.block.Block block) {
        return block == Blocks.TRIAL_SPAWNER || block == Blocks.VAULT;
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
        return other instanceof MinecraftTrialsEditionTask;
    }

    @Override
    protected String toDebugString() {
        return "Entering a trial chamber for Minecraft: Trial(s) Edition";
    }
}
