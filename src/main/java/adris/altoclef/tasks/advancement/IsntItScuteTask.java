package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.SearchWithinBiomeTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.passive.ArmadilloEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.Comparator;
import java.util.List;

public final class IsntItScuteTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/brush_armadillo");
    private static final List<RegistryKey<Biome>> ARMADILLO_BIOMES = List.of(
            BiomeKeys.SAVANNA,
            BiomeKeys.SAVANNA_PLATEAU,
            BiomeKeys.WINDSWEPT_SAVANNA,
            BiomeKeys.BADLANDS,
            BiomeKeys.WOODED_BADLANDS,
            BiomeKeys.ERODED_BADLANDS
    );

    private final SearchWithinBiomeTask[] biomeSearchTasks = ARMADILLO_BIOMES.stream()
            .map(SearchWithinBiomeTask::new)
            .toArray(SearchWithinBiomeTask[]::new);
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final TimerGame brushTimer = new TimerGame(8);
    private ArmadilloEntity target;
    private Task approachTask;
    private int biomeSearchIndex;
    private boolean brushing;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        target = null;
        approachTask = null;
        biomeSearchIndex = 0;
        brushing = false;
        finished = false;
        successful = false;
        releaseBrush();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Isn't It Scute? advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            releaseBrush();
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            releaseBrush();
            setDebugState("Returning to the Overworld to find an armadillo");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (!mod.getItemStorage().hasItem(Items.BRUSH)) {
            releaseBrush();
            if (!TaskCatalogue.taskExists(Items.BRUSH)) {
                return fail(mod, "the brush resource task is unavailable");
            }
            setDebugState("Obtaining a brush to collect an armadillo scute");
            return TaskCatalogue.getItemTask(Items.BRUSH, 1);
        }

        if (target == null || !target.isAlive()) {
            target = findArmadillo(mod);
            approachTask = null;
            brushing = false;
        }
        if (target == null) {
            releaseBrush();
            RegistryKey<Biome> currentBiome = ARMADILLO_BIOMES.stream()
                    .filter(key -> mod.getWorld().getBiome(mod.getPlayer().getBlockPos()).matchesKey(key))
                    .findFirst().orElse(null);
            if (currentBiome != null) {
                setDebugState("Searching " + currentBiome.getValue().getPath() + " for an armadillo");
                return exploreTask;
            }
            RegistryKey<Biome> targetBiome = ARMADILLO_BIOMES.get(biomeSearchIndex);
            SearchWithinBiomeTask searchTask = biomeSearchTasks[biomeSearchIndex];
            biomeSearchIndex = (biomeSearchIndex + 1) % biomeSearchTasks.length;
            setDebugState("Searching " + targetBiome.getValue().getPath() + " for an armadillo");
            return searchTask;
        }

        if (mod.getPlayer().squaredDistanceTo(target) > 3.5 * 3.5) {
            releaseBrush();
            if (approachTask == null) approachTask = new GetToEntityTask(target, 2.5);
            setDebugState("Approaching an armadillo to brush it");
            return approachTask;
        }

        if (!mod.getSlotHandler().forceEquipItem(Items.BRUSH)) {
            releaseBrush();
            return null;
        }

        var rotation = LookHelper.getLookRotation(mod, target.getBoundingBox().getCenter());
        LookHelper.lookAt(mod, target.getBoundingBox().getCenter(), false);
        if (!LookHelper.isLookingAt(mod, rotation)
                || !(MinecraftClient.getInstance().crosshairTarget instanceof EntityHitResult hit)
                || hit.getEntity() != target) {
            releaseBrush();
            setDebugState("Aiming the brush at the armadillo");
            return null;
        }

        if (!brushing) {
            brushing = true;
            brushTimer.reset();
            mod.getController().interactEntity(mod.getPlayer(), target, Hand.MAIN_HAND);
        }
        mod.getInputControls().hold(Input.CLICK_RIGHT);
        setDebugState("Brushing the armadillo for a scute");
        if (brushTimer.elapsed()) {
            brushing = false;
            releaseBrush();
        }
        return null;
    }

    private ArmadilloEntity findArmadillo(AltoClef mod) {
        return mod.getEntityTracker().getTrackedEntities(ArmadilloEntity.class).stream()
                .filter(ArmadilloEntity::isAlive)
                .min(Comparator.comparingDouble(armadillo -> armadillo.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
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

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Isn't It Scute?: " + reason + ".");
        finished = true;
        releaseBrush();
        return null;
    }

    private void releaseBrush() {
        AltoClef mod = AltoClef.getInstance();
        mod.getInputControls().release(Input.CLICK_RIGHT);
        if (mod.getPlayer() != null) mod.getPlayer().stopUsingItem();
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
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof IsntItScuteTask;
    }

    @Override
    protected String toDebugString() {
        return "Brushing an armadillo to collect an armadillo scute";
    }
}
