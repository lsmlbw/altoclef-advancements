package adris.altoclef.tasks.advancement;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.ClientAdvancementManagerAccessor;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.container.LootContainerTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.movement.SearchWithinBiomeTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.block.Blocks;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.BiomeKeys;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SoundOfMusicTask extends Task {
    private static final Identifier ADVANCEMENT_ID =
            Identifier.of("minecraft", "adventure/play_jukebox_in_meadows");
    private static final Item[] MUSIC_DISCS = Registries.ITEM.stream()
            .filter(item -> Registries.ITEM.getId(item).getPath().startsWith("music_disc_"))
            .toArray(Item[]::new);
    private static final List<Item> MUSIC_DISC_LIST = Arrays.asList(MUSIC_DISCS);

    private final SearchWithinBiomeTask searchMeadowTask = new SearchWithinBiomeTask(BiomeKeys.MEADOW);
    private final TimeoutWanderTask exploreTask = new TimeoutWanderTask(true);
    private final Set<BlockPos> checkedChests = new HashSet<>();
    private LootContainerTask chestLootTask;
    private PlaceBlockNearbyTask placeJukeboxTask;
    private Task insertDiscTask;
    private Item musicDisc;
    private BlockPos jukeboxPos;
    private boolean finished;
    private boolean successful;

    @Override
    protected void onStart() {
        checkedChests.clear();
        chestLootTask = null;
        placeJukeboxTask = null;
        insertDiscTask = null;
        musicDisc = null;
        jukeboxPos = null;
        finished = false;
        successful = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (finished) return null;

        AdvancementProgress progress = getAdvancementProgress();
        if (progress == null) {
            setDebugState("Waiting for Sound of Music advancement progress from the server");
            return null;
        }
        if (progress.isDone()) {
            successful = true;
            finished = true;
            return null;
        }

        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            setDebugState("Returning to the Overworld for Sound of Music");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }

        if (!mod.getItemStorage().hasItem(Items.JUKEBOX)) {
            if (!TaskCatalogue.taskExists(Items.JUKEBOX)) {
                return fail(mod, "the jukebox resource task is unavailable");
            }
            setDebugState("Obtaining a jukebox for Sound of Music");
            return TaskCatalogue.getItemTask(Items.JUKEBOX, 1);
        }

        if (musicDisc == null || !mod.getItemStorage().hasItem(musicDisc)) {
            musicDisc = findOwnedMusicDisc(mod);
            if (musicDisc == null) {
                if (chestLootTask != null) {
                    if (!chestLootTask.isFinished()) {
                        setDebugState("Searching chests for a music disc");
                        return chestLootTask;
                    }
                    checkedChests.add(chestLootTask.chest);
                    chestLootTask = null;
                }

                if (musicDisc == null) musicDisc = findOwnedMusicDisc(mod);
                if (musicDisc == null) {
                    ItemTarget[] discTargets = Arrays.stream(MUSIC_DISCS)
                            .map(item -> new ItemTarget(item, 1))
                            .toArray(ItemTarget[]::new);
                    if (mod.getEntityTracker().itemDropped(MUSIC_DISCS)) {
                        setDebugState("Picking up a dropped music disc");
                        return new PickupDroppedItemTask(discTargets, true);
                    }

                    BlockPos chest = mod.getBlockScanner().getNearestBlock(
                            pos -> !checkedChests.contains(pos) && WorldHelper.isUnopenedChest(pos),
                            Blocks.CHEST).orElse(null);
                    if (chest != null) {
                        chestLootTask = new LootContainerTask(chest, MUSIC_DISC_LIST);
                        setDebugState("Searching chests for a music disc");
                        return chestLootTask;
                    }

                    setDebugState("Exploring for a chest containing a music disc");
                    return exploreTask;
                }
            }
        }

        if (!mod.getWorld().getBiome(mod.getPlayer().getBlockPos()).matchesKey(BiomeKeys.MEADOW)) {
            setDebugState("Searching for a Meadow biome");
            return searchMeadowTask;
        }

        if (jukeboxPos == null || !mod.getWorld().getBlockState(jukeboxPos).isOf(Blocks.JUKEBOX)) {
            jukeboxPos = mod.getBlockScanner().getNearestBlock(
                    pos -> mod.getWorld().getBiome(pos).matchesKey(BiomeKeys.MEADOW),
                    Blocks.JUKEBOX).orElse(null);
        }

        if (jukeboxPos == null) {
            if (placeJukeboxTask != null && placeJukeboxTask.isFinished()) {
                jukeboxPos = placeJukeboxTask.getPlaced();
                placeJukeboxTask = null;
            }
            if (jukeboxPos == null) {
                if (placeJukeboxTask == null) {
                    placeJukeboxTask = new PlaceBlockNearbyTask(
                            pos -> mod.getWorld().getBiome(pos).matchesKey(BiomeKeys.MEADOW),
                            Blocks.JUKEBOX);
                }
                setDebugState("Placing a jukebox in the Meadow");
                return placeJukeboxTask;
            }
        }

        if (mod.getWorld().getBlockState(jukeboxPos).get(JukeboxBlock.HAS_RECORD)) {
            setDebugState("Waiting for Sound of Music advancement progress");
            return null;
        }

        if (insertDiscTask == null) {
            insertDiscTask = new InteractWithBlockTask(new ItemTarget(musicDisc, 1), jukeboxPos);
        }
        setDebugState("Playing a music disc in the Meadow");
        return insertDiscTask;
    }

    private Item findOwnedMusicDisc(AltoClef mod) {
        for (Item disc : MUSIC_DISCS) {
            if (mod.getItemStorage().hasItem(disc)) return disc;
        }
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
        mod.logWarning("Cannot complete Sound of Music: " + reason + ".");
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
        return other instanceof SoundOfMusicTask;
    }

    @Override
    protected String toDebugString() {
        return "Complete Sound of Music";
    }
}
