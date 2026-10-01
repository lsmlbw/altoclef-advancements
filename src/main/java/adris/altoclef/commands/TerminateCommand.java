package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasks.resources.wood.CollectBoatTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.baritone.GoalFollowEntity;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import baritone.api.utils.input.Input;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Comparator;
import java.util.Optional;

public class TerminateCommand extends Command {
    public TerminateCommand() {
        super("terminate", "Gathers equipment and hunts the closest player");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        mod.runUserTask(new TerminateTask(), this::finish);
    }

    private static final class TerminateTask extends Task {
        private final EquipArmorTask equipmentTask = new EquipArmorTask(
                Items.DIAMOND_HELMET,
                Items.DIAMOND_CHESTPLATE,
                Items.DIAMOND_LEGGINGS,
                Items.DIAMOND_BOOTS,
                Items.SHIELD
        );
        private final CollectBoatTask boatTask = new CollectBoatTask(1);
        private final ItemTarget boats = new ItemTarget(ItemHelper.WOOD_BOAT, 1);
        private boolean previousAvoidFallingBlocks;
        private boolean swimmingUp;
        private boolean foodInitialized;
        private boolean steeringBoat;
        private boolean boatJumpHeld;
        private boolean previousAllowSprint;
        private boolean previousSprintAscends;
        private boolean previousAllowParkour;
        private boolean previousAllowParkourAscend;
        private boolean previousAllowParkourPlace;
        private boolean previousAllowDiagonalAscend;
        private boolean previousAllowDiagonalDescend;
        private boolean previousAllowDownward;
        private boolean previousOvershootTraverse;
        private boolean previousAllowOvershootDiagonalDescend;
        private int lastTargetRefreshTick = Integer.MIN_VALUE;
        private int lastPathRefreshTick = Integer.MIN_VALUE;
        private PlayerEntity targetPlayer;
        private Entity lastPursuitEntity;
        private BlockPos lastPursuitPosition;

