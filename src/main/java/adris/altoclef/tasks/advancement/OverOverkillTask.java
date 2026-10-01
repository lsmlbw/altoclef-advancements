package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.multiversion.versionedfields.Blocks;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.multiversion.versionedfields.VersionedFieldHelper;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.construction.compound.ConstructIronGolemTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;

public final class OverOverkillTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/over_overkill");
    private static final int SCAFFOLD_HEIGHT = 49;
    private static final double REQUIRED_FALL_DISTANCE = 47;

    private final TimerGame maceReadyTimer = new TimerGame(2);
    private IronGolemEntity target;
    private Task golemTask;
    private Task vaultTask;
    private Task pickupTask;
    private BlockPos towerBase;
    private int towerIndex;
    private boolean preparingStrike;
    private boolean falling;
    private boolean strikeAttempted;
    private double fallStartY;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        target = null;
        golemTask = null;
        vaultTask = null;
        pickupTask = null;
        towerBase = null;
        towerIndex = 0;
        preparingStrike = false;
        falling = false;
        strikeAttempted = false;
        fallStartY = 0;
        finished = false;
        successful = false;
        maceReadyTimer.reset();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Over-Overkill advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            mod.getInputControls().release(Input.MOVE_FORWARD);
            return null;
        }
        if (!VersionedFieldHelper.isSupported(Items.MACE)
                || !VersionedFieldHelper.isSupported(Items.HEAVY_CORE)) {
            return fail("the Mace is not available in this Minecraft version");
        }

        if (!mod.getItemStorage().hasItem(Items.HEAVY_CORE)) {
            if (mod.getEntityTracker().itemDropped(Items.HEAVY_CORE)) {
                if (pickupTask == null) pickupTask = new PickupDroppedItemTask(Items.HEAVY_CORE, 1, true);
                else if (pickupTask.isFinished()) pickupTask.reset();
                setDebugState("Collecting a Heavy Core from an ominous vault");
                return pickupTask;
            }
            if (vaultTask == null) {
                vaultTask = new RevaultingTask(true);
            } else if (vaultTask.isFinished()) {
                vaultTask.reset();
            }
            setDebugState("Opening ominous vaults until a Heavy Core is obtained");
            return vaultTask;
        }

        if (!mod.getItemStorage().hasItem(Items.BREEZE_ROD)) {
            if (!TaskCatalogue.taskExists(Items.BREEZE_ROD)) {
                return fail("no Breeze Rod resource task is available");
            }
            setDebugState("Obtaining a Breeze Rod to craft the Mace");
            return TaskCatalogue.getItemTask(Items.BREEZE_ROD, 1);
        }
        if (!mod.getItemStorage().hasItem(Items.MACE)) {
            if (!TaskCatalogue.taskExists(Items.MACE)) {
                return fail("no Mace crafting recipe is available");
            }
            setDebugState("Crafting the Mace from a Heavy Core and Breeze Rod");
            return TaskCatalogue.getItemTask(Items.MACE, 1);
        }

        if (target == null || !target.isAlive()) {
            target = findGolem(mod);
            if (target == null) {
                towerBase = null;
                if (golemTask == null) {
                    golemTask = new ConstructIronGolemTask();
                } else if (golemTask.isFinished()) {
                    golemTask.reset();
                }
                setDebugState("Constructing a full-health Iron Golem for the Mace strike");
                return golemTask;
            }
            towerBase = null;
            towerIndex = 0;
            preparingStrike = false;
            falling = false;
            strikeAttempted = false;
        }

        if (falling) {
            Vec3d aim = target.getEyePos();
            LookHelper.lookAt(mod, aim, false);
            if (mod.getPlayer().getY() >= fallStartY - 0.2) {
                mod.getInputControls().hold(Input.MOVE_FORWARD);
                return null;
            }
            mod.getInputControls().release(Input.MOVE_FORWARD);
            if (!strikeAttempted && fallStartY - mod.getPlayer().getY() >= REQUIRED_FALL_DISTANCE
                    && mod.getPlayer().squaredDistanceTo(target) <= 4.5 * 4.5) {
                mod.getControllerExtras().attack(target);
                strikeAttempted = true;
                setDebugState("Striking the Iron Golem with a fully charged Mace after a 47-block fall");
                return null;
            }
            if (mod.getPlayer().isOnGround()) {
                mod.getInputControls().release(Input.MOVE_FORWARD);
                falling = false;
                preparingStrike = false;
                strikeAttempted = false;
                maceReadyTimer.reset();
                if (target.isAlive()) {
                    towerIndex = SCAFFOLD_HEIGHT;
                }
            }
            return null;
        }

        if (towerBase == null || !isTowerSiteValid(mod, towerBase)) {
            towerBase = findTowerSite(mod, target);
            towerIndex = 0;
            if (towerBase == null) {
                setDebugState("Finding clear space beside the Iron Golem for a tall Mace-strike scaffold");
                return null;
            }
        }

        if (mod.getItemStorage().getItemCount(Items.SCAFFOLDING) < SCAFFOLD_HEIGHT) {
            if (!TaskCatalogue.taskExists(Items.SCAFFOLDING)) {
                return fail("no scaffolding resource task is available");
            }
            setDebugState("Obtaining scaffolding for the 49-block Mace fall");
            return TaskCatalogue.getItemTask(Items.SCAFFOLDING, SCAFFOLD_HEIGHT);
        }

        if (towerIndex < SCAFFOLD_HEIGHT) {
            BlockPos currentLevel = towerBase.up(towerIndex);
            if (!mod.getPlayer().getBlockPos().equals(currentLevel)) {
                setDebugState("Climbing the Mace-strike scaffold (" + towerIndex + "/" + SCAFFOLD_HEIGHT + ")");
                return new GetToBlockTask(currentLevel);
            }
            BlockPos nextScaffold = towerBase.up(towerIndex + 1);
            if (mod.getWorld().getBlockState(nextScaffold).isOf(Blocks.SCAFFOLDING)) {
                towerIndex++;
                return null;
            }
            if (!mod.getWorld().getBlockState(nextScaffold).isAir()) {
                towerBase = null;
                setDebugState("The scaffold column is obstructed; choosing another strike site");
                return null;
            }
            setDebugState("Building the Mace-strike scaffold (" + towerIndex + "/" + SCAFFOLD_HEIGHT + ")");
            return new PlaceBlockTask(nextScaffold, Blocks.SCAFFOLDING);
        }

        BlockPos topPosition = towerBase.up(SCAFFOLD_HEIGHT);
        if (!mod.getPlayer().getBlockPos().equals(topPosition)) {
            setDebugState("Climbing to the top of the Mace-strike scaffold");
            return new GetToBlockTask(topPosition);
        }
        if (!mod.getSlotHandler().forceEquipItem(Items.MACE)) return null;

        if (!preparingStrike) {
            preparingStrike = true;
            maceReadyTimer.reset();
        }
        LookHelper.lookAt(mod, target.getEyePos(), false);
        if (!maceReadyTimer.elapsed()) {
            setDebugState("Charging the Mace attack before stepping off the scaffold");
            return null;
        }
        fallStartY = mod.getPlayer().getY();
        falling = true;
        setDebugState("Stepping off the scaffold to build enough fall damage for Over-Overkill");
        return null;
    }

    private boolean isTowerSiteValid(AltoClef mod, BlockPos base) {
        if (!mod.getChunkTracker().isChunkLoaded(base)
                || !mod.getWorld().getBlockState(base.down()).isSolidBlock(mod.getWorld(), base.down())) {
            return false;
        }
        int topY = base.getY() + SCAFFOLD_HEIGHT;
        if (topY >= mod.getWorld().getTopY() - 2) return false;
        for (int offset = 1; offset <= SCAFFOLD_HEIGHT; offset++) {
            BlockPos position = base.up(offset);
            if (!mod.getChunkTracker().isChunkLoaded(position)
                    || (!mod.getWorld().getBlockState(position).isAir()
                    && !mod.getWorld().getBlockState(position).isOf(Blocks.SCAFFOLDING))) {
                return false;
            }
        }
        return true;
    }

    private BlockPos findTowerSite(AltoClef mod, IronGolemEntity golem) {
        BlockPos golemPos = golem.getBlockPos();
        return java.util.Arrays.stream(new Direction[]{
                        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
                .map(direction -> golemPos.offset(direction))
                .filter(base -> isTowerSiteValid(mod, base))
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())))
                .orElse(null);
    }

    private IronGolemEntity findGolem(AltoClef mod) {
        return mod.getEntityTracker().getTrackedEntities(IronGolemEntity.class).stream()
                .filter(IronGolemEntity::isAlive)
                .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
    }

    private AdvancementProgress getAdvancementProgress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return null;
        var manager = client.getNetworkHandler().getAdvancementHandler();
        PlacedAdvancement advancement = manager.getManager().get(ADVANCEMENT_ID);
        return advancement == null ? null
                : ((ClientAdvancementManagerAccessor) manager)
                .altoclef$getAdvancementProgresses().get(advancement);
    }

    private Task fail(String reason) {
        AltoClef.getInstance().logWarning("Over-Overkill automation stopped: " + reason + ".");
        finished = true;
        return null;
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
        AltoClef.getInstance().getInputControls().release(Input.MOVE_FORWARD);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof OverOverkillTask;
    }

    @Override
    protected String toDebugString() {
        return "Dealing 50 hearts of damage in one Mace hit for Over-Overkill";
    }
}
