package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;

public final class MonsterHunterTask extends Task {
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private Entity target;
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

        if (target instanceof LivingEntity living && target instanceof Monster && living.getHealth() <= 0) {
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
                if (!(entity instanceof Monster) || entity instanceof WardenEntity || !entity.isAlive()) continue;
                double distance = entity.squaredDistanceTo(mod.getPlayer());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    target = entity;
                }
            }
            if (target != null) {
                killTask = new KillEntityTask(target);
            }
        }

        if (target == null) {
            setDebugState("Searching for a hostile monster");
            return exploreTask;
        }

        setDebugState("Killing " + target.getType().getName().getString() + " for Monster Hunter");
        return killTask;
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
        return other instanceof MonsterHunterTask;
    }

    @Override
    protected String toDebugString() {
        return "Killing a hostile monster";
    }
}
