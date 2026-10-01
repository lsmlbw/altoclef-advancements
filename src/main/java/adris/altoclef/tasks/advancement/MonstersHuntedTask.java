package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MonstersHuntedTask extends Task {
    private static final Identifier ADVANCEMENT_ID = Identifier.of("minecraft", "adventure/kill_all_mobs");
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final TimerGame advancementUpdateTimer = new TimerGame(2);
    private LivingEntity target;
    private String targetCriterion;
    private Task killTask;
    private boolean waitingForProgress;
    private boolean unsupportedCriteriaReported;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        target = null;
        targetCriterion = null;
        killTask = null;
        waitingForProgress = false;
        unsupportedCriteriaReported = false;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        AdvancementProgress advancementProgress = getAdvancementProgress();
        if (advancementProgress == null) {
            setDebugState("Waiting for Monsters Hunted advancement progress from the server");
            return null;
        }
        if (advancementProgress.isDone()) {
            successful = true;
            finished = true;
            return null;
        }

        if (waitingForProgress) {
            if (!advancementUpdateTimer.elapsed()) {
                setDebugState("Waiting for the mob kill to register in advancement progress");
                return null;
            }
            if (!mod.getPlayer().isAlive()) return null;
            waitingForProgress = false;
            target = null;
            targetCriterion = null;
            killTask = null;
        }

        List<String> missingCriteria = new ArrayList<>();
        for (String criterion : advancementProgress.getUnobtainedCriteria()) {
            missingCriteria.add(criterion);
        }
        if (missingCriteria.isEmpty()) {
            setDebugState("Waiting for Monsters Hunted progress to update");
            return null;
        }

        if (target != null && targetCriterion != null
                && !missingCriteria.contains(targetCriterion)) {
            target = null;
            targetCriterion = null;
            killTask = null;
        }

        if (target == null) {
            for (String criterion : missingCriteria) {
                Optional<EntityType<?>> type = findEntityType(criterion);
                if (type.isEmpty()) continue;
                LivingEntity nearby = findNearbyMob(mod, type.get());
                if (nearby != null) {
                    target = nearby;
                    targetCriterion = criterion;
                    break;
                }
            }
        }

        if (target != null) {
            if (killTask == null) killTask = new KillEntityTask(target);
            setDebugState("Hunting " + target.getType().getName().getString()
                    + " for Monsters Hunted");
            if (target.getHealth() <= 0 || !target.isAlive()) {
                waitingForProgress = true;
                advancementUpdateTimer.reset();
                setDebugState("Waiting for the " + targetCriterion + " kill to register");
                return null;
            }
            return killTask;
        }

        String criterion = firstResolvableCriterion(missingCriteria);
        if (criterion == null) {
            if (!unsupportedCriteriaReported) {
                mod.logWarning("Cannot match Monsters Hunted criteria to entity types: "
                        + String.join(", ", missingCriteria) + ".");
                unsupportedCriteriaReported = true;
            }
            setDebugState("Monsters Hunted contains criteria that cannot be matched to entity types");
            return null;
        }

        Dimension desiredDimension = dimensionFor(criterion);
        if (desiredDimension != null && desiredDimension != adris.altoclef.util.helpers.WorldHelper.getCurrentDimension()) {
            setDebugState("Traveling to the " + desiredDimension + " to hunt " + criterion);
            return new DefaultGoToDimensionTask(desiredDimension);
        }

        setDebugState("Exploring for " + criterion + " (" + missingCriteria.size() + " criteria remaining)");
        return exploreTask;
    }

    private AdvancementProgress getAdvancementProgress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return null;
        var advancementManager = client.getNetworkHandler().getAdvancementHandler();
        PlacedAdvancement entry = advancementManager.getManager().get(ADVANCEMENT_ID);
        if (entry == null) return null;
        return ((ClientAdvancementManagerAccessor) advancementManager)
                .altoclef$getAdvancementProgresses().get(entry);
    }

    private Optional<EntityType<?>> findEntityType(String criterion) {
        Identifier id = Identifier.tryParse(criterion);
        if (id == null) id = Identifier.of("minecraft", criterion);
        return Registries.ENTITY_TYPE.getOrEmpty(id);
    }

    private String firstResolvableCriterion(List<String> criteria) {
        for (String criterion : criteria) {
            if (findEntityType(criterion).isPresent()) return criterion;
        }
        return null;
    }

    private LivingEntity findNearbyMob(AltoClef mod, EntityType<?> type) {
        LivingEntity closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Entity entity : mod.getWorld().getEntities()) {
            if (!(entity instanceof LivingEntity living)
                    || !living.isAlive()
                    || living.getType() != type) {
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

    private Dimension dimensionFor(String criterion) {
        Identifier id = Identifier.tryParse(criterion);
        String path = id == null ? criterion : id.getPath();
        if (List.of("blaze", "ghast", "hoglin", "magma_cube", "piglin", "piglin_brute",
                "wither_skeleton", "zoglin", "zombified_piglin").contains(path)) {
            return Dimension.NETHER;
        }
        if (List.of("ender_dragon", "shulker").contains(path)) return Dimension.END;
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
        return other instanceof MonstersHuntedTask;
    }

    @Override
    protected String toDebugString() {
        return "Killing every hostile mob for Monsters Hunted";
    }
}
