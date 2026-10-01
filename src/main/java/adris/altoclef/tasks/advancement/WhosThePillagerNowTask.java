package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;

public final class WhosThePillagerNowTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/whos_the_pillager_now");
    private static final double CROSSBOW_RANGE = 28;
    private static final float WEAKEN_TO_HEALTH = 3.0f;

    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final TimerGame loadTimer = new TimerGame(1.5);
    private final TimerGame hitTimer = new TimerGame(5);
    private PillagerEntity target;
    private float healthBeforeShot;
    private boolean loading;
    private boolean waitingForHit;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        target = null;
        healthBeforeShot = 0;
        loading = false;
        waitingForHit = false;
        finished = false;
        successful = false;
        releaseUse();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Who's the Pillager Now? advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            releaseUse();
            return null;
        }

        if (waitingForHit) {
            if (target != null && !target.isAlive()) {
                waitingForHit = false;
                target = null;
            } else if (target != null && target.getHealth() < healthBeforeShot) {
                waitingForHit = false;
                target = null;
            } else if (!hitTimer.elapsed()) {
                setDebugState("Waiting for the crossbow shot to hit the pillager");
                return null;
            } else {
                waitingForHit = false;
                target = null;
                loading = false;
            }
        }

        if (!mod.getItemStorage().hasItem(Items.CROSSBOW)) {
            releaseUse();
            return getResource(mod, Items.CROSSBOW, "Obtaining a crossbow");
        }
        if (!hasArrow(mod)) {
            releaseUse();
            return getResource(mod, Items.ARROW, "Obtaining arrows to defeat a pillager");
        }

        if (target == null || !target.isAlive()) target = findPillager(mod);
        if (target == null) {
            releaseUse();
            setDebugState("Searching for a pillager");
            return exploreTask;
        }

        if (target.getHealth() > WEAKEN_TO_HEALTH) {
            return weakenTarget(mod);
        }

        double distance = mod.getPlayer().squaredDistanceTo(target);
        if (distance > CROSSBOW_RANGE * CROSSBOW_RANGE) {
            releaseUse();
            setDebugState("Approaching a weakened pillager for a crossbow shot");
            return new GetToEntityTask(target, CROSSBOW_RANGE - 2);
        }

        if (!mod.getSlotHandler().forceEquipItem(Items.CROSSBOW)) {
            releaseUse();
            return null;
        }

        var aimPoint = target.getBoundingBox().getCenter();
        var rotation = LookHelper.getLookRotation(mod, aimPoint);
        LookHelper.lookAt(mod, aimPoint, false);
        if (!LookHelper.isLookingAt(mod, rotation)
                || !(MinecraftClient.getInstance().crosshairTarget instanceof EntityHitResult hit)
                || hit.getEntity() != target) {
            releaseUse();
            setDebugState("Aiming the crossbow at the weakened pillager");
            return null;
        }

        if (!CrossbowItem.isCharged(mod.getPlayer().getMainHandStack())) {
            mod.getInputControls().hold(Input.CLICK_RIGHT);
            if (!loading) {
                loading = true;
                loadTimer.reset();
            }
            if (loadTimer.elapsed()) {
                releaseUse();
                loading = false;
            }
            setDebugState("Loading the crossbow while aiming at the pillager");
            return null;
        }

        healthBeforeShot = target.getHealth();
        releaseUse();
        mod.getInputControls().tryPress(Input.CLICK_RIGHT);
        waitingForHit = true;
        hitTimer.reset();
        setDebugState("Firing the crossbow to finish the pillager");
        return null;
    }

    private Task weakenTarget(AltoClef mod) {
        releaseUse();
        if (mod.getPlayer().squaredDistanceTo(target) > 3.0 * 3.0) {
            setDebugState("Approaching the pillager to weaken it before the crossbow shot");
            return new GetToEntityTask(target, 2.5);
        }

        if (!mod.getSlotHandler().forceDeequip(stack -> !stack.isEmpty())) return null;
        LookHelper.lookAt(mod, target.getBoundingBox().getCenter());
        if (mod.getPlayer().getAttackCooldownProgress(0) >= 1
                && mod.getPlayer().isOnGround()) {
            mod.getControllerExtras().attack(target);
        }
        setDebugState("Weakening the pillager with unarmed attacks");
        return null;
    }

    private PillagerEntity findPillager(AltoClef mod) {
        return mod.getEntityTracker().getTrackedEntities(PillagerEntity.class).stream()
                .filter(PillagerEntity::isAlive)
                .min((first, second) -> Double.compare(
                        first.squaredDistanceTo(mod.getPlayer()),
                        second.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
    }

    private boolean hasArrow(AltoClef mod) {
        return mod.getItemStorage().hasItem(Items.ARROW)
                || mod.getItemStorage().hasItem(Items.SPECTRAL_ARROW)
                || mod.getItemStorage().hasItem(Items.TIPPED_ARROW);
    }

    private Task getResource(AltoClef mod, net.minecraft.item.Item item, String state) {
        if (!TaskCatalogue.taskExists(item)) {
            mod.logWarning("Cannot complete Who's the Pillager Now?: no resource task is available for "
                    + item.getName().getString() + ".");
            finished = true;
            return null;
        }
        setDebugState(state);
        return TaskCatalogue.getItemTask(item, item == Items.ARROW ? 3 : 1);
    }

    private AdvancementProgress getAdvancementProgress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return null;
        var manager = client.getNetworkHandler().getAdvancementHandler();
        PlacedAdvancement entry = manager.getManager().get(ADVANCEMENT_ID);
        if (entry == null) return null;
        return ((ClientAdvancementManagerAccessor) manager)
                .altoclef$getAdvancementProgresses().get(entry);
    }

    private void releaseUse() {
        AltoClef mod = AltoClef.getInstance();
        mod.getInputControls().release(Input.CLICK_RIGHT);
        if (mod.getPlayer() != null) mod.getPlayer().stopUsingItem();
        loading = false;
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
        return other instanceof WhosThePillagerNowTask;
    }

    @Override
    protected String toDebugString() {
        return "Killing a pillager with a crossbow";
    }
}