        @Override
        protected void onStart() {
            AltoClef mod = AltoClef.getInstance();
            mod.getBehaviour().push();
            mod.getBehaviour().avoidWalkingThrough(pos -> isDangerousFallingBlock(mod, pos));
            mod.getBehaviour().avoidBlockBreaking(pos -> isDangerousFallingBlock(mod, pos));

            previousAvoidFallingBlocks = mod.getClientBaritoneSettings().avoidUpdatingFallingBlocks.value;
            mod.getClientBaritoneSettings().avoidUpdatingFallingBlocks.value = true;

            var settings = mod.getClientBaritoneSettings();
            previousAllowSprint = settings.allowSprint.value;
            previousSprintAscends = settings.sprintAscends.value;
            previousAllowParkour = settings.allowParkour.value;
            previousAllowParkourAscend = settings.allowParkourAscend.value;
            previousAllowParkourPlace = settings.allowParkourPlace.value;
            previousAllowDiagonalAscend = settings.allowDiagonalAscend.value;
            previousAllowDiagonalDescend = settings.allowDiagonalDescend.value;
            previousAllowDownward = settings.allowDownward.value;
            previousOvershootTraverse = settings.overshootTraverse.value;
            previousAllowOvershootDiagonalDescend = settings.allowOvershootDiagonalDescend.value;
            settings.allowSprint.value = true;
            settings.sprintAscends.value = true;
            settings.allowParkour.value = true;
            settings.allowParkourAscend.value = true;
            settings.allowParkourPlace.value = true;
            settings.allowDiagonalAscend.value = true;
            settings.allowDiagonalDescend.value = true;
            settings.allowDownward.value = true;
            settings.overshootTraverse.value = true;
            settings.allowOvershootDiagonalDescend.value = true;
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();

            if (!equipmentTask.isFinished()) {
                releaseBoatInputs(mod);
                releaseSwimUp(mod);
                setDebugState("Getting diamond armor and a shield");
                return equipmentTask;
            }

            int foodScore = StorageHelper.calculateInventoryFoodScore();
            if (!foodInitialized && foodScore < 200) {
                releaseBoatInputs(mod);
                releaseSwimUp(mod);
                setDebugState("Getting an initial supply of 200 food units");
                return new CollectFoodTask(200);
            }
            foodInitialized = true;
            if (foodScore < 100) {
                releaseBoatInputs(mod);
                releaseSwimUp(mod);
                setDebugState("Restocking food to 200 units");
                return new CollectFoodTask(200);
            }

            if (!mod.getItemStorage().hasItem(ItemHelper.WOOD_BOAT)) {
                releaseBoatInputs(mod);
                releaseSwimUp(mod);
                setDebugState("Getting a boat");
                return boatTask;
            }

            int currentTick = WorldHelper.getTicks();
            if (targetPlayer == null || currentTick - lastTargetRefreshTick >= 20) {
                targetPlayer = mod.getEntityTracker()
                        .getTrackedEntities(PlayerEntity.class)
                        .stream()
                        .filter(player -> player.isAlive() && !player.isSpectator())
                        .min(Comparator.comparingDouble(player -> player.squaredDistanceTo(mod.getPlayer())))
                        .orElse(null);
                lastTargetRefreshTick = currentTick;
            }

            if (targetPlayer == null || !targetPlayer.isAlive()) {
                releaseBoatInputs(mod);
                releaseSwimUp(mod);
                lastPursuitEntity = null;
                lastPursuitPosition = null;
                setDebugState("Waiting for a nearby player");
                return null;
            }

            PlayerEntity target = targetPlayer;
            swimUpIfUseful(mod, target);

            Optional<WolfEntity> targetWolf = mod.getEntityTracker()
                    .getTrackedEntities(WolfEntity.class)
                    .stream()
                    .filter(WolfEntity::isAlive)
                    .filter(wolf -> wolf.isOwner(target))
                    .min(Comparator.comparingDouble(wolf -> wolf.squaredDistanceTo(mod.getPlayer())));
            Entity pursuitTarget = targetWolf.<Entity>map(wolf -> wolf).orElse(target);

            refreshPursuitPath(mod, pursuitTarget, currentTick);
            Task boatTask = tryUseBoat(mod, pursuitTarget);
            if (boatTask != null) {
                return boatTask;
            }

            if (targetWolf.isPresent()) {
                setDebugState("Eliminating the target player's wolf");
                return new KillEntityTask(targetWolf.get());
            }

            setDebugState("Hunting " + target.getName().getString());
            return new KillEntityTask(target);
        }

        private void refreshPursuitPath(AltoClef mod, Entity target, int currentTick) {
            boolean targetChanged = target != lastPursuitEntity;
            if (!targetChanged && lastPathRefreshTick != Integer.MIN_VALUE
                    && currentTick - lastPathRefreshTick < 20) {
                return;
            }
            BlockPos targetPosition = target.getBlockPos();
            boolean targetMoved = lastPursuitPosition == null
                    || lastPursuitPosition.getSquaredDistance(targetPosition) >= 9;
            boolean outOfReach = target.squaredDistanceTo(mod.getPlayer())
                    > Math.pow(Math.max(1, mod.getModSettings().getEntityReachRange() - 1), 2);
            if (!(mod.getPlayer().getVehicle() instanceof BoatEntity)
                    && outOfReach && (targetChanged || targetMoved)) {
                mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(
                        new GoalFollowEntity(target, Math.max(1, mod.getModSettings().getEntityReachRange() - 1))
                );
                lastPursuitPosition = targetPosition.toImmutable();
            }
            lastPathRefreshTick = currentTick;
            lastPursuitEntity = target;
        }

