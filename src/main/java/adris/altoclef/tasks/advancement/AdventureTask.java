package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.AnimalEntity;

public final class AdventureTask extends Task {
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private Entity target;
    private Task killTask;
    private boolean finished;
    private boolean killedMonster;

    @Override
    protected void onStart() {
        target = null;
        killTask = null;
        finished = false;
        killedMonster = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (target != null && target instanceof LivingEntity living
                && living.getHealth() <= 0) {
            killedMonster = target instanceof Monster && !(target instanceof WardenEntity);
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
                if (!entity.isAlive() || !(entity instanceof AnimalEntity || entity instanceof Monster)) continue;
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
            setDebugState("Searching for an animal or hostile mob");
            return exploreTask;
        }

        setDebugState("Killing " + target.getType().getName().getString() + " for Adventure");
        return killTask;
    }

    public boolean killedMonster() {
        return killedMonster;
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
        return other instanceof AdventureTask;
    }

    @Override
    protected String toDebugString() {
        return "Killing a mob for Adventure";
    }
}
