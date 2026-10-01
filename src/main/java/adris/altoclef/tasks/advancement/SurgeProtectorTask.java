package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Comparator;

public final class SurgeProtectorTask extends Task {
    private static final int PILLAR_BLOCKS = 4;
    private static final int ROD_OFFSET = 6;

    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private VillagerEntity villager;
    private BlockPos pillarBase;
    private BlockPos rodPos;
    private int pillarIndex;
    private boolean rodWasPowered;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        villager = null;
        pillarBase = null;
        rodPos = null;
        pillarIndex = 0;
        rodWasPowered = false;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            setDebugState("Returning to the Overworld to set up the lightning rod");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (!mod.getItemStorage().hasItem(Items.LIGHTNING_ROD)) {
            if (!TaskCatalogue.taskExists(Items.LIGHTNING_ROD)) {
                mod.logWarning("Cannot complete Surge Protector: no lightning rod resource task is available.");
                finished = true;
                return null;
            }
            setDebugState("Obtaining a lightning rod");
            return TaskCatalogue.getItemTask(Items.LIGHTNING_ROD, 1);
        }

        if (villager == null || !villager.isAlive()
                || villager.getHealth() < villager.getMaxHealth()) {
            villager = findVillager(mod);
        }
        if (villager == null) {
            if (rodPos == null || !mod.getWorld().getBlockState(rodPos).isOf(Blocks.LIGHTNING_ROD)) {
                pillarBase = null;
            }
            setDebugState("Searching for a villager to protect from lightning");
            return exploreTask;
        }

        if (pillarBase == null || rodPos == null
                || !isVillagerNearSetup() && !mod.getWorld().getBlockState(rodPos).isOf(Blocks.LIGHTNING_ROD)) {
            if (!findSetupPosition(mod)) {
                setDebugState("Finding clear, nonflammable ground near a villager");
                return exploreTask;
            }
            pillarIndex = 0;
        }

        if (mod.getItemStorage().getItemCount(Items.COBBLESTONE) < PILLAR_BLOCKS) {
            if (!TaskCatalogue.taskExists(Items.COBBLESTONE)) {
                mod.logWarning("Cannot complete Surge Protector: no cobblestone resource task is available.");
                finished = true;
                return null;
            }
            setDebugState("Obtaining cobblestone for the lightning rod pillar");
            return TaskCatalogue.getItemTask(Items.COBBLESTONE, PILLAR_BLOCKS);
        }

        while (pillarIndex < PILLAR_BLOCKS
                && mod.getWorld().getBlockState(pillarBase.up(pillarIndex)).isOf(Blocks.COBBLESTONE)) {
            pillarIndex++;
        }
        if (pillarIndex < PILLAR_BLOCKS) {
            BlockPos placePos = pillarBase.up(pillarIndex);
            if (!mod.getWorld().getBlockState(placePos).isAir()) {
                pillarBase = null;
                setDebugState("Repositioning the lightning rod setup around obstacles");
                return null;
            }
            setDebugState("Building the nonflammable lightning rod pillar");
            return new PlaceBlockTask(placePos, Blocks.COBBLESTONE);
        }

        if (rodPos == null) rodPos = pillarBase.up(PILLAR_BLOCKS);
        if (!mod.getWorld().getBlockState(rodPos).isOf(Blocks.LIGHTNING_ROD)) {
            if (!mod.getWorld().getBlockState(rodPos).isAir()) {
                pillarBase = null;
                rodPos = null;
                return null;
            }
            setDebugState("Placing the lightning rod above the pillar");
            return new PlaceBlockTask(rodPos, Blocks.LIGHTNING_ROD);
        }

        BlockPos playerStand = villager.getBlockPos().offset(
                Direction.fromHorizontal((int) (villager.getYaw() / 90 + 2) & 3));
        if (!mod.getPlayer().getBlockPos().isWithinDistance(rodPos, 28)) {
            setDebugState("Staying within 30 blocks of the lightning rod");
            return new GetToBlockTask(playerStand);
        }

        boolean rodPowered = mod.getWorld().getBlockState(rodPos).get(Properties.POWERED);

        boolean lightningNearby = false;
        for (Entity entity : mod.getWorld().getEntities()) {
            if (entity instanceof LightningEntity
                    && entity.squaredDistanceTo(rodPos.getX(), rodPos.getY(), rodPos.getZ()) <= 16) {
                lightningNearby = true;
                break;
            }
        }
        if (mod.getWorld().isThundering() && (lightningNearby || rodPowered && !rodWasPowered)
                && villager.isAlive() && villager.getHealth() >= villager.getMaxHealth()) {
            successful = true;
            finished = true;
            return null;
        }
        rodWasPowered = rodPowered;

        if (villager.squaredDistanceTo(rodPos.getX(), rodPos.getY(), rodPos.getZ()) > 12 * 12) {
            VillagerEntity nearbyVillager = findNearbyVillager(mod, rodPos);
            if (nearbyVillager != null) {
                villager = nearbyVillager;
                return null;
            }
            setDebugState("Waiting for a healthy villager to approach the lightning rod");
            return null;
        }
        setDebugState(mod.getWorld().isThundering()
                ? "Waiting for lightning to strike the rod near the villager"
                : "Waiting for a thunderstorm; the villager is protected beside the rod");
        return null;
    }

    private VillagerEntity findVillager(AltoClef mod) {
        return mod.getEntityTracker().getTrackedEntities(VillagerEntity.class).stream()
                .filter(villager -> villager.isAlive()
                        && villager.getHealth() >= villager.getMaxHealth())
                .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
    }

    private VillagerEntity findNearbyVillager(AltoClef mod, BlockPos position) {
        return mod.getEntityTracker().getTrackedEntities(VillagerEntity.class).stream()
                .filter(villager -> villager.isAlive()
                        && villager.getHealth() >= villager.getMaxHealth()
                        && villager.squaredDistanceTo(position.getX(), position.getY(), position.getZ()) <= 12 * 12)
                .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(position.getX(),
                        position.getY(), position.getZ())))
                .orElse(null);
    }

    private boolean findSetupPosition(AltoClef mod) {
        BlockPos villagerPos = villager.getBlockPos();
        for (Direction direction : new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos candidate = villagerPos.offset(direction, ROD_OFFSET);
            if (!WorldHelper.isSolidBlock(candidate.down())) continue;
            boolean clear = true;
            for (int y = 0; y <= PILLAR_BLOCKS; y++) {
                if (!mod.getWorld().getBlockState(candidate.up(y)).isAir()) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                pillarBase = candidate;
                rodPos = candidate.up(PILLAR_BLOCKS);
                return true;
            }
        }
        return false;
    }

    private boolean isVillagerNearSetup() {
        return villager != null && villager.isAlive()
                && villager.squaredDistanceTo(rodPos.getX(), rodPos.getY(), rodPos.getZ()) <= 12 * 12;
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
        return other instanceof SurgeProtectorTask;
    }

    @Override
    protected String toDebugString() {
        return "Protecting a villager from lightning with a lightning rod";
    }
}
