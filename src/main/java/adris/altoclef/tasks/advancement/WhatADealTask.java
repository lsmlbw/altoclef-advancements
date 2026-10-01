package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.slot.MoveItemToSlotFromInventoryTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.tasks.movement.GetToBlockTask;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.village.TradeOffer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WhatADealTask extends Task {
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final Set<Entity> unusableMerchants = new HashSet<>();
    private MerchantEntity target;
    private ItemStack firstPayment;
    private ItemStack secondPayment;
    private int offerIndex;
    private int usesBeforeTrade;
    private boolean offerSelected;
    private boolean tradeClicked;
    private boolean finished;
    private boolean successful;
    private final int minimumTradeY;
    private final BlockPos stagingPosition;

    public WhatADealTask() {
        this(Integer.MIN_VALUE, null);
    }

    public WhatADealTask(int minimumTradeY, BlockPos stagingPosition) {
        this.minimumTradeY = minimumTradeY;
        this.stagingPosition = stagingPosition;
    }

    @Override
    protected void onStart() {
        unusableMerchants.clear();
        target = null;
        firstPayment = ItemStack.EMPTY;
        secondPayment = ItemStack.EMPTY;
        offerIndex = -1;
        usesBeforeTrade = -1;
        offerSelected = false;
        tradeClicked = false;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        MinecraftClient client = MinecraftClient.getInstance();
        if (minimumTradeY != Integer.MIN_VALUE && mod.getPlayer().getY() < minimumTradeY) {
            if (client.player.currentScreenHandler instanceof MerchantScreenHandler) {
                StorageHelper.closeScreen();
                offerSelected = false;
                tradeClicked = false;
                target = null;
            }
            setDebugState("Returning to the required trade height");
            return new GetToBlockTask(stagingPosition);
        }

        if (client.player.currentScreenHandler instanceof MerchantScreenHandler handler) {
            if (tradeClicked) {
                if (offerIndex < handler.getRecipes().size()
                        && handler.getRecipes().get(offerIndex).getUses() > usesBeforeTrade) {
                    successful = true;
                    finished = true;
                }
                return null;
            }
            return tradeFromScreen(mod, handler);
        }

        if (target == null || !target.isAlive() || unusableMerchants.contains(target)) {
            target = findMerchant(mod);
            offerSelected = false;
        }
        if (target == null) {
            if (minimumTradeY != Integer.MIN_VALUE) {
                setDebugState("Waiting for the leashed villager to reach the trading platform");
                return null;
            }
            setDebugState("Searching for a villager or wandering trader");
            return exploreTask;
        }

        if (target.squaredDistanceTo(mod.getPlayer()) > 4.5 * 4.5) {
            if (minimumTradeY != Integer.MIN_VALUE) {
                setDebugState("Waiting for the villager to come within trading distance");
                return null;
            }
            setDebugState("Approaching a merchant");
            return new GetToEntityTask(target, 3.5);
        }

        setDebugState("Opening the merchant's trade offers");
        mod.getController().interactEntity(mod.getPlayer(), target, Hand.MAIN_HAND);
        return null;
    }

    private Task tradeFromScreen(AltoClef mod, MerchantScreenHandler handler) {
        if (!offerSelected) {
            TradeOfferChoice choice = chooseOffer(mod, handler);
            if (choice == null) {
                unusableMerchants.add(target);
                StorageHelper.closeScreen();
                target = null;
                setDebugState("This merchant has no available trade with obtainable items");
                return null;
            }
            offerIndex = choice.index;
            firstPayment = choice.first.copy();
            secondPayment = choice.second.copy();
            offerSelected = true;

            if (!hasRequiredItems(mod)) {
                StorageHelper.closeScreen();
                offerSelected = false;
                return collectNextMissingItem(mod);
            }
            MinecraftClient.getInstance().interactionManager.clickButton(handler.syncId, offerIndex);
            usesBeforeTrade = handler.getRecipes().get(offerIndex).getUses();
            return null;
        }

        TradeOffer offer = handler.getRecipes().get(offerIndex);
        if (offer.isDisabled()) {
            unusableMerchants.add(target);
            StorageHelper.closeScreen();
            target = null;
            offerSelected = false;
            setDebugState("Selected trade is unavailable; finding another merchant");
            return null;
        }

        ItemStack firstSlot = handler.getSlot(0).getStack();
        if (!paymentPlaced(firstSlot, firstPayment)) {
            return new MoveItemToSlotFromInventoryTask(
                    new ItemTarget(firstPayment.getItem(), firstPayment.getCount()), new MerchantSlot(0));
        }
        if (!secondPayment.isEmpty()) {
            ItemStack secondSlot = handler.getSlot(1).getStack();
            if (!paymentPlaced(secondSlot, secondPayment)) {
                return new MoveItemToSlotFromInventoryTask(
                        new ItemTarget(secondPayment.getItem(), secondPayment.getCount()), new MerchantSlot(1));
            }
        }

        if (!handler.getSlot(2).hasStack()) {
            setDebugState("Waiting for the villager to prepare the trade");
            return null;
        }
        setDebugState("Completing the villager trade");
        mod.getSlotHandler().clickSlot(new MerchantSlot(2), 0, SlotActionType.QUICK_MOVE);
        tradeClicked = true;
        return null;
    }

    private TradeOfferChoice chooseOffer(AltoClef mod, MerchantScreenHandler handler) {
        List<TradeOfferChoice> choices = new ArrayList<>();
        for (int i = 0; i < handler.getRecipes().size(); i++) {
            TradeOffer offer = handler.getRecipes().get(i);
            if (offer.isDisabled()) continue;
            ItemStack first = offer.getDisplayedFirstBuyItem();
            ItemStack second = offer.getDisplayedSecondBuyItem();
            if (first.isEmpty() || !TaskCatalogue.taskExists(first.getItem())
                    || !second.isEmpty() && !TaskCatalogue.taskExists(second.getItem())) {
                continue;
            }
            int requiredFirst = first.getCount();
            int requiredSecond = second.isEmpty() ? 0 : second.getCount();
            boolean affordable = mod.getItemStorage().getItemCount(first.getItem())
                    >= requiredFirst + (first.getItem() == second.getItem() ? requiredSecond : 0)
                    && (second.isEmpty() || first.getItem() == second.getItem()
                    || mod.getItemStorage().getItemCount(second.getItem()) >= requiredSecond);
            choices.add(new TradeOfferChoice(i, first.copy(), second.copy(), affordable,
                    missingCount(mod, first, second), first.getItem() == Items.EMERALD
                    || !second.isEmpty() && second.getItem() == Items.EMERALD));
        }
        return choices.stream()
                .min(Comparator.comparing(TradeOfferChoice::requiresEmeralds)
                        .thenComparing(choice -> !choice.affordable)
                        .thenComparingInt(TradeOfferChoice::missingItems)
                        .thenComparingInt(choice -> choice.first.getCount() + choice.second.getCount()))
                .orElse(null);
    }

    private int missingCount(AltoClef mod, ItemStack first, ItemStack second) {
        int firstRequired = first.getCount();
        int secondRequired = second.isEmpty() ? 0 : second.getCount();
        int firstAvailable = mod.getItemStorage().getItemCount(first.getItem());
        if (!second.isEmpty() && first.getItem() == second.getItem()) {
            return Math.max(0, firstRequired + secondRequired - firstAvailable);
        }
        return Math.max(0, firstRequired - firstAvailable)
                + (second.isEmpty() ? 0 : Math.max(0,
                secondRequired - mod.getItemStorage().getItemCount(second.getItem())));
    }

    private boolean hasRequiredItems(AltoClef mod) {
        return missingCount(mod, firstPayment, secondPayment) == 0;
    }

    private Task collectNextMissingItem(AltoClef mod) {
        Item missing = null;
        if (!secondPayment.isEmpty() && firstPayment.getItem() == secondPayment.getItem()) {
            if (mod.getItemStorage().getItemCount(firstPayment.getItem())
                    < firstPayment.getCount() + secondPayment.getCount()) {
                missing = firstPayment.getItem();
            }
        } else {
            if (mod.getItemStorage().getItemCount(firstPayment.getItem()) < firstPayment.getCount()) {
                missing = firstPayment.getItem();
            } else if (!secondPayment.isEmpty()
                    && mod.getItemStorage().getItemCount(secondPayment.getItem()) < secondPayment.getCount()) {
                missing = secondPayment.getItem();
            }
        }
        if (missing == null) return null;
        int requiredCount = missing == firstPayment.getItem()
                ? firstPayment.getCount()
                : secondPayment.getCount();
        if (missing == firstPayment.getItem() && missing == secondPayment.getItem()) {
            requiredCount += secondPayment.getCount();
        }
        setDebugState("Gathering " + missing.getName().getString() + " for the trade");
        return TaskCatalogue.getItemTask(missing, requiredCount);
    }

    private boolean paymentPlaced(ItemStack placed, ItemStack required) {
        return !required.isEmpty() && placed.isOf(required.getItem())
                && placed.getCount() >= required.getCount();
    }

    private MerchantEntity findMerchant(AltoClef mod) {
        return java.util.stream.Stream.concat(
                        mod.getEntityTracker().getTrackedEntities(VillagerEntity.class).stream(),
                        mod.getEntityTracker().getTrackedEntities(WanderingTraderEntity.class).stream())
                .filter(MerchantEntity.class::isInstance)
                .map(MerchantEntity.class::cast)
                .filter(Entity::isAlive)
                .filter(entity -> !unusableMerchants.contains(entity))
                .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mod.getPlayer())))
                .orElse(null);
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
        if (MinecraftClient.getInstance().player != null
                && MinecraftClient.getInstance().player.currentScreenHandler instanceof MerchantScreenHandler) {
            StorageHelper.closeScreen();
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof WhatADealTask task
                && task.minimumTradeY == minimumTradeY
                && java.util.Objects.equals(task.stagingPosition, stagingPosition);
    }

    @Override
    protected String toDebugString() {
        return "Trading with a villager or wandering trader";
    }

    private record TradeOfferChoice(int index, ItemStack first, ItemStack second,
                                    boolean affordable, int missingItems, boolean requiresEmeralds) {
    }

    private static final class MerchantSlot extends Slot {
        private MerchantSlot(int slot) {
            super(slot, false);
        }

        @Override
        protected int inventorySlotToWindowSlot(int inventorySlot) {
            return -1;
        }

        @Override
        protected int windowSlotToInventorySlot(int windowSlot) {
            return -1;
        }

        @Override
        protected String getName() {
            return "Merchant";
        }
    }
}
