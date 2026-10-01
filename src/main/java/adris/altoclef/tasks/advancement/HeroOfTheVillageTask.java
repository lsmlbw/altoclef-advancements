package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EvokerEntity;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public final class HeroOfTheVillageTask extends Task {
    private static final double VILLAGE_RADIUS = 24;
    private static final double RAIDER_RADIUS = 48;

    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final TimerGame raidOmenDuration = new TimerGame(35);
    private Entity captain;
    private VillagerEntity villager;
    private LivingEntity raider;
    private BlockPos villageBellPosition;
    private Task activeTask;
    private boolean captainKilled;
    private boolean usingBottle;
    private int bottleCountBeforeUse;
    private boolean bottleDrank;
    private boolean raidStarted;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        captain = null;
        villager = null;
        raider = null;
        villageBellPosition = null;
        activeTask = null;
        captainKilled = false;
        usingBottle = false;
        bottleCountBeforeUse = 0;
        bottleDrank = false;
        raidStarted = false;
        finished = false;
        successful = false;
        raidOmenDuration.reset();
        releaseUse();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;
        if (mod.getPlayer().hasStatusEffect(StatusEffects.HERO_OF_THE_VILLAGE)) {
            successful = true;
            finished = true;
            releaseUse();
            return null;
        }

        if (!mod.getItemStorage().hasItem(Items.OMINOUS_BOTTLE) && !bottleDrank) {
            releaseUse();
            usingBottle = false;
            if (captain != null && captain.isAlive()) {
                setDebugState("Defeating an illager captain for an ominous bottle");
                activeTask = new KillEntityTask(captain);
                return activeTask;
            }
            if (captain != null && !captain.isAlive()) {
                captainKilled = true;
                captain = null;
            }
            if (mod.getEntityTracker().itemDropped(Items.OMINOUS_BOTTLE)) {
                setDebugState("Collecting the ominous bottle");
                return new PickupDroppedItemTask(new ItemTarget(Items.OMINOUS_BOTTLE, 1), true);
            }
            if (captainKilled) {
                captainKilled = false;
                return null;
            }
            captain = findCaptain(mod);
            if (captain == null) {
                setDebugState("Searching for an illager captain carrying a banner");
                return exploreTask;
            }
            setDebugState("Approaching an illager captain");
            activeTask = new KillEntityTask(captain);
            return activeTask;
        }

        if (villager == null || !villager.isAlive()) {
            villager = findVillager(mod);
        }
        if (villager == null) {
            setDebugState("Searching for a villager to establish a village");
            return exploreTask;
        }

        if (villager.squaredDistanceTo(mod.getPlayer()) > VILLAGE_RADIUS * VILLAGE_RADIUS) {
            setDebugState("Moving within the village before starting the raid");
            return new GetToEntityTask(villager, 12);
        }

        if (!isVillageEstablished(mod)) {
            if (!mod.getItemStorage().hasItem(Items.BELL)) {
                setDebugState("Obtaining a bell to establish a village around the villager");
                return TaskCatalogue.getItemTask(Items.BELL, 1);
            }
            if (villageBellPosition == null || !mod.getWorld().getBlockState(villageBellPosition).isReplaceable()) {
                villageBellPosition = findBellPlacement(mod, villager);
            }
            if (villageBellPosition == null) {
                setDebugState("Finding clear ground beside the villager for a village bell");
                return exploreTask;
            }
            setDebugState("Placing a bell beside the villager to establish a village");
            return new PlaceBlockTask(villageBellPosition, Blocks.BELL);
        }

        if (!bottleDrank) {
            if (!mod.getSlotHandler().forceEquipItem(Items.OMINOUS_BOTTLE)) return null;
            if (!usingBottle) {
                bottleCountBeforeUse = mod.getItemStorage().getItemCount(Items.OMINOUS_BOTTLE);
                usingBottle = true;
            }
            mod.getInputControls().hold(Input.CLICK_RIGHT);
            if (mod.getItemStorage().getItemCount(Items.OMINOUS_BOTTLE) < bottleCountBeforeUse) {
                releaseUse();
                usingBottle = false;
                bottleDrank = true;
                raidOmenDuration.reset();
            }
            setDebugState("Drinking an ominous bottle in the village");
            return null;
        }

        if (!raidStarted) {
            if (mod.getPlayer().hasStatusEffect(StatusEffects.RAID_OMEN)
                    || mod.getPlayer().hasStatusEffect(StatusEffects.BAD_OMEN)
                    || !raidOmenDuration.elapsed()) {
                setDebugState("Waiting for Raid Omen to start the raid");
                return null;
            }
            raidStarted = true;
        }

        if (raider != null && raider.getHealth() <= 0) {
            raider = null;
            activeTask = null;
        } else if (raider != null && !raider.isAlive()) {
            raider = null;
            activeTask = null;
        }

        if (raider == null) raider = findRaider(mod, villager);
        if (raider == null) {
            setDebugState("Waiting near the villager for the next raid wave");
            return null;
        }

        setDebugState("Defeating a raid mob");
        activeTask = new KillEntityTask(raider);
        return activeTask;
    }

    private Entity findCaptain(AltoClef mod) {
        Entity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Entity entity : mod.getWorld().getEntities()) {
            if (!(entity instanceof IllagerEntity illager)
                    || !(illager instanceof PillagerEntity
                    || illager instanceof VindicatorEntity
                    || illager instanceof EvokerEntity)
                    || !entity.isAlive()
                    || !illager.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.WHITE_BANNER)) {
                continue;
            }
            double distance = entity.squaredDistanceTo(mod.getPlayer());
            if (distance < closestDistance) {
                closest = entity;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private VillagerEntity findVillager(AltoClef mod) {
        return mod.getEntityTracker().getTrackedEntities(VillagerEntity.class).stream()
                .filter(Entity::isAlive)
                .min((first, second) -> Double.compare(
                        first.squaredDistanceTo(mod.getPlayer()),
                        second.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
    }

    private boolean isVillageEstablished(AltoClef mod) {
        if (villageBellPosition != null
                && mod.getWorld().getBlockState(villageBellPosition).isOf(Blocks.BELL)) {
            return true;
        }
        return mod.getBlockScanner().getKnownLocations(Blocks.BELL).stream()
                .anyMatch(pos -> pos.getSquaredDistance(villager.getBlockPos()) <= 8 * 8);
    }

    private BlockPos findBellPlacement(AltoClef mod, VillagerEntity villager) {
        BlockPos center = villager.getBlockPos();
        BlockPos[] candidates = {
                center.east(), center.west(), center.north(), center.south(),
                center.east(2), center.west(2), center.north(2), center.south(2),
                center.add(1, 0, 1), center.add(1, 0, -1),
                center.add(-1, 0, 1), center.add(-1, 0, -1)
        };
        for (BlockPos candidate : candidates) {
            if (mod.getWorld().getBlockState(candidate).isReplaceable()
                    && adris.altoclef.util.helpers.WorldHelper.isSolidBlock(candidate.down())) {
                return candidate;
            }
        }
        return null;
    }

    private LivingEntity findRaider(AltoClef mod, VillagerEntity villageCenter) {
        LivingEntity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Entity entity : mod.getWorld().getEntities()) {
            if (!(entity instanceof LivingEntity living)
                    || !isRaidMob(living)
                    || !living.isAlive()
                    || Math.abs(living.getX() - villageCenter.getX()) > RAIDER_RADIUS
                    || Math.abs(living.getZ() - villageCenter.getZ()) > RAIDER_RADIUS) {
                continue;
            }
            double distance = living.squaredDistanceTo(mod.getPlayer());
            if (distance < closestDistance) {
                closest = living;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private boolean isRaidMob(LivingEntity entity) {
        return entity instanceof PillagerEntity
                || entity instanceof VindicatorEntity
                || entity instanceof EvokerEntity
                || entity instanceof RavagerEntity
                || entity instanceof VexEntity
                || entity instanceof WitchEntity;
    }

    private void releaseUse() {
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
        releaseUse();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof HeroOfTheVillageTask;
    }

    @Override
    protected String toDebugString() {
        return "Defending a village from a raid";
    }
}
