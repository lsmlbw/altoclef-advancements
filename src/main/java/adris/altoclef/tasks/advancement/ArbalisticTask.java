package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
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
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ArbalisticTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/arbalistic");
    private static final int CROSSBOW_BATCH_SIZE = 8;
    private static final int LAPIS_PER_ENCHANT = 3;
    private static final int REQUIRED_MOBS = 5;
    private static final double MAX_SHOT_RANGE = 28;
    private static final double WEAKENED_HEALTH = 5;
    private static final double MELEE_REACH = 3.2;

    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final Task experienceTask = new KillEntitiesTask(net.minecraft.entity.mob.Monster.class);
    private final TimerGame loadTimer = new TimerGame(1.5);
    private final TimerGame hitTimer = new TimerGame(5);
    private final TimerGame lureTimer = new TimerGame(12);
    private final TimerGame armorStandTimer = new TimerGame(3);
    private static final MobKind[] LURE_KINDS = {
            new MobKind(CowEntity.class, Items.WHEAT),
            new MobKind(SheepEntity.class, Items.WHEAT),
            new MobKind(PigEntity.class, Items.CARROT),
            new MobKind(ChickenEntity.class, Items.WHEAT_SEEDS)
    };
    private LivingEntity weakeningTarget;
    private List<LivingEntity> targets = List.of();
    private Vec3d targetAimPoint;
    private LivingEntity lureTarget;
    private BlockPos pitPosition;
    private BlockPos stagingPosition;
    private Task openTableTask;
    private int crossbowBatch = CROSSBOW_BATCH_SIZE;
    private int enchantmentsAttempted;
    private int lureKindIndex;
    private boolean loading;
    private boolean waitingForHit;
    private boolean waitingForLuredMob;
    private boolean armorStandPlaced;
    private boolean placingArmorStand;
    private boolean mobLineupReady;
    private boolean returningToPit;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        weakeningTarget = null;
        targets = List.of();
        targetAimPoint = null;
        lureTarget = null;
        pitPosition = null;
        stagingPosition = null;
        openTableTask = null;
        crossbowBatch = CROSSBOW_BATCH_SIZE;
        enchantmentsAttempted = 0;
        lureKindIndex = 0;
        loading = false;
        waitingForHit = false;
        waitingForLuredMob = false;
        armorStandPlaced = false;
        placingArmorStand = false;
        mobLineupReady = false;
        returningToPit = false;
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
            setDebugState("Waiting for Arbalistic advancement progress from the server");
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
            setDebugState("Returning to the Overworld to gather mobs for Arbalistic");
            return new adris.altoclef.tasks.movement.DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (!hasPiercingFourCrossbow(mod)) {
            releaseUse();
            if (MinecraftClient.getInstance().player.currentScreenHandler
                    instanceof EnchantmentScreenHandler handler) {
                return enchantCrossbow(mod, handler);
            }
            if (enchantmentsAttempted >= crossbowBatch) crossbowBatch += CROSSBOW_BATCH_SIZE;
            if (mod.getItemStorage().getItemCountInventoryOnly(Items.CROSSBOW) < crossbowBatch) {
                return getResource(mod, Items.CROSSBOW, crossbowBatch,
                        "Obtaining crossbows for Piercing IV attempts");
            }
            return prepareEnchanting(mod);
        }

        if (!hasArrow(mod)) {
            releaseUse();
            return getResource(mod, Items.ARROW, 16, "Obtaining arrows for Arbalistic");
        }

        if (!mobLineupReady) {
            Task lineupTask = prepareMobLineup(mod);
            if (!mobLineupReady) return lineupTask;
        }

        if (waitingForHit) {
            if (!hitTimer.elapsed()) {
                setDebugState("Waiting for one crossbow shot to hit all five unique mobs");
                return null;
            }
            waitingForHit = false;
            targets = List.of();
            targetAimPoint = null;
            loading = false;
        }

        if (weakeningTarget != null
                && (!weakeningTarget.isAlive() || weakeningTarget.getHealth() <= WEAKENED_HEALTH)) {
            weakeningTarget = null;
        }

        if (weakeningTarget == null) {
            List<LivingEntity> candidates = findNearbyEntities(mod);
            weakeningTarget = candidates.stream()
                    .filter(entity -> entity.getHealth() > WEAKENED_HEALTH)
                    .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mod.getPlayer())))
                    .orElse(null);
            if (weakeningTarget != null) return weakenEntity(mod, weakeningTarget);

            targets = findAlignedUniqueEntities(mod, candidates);
        }

        if (targets.size() >= REQUIRED_MOBS) return shootThroughTargets(mod);

        releaseUse();
        setDebugState("Searching for five unique mobs aligned for one Piercing IV shot");
        return exploreTask;
    }

    private Task prepareEnchanting(AltoClef mod) {
        if (findUnenchantedCrossbow(mod) == null) {
            if (mod.getItemStorage().getItemCountInventoryOnly(Items.CROSSBOW) >= crossbowBatch) {
                crossbowBatch += CROSSBOW_BATCH_SIZE;
            }
            return getResource(mod, Items.CROSSBOW, crossbowBatch,
                    "Obtaining more crossbows for Piercing IV attempts");
        }
        int lapisNeeded = Math.max(LAPIS_PER_ENCHANT,
                (crossbowBatch - enchantmentsAttempted) * LAPIS_PER_ENCHANT);
        if (mod.getItemStorage().getItemCountInventoryOnly(Items.LAPIS_LAZULI) < lapisNeeded) {
            return getResource(mod, Items.LAPIS_LAZULI, lapisNeeded,
                    "Obtaining lapis lazuli for Piercing IV attempts");
        }
        if (!mod.getItemStorage().hasItem(Items.ENCHANTING_TABLE)) {
            return getResource(mod, Items.ENCHANTING_TABLE, 1, "Obtaining an enchanting table");
        }
        if (mod.getPlayer().experienceLevel < 3) {
            setDebugState("Gaining experience for crossbow enchanting");
            return experienceTask;
        }
        var table = mod.getBlockScanner().getNearestBlock(Blocks.ENCHANTING_TABLE);
        if (table.isEmpty()) {
            setDebugState("Placing an enchanting table");
            return new PlaceBlockNearbyTask(Blocks.ENCHANTING_TABLE);
        }
        if (openTableTask == null) openTableTask = new InteractWithBlockTask(table.get());
        setDebugState("Opening an enchanting table to seek Piercing IV");
        return openTableTask;
    }

    private Task prepareMobLineup(AltoClef mod) {
        if (pitPosition == null) {
            pitPosition = findPitPosition(mod);
            if (pitPosition == null) {
                setDebugState("Finding clear, level ground for a five-mob trap");
                return exploreTask;
            }
            stagingPosition = findStagingPosition(mod, pitPosition);
            if (stagingPosition == null) {
                pitPosition = null;
                return null;
            }
        }
        if (returningToPit) {
            if (mod.getPlayer().getBlockPos().getSquaredDistance(stagingPosition) > 4) {
                setDebugState("Returning to the mob trap after gathering supplies");
                return new GetToBlockTask(stagingPosition);
            }
            returningToPit = false;
        }

        if (!mod.getWorld().getBlockState(pitPosition).isAir()) {
            setDebugState("Digging the first block of the mob trap");
            return new DestroyBlockTask(pitPosition);
        }
        if (!mod.getWorld().getBlockState(pitPosition.down()).isAir()) {
            setDebugState("Digging the mob trap two blocks deep");
            return new DestroyBlockTask(pitPosition.down());
        }

        if (!armorStandPlaced) {
            if (findArmorStandInPit(mod) != null) {
                armorStandPlaced = true;
                placingArmorStand = false;
            } else if (placingArmorStand) {
                if (!armorStandTimer.elapsed()) {
                    setDebugState("Waiting for the armor stand to settle in the trap");
                    return null;
                }
                placingArmorStand = false;
            } else if (!mod.getItemStorage().hasItem(Items.ARMOR_STAND)) {
                returningToPit = true;
                return getResource(mod, Items.ARMOR_STAND, 1,
                        "Obtaining an armor stand as the fifth unique mob");
            } else {
                if (!mod.getSlotHandler().forceEquipItem(Items.ARMOR_STAND)) return null;
                BlockPos floor = pitPosition.down(2);
                Vec3d hitPosition = Vec3d.ofCenter(floor).add(0, 0.5, 0);
                var rotation = LookHelper.getLookRotation(mod, hitPosition);
                LookHelper.lookAt(mod, hitPosition, false);
                if (!LookHelper.isLookingAt(mod, rotation)) {
                    setDebugState("Aiming at the bottom of the mob trap");
                    return null;
                }
                BlockHitResult hit = new BlockHitResult(hitPosition, Direction.UP, floor, false);
                ActionResult result = MinecraftClient.getInstance().interactionManager
                        .interactBlock(mod.getPlayer(), Hand.MAIN_HAND, hit);
                if (result.shouldSwingHand()) mod.getPlayer().swingHand(Hand.MAIN_HAND);
                placingArmorStand = true;
                armorStandTimer.reset();
                setDebugState("Placing an armor stand in the mob trap");
                return null;
            }
        }

        if (lureKindIndex >= LURE_KINDS.length) {
            mobLineupReady = true;
            lureTarget = null;
            return null;
        }

        MobKind kind = LURE_KINDS[lureKindIndex];
        if (findKindInPit(mod, kind) != null) {
            lureKindIndex++;
            lureTarget = null;
            waitingForLuredMob = false;
            return null;
        }
        if (lureTarget == null || !lureTarget.isAlive()
                || !kind.entityClass().isInstance(lureTarget)) {
            lureTarget = findNearbyMob(mod, kind);
        }
        if (lureTarget == null) {
            setDebugState("Searching for a " + kind.entityClass().getSimpleName()
                    + " to lure into the mob trap");
            return exploreTask;
        }
        if (!mod.getItemStorage().hasItem(kind.food())) {
            returningToPit = true;
            return getResource(mod, kind.food(), 1,
                    "Obtaining food to lure a " + kind.entityClass().getSimpleName());
        }
        if (!mod.getSlotHandler().forceEquipItem(kind.food())) return null;

        if (isInsidePit(lureTarget)) {
            lureKindIndex++;
            lureTarget = null;
            waitingForLuredMob = false;
            return null;
        }
        if (mod.getPlayer().squaredDistanceTo(lureTarget) > 5.5 * 5.5) {
            waitingForLuredMob = false;
            setDebugState("Approaching a " + kind.entityClass().getSimpleName()
                    + " while holding its food");
            return new GetToEntityTask(lureTarget, 4.5);
        }
        if (mod.getPlayer().getBlockPos().getSquaredDistance(stagingPosition) > 2) {
            waitingForLuredMob = true;
            lureTimer.reset();
            setDebugState("Leading the " + kind.entityClass().getSimpleName()
                    + " toward the trap");
            return new GetToBlockTask(stagingPosition);
        }
        if (!waitingForLuredMob) {
            waitingForLuredMob = true;
            lureTimer.reset();
        } else if (lureTimer.elapsed()) {
            waitingForLuredMob = false;
            lureTarget = null;
        }
        setDebugState("Waiting for the " + kind.entityClass().getSimpleName()
                + " to fall into the trap");
        return null;
    }

    private BlockPos findPitPosition(AltoClef mod) {
        BlockPos player = mod.getPlayer().getBlockPos();
        for (int radius = 3; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos candidate = player.add(dx, dy - 1, dz);
                        if (!isGoodPitPosition(mod, candidate)) continue;
                        if (findStagingPosition(mod, candidate) != null) return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isGoodPitPosition(AltoClef mod, BlockPos pos) {
        BlockState surface = mod.getWorld().getBlockState(pos);
        BlockState underSurface = mod.getWorld().getBlockState(pos.down());
        return !surface.isAir()
                && surface.getFluidState().isEmpty()
                && !underSurface.isAir()
                && underSurface.getFluidState().isEmpty()
                && WorldHelper.isSolidBlock(pos.down(2))
                && WorldHelper.canBreak(pos)
                && WorldHelper.canBreak(pos.down())
                && mod.getWorld().getBlockState(pos.up()).isAir()
                && mod.getWorld().getBlockState(pos.up(2)).isAir();
    }

    private BlockPos findStagingPosition(AltoClef mod, BlockPos pit) {
        for (Direction direction : new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos floor = pit.offset(direction);
            BlockPos standing = floor.up();
            if (WorldHelper.isSolidBlock(floor)
                    && mod.getWorld().getBlockState(standing).isAir()
                    && mod.getWorld().getBlockState(standing.up()).isAir()) {
                return standing;
            }
        }
        return null;
    }

    private boolean isInsidePit(LivingEntity entity) {
        if (pitPosition == null) return false;
        return Math.abs(entity.getX() - (pitPosition.getX() + 0.5)) < 0.42
                && Math.abs(entity.getZ() - (pitPosition.getZ() + 0.5)) < 0.42
                && entity.getY() >= pitPosition.getY() - 2.2
                && entity.getY() <= pitPosition.getY() + 0.35;
    }

    private ArmorStandEntity findArmorStandInPit(AltoClef mod) {
        for (Entity entity : mod.getWorld().getEntities()) {
            if (entity instanceof ArmorStandEntity armorStand
                    && armorStand.isAlive()
                    && isInsidePit(armorStand)) {
                return armorStand;
            }
        }
        return null;
    }

    private LivingEntity findKindInPit(AltoClef mod, MobKind kind) {
        for (Entity entity : mod.getWorld().getEntities()) {
            if (kind.entityClass().isInstance(entity)
                    && entity instanceof LivingEntity living
                    && living.isAlive()
                    && isInsidePit(living)) {
                return living;
            }
        }
        return null;
    }

    private LivingEntity findNearbyMob(AltoClef mod, MobKind kind) {
        LivingEntity nearest = null;
        double nearestDistance = 40 * 40;
        for (Entity entity : mod.getWorld().getEntities()) {
            if (!kind.entityClass().isInstance(entity)
                    || !(entity instanceof LivingEntity living)
                    || !living.isAlive()
                    || isInsidePit(living)) {
                continue;
            }
            double distance = living.squaredDistanceTo(mod.getPlayer());
            if (distance < nearestDistance) {
                nearest = living;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private Task enchantCrossbow(AltoClef mod, EnchantmentScreenHandler handler) {
        ItemStack input = StorageHelper.getItemStackInSlot(EnchantingTableSlot.ITEM);
        if (!input.isEmpty() && input.isOf(Items.CROSSBOW) && input.hasEnchantments()) {
            mod.getSlotHandler().clickSlot(EnchantingTableSlot.ITEM, 0, SlotActionType.QUICK_MOVE);
            return null;
        }
        if (input.isEmpty()) {
            ItemStack cursor = StorageHelper.getItemStackInSlot(CursorSlot.SLOT);
            if (cursor.isOf(Items.CROSSBOW) && !cursor.hasEnchantments()) {
                mod.getSlotHandler().clickSlot(EnchantingTableSlot.ITEM, 0, SlotActionType.PICKUP);
                return null;
            }
            Slot source = findUnenchantedCrossbow(mod);
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
            if (mod.getItemStorage().getItemCountInventoryOnly(Items.LAPIS_LAZULI) < LAPIS_PER_ENCHANT) {
                closeEnchantingScreen();
                openTableTask = null;
                return prepareEnchanting(mod);
            }
            return new MoveItemToSlotFromInventoryTask(
                    new ItemTarget(Items.LAPIS_LAZULI, LAPIS_PER_ENCHANT), EnchantingTableSlot.LAPIS);
        }

        int option = bestAvailableEnchantOption(mod, handler);
        if (option < 0) {
            if (mod.getPlayer().experienceLevel < 3) {
                closeEnchantingScreen();
                openTableTask = null;
                return experienceTask;
            }
            setDebugState("Waiting for an available crossbow enchantment offer");
            return null;
        }
        MinecraftClient.getInstance().interactionManager.clickButton(handler.syncId, option);
        enchantmentsAttempted++;
        setDebugState("Trying an enchanting-table offer for Piercing IV");
        return null;
    }

    private int bestAvailableEnchantOption(AltoClef mod, EnchantmentScreenHandler handler) {
        int best = -1;
        for (int option = 0; option < handler.enchantmentPower.length; option++) {
            int cost = option + 1;
            if (handler.enchantmentPower[option] > 0
                    && mod.getPlayer().experienceLevel >= cost
                    && handler.getLapisCount() >= cost) best = option;
        }
        return best;
    }

    private Task weakenEntity(AltoClef mod, LivingEntity entity) {
        releaseUse();
        if (mod.getPlayer().squaredDistanceTo(entity) > MELEE_REACH * MELEE_REACH) {
            setDebugState("Approaching a mob to weaken it before the Piercing IV shot");
            return new GetToEntityTask(entity, MELEE_REACH - 0.5);
        }
        if (!mod.getSlotHandler().forceDeequip(stack -> !stack.isEmpty())) return null;
        LookHelper.lookAt(mod, entity.getBoundingBox().getCenter());
        if (mod.getPlayer().getAttackCooldownProgress(0) >= 1 && mod.getPlayer().isOnGround()) {
            mod.getControllerExtras().attack(entity);
        }
        setDebugState("Weakening a unique mob for the five-mob crossbow shot");
        return null;
    }

    private Task shootThroughTargets(AltoClef mod) {
        if (targetAimPoint == null || targets.size() < REQUIRED_MOBS) return null;
        Vec3d aimPoint = targetAimPoint;
        var rotation = LookHelper.getLookRotation(mod, aimPoint);
        LookHelper.lookAt(mod, aimPoint, false);
        if (!LookHelper.isLookingAt(mod, rotation)
                || !(MinecraftClient.getInstance().crosshairTarget instanceof EntityHitResult hit)
                || !targets.contains(hit.getEntity())) {
            releaseUse();
            setDebugState("Aiming through all five unique mobs");
            return null;
        }

        if (!hasPiercingFour(mod.getPlayer().getMainHandStack())) {
            Slot slot = findPiercingFourCrossbow(mod);
            if (slot == null) return null;
            mod.getSlotHandler().forceEquipSlot(slot);
            return null;
        }
        if (!CrossbowItem.isCharged(mod.getPlayer().getMainHandStack())) {
            mod.getInputControls().hold(Input.CLICK_RIGHT);
            if (!loading) {
                loading = true;
                loadTimer.reset();
            }
            if (loadTimer.elapsed()) releaseUse();
            setDebugState("Loading the Piercing IV crossbow for the five-mob shot");
            return null;
        }
        releaseUse();
        mod.getInputControls().tryPress(Input.CLICK_RIGHT);
        waitingForHit = true;
        hitTimer.reset();
        setDebugState("Firing one Piercing IV arrow through five unique mobs");
        return null;
    }

    private List<LivingEntity> findNearbyEntities(AltoClef mod) {
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : mod.getWorld().getEntities()) {
            if (!(entity instanceof LivingEntity living)
                    || !living.isAlive()
                    || !isValidTarget(living, mod)
                    || living.squaredDistanceTo(mod.getPlayer()) > MAX_SHOT_RANGE * MAX_SHOT_RANGE) {
                continue;
            }
            candidates.add(living);
        }
        candidates.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mod.getPlayer())));
        return candidates;
    }

    private boolean isValidTarget(LivingEntity entity, AltoClef mod) {
        return entity != mod.getPlayer()
                && !(entity instanceof PlayerEntity)
                && !(entity instanceof WardenEntity)
                && !(entity instanceof EnderDragonEntity)
                && isInsidePit(entity)
                && !entity.isInvulnerable();
    }

    private List<LivingEntity> findAlignedUniqueEntities(AltoClef mod, List<LivingEntity> entities) {
        Vec3d eyePosition = mod.getPlayer().getEyePos();
        List<LivingEntity> best = List.of();
        Vec3d bestAimPoint = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (LivingEntity aimTarget : entities) {
            if (aimTarget.getHealth() > WEAKENED_HEALTH) continue;
            for (Vec3d aimPoint : samplePoints(aimTarget)) {
                Vec3d vector = aimPoint.subtract(eyePosition);
                double distance = vector.length();
                if (distance < 2 || distance > MAX_SHOT_RANGE) continue;
                Vec3d direction = vector.normalize();
                List<RayHit> intersections = new ArrayList<>();
                for (LivingEntity entity : entities) {
                    if (entity.getHealth() > WEAKENED_HEALTH) continue;
                    double entry = rayBoxEntry(eyePosition, direction, entity.getBoundingBox().expand(0.03));
                    if (Double.isFinite(entry) && entry <= distance + 0.2) {
                        intersections.add(new RayHit(entity, entry));
                    }
                }
                intersections.sort(Comparator.comparingDouble(RayHit::distance));
                List<LivingEntity> line = new ArrayList<>();
                Set<Object> entityTypes = new HashSet<>();
                for (RayHit intersection : intersections) {
                    if (entityTypes.add(intersection.entity().getType())) {
                        line.add(intersection.entity());
                    }
                }
                if (line.size() > best.size()
                        || line.size() == best.size() && distance < bestDistance) {
                    best = line;
                    bestAimPoint = aimPoint;
                    bestDistance = distance;
                }
            }
        }
        targetAimPoint = best.size() >= REQUIRED_MOBS ? bestAimPoint : null;
        return best.size() >= REQUIRED_MOBS ? best.subList(0, REQUIRED_MOBS) : List.of();
    }

    private List<Vec3d> samplePoints(LivingEntity entity) {
        var box = entity.getBoundingBox();
        List<Vec3d> points = new ArrayList<>(27);
        double[] fractions = {0.2, 0.5, 0.8};
        for (double xFraction : fractions) {
            for (double yFraction : fractions) {
                for (double zFraction : fractions) {
                    points.add(new Vec3d(
                            box.minX + (box.maxX - box.minX) * xFraction,
                            box.minY + (box.maxY - box.minY) * yFraction,
                            box.minZ + (box.maxZ - box.minZ) * zFraction));
                }
            }
        }
        return points;
    }

    private double rayBoxEntry(Vec3d origin, Vec3d direction, net.minecraft.util.math.Box box) {
        double min = 0;
        double max = MAX_SHOT_RANGE;
        double[] origins = {origin.x, origin.y, origin.z};
        double[] directions = {direction.x, direction.y, direction.z};
        double[] mins = {box.minX, box.minY, box.minZ};
        double[] maxes = {box.maxX, box.maxY, box.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directions[axis]) < 1.0E-7) {
                if (origins[axis] < mins[axis] || origins[axis] > maxes[axis]) return Double.POSITIVE_INFINITY;
                continue;
            }
            double first = (mins[axis] - origins[axis]) / directions[axis];
            double second = (maxes[axis] - origins[axis]) / directions[axis];
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            min = Math.max(min, first);
            max = Math.min(max, second);
            if (max < min) return Double.POSITIVE_INFINITY;
        }
        return min;
    }

    private boolean hasPiercingFourCrossbow(AltoClef mod) {
        return mod.getItemStorage().getItemStacksPlayerInventory(false).stream()
                .anyMatch(this::hasPiercingFour);
    }

    private boolean hasPiercingFour(ItemStack stack) {
        if (!stack.isOf(Items.CROSSBOW)) return false;
        var piercing = AltoClef.getInstance().getWorld().getRegistryManager()
                .get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.PIERCING).orElseThrow();
        return EnchantmentHelper.getLevel(piercing, stack) >= 4;
    }

    private Slot findPiercingFourCrossbow(AltoClef mod) {
        return mod.getItemStorage().getSlotsWithItemPlayerInventory(false, Items.CROSSBOW).stream()
                .filter(slot -> hasPiercingFour(StorageHelper.getItemStackInSlot(slot)))
                .findFirst().orElse(null);
    }

    private Slot findUnenchantedCrossbow(AltoClef mod) {
        return mod.getItemStorage().getSlotsWithItemPlayerInventory(false, Items.CROSSBOW).stream()
                .filter(slot -> {
                    ItemStack stack = StorageHelper.getItemStackInSlot(slot);
                    return stack.isOf(Items.CROSSBOW) && !stack.hasEnchantments();
                })
                .findFirst().orElse(null);
    }

    private boolean hasArrow(AltoClef mod) {
        return mod.getItemStorage().hasItem(Items.ARROW)
                || mod.getItemStorage().hasItem(Items.SPECTRAL_ARROW)
                || mod.getItemStorage().hasItem(Items.TIPPED_ARROW);
    }

    private Task getResource(AltoClef mod, net.minecraft.item.Item item, int count, String state) {
        if (!TaskCatalogue.taskExists(item)) {
            mod.logWarning("Cannot complete Arbalistic: no resource task is available for "
                    + item.getName().getString() + ".");
            finished = true;
            return null;
        }
        setDebugState(state);
        return TaskCatalogue.getItemTask(item, count);
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

    private void closeEnchantingScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null
                && client.player.currentScreenHandler instanceof EnchantmentScreenHandler) {
            StorageHelper.closeScreen();
        }
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
        closeEnchantingScreen();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ArbalisticTask;
    }

    @Override
    protected String toDebugString() {
        return "Killing five unique mobs with one Piercing IV crossbow shot";
    }

    private record MobKind(Class<? extends LivingEntity> entityClass, net.minecraft.item.Item food) {
    }

    private record RayHit(LivingEntity entity, double distance) {
    }
}
