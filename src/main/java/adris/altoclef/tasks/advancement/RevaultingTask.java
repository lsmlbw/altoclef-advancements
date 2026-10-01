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
import baritone.api.utils.input.Input;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class RevaultingTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/revaulting");
    private static final double SPAWNER_MOB_RADIUS = 14;
    private final boolean openVaultEvenIfAdvancementComplete;

    private final Task searchTask = new SearchChunkForBlockTask(Blocks.TRIAL_SPAWNER, Blocks.VAULT);
    private final TimeoutWanderTask findCaptainTask = new TimeoutWanderTask(true);
    private final TimerGame spawnerWaitTimer = new TimerGame(5);
    private final Set<BlockPos> attemptedSpawners = new HashSet<>();
    private BlockPos spawnerPos;
    private BlockPos vaultPos;
    private PillagerEntity captain;
    private Task activeTask;
    private Task movementTask;
    private Task pickupTask;
    private InteractWithBlockTask unlockVaultTask;
    private int bottleCountBeforeUse;
    private boolean drinkingBottle;
    private boolean sawSpawnerActivity;
    private int keyCountBeforeUnlock;
    private boolean waitingForKeyConsumption;

    public RevaultingTask() {
        this(false);
    }

    public RevaultingTask(boolean openVaultEvenIfAdvancementComplete) {
        this.openVaultEvenIfAdvancementComplete = openVaultEvenIfAdvancementComplete;
    }
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        spawnerPos = null;
        vaultPos = null;
        captain = null;
        activeTask = null;
        movementTask = null;
        pickupTask = null;
        unlockVaultTask = null;
        bottleCountBeforeUse = 0;
        drinkingBottle = false;
        sawSpawnerActivity = false;
        keyCountBeforeUnlock = 0;
        waitingForKeyConsumption = false;
        attemptedSpawners.clear();
        finished = false;
        successful = false;
        spawnerWaitTimer.reset();
        releaseUse();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (!VersionedFieldHelper.isSupported(Items.OMINOUS_TRIAL_KEY)
                || !VersionedFieldHelper.isSupported(Items.OMINOUS_BOTTLE)
                || !VersionedFieldHelper.isSupported(Blocks.VAULT)
                || !VersionedFieldHelper.isSupported(Blocks.TRIAL_SPAWNER)) {
            return fail(mod, "ominous trial keys and vaults are not available in this Minecraft version");
        }

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Revaulting advancement progress from the server");
            return null;
        }
        if (progress.isDone() && !openVaultEvenIfAdvancementComplete) {
            successful = true;
            finished = true;
            releaseUse();
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            releaseUse();
            setDebugState("Returning to the Overworld to open an ominous vault");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (mod.getItemStorage().hasItem(Items.OMINOUS_TRIAL_KEY)) {
            releaseUse();
            if (vaultPos == null || !isOminousVault(mod, vaultPos)) {
                vaultPos = findOminousVault(mod);
                unlockVaultTask = null;
                movementTask = null;
            }
            if (vaultPos == null) {
                setDebugState("Searching trial chambers for an ominous vault");
                return searchTask;
            }
            if (mod.getPlayer().getBlockPos().getSquaredDistance(vaultPos) > 16) {
                if (movementTask == null) movementTask = new GetToBlockTask(vaultPos);
                setDebugState("Approaching an ominous vault");
                return movementTask;
            }
            if (unlockVaultTask == null) {
                unlockVaultTask = new InteractWithBlockTask(Items.OMINOUS_TRIAL_KEY, vaultPos);
            }
            if (openVaultEvenIfAdvancementComplete) {
                if (waitingForKeyConsumption
                        && mod.getItemStorage().getItemCount(Items.OMINOUS_TRIAL_KEY) < keyCountBeforeUnlock) {
                    successful = true;
                    finished = true;
                    releaseUse();
                    return null;
                }
                if (!waitingForKeyConsumption) {
                    keyCountBeforeUnlock = mod.getItemStorage().getItemCount(Items.OMINOUS_TRIAL_KEY);
                    waitingForKeyConsumption = true;
                }
            }
            setDebugState("Using the Ominous Trial Key on an ominous vault");
            return unlockVaultTask;
        }

        if (openVaultEvenIfAdvancementComplete && waitingForKeyConsumption) {
            successful = true;
            finished = true;
            releaseUse();
            return null;
        }

        if (mod.getEntityTracker().itemDropped(Items.OMINOUS_TRIAL_KEY)) {
            releaseUse();
            if (pickupTask == null) {
                pickupTask = new PickupDroppedItemTask(Items.OMINOUS_TRIAL_KEY, 1, true);
            }
            setDebugState("Collecting an Ominous Trial Key ejected by an ominous trial spawner");
            return pickupTask;
        }

        BlockPos nextSpawner = spawnerPos != null
                && mod.getChunkTracker().isChunkLoaded(spawnerPos)
                && mod.getWorld().getBlockState(spawnerPos).isOf(Blocks.TRIAL_SPAWNER)
                && !attemptedSpawners.contains(spawnerPos)
                ? spawnerPos
                : findSpawner(mod);
        if (nextSpawner == null || !nextSpawner.equals(spawnerPos)) {
            movementTask = null;
            activeTask = null;
            sawSpawnerActivity = false;
        }
        spawnerPos = nextSpawner;
        if (spawnerPos == null) {
            releaseUse();
            setDebugState("Searching trial chambers for a trial spawner");
            return searchTask;
        }

        if (!mod.getPlayer().hasStatusEffect(StatusEffects.BAD_OMEN)
                && !isSpawnerOminous(mod, spawnerPos)) {
            releaseUse();
            if (mod.getItemStorage().hasItem(Items.OMINOUS_BOTTLE)) {
                if (mod.getPlayer().getBlockPos().getSquaredDistance(spawnerPos) > 16) {
                    if (movementTask == null) movementTask = new GetToBlockTask(spawnerPos.up());
                    setDebugState("Approaching a trial spawner before drinking the Ominous Bottle");
                    return movementTask;
                }
                if (!mod.getSlotHandler().forceEquipItem(Items.OMINOUS_BOTTLE)) return null;
                if (!drinkingBottle) {
                    bottleCountBeforeUse = mod.getItemStorage().getItemCount(Items.OMINOUS_BOTTLE);
                    drinkingBottle = true;
                }
                mod.getInputControls().hold(Input.CLICK_RIGHT);
                if (mod.getItemStorage().getItemCount(Items.OMINOUS_BOTTLE) < bottleCountBeforeUse) {
                    releaseUse();
                    drinkingBottle = false;
                    movementTask = null;
                }
                setDebugState("Drinking an Ominous Bottle beside the trial spawner");
                return null;
            }

            if (mod.getEntityTracker().itemDropped(Items.OMINOUS_BOTTLE)) {
                releaseUse();
                if (pickupTask == null) {
                    pickupTask = new PickupDroppedItemTask(Items.OMINOUS_BOTTLE, 1, true);
                }
                setDebugState("Collecting an Ominous Bottle from a patrol captain");
                return pickupTask;
            }

            captain = findCaptain(mod);
            if (captain == null) {
                setDebugState("Searching for a pillager patrol captain carrying an ominous banner");
                return findCaptainTask;
            }
            if (activeTask == null || activeTask.isFinished()) {
                activeTask = new KillEntityTask(captain);
            }
            setDebugState("Defeating a pillager patrol captain for an Ominous Bottle");
            return activeTask;
        }

        if (!isSpawnerOminous(mod, spawnerPos)) {
            if (mod.getPlayer().getBlockPos().getSquaredDistance(spawnerPos) > 16) {
                if (movementTask == null) movementTask = new GetToBlockTask(spawnerPos.up());
                setDebugState("Standing close enough for the trial spawner to become ominous");
                return movementTask;
            }
            setDebugState("Waiting for the trial spawner to become ominous");
            return null;
        }

        Object spawnerState = getPropertyValue(mod.getWorld().getBlockState(spawnerPos), "trial_spawner_state");
        if (spawnerState != null && (spawnerState.toString().equals("active")
                || spawnerState.toString().equals("waiting_for_reward_ejection")
                || spawnerState.toString().equals("ejecting_reward"))) {
            if (!sawSpawnerActivity) spawnerWaitTimer.reset();
            sawSpawnerActivity = true;
        }

        MobEntity targetMob = mod.getEntityTracker().getTrackedEntities(MobEntity.class).stream()
                .filter(HostileEntity.class::isInstance)
                .filter(MobEntity::isAlive)
                .filter(entity -> entity.squaredDistanceTo(
                        spawnerPos.getX(), spawnerPos.getY(), spawnerPos.getZ())
                        <= SPAWNER_MOB_RADIUS * SPAWNER_MOB_RADIUS)
                .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
        if (targetMob != null) {
            if (activeTask == null || activeTask.isFinished()
                    || captain != null && !captain.isAlive()) {
                captain = null;
                activeTask = new KillEntityTask(targetMob);
            }
            setDebugState("Defeating mobs from the ominous trial spawner");
            return activeTask;
        }
        activeTask = null;

        if (mod.getPlayer().getBlockPos().getSquaredDistance(spawnerPos) > 16) {
            if (movementTask == null) movementTask = new GetToBlockTask(spawnerPos.up());
            setDebugState("Approaching the ominous trial spawner");
            return movementTask;
        }

        if (spawnerWaitTimer.elapsed()) {
            if (sawSpawnerActivity && spawnerState != null
                    && spawnerState.toString().equals("cooldown")) {
                attemptedSpawners.add(spawnerPos.toImmutable());
                spawnerPos = null;
                sawSpawnerActivity = false;
                movementTask = null;
                setDebugState("Searching another ominous trial spawner after the reward cooldown");
                return null;
            }
        }
        setDebugState("Waiting for the ominous trial spawner to release another wave");
        return null;
    }

    private PillagerEntity findCaptain(AltoClef mod) {
        return mod.getEntityTracker().getTrackedEntities(PillagerEntity.class).stream()
                .filter(PillagerEntity::isAlive)
                .filter(entity -> net.minecraft.registry.Registries.ITEM.getId(
                        entity.getEquippedStack(EquipmentSlot.HEAD).getItem())
                        .getPath().equals("ominous_banner"))
                .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
    }

    private BlockPos findSpawner(AltoClef mod) {
        return mod.getBlockScanner().getKnownLocations(Blocks.TRIAL_SPAWNER).stream()
                .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                .filter(pos -> mod.getWorld().getBlockState(pos).isOf(Blocks.TRIAL_SPAWNER))
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())))
                .orElse(null);
    }

    private BlockPos findOminousVault(AltoClef mod) {
        return mod.getBlockScanner().getKnownLocations(Blocks.VAULT).stream()
                .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                .filter(pos -> isOminousVault(mod, pos))
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())))
                .orElse(null);
    }

    private boolean isOminousVault(AltoClef mod, BlockPos pos) {
        var state = mod.getWorld().getBlockState(pos);
        return state.isOf(Blocks.VAULT) && getOminousProperty(state);
    }

    private boolean isSpawnerOminous(AltoClef mod, BlockPos pos) {
        var state = mod.getWorld().getBlockState(pos);
        return state.isOf(Blocks.TRIAL_SPAWNER) && getOminousProperty(state);
    }

    private boolean getOminousProperty(net.minecraft.block.BlockState state) {
        Object value = getPropertyValue(state, "ominous");
        return Boolean.TRUE.equals(value);
    }

    private Object getPropertyValue(net.minecraft.block.BlockState state, String name) {
        return state.getEntries().entrySet().stream()
                .filter(entry -> entry.getKey().getName().equals(name))
                .map(entry -> entry.getValue())
                .findFirst()
                .orElse(null);
    }

    private void releaseUse() {
        AltoClef.getInstance().getInputControls().release(Input.CLICK_RIGHT);
        if (AltoClef.getInstance().getPlayer() != null) {
            AltoClef.getInstance().getPlayer().stopUsingItem();
        }
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Revaulting: " + reason + ".");
        finished = true;
        releaseUse();
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
        releaseUse();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof RevaultingTask task
                && task.openVaultEvenIfAdvancementComplete == openVaultEvenIfAdvancementComplete;
    }

    @Override
    protected String toDebugString() {
        return "Using an Ominous Trial Key on an ominous vault for Revaulting";
    }
}
