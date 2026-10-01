package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.stream.Stream;

public final class StarTraderTask extends Task {
    private static final int REQUIRED_Y = 319;
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final TimerGame leashTimer = new TimerGame(2);
    private WhatADealTask tradeTask;
    private MerchantEntity merchant;
    private BlockPos towerBase;
    private BlockPos stagingPosition;
    private int towerIndex;
    private int requiredScaffolding;
    private boolean waitingForLeash;
    private boolean finished;
    private boolean successful;

    public StarTraderTask() {
    }

    @Override
    protected void onStart() {
        merchant = null;
        towerBase = null;
        stagingPosition = null;
        towerIndex = 0;
        requiredScaffolding = 0;
        waitingForLeash = false;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;
        if (mod.getPlayer().getY() >= REQUIRED_Y) {
            if (stagingPosition == null) {
                stagingPosition = mod.getPlayer().getBlockPos();
            }
            if (tradeTask == null) tradeTask = new WhatADealTask(REQUIRED_Y, stagingPosition);
            if (tradeTask.isFinished()) {
                successful = tradeTask.wasSuccessful();
                finished = true;
                return null;
            }
            setDebugState("Trading with a merchant from Y=319 or higher");
            return tradeTask;
        }

        if (merchant == null || !merchant.isAlive()) {
            merchant = findMerchant(mod);
            waitingForLeash = false;
        }
        if (merchant == null) {
            setDebugState("Searching for a villager or wandering trader to transport");
            return exploreTask;
        }

        if (!merchant.isLeashed()) {
            if (!mod.getItemStorage().hasItem(Items.LEAD)) {
                if (!TaskCatalogue.taskExists(Items.LEAD)) {
                    return fail(mod, "no lead resource task is available");
                }
                setDebugState("Obtaining a lead to transport the villager");
                return TaskCatalogue.getItemTask(Items.LEAD, 1);
            }
            if (merchant.squaredDistanceTo(mod.getPlayer()) > 3.5 * 3.5) {
                setDebugState("Approaching the villager to attach a lead");
                return new GetToEntityTask(merchant, 3);
            }
            if (!mod.getSlotHandler().forceEquipItem(Items.LEAD)) return null;
            if (!waitingForLeash) {
                mod.getController().interactEntity(mod.getPlayer(), merchant, Hand.MAIN_HAND);
                waitingForLeash = true;
                leashTimer.reset();
                setDebugState("Attaching a lead to the merchant");
                return null;
            }
            if (!leashTimer.elapsed()) {
                setDebugState("Waiting for the lead to attach");
                return null;
            }
            waitingForLeash = false;
            if (!merchant.isLeashed()) {
                return fail(mod, "the merchant could not be leashed for transport");
            }
        }

        if (towerBase == null) {
            towerBase = mod.getPlayer().getBlockPos();
            stagingPosition = new BlockPos(towerBase.getX(), REQUIRED_Y, towerBase.getZ());
            towerIndex = 0;
            requiredScaffolding = Math.max(1, REQUIRED_Y - towerBase.getY());
        }

        if (mod.getItemStorage().getItemCount(Items.SCAFFOLDING) < requiredScaffolding) {
            if (!TaskCatalogue.taskExists(Items.SCAFFOLDING)) {
                return fail(mod, "no scaffolding resource task is available");
            }
            setDebugState("Obtaining scaffolding to transport a villager to the build limit");
            return TaskCatalogue.getItemTask(Items.SCAFFOLDING, requiredScaffolding);
        }

        if (towerIndex < requiredScaffolding) {
            BlockPos currentLevel = towerBase.up(towerIndex);
            if (!mod.getPlayer().getBlockPos().equals(currentLevel)) {
                setDebugState("Climbing the scaffold while keeping the merchant leashed");
                return new GetToBlockTask(currentLevel);
            }
            BlockPos nextScaffold = towerBase.up(towerIndex + 1);
            if (mod.getWorld().getBlockState(nextScaffold).isOf(Blocks.SCAFFOLDING)) {
                towerIndex++;
                return null;
            }
            if (!mod.getWorld().getBlockState(nextScaffold).isAir()) {
                return fail(mod, "the vertical scaffold is obstructed at " + nextScaffold.toShortString());
            }
            setDebugState("Building the merchant transport tower (" + towerIndex + "/"
                    + requiredScaffolding + ")");
            return new PlaceBlockTask(nextScaffold, Blocks.SCAFFOLDING);
        }

        if (!mod.getPlayer().getBlockPos().equals(stagingPosition)) {
            setDebugState("Climbing to the build limit for the trade");
            return new GetToBlockTask(stagingPosition);
        }

        setDebugState("Waiting for the leashed merchant to reach the build limit");
        return null;
    }

    private MerchantEntity findMerchant(AltoClef mod) {
        return Stream.concat(
                        mod.getEntityTracker().getTrackedEntities(VillagerEntity.class).stream(),
                        mod.getEntityTracker().getTrackedEntities(WanderingTraderEntity.class).stream())
                .filter(MerchantEntity.class::isInstance)
                .map(MerchantEntity.class::cast)
                .filter(Entity::isAlive)
                .filter(entity -> !entity.isLeashed())
                .min(Comparator.comparing((MerchantEntity entity) -> !(entity instanceof VillagerEntity))
                        .thenComparingDouble(entity -> entity.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
    }

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Star Trader: " + reason + ".");
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
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof StarTraderTask;
    }

    @Override
    protected String toDebugString() {
        return "Trading with a villager at the build limit";
    }
}
