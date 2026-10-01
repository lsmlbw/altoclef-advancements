package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.multiversion.versionedfields.Blocks;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.multiversion.versionedfields.VersionedFieldHelper;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;

public final class UnderLockAndKeyTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/under_lock_and_key");
    private static final double SPAWNER_MOB_RADIUS = 12;

    private final Task searchTask = new SearchChunkForBlockTask(Blocks.TRIAL_SPAWNER, Blocks.VAULT);
    private final TimeoutWanderTask waitForSpawnerTask = new TimeoutWanderTask(2, true);
    private final TimerGame spawnerWaitTimer = new TimerGame(5);
    private BlockPos spawnerPos;
    private BlockPos vaultPos;
    private Task movementTask;
    private Task killTask;
    private Task pickupKeyTask;
    private InteractWithBlockTask unlockVaultTask;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        spawnerPos = null;
        vaultPos = null;
        movementTask = null;
        killTask = null;
        pickupKeyTask = null;
        unlockVaultTask = null;
        finished = false;
        successful = false;
        spawnerWaitTimer.reset();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (!VersionedFieldHelper.isSupported(Items.TRIAL_KEY)
                || !VersionedFieldHelper.isSupported(Blocks.VAULT)
                || !VersionedFieldHelper.isSupported(Blocks.TRIAL_SPAWNER)) {
            return fail(mod, "trial keys and vaults are not available in this Minecraft version");
        }

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Under Lock and Key advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            setDebugState("Returning to the Overworld to unlock a vault");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        boolean hasTrialKey = mod.getItemStorage().hasItem(Items.TRIAL_KEY);
        if (hasTrialKey) {
            if (vaultPos == null || !isNormalVault(mod, vaultPos)) {
                vaultPos = findNormalVault(mod);
                unlockVaultTask = null;
                movementTask = null;
            }
            if (vaultPos == null) {
                setDebugState("Searching trial chambers for a normal vault");
                return searchTask;
            }
            if (mod.getPlayer().getBlockPos().getSquaredDistance(vaultPos) > 16) {
                if (movementTask == null) movementTask = new GetToBlockTask(vaultPos);
                setDebugState("Approaching a normal vault");
                return movementTask;
            }
            if (unlockVaultTask == null) {
                unlockVaultTask = new InteractWithBlockTask(Items.TRIAL_KEY, vaultPos);
            }
            setDebugState("Using the trial key on a normal vault");
            return unlockVaultTask;
        }

        if (mod.getEntityTracker().itemDropped(Items.TRIAL_KEY)) {
            if (pickupKeyTask == null) pickupKeyTask = new PickupDroppedItemTask(Items.TRIAL_KEY, 1, true);
            setDebugState("Collecting the trial key ejected by a trial spawner");
            return pickupKeyTask;
        }

        BlockPos nextSpawner = findSpawner(mod);
        if (nextSpawner == null || !nextSpawner.equals(spawnerPos)) {
            movementTask = null;
            killTask = null;
        }
        spawnerPos = nextSpawner;
        if (spawnerPos == null) {
            setDebugState("Searching trial chambers for a trial spawner");
            return searchTask;
        }

        MobEntity targetMob = mod.getEntityTracker().getTrackedEntities(MobEntity.class).stream()
                .filter(HostileEntity.class::isInstance)
                .filter(MobEntity::isAlive)
                .filter(entity -> entity.squaredDistanceTo(spawnerPos.getX(), spawnerPos.getY(), spawnerPos.getZ())
                        <= SPAWNER_MOB_RADIUS * SPAWNER_MOB_RADIUS)
                .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
        if (targetMob != null) {
            if (!(killTask instanceof KillEntityTask current) || current.isFinished()) {
                killTask = new KillEntityTask(targetMob);
            }
            setDebugState("Defeating trial-spawned mobs to earn a trial key");
            return killTask;
        }
        killTask = null;

        if (mod.getPlayer().getBlockPos().getSquaredDistance(spawnerPos) > 16) {
            if (movementTask == null) movementTask = new GetToBlockTask(spawnerPos.up());
            setDebugState("Approaching a trial spawner to activate it");
            return movementTask;
        }

        if (spawnerWaitTimer.elapsed()) {
            spawnerWaitTimer.reset();
            movementTask = null;
        }
        setDebugState("Waiting for the trial spawner to release another wave of mobs");
        return waitForSpawnerTask;
    }

    private BlockPos findNormalVault(AltoClef mod) {
        return mod.getBlockScanner().getKnownLocations(Blocks.VAULT).stream()
                .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                .filter(pos -> isNormalVault(mod, pos))
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())))
                .orElse(null);
    }

    private boolean isNormalVault(AltoClef mod, BlockPos pos) {
        var state = mod.getWorld().getBlockState(pos);
        if (!state.isOf(Blocks.VAULT)) return false;
        return state.getEntries().entrySet().stream()
                .filter(entry -> entry.getKey().getName().equals("ominous"))
                .map(entry -> entry.getValue())
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .findFirst()
                .map(ominous -> !ominous)
                .orElse(false);
    }

    private BlockPos findSpawner(AltoClef mod) {
        return mod.getBlockScanner().getKnownLocations(Blocks.TRIAL_SPAWNER).stream()
                .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                .filter(pos -> mod.getWorld().getBlockState(pos).isOf(Blocks.TRIAL_SPAWNER))
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())))
                .orElse(null);
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Under Lock and Key: " + reason + ".");
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
        return other instanceof UnderLockAndKeyTask;
    }

    @Override
    protected String toDebugString() {
        return "Using a trial key on a normal vault for Under Lock and Key";
    }
}
