package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import baritone.api.utils.input.Input;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class CavesAndCliffsTask extends Task {
    private static final int SHAFT_TOP_Y = 319;
    private static final int SHAFT_BOTTOM_Y = -60;
    private static final int WATER_Y = -61;
    private static final int SUPPORT_Y = -62;
    private static final int SCAFFOLDING_COUNT = SHAFT_TOP_Y - WATER_Y + 1;

    private final Task searchBedrockTask = new SearchChunkForBlockTask(Blocks.BEDROCK);
    private BlockPos waterFloor;
    private BlockPos fallColumn;
    private BlockPos pillarBase;
    private Direction pillarDirection;
    private Task placeWaterTask;
    private Task currentBlockTask;
    private int pillarIndex;
    private int shaftY;
    private double fallStartY;
    private boolean waterReady;
    private boolean falling;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        waterFloor = null;
        fallColumn = null;
        pillarBase = null;
        pillarDirection = null;
        placeWaterTask = null;
        currentBlockTask = null;
        pillarIndex = 0;
        shaftY = SHAFT_TOP_Y;
        fallStartY = 0;
        waterReady = false;
        falling = false;
        finished = false;
        successful = false;
        releaseMovement();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (falling) return monitorFall(mod);

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            setDebugState("Returning to the Overworld for the Caves & Cliffs fall");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (waterFloor == null && !mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
            if (!TaskCatalogue.taskExists(Items.WATER_BUCKET)) {
                return fail(mod, "no water bucket resource task is available");
            }
            setDebugState("Obtaining a water bucket for the landing");
            return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
        }

        if (waterFloor == null) {
            Optional<BlockPos> setup = findBottomSetup(mod);
            if (setup.isEmpty()) {
                setDebugState("Searching for a clear bottom bedrock position");
                return searchBedrockTask;
            }
            waterFloor = setup.get();
            fallColumn = waterFloor;
            pillarDirection = findPillarDirection(mod, waterFloor);
            if (pillarDirection == null) {
                waterFloor = null;
                fallColumn = null;
                return null;
            }
            pillarBase = waterFloor.offset(pillarDirection);
            pillarIndex = 0;
        }

        BlockPos waterPos = waterFloor.up();
        if (!mod.getWorld().getBlockState(waterPos).isOf(Blocks.WATER)) {
            if (!waterReady) {
                if (placeWaterTask == null) {
                    placeWaterTask = new InteractWithBlockTask(Items.WATER_BUCKET, waterFloor);
                }
                setDebugState("Placing water above bedrock before preparing the drop shaft");
                return placeWaterTask;
            }
            return fail(mod, "the landing water did not remain in place");
        }
        waterReady = true;

        if (mod.getItemStorage().getItemCount(Items.SCAFFOLDING) < SCAFFOLDING_COUNT) {
            if (!TaskCatalogue.taskExists(Items.SCAFFOLDING)) {
                return fail(mod, "no scaffolding resource task is available");
            }
            setDebugState("Obtaining six stacks of scaffolding for the climb");
            return TaskCatalogue.getItemTask(Items.SCAFFOLDING, SCAFFOLDING_COUNT);
        }

        if (pillarIndex <= SHAFT_TOP_Y - WATER_Y) {
            BlockPos scaffoldPos = pillarBase.up(pillarIndex + 1);
            if (mod.getWorld().getBlockState(scaffoldPos).isOf(Blocks.SCAFFOLDING)) {
                pillarIndex++;
                return null;
            }
            if (!mod.getWorld().getBlockState(scaffoldPos).isAir()) {
                return fail(mod, "the scaffolding column is obstructed at " + scaffoldPos.toShortString());
            }
            setDebugState("Building the supported scaffolding climb (" + pillarIndex + "/"
                    + SCAFFOLDING_COUNT + ")");
            return new PlaceBlockTask(scaffoldPos, Blocks.SCAFFOLDING);
        }

        if (shaftY >= SHAFT_BOTTOM_Y) {
            BlockPos playerLevel = new BlockPos(pillarBase.getX(), shaftY + 1, pillarBase.getZ());
            if (!mod.getPlayer().getBlockPos().equals(playerLevel)) {
                setDebugState("Moving down the scaffolding while clearing the fall shaft");
                return new GetToBlockTask(playerLevel);
            }

            BlockPos target = new BlockPos(fallColumn.getX(), shaftY, fallColumn.getZ());
            var state = mod.getWorld().getBlockState(target);
            if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.LAVA)) {
                return fail(mod, "an unbreakable bedrock block or lava obstructs the fall shaft at "
                        + target.toShortString());
            }
            if (state.isOf(Blocks.WATER)) {
                return fail(mod, "water obstructs the fall shaft above the protected landing");
            }
            for (Direction direction : new Direction[]{
                    Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                if (mod.getWorld().getBlockState(target.offset(direction)).isOf(Blocks.LAVA)) {
                    return fail(mod, "lava is adjacent to the fall shaft at " + target.toShortString());
                }
            }
            if (state.isAir()) {
                shaftY--;
                currentBlockTask = null;
                return null;
            }
            if (currentBlockTask == null) currentBlockTask = new DestroyBlockTask(target);
            setDebugState("Clearing the protected fall shaft at Y=" + shaftY);
            return currentBlockTask;
        }

        BlockPos topPosition = new BlockPos(pillarBase.getX(), SHAFT_TOP_Y + 1, pillarBase.getZ());
        if (!mod.getPlayer().getBlockPos().equals(topPosition)) {
            setDebugState("Climbing to the top of the scaffolding");
            return new GetToBlockTask(topPosition);
        }

        Vec3d target = Vec3d.ofCenter(new BlockPos(fallColumn.getX(), SHAFT_TOP_Y, fallColumn.getZ()));
        var rotation = LookHelper.getLookRotation(mod, target);
        LookHelper.lookAt(mod, target, false);
        if (!LookHelper.isLookingAt(mod, rotation)) {
            releaseMovement();
            setDebugState("Aiming into the cleared fall shaft");
            return null;
        }

        double dx = mod.getPlayer().getX() - (fallColumn.getX() + 0.5);
        double dz = mod.getPlayer().getZ() - (fallColumn.getZ() + 0.5);
        if (Math.abs(dx) < 0.15 && Math.abs(dz) < 0.15) {
            fallStartY = mod.getPlayer().getY();
            if (fallStartY < SHAFT_TOP_Y) {
                return fail(mod, "the player is not at the required build-limit height");
            }
            falling = true;
            releaseMovement();
            return null;
        }
        mod.getInputControls().hold(Input.MOVE_FORWARD);
        setDebugState("Stepping into the prepared shaft from the build limit");
        return null;
    }

    private Task monitorFall(AltoClef mod) {
        releaseMovement();
        if (!mod.getPlayer().isAlive()) {
            return fail(mod, "the fall was not survived");
        }
        double verticalDistance = fallStartY - mod.getPlayer().getY();
        if (mod.getPlayer().getY() <= -59 && verticalDistance > 379
                && (mod.getPlayer().isOnGround() || mod.getPlayer().isTouchingWater())) {
            successful = true;
            finished = true;
            return null;
        }
        if (mod.getPlayer().getY() < SHAFT_BOTTOM_Y - 5) {
            return fail(mod, "the player passed the landing water without confirming a safe landing");
        }
        setDebugState("Verifying survival after a " + Math.round(verticalDistance) + " block fall");
        return null;
    }

    private Optional<BlockPos> findBottomSetup(AltoClef mod) {
        List<BlockPos> bedrockLocations = mod.getBlockScanner().getKnownLocations(Blocks.BEDROCK);
        return bedrockLocations.stream()
                .filter(pos -> pos.getY() == SUPPORT_Y)
                .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                .filter(pos -> mod.getWorld().getBlockState(pos).isOf(Blocks.BEDROCK))
                .filter(pos -> mod.getWorld().getBlockState(pos.up()).isAir())
                .filter(pos -> mod.getWorld().getBlockState(pos.up(2)).isAir())
                .filter(pos -> hasAdjacentBedrock(mod, pos))
                .min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(mod.getPlayer().getBlockPos())));
    }

    private boolean hasAdjacentBedrock(AltoClef mod, BlockPos pos) {
        for (Direction direction : new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos support = pos.offset(direction);
            if (mod.getWorld().getBlockState(support).isOf(Blocks.BEDROCK)) return true;
        }
        return false;
    }

    private Direction findPillarDirection(AltoClef mod, BlockPos pos) {
        for (Direction direction : new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos support = pos.offset(direction);
            if (mod.getWorld().getBlockState(support).isOf(Blocks.BEDROCK)) return direction;
        }
        return null;
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Caves & Cliffs safely: " + reason + ".");
        releaseMovement();
        finished = true;
        return null;
    }

    private void releaseMovement() {
        AltoClef.getInstance().getInputControls().release(Input.MOVE_FORWARD);
        AltoClef.getInstance().getInputControls().release(Input.JUMP);
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
        releaseMovement();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof CavesAndCliffsTask;
    }

    @Override
    protected String toDebugString() {
        return "Falling from build limit to the bottom of the Overworld";
    }
}
