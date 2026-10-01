package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.slot.MoveItemToSlotFromInventoryTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.CursorSlot;
import adris.altoclef.util.slots.EnchantingTableSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Comparator;

public final class TwoBirdsOneArrowTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/two_birds_one_arrow");
    private static final int CROSSBOW_BATCH_SIZE = 6;
    private static final int LAPIS_PER_ENCHANT = 3;
    private static final double WEAK_PHANTOM_HEALTH = 7.0;
    private static final double MELEE_REACH = 3.5;
    private static final double MAX_SHOT_RANGE = 30;

    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final Task experienceTask = new KillEntitiesTask(Monster.class);
    private final TimerGame loadTimer = new TimerGame(1.5);
    private final TimerGame hitTimer = new TimerGame(5);
    private PhantomEntity weakeningTarget;
    private PhantomEntity nearTarget;
    private PhantomEntity farTarget;
    private Task openTableTask;
    private boolean loading;
    private boolean waitingForHit;
    private boolean waitingForEnchant;
    private int enchantedCrossbows;
    private int targetCrossbowCount;
    private long startWorldTime;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        weakeningTarget = null;
        nearTarget = null;
        farTarget = null;
        openTableTask = null;
        loading = false;
        waitingForHit = false;
        waitingForEnchant = false;
        enchantedCrossbows = 0;
        targetCrossbowCount = CROSSBOW_BATCH_SIZE;
        startWorldTime = WorldHelper.getCurrentDimension() == Dimension.OVERWORLD
                ? AltoClef.getInstance().getWorld().getTime()
                : -1;
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
            setDebugState("Waiting for Two Birds, One Arrow advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            releaseUse();
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            releaseUse();
            setDebugState("Returning to the Overworld to hunt phantoms");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (!hasPiercingCrossbow(mod)) {
            releaseUse();
            if (MinecraftClient.getInstance().player.currentScreenHandler instanceof EnchantmentScreenHandler handler) {
                return enchantCrossbow(mod, handler);
            }
            if (enchantedCrossbows >= targetCrossbowCount) {
                targetCrossbowCount += CROSSBOW_BATCH_SIZE;
            }
            if (mod.getItemStorage().getItemCountInventoryOnly(Items.CROSSBOW) < targetCrossbowCount) {
                if (!TaskCatalogue.taskExists(Items.CROSSBOW)) {
                    return fail(mod, "no crossbow resource task is available");
                }
                if (mod.getItemStorage().getItemCountInventoryOnly(Items.CROSSBOW) == 0) {
                    setDebugState("Obtaining crossbows to enchant with Piercing");
                } else {
                    setDebugState("Obtaining spare crossbows for repeated Piercing enchantment attempts");
                }
                return TaskCatalogue.getItemTask(Items.CROSSBOW, targetCrossbowCount);
            }
            if (waitingForEnchant) {
                return waitForEnchantment(mod);
            }
            return prepareEnchanting(mod);
        }

        if (mod.getItemStorage().getItemCountInventoryOnly(Items.ARROW) < 8) {
            return getResource(mod, Items.ARROW, 16, "Obtaining arrows for repeated attempts");
        }

        if (waitingForHit) {
            if (!hitTimer.elapsed()) {
                setDebugState("Waiting for the piercing arrow to hit both phantoms");
                return null;
            }
            waitingForHit = false;
            nearTarget = null;
            farTarget = null;
            loading = false;
        }

        if (worldHasWaitedLongEnough(mod)) {
            List<PhantomEntity> phantoms = findPhantoms(mod);
            if (weakeningTarget != null && (!weakeningTarget.isAlive()
                    || weakeningTarget.getHealth() <= WEAK_PHANTOM_HEALTH)) {
                weakeningTarget = null;
            }
            if (weakeningTarget == null) weakeningTarget = findWeakeningTarget(phantoms);
            if (weakeningTarget != null) {
                return weakenPhantom(mod, weakeningTarget);
            }
            PhantomPair pair = findAlignedPair(mod, phantoms);
            if (pair != null) {
                nearTarget = pair.near;
                farTarget = pair.far;
                return shootThroughPair(mod);
            }
        }

        setDebugState(worldHasWaitedLongEnough(mod)
                ? "Waiting for two weakened phantoms to line up for one piercing shot"
                : "Waiting three in-game days for phantoms to spawn without sleeping");
        return exploreTask;
    }

    private Task prepareEnchanting(AltoClef mod) {
        if (enchantedCrossbows >= targetCrossbowCount) targetCrossbowCount += CROSSBOW_BATCH_SIZE;
        if (findUnenchantedCrossbow(mod) == null) {
            if (mod.getItemStorage().getItemCountInventoryOnly(Items.CROSSBOW) >= targetCrossbowCount) {
                targetCrossbowCount += CROSSBOW_BATCH_SIZE;
            }
            return getResource(mod, Items.CROSSBOW, targetCrossbowCount,
                    "Obtaining more crossbows for Piercing enchantment attempts");
        }
        int lapisNeeded = Math.max(LAPIS_PER_ENCHANT,
                (targetCrossbowCount - enchantedCrossbows) * LAPIS_PER_ENCHANT);
        if (mod.getItemStorage().getItemCountInventoryOnly(Items.LAPIS_LAZULI)
                < lapisNeeded) {
            return getResource(mod, Items.LAPIS_LAZULI, lapisNeeded,
                    "Obtaining lapis lazuli for crossbow enchanting");
        }
        if (!mod.getItemStorage().hasItem(Items.ENCHANTING_TABLE)) {
            return getResource(mod, Items.ENCHANTING_TABLE, 1,
                    "Obtaining an enchanting table");
        }
        if (mod.getPlayer().experienceLevel < 3) {
            setDebugState("Gaining experience for a crossbow enchantment");
            return experienceTask;
        }

        var table = mod.getBlockScanner().getNearestBlock(Blocks.ENCHANTING_TABLE);
        if (table.isEmpty()) {
            setDebugState("Placing an enchanting table");
            return new PlaceBlockNearbyTask(Blocks.ENCHANTING_TABLE);
        }
        if (openTableTask == null) openTableTask = new InteractWithBlockTask(table.get());
        setDebugState("Opening an enchanting table to seek Piercing");
        return openTableTask;
    }

    private Task enchantCrossbow(AltoClef mod, EnchantmentScreenHandler handler) {
        ItemStack input = StorageHelper.getItemStackInSlot(EnchantingTableSlot.ITEM);
        if (!input.isEmpty() && input.isOf(Items.CROSSBOW) && input.hasEnchantments()) {
            mod.getSlotHandler().clickSlot(EnchantingTableSlot.ITEM, 0, SlotActionType.QUICK_MOVE);
            waitingForEnchant = true;
            setDebugState("Checking whether the crossbow received Piercing");
            return null;
        }

        if (input.isEmpty()) {
            ItemStack cursor = StorageHelper.getItemStackInSlot(CursorSlot.SLOT);
            if (cursor.isOf(Items.CROSSBOW) && !cursor.hasEnchantments()) {
                mod.getSlotHandler().clickSlot(EnchantingTableSlot.ITEM, 0, SlotActionType.PICKUP);
                return null;
            }
            var source = findUnenchantedCrossbow(mod);
            if (source == null) {
                closeEnchantingScreen();
                openTableTask = null;
                return prepareEnchanting(mod);
            }
            mod.getSlotHandler().clickSlot(source, 0, SlotActionType.PICKUP);
            setDebugState("Putting an unenchanted crossbow in the enchanting table");
            return null;
        }

        ItemStack lapis = StorageHelper.getItemStackInSlot(EnchantingTableSlot.LAPIS);
        if (lapis.isEmpty() || lapis.getCount() < LAPIS_PER_ENCHANT) {
            if (mod.getItemStorage().getItemCountInventoryOnly(Items.LAPIS_LAZULI)
                    < LAPIS_PER_ENCHANT) {
                closeEnchantingScreen();
                openTableTask = null;
                return getResource(mod, Items.LAPIS_LAZULI,
                        Math.max(LAPIS_PER_ENCHANT,
                                (targetCrossbowCount - enchantedCrossbows) * LAPIS_PER_ENCHANT),
                        "Obtaining more lapis lazuli for crossbow enchanting");
            }
            return new MoveItemToSlotFromInventoryTask(
                    new ItemTarget(Items.LAPIS_LAZULI, LAPIS_PER_ENCHANT),
                    EnchantingTableSlot.LAPIS);
        }

        int option = bestAvailableEnchantOption(mod, handler);
        if (option < 0) {
            if (mod.getPlayer().experienceLevel < 3) {
                closeEnchantingScreen();
                openTableTask = null;
                setDebugState("Gaining experience before enchanting the crossbow");
                return experienceTask;
            }
            setDebugState("Waiting for an available crossbow enchantment");
            return null;
        }

        MinecraftClient.getInstance().interactionManager.clickButton(handler.syncId, option);
        enchantedCrossbows++;
        waitingForEnchant = true;
        setDebugState("Trying an enchanting-table offer for Piercing");
        return null;
    }

    private Task waitForEnchantment(AltoClef mod) {
        if (hasPiercingCrossbow(mod)) {
            waitingForEnchant = false;
            return null;
        }
        if (findUnenchantedCrossbow(mod) != null) {
            waitingForEnchant = false;
            setDebugState("The crossbow lacks Piercing; trying another crossbow");
            return null;
        }
        setDebugState("Waiting for another unenchanted crossbow");
        return prepareEnchanting(mod);
    }

    private int bestAvailableEnchantOption(AltoClef mod, EnchantmentScreenHandler handler) {
        int best = -1;
        for (int option = 0; option < handler.enchantmentPower.length; option++) {
            int levelCost = option + 1;
            if (handler.enchantmentPower[option] > 0
                    && mod.getPlayer().experienceLevel >= levelCost
                    && handler.getLapisCount() >= levelCost) {
                best = option;
            }
        }
        return best;
    }

    private Task weakenPhantom(AltoClef mod, PhantomEntity phantom) {
        releaseUse();
        if (phantom.squaredDistanceTo(mod.getPlayer()) > MELEE_REACH * MELEE_REACH) {
            setDebugState("Approaching a phantom to weaken it before the piercing shot");
            return new GetToEntityTask(phantom, MELEE_REACH - 0.5);
        }
        if (!mod.getItemStorage().hasItem(Items.STONE_SWORD)) {
            if (!TaskCatalogue.taskExists(Items.STONE_SWORD)) {
                return fail(mod, "no stone sword resource task is available for controlled phantom weakening");
            }
            setDebugState("Obtaining a stone sword to weaken phantoms without killing them");
            return TaskCatalogue.getItemTask(Items.STONE_SWORD, 1);
        }
        if (mod.getItemStorage().hasItem(Items.STONE_SWORD)
                && !mod.getSlotHandler().forceEquipItem(Items.STONE_SWORD)) {
            return null;
        }
        LookHelper.lookAt(mod, phantom.getBoundingBox().getCenter());
        if (mod.getPlayer().getAttackCooldownProgress(0) >= 1
                && mod.getPlayer().isOnGround()) {
            float healthBeforeHit = phantom.getHealth();
            mod.getControllerExtras().attack(phantom);
            if (phantom.getHealth() < healthBeforeHit) {
                setDebugState("Weakening a phantom for the piercing shot");
            }
        }
        return null;
    }

    private Task shootThroughPair(AltoClef mod) {
        Vec3d aimPoint = farTarget.getBoundingBox().getCenter();
        var rotation = LookHelper.getLookRotation(mod, aimPoint);
        LookHelper.lookAt(mod, aimPoint, false);
        if (!LookHelper.isLookingAt(mod, rotation)) {
            releaseUse();
            setDebugState("Aiming through both aligned phantoms");
            return null;
        }
        if (!(MinecraftClient.getInstance().crosshairTarget instanceof EntityHitResult hit)
                || (hit.getEntity() != nearTarget && hit.getEntity() != farTarget)) {
            releaseUse();
            setDebugState("Aligning the shot so the arrow passes through both phantoms");
            return null;
        }
        if (!hasPiercing(mod.getPlayer().getMainHandStack())) {
            Slot piercingCrossbow = findPiercingCrossbow(mod);
            if (piercingCrossbow == null) return null;
            mod.getSlotHandler().forceEquipSlot(piercingCrossbow);
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
            setDebugState("Loading the Piercing crossbow while aligned with both phantoms");
            return null;
        }

        releaseUse();
        mod.getInputControls().tryPress(Input.CLICK_RIGHT);
        waitingForHit = true;
        hitTimer.reset();
        setDebugState("Firing one piercing arrow through both weakened phantoms");
        return null;
    }

    private PhantomPair findAlignedPair(AltoClef mod, List<PhantomEntity> phantoms) {
        PhantomPair best = null;
        double smallestAngle = Double.POSITIVE_INFINITY;
        Vec3d eyePos = mod.getPlayer().getEyePos();
        for (int i = 0; i < phantoms.size(); i++) {
            PhantomEntity first = phantoms.get(i);
            if (first.getHealth() > WEAK_PHANTOM_HEALTH) continue;
            Vec3d firstVector = first.getBoundingBox().getCenter().subtract(eyePos);
            double firstDistance = firstVector.length();
            if (firstDistance > MAX_SHOT_RANGE || firstDistance < 2) continue;
            for (int j = i + 1; j < phantoms.size(); j++) {
                PhantomEntity second = phantoms.get(j);
                if (second.getHealth() > WEAK_PHANTOM_HEALTH) continue;
                Vec3d secondVector = second.getBoundingBox().getCenter().subtract(eyePos);
                double secondDistance = secondVector.length();
                if (secondDistance > MAX_SHOT_RANGE || secondDistance < 2) continue;
                PhantomEntity near = firstDistance <= secondDistance ? first : second;
                PhantomEntity far = near == first ? second : first;
                Vec3d nearVector = near.getBoundingBox().getCenter().subtract(eyePos);
                Vec3d farVector = far.getBoundingBox().getCenter().subtract(eyePos);
                Vec3d nearDirection = nearVector.normalize();
                double along = farVector.dotProduct(nearDirection);
                double nearDistance = near.getBoundingBox().getCenter().distanceTo(eyePos);
                if (along <= nearDistance) continue;
                double missDistance = farVector.subtract(nearDirection.multiply(along)).length();
                if (missDistance > Math.max(0.15, far.getWidth() * 0.45)) continue;
                double cosine = Math.clamp(
                        nearDirection.dotProduct(farVector.normalize()), -1.0, 1.0);
                double angle = Math.acos(cosine);
                if (angle < smallestAngle) {
                    smallestAngle = angle;
                    best = new PhantomPair(near, far);
                }
            }
        }
        return best;
    }

    private List<PhantomEntity> findPhantoms(AltoClef mod) {
        return mod.getEntityTracker().getTrackedEntities(PhantomEntity.class).stream()
                .filter(PhantomEntity::isAlive)
                .sorted(Comparator.comparingDouble(phantom -> phantom.squaredDistanceTo(mod.getPlayer())))
                .toList();
    }

    private PhantomEntity findWeakeningTarget(List<PhantomEntity> phantoms) {
        return phantoms.stream()
                .filter(phantom -> phantom.getHealth() > WEAK_PHANTOM_HEALTH)
                .findFirst()
                .orElse(null);
    }

    private boolean hasPiercingCrossbow(AltoClef mod) {
        return mod.getItemStorage().getItemStacksPlayerInventory(false).stream()
                .filter(stack -> stack.isOf(Items.CROSSBOW))
                .anyMatch(this::hasPiercing);
    }

    private boolean hasPiercing(ItemStack stack) {
        if (!stack.isOf(Items.CROSSBOW)) return false;
        var piercing = AltoClef.getInstance().getWorld().getRegistryManager()
                .get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.PIERCING).orElseThrow();
        return EnchantmentHelper.getLevel(piercing, stack) > 0;
    }

    private Slot findPiercingCrossbow(AltoClef mod) {
        return mod.getItemStorage().getSlotsWithItemPlayerInventory(false, Items.CROSSBOW).stream()
                .filter(slot -> hasPiercing(StorageHelper.getItemStackInSlot(slot)))
                .findFirst()
                .orElse(null);
    }

    private adris.altoclef.util.slots.Slot findUnenchantedCrossbow(AltoClef mod) {
        return mod.getItemStorage().getSlotsWithItemPlayerInventory(false, Items.CROSSBOW).stream()
                .filter(slot -> {
                    ItemStack stack = StorageHelper.getItemStackInSlot(slot);
                    return !stack.isEmpty() && stack.isOf(Items.CROSSBOW) && !stack.hasEnchantments();
                })
                .findFirst()
                .orElse(null);
    }

    private Task getResource(AltoClef mod, net.minecraft.item.Item item, int count, String state) {
        if (!TaskCatalogue.taskExists(item)) {
            return fail(mod, "no resource task is available for " + item.getName().getString());
        }
        setDebugState(state);
        return TaskCatalogue.getItemTask(item, count);
    }

    private boolean worldHasWaitedLongEnough(AltoClef mod) {
        if (startWorldTime < 0) {
            startWorldTime = mod.getWorld().getTime();
            return false;
        }
        return mod.getWorld().getTime() - startWorldTime >= 72_000;
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
        mod.logWarning("Cannot complete Two Birds, One Arrow: " + reason + ".");
        finished = true;
        releaseUse();
        closeEnchantingScreen();
        return null;
    }

    private void releaseUse() {
        AltoClef mod = AltoClef.getInstance();
        mod.getInputControls().release(Input.CLICK_RIGHT);
        if (mod.getPlayer() != null) mod.getPlayer().stopUsingItem();
        loading = false;
    }

    private void closeEnchantingScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null
                && client.player.currentScreenHandler instanceof EnchantmentScreenHandler) {
            StorageHelper.closeScreen();
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
        closeEnchantingScreen();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof TwoBirdsOneArrowTask;
    }

    @Override
    protected String toDebugString() {
        return "Killing two phantoms with one Piercing crossbow arrow";
    }

    private record PhantomPair(PhantomEntity near, PhantomEntity far) {
    }
}
