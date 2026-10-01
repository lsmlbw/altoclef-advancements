package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Items;

public final class OlBetsyTask extends Task {
    private final TimerGame loadTimer = new TimerGame(2);
    private final TimerGame fireTimeout = new TimerGame(3);
    private boolean loading;
    private boolean fired;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        loading = false;
        fired = false;
        finished = false;
        successful = false;
        releaseUse();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        if (!mod.getItemStorage().hasItem(Items.CROSSBOW)) {
            if (!TaskCatalogue.taskExists(Items.CROSSBOW)) {
                mod.logWarning("Cannot complete Ol' Betsy: no crossbow resource task is available.");
                finished = true;
                return null;
            }
            releaseUse();
            setDebugState("Obtaining a crossbow");
            return TaskCatalogue.getItemTask(Items.CROSSBOW, 1);
        }
        if (!mod.getItemStorage().hasItem(Items.ARROW)
                && !mod.getItemStorage().hasItem(Items.SPECTRAL_ARROW)
                && !mod.getItemStorage().hasItem(Items.TIPPED_ARROW)) {
            if (!TaskCatalogue.taskExists(Items.ARROW)) {
                mod.logWarning("Cannot complete Ol' Betsy: no arrow resource task is available.");
                finished = true;
                return null;
            }
            releaseUse();
            setDebugState("Obtaining an arrow for the crossbow");
            return TaskCatalogue.getItemTask(Items.ARROW, 1);
        }

        if (!mod.getSlotHandler().forceEquipItem(Items.CROSSBOW)) {
            releaseUse();
            return null;
        }

        var crossbow = mod.getPlayer().getMainHandStack();
        if (!CrossbowItem.isCharged(crossbow)) {
            fired = false;
            mod.getInputControls().hold(Input.CLICK_RIGHT);
            if (!loading) {
                loading = true;
                loadTimer.reset();
            }
            if (loadTimer.elapsed()) {
                releaseUse();
                loading = false;
            }
            setDebugState("Loading the crossbow");
            return null;
        }

        if (!fired) {
            releaseUse();
            mod.getInputControls().tryPress(Input.CLICK_RIGHT);
            fired = true;
            fireTimeout.reset();
            setDebugState("Firing the loaded crossbow");
            return null;
        }

        if (!CrossbowItem.isCharged(mod.getPlayer().getMainHandStack())) {
            successful = true;
            finished = true;
            releaseUse();
            return null;
        }
        if (fireTimeout.elapsed()) {
            fired = false;
        }
        setDebugState("Waiting for the crossbow to fire");
        return null;
    }

    private void releaseUse() {
        AltoClef.getInstance().getInputControls().release(Input.CLICK_RIGHT);
        if (AltoClef.getInstance().getPlayer() != null) {
            AltoClef.getInstance().getPlayer().stopUsingItem();
        }
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
        return other instanceof OlBetsyTask;
    }

    @Override
    protected String toDebugString() {
        return "Loading and firing a crossbow";
    }
}
