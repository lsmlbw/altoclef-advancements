package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.multiversion.versionedfields.Blocks;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public final class LightAsARabbitTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/walk_on_powder_snow_with_leather_boots");

    private final Task findPowderSnowTask = new SearchChunkForBlockTask(Blocks.POWDER_SNOW);
    private final EquipArmorTask equipBootsTask = new EquipArmorTask(Items.LEATHER_BOOTS);
    private final TimerGame progressTimer = new TimerGame(2);
    private final Set<BlockPos> attemptedPowderSnow = new HashSet<>();
    private BlockPos target;
    private Task walkToPowderSnowTask;
    private boolean waitingForProgress;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        attemptedPowderSnow.clear();
        target = null;
        walkToPowderSnowTask = null;
        waitingForProgress = false;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Light as a Rabbit advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            setDebugState("Returning to the Overworld to find powder snow");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (!mod.getItemStorage().hasItem(Items.LEATHER_BOOTS)
                && !StorageHelper.isArmorEquipped(Items.LEATHER_BOOTS)) {
            if (!TaskCatalogue.taskExists(Items.LEATHER_BOOTS)) {
                return fail(mod, "the leather boots resource task is unavailable");
            }
            setDebugState("Obtaining leather boots for Light as a Rabbit");
            return equipBootsTask;
        }

        if (!StorageHelper.isArmorEquipped(Items.LEATHER_BOOTS)) {
            setDebugState("Equipping leather boots before approaching powder snow");
            return equipBootsTask;
        }

        if (waitingForProgress) {
            if (!progressTimer.elapsed()) {
                setDebugState("Waiting for the powder snow visit to register");
                return null;
            }
            waitingForProgress = false;
            if (target != null) attemptedPowderSnow.add(target.toImmutable());
            target = null;
            walkToPowderSnowTask = null;
        }

        if (target == null || !mod.getWorld().getBlockState(target).isOf(Blocks.POWDER_SNOW)) {
            target = mod.getBlockScanner().getNearestBlock(
                    pos -> !attemptedPowderSnow.contains(pos.toImmutable()),
                    Blocks.POWDER_SNOW).orElse(null);
            walkToPowderSnowTask = null;
        }

        if (target == null) {
            setDebugState("Searching for natural powder snow");
            return findPowderSnowTask;
        }

        if (walkToPowderSnowTask == null) {
            walkToPowderSnowTask = new GetToBlockTask(target.up());
        }
        if (!walkToPowderSnowTask.isFinished()) {
            setDebugState("Walking on powder snow while wearing leather boots");
            return walkToPowderSnowTask;
        }

        waitingForProgress = true;
        progressTimer.reset();
        setDebugState("Waiting for Light as a Rabbit completion");
        return null;
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

    private Task fail(AltoClef mod, String reason) {
        mod.logWarning("Cannot complete Light as a Rabbit: " + reason + ".");
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
        return other instanceof LightAsARabbitTask;
    }

    @Override
    protected String toDebugString() {
        return "Walk on powder snow wearing leather boots for Light as a Rabbit";
    }
}
