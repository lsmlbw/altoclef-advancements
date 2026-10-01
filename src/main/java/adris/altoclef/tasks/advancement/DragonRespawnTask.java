package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class DragonRespawnTask extends Task {
    private static final int REQUIRED_CRYSTALS = 4;
    private final TimerGame spawnTimer = new TimerGame(120);
    private final TimerGame locateTimer = new TimerGame(60);
    private final TimerGame placementTimer = new TimerGame(30);
    private final DefaultGoToDimensionTask returnToEndTask = new DefaultGoToDimensionTask(Dimension.END);
    private List<BlockPos> crystalBases;
    private int nextCrystal;
    private int crystalCountBeforePlacement;
    private Task placementTask;
    private PickupDroppedItemTask eggPickupTask;
    private boolean waitingForDragon;
    private boolean warnedMissingEgg;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        crystalBases = null;
        nextCrystal = 0;
        crystalCountBeforePlacement = 0;
        placementTask = null;
        eggPickupTask = null;
        waitingForDragon = false;
        warnedMissingEgg = false;
        finished = false;
        successful = false;
        locateTimer.reset();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;
        if (!mod.getItemStorage().hasItem(Items.DRAGON_EGG)) {
            if (mod.getEntityTracker().itemDropped(Items.DRAGON_EGG)) {
                if (eggPickupTask == null) eggPickupTask = new PickupDroppedItemTask(Items.DRAGON_EGG, 1, true);
                setDebugState("Collecting the dragon egg before respawning the dragon");
                return eggPickupTask;
            }
            if (!warnedMissingEgg) {
                mod.logWarning("Skipping The End... Again... to protect the dragon egg: collect the egg into your inventory before respawning the dragon.");
                warnedMissingEgg = true;
            }
            finished = true;
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.END) {
            setDebugState("Returning to the End after securing the dragon egg");
            return returnToEndTask;
        }

        if (mod.getEntityTracker().getTrackedEntities(EnderDragonEntity.class).stream()
                .anyMatch(EnderDragonEntity::isAlive)) {
            successful = true;
            finished = true;
            return null;
        }

        if (waitingForDragon) {
            if (spawnTimer.elapsed()) {
                mod.logWarning("The End... Again... respawn sequence did not produce a detectable Ender Dragon.");
                finished = true;
            } else {
                setDebugState("Waiting for the respawned Ender Dragon");
            }
            return null;
        }

        if (crystalBases == null) {
            crystalBases = findCrystalBases(mod);
            if (crystalBases == null) {
                if (locateTimer.elapsed()) {
                    mod.logWarning("Could not identify the four bedrock crystal positions around the End exit portal.");
                    finished = true;
                    return null;
                }
                setDebugState("Locating the exit portal bedrock ring");
                return null;
            }
        }

        if (placementTask != null) {
            if (mod.getItemStorage().getItemCount(Items.END_CRYSTAL) < crystalCountBeforePlacement) {
                nextCrystal++;
                placementTask = null;
            } else if (placementTimer.elapsed()) {
                mod.logWarning("Could not place an End Crystal on the exit portal bedrock.");
                finished = true;
                return null;
            } else {
                setDebugState("Placing end crystal " + (nextCrystal + 1) + " of " + REQUIRED_CRYSTALS);
                return placementTask;
            }
        }

        if (nextCrystal >= REQUIRED_CRYSTALS) {
            waitingForDragon = true;
            spawnTimer.reset();
            setDebugState("Waiting for the Ender Dragon respawn sequence");
            return null;
        }

        if (!mod.getItemStorage().hasItem(Items.END_CRYSTAL)) {
            if (!TaskCatalogue.taskExists(Items.END_CRYSTAL)) {
                mod.logWarning("Cannot respawn the Ender Dragon: no End Crystal crafting task is available.");
                finished = true;
                return null;
            }
            setDebugState("Crafting End Crystals");
            return TaskCatalogue.getItemTask(Items.END_CRYSTAL,
                    REQUIRED_CRYSTALS - nextCrystal);
        }

        BlockPos base = crystalBases.get(nextCrystal);
        if (!isValidCrystalBase(mod, base)) {
            crystalBases = findCrystalBases(mod);
            if (crystalBases == null) {
                setDebugState("Waiting for the exit portal bedrock positions to load");
                return null;
            }
            base = crystalBases.get(nextCrystal);
        }
        crystalCountBeforePlacement = mod.getItemStorage().getItemCount(Items.END_CRYSTAL);
        placementTask = new InteractWithBlockTask(Items.END_CRYSTAL, base);
        placementTimer.reset();
        setDebugState("Placing end crystal " + (nextCrystal + 1) + " of " + REQUIRED_CRYSTALS);
        return placementTask;
    }

    private List<BlockPos> findCrystalBases(AltoClef mod) {
        List<BlockPos> portalBlocks = mod.getBlockScanner().getKnownLocations(Blocks.END_PORTAL).stream()
                .filter(pos -> mod.getWorld().getBlockState(pos).isOf(Blocks.END_PORTAL))
                .toList();
        if (portalBlocks.isEmpty()) return null;

        double averageX = portalBlocks.stream().mapToInt(BlockPos::getX).average().orElseThrow();
        double averageZ = portalBlocks.stream().mapToInt(BlockPos::getZ).average().orElseThrow();
        int centerX = (int) Math.round(averageX);
        int centerZ = (int) Math.round(averageZ);
        int portalY = portalBlocks.getFirst().getY();
        for (int radius : new int[]{3, 2, 4}) {
            for (int y = portalY - 1; y <= portalY + 2; y++) {
                List<BlockPos> candidates = List.of(
                        new BlockPos(centerX + radius, y, centerZ),
                        new BlockPos(centerX - radius, y, centerZ),
                        new BlockPos(centerX, y, centerZ + radius),
                        new BlockPos(centerX, y, centerZ - radius));
                if (candidates.stream().allMatch(pos -> isValidCrystalBase(mod, pos))) {
                    return new ArrayList<>(candidates);
                }
            }
        }

        return null;
    }

    private boolean isValidCrystalBase(AltoClef mod, BlockPos pos) {
        return mod.getChunkTracker().isChunkLoaded(pos)
                && mod.getWorld().getBlockState(pos).isOf(Blocks.BEDROCK)
                && mod.getWorld().getBlockState(pos.up()).isAir()
                && mod.getWorld().getBlockState(pos.up(2)).isAir()
                && mod.getEntityTracker().getTrackedEntities(EndCrystalEntity.class).stream()
                .noneMatch(crystal -> crystal.getBlockPos().equals(pos.up()));
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
        return other instanceof DragonRespawnTask;
    }

    @Override
    protected String toDebugString() {
        return "Respawning the Ender Dragon";
    }
}
