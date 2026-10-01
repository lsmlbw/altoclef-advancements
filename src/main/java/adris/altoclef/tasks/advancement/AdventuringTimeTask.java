package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.tasks.movement.SearchWithinBiomeTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;

public final class AdventuringTimeTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/adventuring_time");
    private final TimerGame progressTimer = new TimerGame(2);
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private RegistryKey<Biome> targetBiome;
    private SearchWithinBiomeTask searchTask;
    private boolean waitingForProgress;
    private boolean failureReported;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        targetBiome = null;
        searchTask = null;
        waitingForProgress = false;
        failureReported = false;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Adventuring Time advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            targetBiome = null;
            searchTask = null;
            waitingForProgress = false;
            setDebugState("Returning to the Overworld to discover its biomes");
            return new adris.altoclef.tasks.movement.DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (waitingForProgress) {
            if (!progressTimer.elapsed()) {
                setDebugState("Waiting for the biome visit to register with the server");
                return null;
            }
            waitingForProgress = false;
            if (!isCriterionMissing(progress, targetBiome)) {
                targetBiome = null;
                searchTask = null;
            } else {
                searchTask = null;
            }
        }

        if (targetBiome != null && !isCriterionMissing(progress, targetBiome)) {
            targetBiome = null;
            searchTask = null;
        }

        if (targetBiome == null) {
            List<RegistryKey<Biome>> missingBiomes = getMissingBiomes(progress);
            if (finished) return null;
            if (missingBiomes.isEmpty()) {
                setDebugState("Waiting for the Adventuring Time progress update");
                return null;
            }
            targetBiome = missingBiomes.get(0);
        }

        if (mod.getWorld().getBiome(mod.getPlayer().getBlockPos()).matchesKey(targetBiome)) {
            waitingForProgress = true;
            progressTimer.reset();
            setDebugState("Waiting for " + targetBiome.getValue().getPath()
                    + " to register as discovered");
            return null;
        }

        if (searchTask == null || searchTask.isFinished()) {
            searchTask = new SearchWithinBiomeTask(targetBiome);
        }
        int missingCount = 0;
        for (String ignored : progress.getUnobtainedCriteria()) missingCount++;
        setDebugState("Searching for Overworld biome " + targetBiome.getValue().getPath()
                + " (" + missingCount + " biomes remaining)");
        return searchTask;
    }

    private List<RegistryKey<Biome>> getMissingBiomes(AdvancementProgress progress) {
        List<RegistryKey<Biome>> missing = new ArrayList<>();
        for (String criterion : progress.getUnobtainedCriteria()) {
            Identifier id = Identifier.tryParse(criterion);
            if (id == null) {
                reportInvalidCriterion(criterion);
                continue;
            }
            RegistryKey<Biome> key = RegistryKey.of(RegistryKeys.BIOME, id);
            if (MinecraftClient.getInstance().world.getRegistryManager()
                    .get(RegistryKeys.BIOME).containsId(id)) {
                missing.add(key);
            } else {
                reportInvalidCriterion(criterion);
            }
        }
        return missing;
    }

    private boolean isCriterionMissing(AdvancementProgress progress, RegistryKey<Biome> biome) {
        if (biome == null) return false;
        for (String criterion : progress.getUnobtainedCriteria()) {
            if (criterion.equals(biome.getValue().toString())) return true;
        }
        return false;
    }

    private void reportInvalidCriterion(String criterion) {
        if (failureReported) return;
        AltoClef.getInstance().logWarning(
                "Cannot complete Adventuring Time: unrecognized Overworld biome criterion '"
                        + criterion + "'.");
        failureReported = true;
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
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof AdventuringTimeTask;
    }

    @Override
    protected String toDebugString() {
        return "Visiting every Overworld biome for Adventuring Time";
    }
}
