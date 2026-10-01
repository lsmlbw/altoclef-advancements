package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.EvokerEntity;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class VoluntaryExileTask extends Task {
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private IllagerEntity target;
    private Task killTask;
    private boolean finished;

    @Override
    protected void onStart() {
        target = null;
        killTask = null;
        finished = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (target != null && target.getHealth() <= 0) {
            finished = true;
            return null;
        }
        if (target != null && !target.isAlive()) {
            target = null;
            killTask = null;
        }

        if (target == null) {
            double closestDistance = Double.POSITIVE_INFINITY;
            for (Entity entity : mod.getWorld().getEntities()) {
                if (!(entity instanceof IllagerEntity illager)
                        || !isEligibleCaptain(illager)
                        || !illager.isAlive()) {
                    continue;
                }
                double distance = illager.squaredDistanceTo(mod.getPlayer());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    target = illager;
                }
            }
            if (target != null) {
                killTask = new KillEntityTask(target);
            }
        }

        if (target == null) {
            setDebugState("Searching for an illager raid captain");
            return exploreTask;
        }

        setDebugState("Killing the illager raid captain for Voluntary Exile");
        return killTask;
    }

    private boolean isEligibleCaptain(IllagerEntity illager) {
        if (!(illager instanceof PillagerEntity
                || illager instanceof VindicatorEntity
                || illager instanceof EvokerEntity)) {
            return false;
        }
        ItemStack headItem = illager.getEquippedStack(EquipmentSlot.HEAD);
        return headItem.isOf(Items.WHITE_BANNER);
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
        return other instanceof VoluntaryExileTask;
    }

    @Override
    protected String toDebugString() {
        return "Killing an illager captain for Voluntary Exile";
    }
}
