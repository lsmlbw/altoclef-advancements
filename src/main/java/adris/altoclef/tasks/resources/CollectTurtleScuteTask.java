package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.Optional;

public final class CollectTurtleScuteTask extends ResourceTask {
    private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(true);

    public CollectTurtleScuteTask(int targetCount) {
        super(Items.TURTLE_SCUTE, targetCount);
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        Optional<TurtleEntity> baby = mod.getEntityTracker().getTrackedEntities(TurtleEntity.class).stream()
                .filter(TurtleEntity::isBaby)
                .filter(TurtleEntity::isAlive)
                .findFirst();
        if (baby.isEmpty()) {
            setDebugState("Searching for a baby turtle to grow into a scute");
            return wanderTask;
        }

        if (!mod.getItemStorage().hasItem(Items.SEAGRASS)) {
            if (!TaskCatalogue.taskExists(Items.SEAGRASS)) {
                mod.logWarning("Cannot grow a baby turtle: no seagrass resource task is available.");
                return wanderTask;
            }
            return TaskCatalogue.getItemTask(Items.SEAGRASS, 10);
        }

        TurtleEntity turtle = baby.get();
        if (!WorldHelper.inRangeXZ(mod.getPlayer(), turtle.getBlockPos(), 3)) {
            return new adris.altoclef.tasks.movement.GetToEntityTask(turtle, 2);
        }
        if (mod.getSlotHandler().forceEquipItem(Items.SEAGRASS)) {
            mod.getController().interactEntity(mod.getPlayer(), turtle, Hand.MAIN_HAND);
            setDebugState("Feeding seagrass to a baby turtle");
        }
        return null;
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectTurtleScuteTask task
                && task.getItemTargets()[0].getTargetCount() == getItemTargets()[0].getTargetCount();
    }

    @Override
    protected String toDebugStringName() {
        return "Collect turtle scutes";
    }
}
