package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.multiversion.versionedfields.Blocks;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.BreezeEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Set;

public final class BlowbackTask extends Task {
    private static final Identifier BLOWBACK_ID =
            Identifier.of("minecraft", "adventure/blowback");
    private static final double PROJECTILE_REACH = 5.5;
    private static final double SAFE_BREEZE_HEALTH = 5;

    private final Task searchSpawnerTask = new SearchChunkForBlockTask(Blocks.TRIAL_SPAWNER);
    private final Set<ProjectileEntity> attackedCharges =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private BreezeEntity breeze;
    private Task approachTask;
    private boolean deflecting;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        breeze = null;
        approachTask = null;
        deflecting = false;
        attackedCharges.clear();
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Blowback advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            return null;
        }
        if (!WorldHelper.getCurrentDimension().equals(Dimension.OVERWORLD)) {
            setDebugState("Returning to the Overworld to find a Breeze");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (breeze == null || !breeze.isAlive()) {
            breeze = findBreeze(mod);
            approachTask = null;
            deflecting = false;
            attackedCharges.clear();
            if (breeze == null) {
                BlockPos spawner = findSpawner(mod);
                if (spawner != null) {
                    setDebugState("Approaching a trial spawner to find a Breeze");
                    return new GetToBlockTask(spawner.up());
                }
                setDebugState("Searching trial chambers for a Breeze");
                return searchSpawnerTask;
            }
        }

        if (mod.getPlayer().squaredDistanceTo(breeze) > 12 * 12) {
            if (approachTask == null) approachTask = new GetToEntityTask(breeze, 10);
            setDebugState("Approaching a Breeze for Blowback");
            return approachTask;
        }

        if (!deflecting && breeze.getHealth() > SAFE_BREEZE_HEALTH) {
            if (mod.getPlayer().squaredDistanceTo(breeze) > 3.5 * 3.5) {
                approachTask = new GetToEntityTask(breeze, 2.8);
                setDebugState("Moving into wooden-sword range to weaken the Breeze carefully");
                return approachTask;
            }
            if (!mod.getItemStorage().hasItem(Items.WOODEN_SWORD)) {
                if (!TaskCatalogue.taskExists(Items.WOODEN_SWORD)) {
                    return fail(mod, "no wooden sword resource task is available");
                }
                setDebugState("Obtaining a wooden sword to weaken the Breeze safely");
                return TaskCatalogue.getItemTask(Items.WOODEN_SWORD, 1);
            }
            if (!mod.getSlotHandler().forceEquipItem(Items.WOODEN_SWORD)) {
                return null;
            }
            LookHelper.lookAt(mod, breeze.getBoundingBox().getCenter());
            if (mod.getPlayer().squaredDistanceTo(breeze) <= 3.5 * 3.5
                    && mod.getPlayer().getAttackCooldownProgress(0) >= 1
                    && mod.getPlayer().isOnGround()) {
                mod.getControllerExtras().attack(breeze);
            }
            if (breeze.getHealth() <= SAFE_BREEZE_HEALTH) {
                deflecting = true;
                attackedCharges.clear();
            }
            setDebugState("Carefully weakening the Breeze before deflecting its wind charges");
            return null;
        }

        deflecting = true;
        if (breeze.getHealth() <= 0 || !breeze.isAlive()) {
            successful = true;
            finished = true;
            return null;
        }

        LookHelper.lookAt(mod, breeze.getEyePos());
        ProjectileEntity charge = findIncomingBreezeCharge(mod);
        if (charge != null) {
            mod.getControllerExtras().attack(charge);
            attackedCharges.add(charge);
            setDebugState("Deflecting the Breeze's wind charge back at it");
            return null;
        }

        setDebugState("Waiting for the weakened Breeze to shoot a wind charge");
        return null;
    }

    private BreezeEntity findBreeze(AltoClef mod) {
        return mod.getEntityTracker().getTrackedEntities(BreezeEntity.class).stream()
                .filter(BreezeEntity::isAlive)
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

    private ProjectileEntity findIncomingBreezeCharge(AltoClef mod) {
        return mod.getEntityTracker().getTrackedEntities(ProjectileEntity.class).stream()
                .filter(ProjectileEntity::isAlive)
                .filter(projectile -> isBreezeWindCharge(projectile))
                .filter(projectile -> projectile.getOwner() == breeze)
                .filter(projectile -> !attackedCharges.contains(projectile))
                .filter(projectile -> projectile.squaredDistanceTo(mod.getPlayer()) <= PROJECTILE_REACH * PROJECTILE_REACH)
                .filter(projectile -> projectile.getVelocity()
                        .dotProduct(mod.getPlayer().getPos().subtract(projectile.getPos())) > 0)
                .min(Comparator.comparingDouble(projectile -> projectile.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
    }

    private boolean isBreezeWindCharge(ProjectileEntity projectile) {
        Identifier type = net.minecraft.registry.Registries.ENTITY_TYPE.getId(projectile.getType());
        return type.getPath().equals("breeze_wind_charge");
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Blowback: " + reason + ".");
        finished = true;
        return null;
    }

    private AdvancementProgress getAdvancementProgress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return null;
        var manager = client.getNetworkHandler().getAdvancementHandler();
        PlacedAdvancement entry = manager.getManager().get(BLOWBACK_ID);
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
        return other instanceof BlowbackTask;
    }

    @Override
    protected String toDebugString() {
        return "Killing a Breeze with its deflected wind charge for Blowback";
    }
}