        private Task tryUseBoat(AltoClef mod, Entity target) {
            if (mod.getPlayer().getVehicle() instanceof BoatEntity) {
                if (target.squaredDistanceTo(mod.getPlayer()) > 100) {
                    if (boatJumpHeld) {
                        mod.getInputControls().release(Input.JUMP);
                        boatJumpHeld = false;
                    }
                    LookHelper.lookAt(mod, target.getEyePos());
                    mod.getInputControls().hold(Input.MOVE_FORWARD);
                    steeringBoat = true;
                } else if (!boatJumpHeld) {
                    releaseBoatSteering(mod);
                    mod.getInputControls().hold(Input.JUMP);
                    boatJumpHeld = true;
                }
                return null;
            }
            releaseBoatSteering(mod);
            if (boatJumpHeld) {
                mod.getInputControls().release(Input.JUMP);
                boatJumpHeld = false;
            }
            if (!target.isInRange(mod.getPlayer(), 12) && mod.getPlayer().isTouchingWater()) {
                Optional<BoatEntity> nearbyBoat = mod.getEntityTracker()
                        .getTrackedEntities(BoatEntity.class)
                        .stream()
                        .filter(boat -> boat.isAlive() && boat.isInRange(mod.getPlayer(), 4))
                        .min(Comparator.comparingDouble(boat -> boat.squaredDistanceTo(mod.getPlayer())));
                if (nearbyBoat.isPresent()) {
                    mod.getController().interactEntity(mod.getPlayer(), nearbyBoat.get(), Hand.MAIN_HAND);
                    return null;
                }

                Optional<BlockPos> surfaceWater = mod.getBlockScanner().getNearestBlock(
                        mod.getPlayer().getPos(),
                        pos -> mod.getWorld().getBlockState(pos).isOf(Blocks.WATER)
                                && !mod.getWorld().getBlockState(pos.up()).isOf(Blocks.WATER),
                        Blocks.WATER
                );
                if (surfaceWater.isPresent()) {
                    setDebugState("Launching a boat to cross the water");
                    return new InteractWithBlockTask(boats, Direction.UP, surfaceWater.get(), false);
                }
            }
            return null;
        }

        private void swimUpIfUseful(AltoClef mod, PlayerEntity target) {
            boolean shouldSwimUp = mod.getPlayer().isTouchingWater()
                    && target.getY() > mod.getPlayer().getY() + 1;
            if (shouldSwimUp && !swimmingUp) {
                mod.getInputControls().hold(Input.JUMP);
                swimmingUp = true;
            } else if (!shouldSwimUp) {
                releaseSwimUp(mod);
            }
        }

        private void releaseSwimUp(AltoClef mod) {
            if (swimmingUp) {
                mod.getInputControls().release(Input.JUMP);
                swimmingUp = false;
            }
        }

        private void releaseBoatSteering(AltoClef mod) {
            if (steeringBoat) {
                mod.getInputControls().release(Input.MOVE_FORWARD);
                steeringBoat = false;
            }
        }

        private void releaseBoatInputs(AltoClef mod) {
            releaseBoatSteering(mod);
            if (boatJumpHeld) {
                mod.getInputControls().release(Input.JUMP);
                boatJumpHeld = false;
            }
        }

        private boolean isDangerousFallingBlock(AltoClef mod, BlockPos pos) {
            return isFallingHazard(mod, pos) || isFallingHazard(mod, pos.up());
        }

        private boolean isFallingHazard(AltoClef mod, BlockPos pos) {
            return mod.getWorld().getBlockState(pos).getBlock() instanceof FallingBlock
                    || mod.getWorld().getBlockState(pos).isOf(Blocks.POINTED_DRIPSTONE);
        }

        @Override
        protected void onStop(Task interruptTask) {
            AltoClef mod = AltoClef.getInstance();
            releaseSwimUp(mod);
            releaseBoatInputs(mod);
            mod.getClientBaritoneSettings().avoidUpdatingFallingBlocks.value = previousAvoidFallingBlocks;
            var settings = mod.getClientBaritoneSettings();
            settings.allowSprint.value = previousAllowSprint;
            settings.sprintAscends.value = previousSprintAscends;
            settings.allowParkour.value = previousAllowParkour;
            settings.allowParkourAscend.value = previousAllowParkourAscend;
            settings.allowParkourPlace.value = previousAllowParkourPlace;
            settings.allowDiagonalAscend.value = previousAllowDiagonalAscend;
            settings.allowDiagonalDescend.value = previousAllowDiagonalDescend;
            settings.allowDownward.value = previousAllowDownward;
            settings.overshootTraverse.value = previousOvershootTraverse;
            settings.allowOvershootDiagonalDescend.value = previousAllowOvershootDiagonalDescend;
            mod.getBehaviour().pop();
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof TerminateTask;
        }

        @Override
        protected String toDebugString() {
            return "Terminating the closest player";
        }
    }
}
