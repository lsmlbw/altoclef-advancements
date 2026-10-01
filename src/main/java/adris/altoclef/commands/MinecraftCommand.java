package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.multiversion.world.WorldVer;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.entity.AbstractDoToEntityTask;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.container.LootContainerTask;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.EnterNetherPortalTask;
import adris.altoclef.tasks.movement.FastTravelTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasks.movement.SearchWithinBiomeTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.speedrun.beatgame.BeatMinecraftTask;
import adris.altoclef.tasks.advancement.DragonRespawnTask;
import adris.altoclef.tasks.advancement.AdventureTask;
import adris.altoclef.tasks.advancement.EnchanterTask;
import adris.altoclef.tasks.advancement.EndCityTask;
import adris.altoclef.tasks.advancement.FuriousCocktailTask;
import adris.altoclef.tasks.advancement.GreatViewTask;
import adris.altoclef.tasks.advancement.ZombieDoctorTask;
import adris.altoclef.tasks.advancement.VoluntaryExileTask;
import adris.altoclef.tasks.advancement.HeroOfTheVillageTask;
import adris.altoclef.tasks.advancement.IsItABirdTask;
import adris.altoclef.tasks.advancement.IsItABalloonTask;
import adris.altoclef.tasks.advancement.AThrowawayJokeTask;
import adris.altoclef.tasks.advancement.MonsterHunterTask;
import adris.altoclef.tasks.advancement.PowerOfBooksTask;
import adris.altoclef.tasks.advancement.WhatADealTask;
import adris.altoclef.tasks.advancement.CraftingANewLookTask;
import adris.altoclef.tasks.advancement.SmithingWithStyleTask;
import adris.altoclef.tasks.advancement.StickySituationTask;
import adris.altoclef.tasks.advancement.OlBetsyTask;
import adris.altoclef.tasks.advancement.SurgeProtectorTask;
import adris.altoclef.tasks.advancement.CavesAndCliffsTask;
import adris.altoclef.tasks.advancement.RespectingTheRemnantsTask;
import adris.altoclef.tasks.advancement.Sneak100Task;
import adris.altoclef.tasks.advancement.SweetDreamsTask;
import adris.altoclef.tasks.advancement.ItSpreadsTask;
import adris.altoclef.tasks.advancement.TakeAimTask;
import adris.altoclef.tasks.advancement.MonstersHuntedTask;
import adris.altoclef.tasks.advancement.PostmortalTask;
import adris.altoclef.tasks.advancement.HiredHelpTask;
import adris.altoclef.tasks.advancement.StarTraderTask;
import adris.altoclef.tasks.advancement.TwoBirdsOneArrowTask;
import adris.altoclef.tasks.advancement.WhosThePillagerNowTask;
import adris.altoclef.tasks.advancement.ArbalisticTask;
import adris.altoclef.tasks.advancement.CarefulRestorationTask;
import adris.altoclef.tasks.advancement.AdventuringTimeTask;
import adris.altoclef.tasks.advancement.SoundOfMusicTask;
import adris.altoclef.tasks.advancement.LightAsARabbitTask;
import adris.altoclef.tasks.advancement.IsItAPlaneTask;
import adris.altoclef.tasks.advancement.VeryVeryFrighteningTask;
import adris.altoclef.tasks.advancement.SniperDuelTask;
import adris.altoclef.tasks.advancement.BullseyeTask;
import adris.altoclef.tasks.advancement.IsntItScuteTask;
import adris.altoclef.tasks.advancement.MinecraftTrialsEditionTask;
import adris.altoclef.tasks.advancement.CraftersCraftingCraftersTask;
import adris.altoclef.tasks.advancement.CountryLodeTakeMeHomeTask;
import adris.altoclef.tasks.advancement.LightenUpTask;
import adris.altoclef.tasks.advancement.WhoNeedsRocketsTask;
import adris.altoclef.tasks.advancement.UnderLockAndKeyTask;
import adris.altoclef.tasks.advancement.RevaultingTask;
import adris.altoclef.tasks.advancement.BlowbackTask;
import adris.altoclef.tasks.advancement.OverOverkillTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.EntityHelper;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.time.TimerGame;
import adris.altoclef.util.slots.BrewingStandSlot;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import baritone.api.utils.input.Input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class MinecraftCommand extends Command {
    public MinecraftCommand() {
        super("minecraft", "Beats the game and pursues supported advancement requirements");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        mod.runUserTask(new AdvancementItemTask(), this::finish);
    }

    private static final class AdvancementItemTask extends Task {
        private final List<Requirement> requirements = createRequirements();
        private BeatMinecraftTask beatMinecraftTask;
        private EquipArmorTask ironArmorTask;
        private EquipArmorTask netheriteArmorTask;
        private AdventureObjectivesTask adventureObjectivesTask;
        private WitheringHeightsTask witheringHeightsTask;
        private BringHomeBeaconTask bringHomeBeaconTask;
        private FuriousCocktailTask furiousCocktailTask;
        private EnchanterTask enchanterTask;
        private ZombieDoctorTask zombieDoctorTask;
        private EndCityTask endCityTask;
        private GreatViewTask greatViewTask;
        private DragonRespawnTask dragonRespawnTask;
        private AdventureTask adventureTask;
        private VoluntaryExileTask voluntaryExileTask;
        private HeroOfTheVillageTask heroOfTheVillageTask;
        private IsItABirdTask isItABirdTask;
        private IsItABalloonTask isItABalloonTask;
        private AThrowawayJokeTask aThrowawayJokeTask;
        private MonsterHunterTask monsterHunterTask;
        private PowerOfBooksTask powerOfBooksTask;
        private WhatADealTask whatADealTask;
        private CraftingANewLookTask craftingANewLookTask;
        private SmithingWithStyleTask smithingWithStyleTask;
        private StickySituationTask stickySituationTask;
        private OlBetsyTask olBetsyTask;
        private SurgeProtectorTask surgeProtectorTask;
        private CavesAndCliffsTask cavesAndCliffsTask;
        private RespectingTheRemnantsTask respectingTheRemnantsTask;
        private Sneak100Task sneak100Task;
        private SweetDreamsTask sweetDreamsTask;
        private ItSpreadsTask itSpreadsTask;
        private TakeAimTask takeAimTask;
        private MonstersHuntedTask monstersHuntedTask;
        private PostmortalTask postmortalTask;
        private HiredHelpTask hiredHelpTask;
        private StarTraderTask starTraderTask;
        private TwoBirdsOneArrowTask twoBirdsOneArrowTask;
        private WhosThePillagerNowTask whosThePillagerNowTask;
        private ArbalisticTask arbalisticTask;
        private CarefulRestorationTask carefulRestorationTask;
        private AdventuringTimeTask adventuringTimeTask;
        private SoundOfMusicTask soundOfMusicTask;
        private LightAsARabbitTask lightAsARabbitTask;
        private IsItAPlaneTask isItAPlaneTask;
        private VeryVeryFrighteningTask veryVeryFrighteningTask;
        private SniperDuelTask sniperDuelTask;
        private BullseyeTask bullseyeTask;
        private IsntItScuteTask isntItScuteTask;
        private MinecraftTrialsEditionTask minecraftTrialsEditionTask;
        private CraftersCraftingCraftersTask craftersCraftingCraftersTask;
        private CountryLodeTakeMeHomeTask countryLodeTakeMeHomeTask;
        private LightenUpTask lightenUpTask;
        private WhoNeedsRocketsTask whoNeedsRocketsTask;
        private UnderLockAndKeyTask underLockAndKeyTask;
        private RevaultingTask revaultingTask;
        private BlowbackTask blowbackTask;
        private OverOverkillTask overOverkillTask;
        private int nextRequirement;
        private boolean gameProgressFinished;
        private boolean ironArmorEquipped;
        private boolean netheriteArmorEquipped;
        private boolean completionReported;

        @Override
        protected void onStart() {
            beatMinecraftTask = new BeatMinecraftTask(AltoClef.getInstance());
            ironArmorTask = new EquipArmorTask(Items.IRON_HELMET, Items.IRON_CHESTPLATE,
                    Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            netheriteArmorTask = new EquipArmorTask(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
                    Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
            adventureObjectivesTask = new AdventureObjectivesTask();
            witheringHeightsTask = new WitheringHeightsTask();
            bringHomeBeaconTask = new BringHomeBeaconTask(witheringHeightsTask);
            furiousCocktailTask = new FuriousCocktailTask();
            enchanterTask = new EnchanterTask();
            zombieDoctorTask = new ZombieDoctorTask();
            endCityTask = new EndCityTask();
            greatViewTask = new GreatViewTask();
            dragonRespawnTask = new DragonRespawnTask();
            adventureTask = new AdventureTask();
            voluntaryExileTask = new VoluntaryExileTask();
            heroOfTheVillageTask = new HeroOfTheVillageTask();
            isItABirdTask = new IsItABirdTask();
            isItABalloonTask = new IsItABalloonTask();
            aThrowawayJokeTask = new AThrowawayJokeTask();
            monsterHunterTask = new MonsterHunterTask();
            powerOfBooksTask = new PowerOfBooksTask();
            whatADealTask = new WhatADealTask();
            craftingANewLookTask = new CraftingANewLookTask();
            smithingWithStyleTask = new SmithingWithStyleTask();
            stickySituationTask = new StickySituationTask();
            olBetsyTask = new OlBetsyTask();
            surgeProtectorTask = new SurgeProtectorTask();
            cavesAndCliffsTask = new CavesAndCliffsTask();
            respectingTheRemnantsTask = new RespectingTheRemnantsTask();
            sneak100Task = new Sneak100Task();
            sweetDreamsTask = new SweetDreamsTask();
            itSpreadsTask = new ItSpreadsTask();
            takeAimTask = new TakeAimTask();
            monstersHuntedTask = new MonstersHuntedTask();
            postmortalTask = new PostmortalTask();
            hiredHelpTask = new HiredHelpTask();
            starTraderTask = new StarTraderTask();
            twoBirdsOneArrowTask = new TwoBirdsOneArrowTask();
            whosThePillagerNowTask = new WhosThePillagerNowTask();
            arbalisticTask = new ArbalisticTask();
            carefulRestorationTask = new CarefulRestorationTask();
            adventuringTimeTask = new AdventuringTimeTask();
            soundOfMusicTask = new SoundOfMusicTask();
            lightAsARabbitTask = new LightAsARabbitTask();
            isItAPlaneTask = new IsItAPlaneTask(dragonRespawnTask);
            veryVeryFrighteningTask = new VeryVeryFrighteningTask();
            sniperDuelTask = new SniperDuelTask();
            bullseyeTask = new BullseyeTask();
            isntItScuteTask = new IsntItScuteTask();
            minecraftTrialsEditionTask = new MinecraftTrialsEditionTask();
            craftersCraftingCraftersTask = new CraftersCraftingCraftersTask();
            countryLodeTakeMeHomeTask = new CountryLodeTakeMeHomeTask();
            lightenUpTask = new LightenUpTask();
            whoNeedsRocketsTask = new WhoNeedsRocketsTask();
            underLockAndKeyTask = new UnderLockAndKeyTask();
            revaultingTask = new RevaultingTask();
            blowbackTask = new BlowbackTask();
            overOverkillTask = new OverOverkillTask();
            nextRequirement = 0;
            gameProgressFinished = false;
            ironArmorEquipped = false;
            netheriteArmorEquipped = false;
            completionReported = false;
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (!gameProgressFinished) {
                if (!beatMinecraftTask.isFinished()) {
                    setDebugState("Completing game progression");
                    return beatMinecraftTask;
                }
                gameProgressFinished = true;
            }

            if (!adventureTask.isFinished()) {
                setDebugState("Killing a mob for the Adventure advancement");
                return adventureTask;
            }

            if (!adventureTask.killedMonster() && !monsterHunterTask.isFinished()) {
                setDebugState("Killing a hostile monster for Monster Hunter");
                return monsterHunterTask;
            }

            if (!voluntaryExileTask.isFinished()) {
                setDebugState("Hunting an illager captain for Voluntary Exile");
                return voluntaryExileTask;
            }

            if (!heroOfTheVillageTask.isFinished()) {
                setDebugState("Defending a village from a raid for Hero of the Village");
                return heroOfTheVillageTask;
            }

            if (!postmortalTask.isFinished()) {
                setDebugState("Using a totem to survive fatal damage for Postmortal");
                return postmortalTask;
            }

            if (!isItABirdTask.isFinished()) {
                setDebugState("Finding a parrot and using a spyglass for Is It a Bird?");
                return isItABirdTask;
            }

            if (!isItABalloonTask.isFinished()) {
                setDebugState("Looking at a ghast through a spyglass for Is It a Balloon?");
                return isItABalloonTask;
            }

            if (!aThrowawayJokeTask.isFinished()) {
                setDebugState("Throwing a trident at a mob for A Throwaway Joke");
                return aThrowawayJokeTask;
            }

            if (!takeAimTask.isFinished()) {
                setDebugState("Shooting a mob with an arrow for Take Aim");
                return takeAimTask;
            }

            if (!powerOfBooksTask.isFinished()) {
                setDebugState("Reading a chiseled bookshelf with a comparator for The Power of Books");
                return powerOfBooksTask;
            }

            while (nextRequirement < requirements.size()) {
                Requirement requirement = requirements.get(nextRequirement);
                if (mod.getItemStorage().getItemCount(requirement.item) >= requirement.count) {
                    nextRequirement++;
                    continue;
                }

                if (!TaskCatalogue.taskExists(requirement.item)) {
                    mod.logWarning("Skipping " + requirement.advancement + ": no resource task is available for "
                            + requirement.item.getName().getString() + ".");
                    nextRequirement++;
                    continue;
                }

                setDebugState("Collecting " + requirement.item.getName().getString()
                        + " for " + requirement.advancement);
                return TaskCatalogue.getItemTask(requirement.item, requirement.count);
            }

            if (!ironArmorEquipped) {
                if (!ironArmorTask.isFinished()) {
                    setDebugState("Obtaining and equipping iron armor");
                    return ironArmorTask;
                }
                ironArmorEquipped = true;
            }

            if (!netheriteArmorEquipped) {
                if (!netheriteArmorTask.isFinished()) {
                    setDebugState("Obtaining and equipping netherite armor");
                    return netheriteArmorTask;
                }
                netheriteArmorEquipped = true;
            }

            if (!adventureObjectivesTask.isFinished()) {
                setDebugState("Completing Nether exploration and long-distance travel advancements");
                return adventureObjectivesTask;
            }

            if (!witheringHeightsTask.isFinished()) {
                setDebugState("Summoning the Wither for Withering Heights");
                return witheringHeightsTask;
            }

            if (!bringHomeBeaconTask.isFinished()) {
                setDebugState("Defeating the Wither and activating a beacon");
                return bringHomeBeaconTask;
            }

            if (!furiousCocktailTask.isFinished()) {
                setDebugState("Applying all potion effects for A Furious Cocktail");
                return furiousCocktailTask;
            }

            if (!enchanterTask.isFinished()) {
                setDebugState("Enchanting an item at an enchanting table");
                return enchanterTask;
            }

            if (!zombieDoctorTask.isFinished()) {
                setDebugState("Curing a zombie villager for Zombie Doctor");
                return zombieDoctorTask;
            }

            if (!whatADealTask.isFinished()) {
                setDebugState("Trading with a villager or wandering trader for What a Deal!");
                return whatADealTask;
            }

            if (!hiredHelpTask.isFinished()) {
                setDebugState("Summoning an iron golem for Hired Help");
                return hiredHelpTask;
            }

            if (!starTraderTask.isFinished()) {
                setDebugState("Trading with a villager at build height for Star Trader");
                return starTraderTask;
            }

            if (!craftingANewLookTask.isFinished()) {
                setDebugState("Trimming armor for Crafting a New Look");
                return craftingANewLookTask;
            }

            if (!smithingWithStyleTask.isFinished()) {
                setDebugState("Applying every exclusive armor trim for Smithing with Style");
                return smithingWithStyleTask;
            }

            if (!stickySituationTask.isFinished()) {
                setDebugState("Jumping against a honey block for Sticky Situation");
                return stickySituationTask;
            }

            if (!olBetsyTask.isFinished()) {
                setDebugState("Loading and firing a crossbow for Ol' Betsy");
                return olBetsyTask;
            }

            if (!whosThePillagerNowTask.isFinished()) {
                setDebugState("Killing a pillager with a crossbow for Who's the Pillager Now?");
                return whosThePillagerNowTask;
            }

            if (!arbalisticTask.isFinished()) {
                setDebugState("Killing five unique mobs with one Piercing IV arrow for Arbalistic");
                return arbalisticTask;
            }

            if (!twoBirdsOneArrowTask.isFinished()) {
                setDebugState("Killing two phantoms with one Piercing crossbow arrow");
                return twoBirdsOneArrowTask;
            }

            if (!surgeProtectorTask.isFinished()) {
                setDebugState("Protecting a villager from lightning for Surge Protector");
                return surgeProtectorTask;
            }

            if (!veryVeryFrighteningTask.isFinished()) {
                setDebugState("Striking a villager with Channeling lightning for Very Very Frightening");
                return veryVeryFrighteningTask;
            }

            if (!sniperDuelTask.isFinished()) {
                setDebugState("Killing a skeleton from at least 50 blocks away for Sniper Duel");
                return sniperDuelTask;
            }

            if (!bullseyeTask.isFinished()) {
                setDebugState("Hitting the target bullseye from 30+ blocks away");
                return bullseyeTask;
            }

            if (!isntItScuteTask.isFinished()) {
                setDebugState("Brushing an armadillo for Isn't It Scute?");
                return isntItScuteTask;
            }

            if (!minecraftTrialsEditionTask.isFinished()) {
                setDebugState("Entering a trial chamber for Minecraft: Trial(s) Edition");
                return minecraftTrialsEditionTask;
            }

            if (!craftersCraftingCraftersTask.isFinished()) {
                setDebugState("Crafting a Crafter in a Crafter");
                return craftersCraftingCraftersTask;
            }

            if (!countryLodeTakeMeHomeTask.isFinished()) {
                setDebugState("Using a compass on a lodestone for Country Lode, Take Me Home");
                return countryLodeTakeMeHomeTask;
            }

            if (!lightenUpTask.isFinished()) {
                setDebugState("Scraping a lit, oxidized copper bulb for Lighten Up");
                return lightenUpTask;
            }

            if (!whoNeedsRocketsTask.isFinished()) {
                setDebugState("Launching at least eight blocks with a wind charge for Who Needs Rockets?");
                return whoNeedsRocketsTask;
            }

            if (!underLockAndKeyTask.isFinished()) {
                setDebugState("Using a trial key on a normal vault for Under Lock and Key");
                return underLockAndKeyTask;
            }

            if (!revaultingTask.isFinished()) {
                setDebugState("Using an Ominous Trial Key on an ominous vault for Revaulting");
                return revaultingTask;
            }

            if (!blowbackTask.isFinished()) {
                setDebugState("Killing a Breeze with its deflected wind charge for Blowback");
                return blowbackTask;
            }

            if (!overOverkillTask.isFinished()) {
                setDebugState("Dealing 50 hearts of damage in one Mace hit for Over-Overkill");
                return overOverkillTask;
            }

            if (!cavesAndCliffsTask.isFinished()) {
                setDebugState("Preparing and surviving the Caves & Cliffs fall");
                return cavesAndCliffsTask;
            }

            if (!respectingTheRemnantsTask.isFinished()) {
                setDebugState("Brushing suspicious archaeology for Respecting the Remnants");
                return respectingTheRemnantsTask;
            }

            if (!carefulRestorationTask.isFinished()) {
                setDebugState("Crafting a decorated pot from pottery sherds for Careful Restoration");
                return carefulRestorationTask;
            }

            if (!adventuringTimeTask.isFinished()) {
                setDebugState("Discovering every Overworld biome for Adventuring Time");
                return adventuringTimeTask;
            }

            if (!soundOfMusicTask.isFinished()) {
                setDebugState("Playing a music disc in a Meadow for Sound of Music");
                return soundOfMusicTask;
            }

            if (!lightAsARabbitTask.isFinished()) {
                setDebugState("Walking on powder snow in leather boots for Light as a Rabbit");
                return lightAsARabbitTask;
            }

            if (!sneak100Task.isFinished()) {
                setDebugState("Sneaking near a sculk sensor for Sneak 100");
                return sneak100Task;
            }

            if (!sweetDreamsTask.isFinished()) {
                setDebugState("Sleeping in a bed for Sweet Dreams");
                return sweetDreamsTask;
            }

            if (!itSpreadsTask.isFinished()) {
                setDebugState("Killing a mob near a sculk catalyst for It Spreads");
                return itSpreadsTask;
            }

            if (!endCityTask.isFinished()) {
                setDebugState("Finding an End City for The City at the End of the Game");
                return endCityTask;
            }

            if (!greatViewTask.isFinished()) {
                setDebugState("Traveling under Levitation for Great View From Up Here");
                return greatViewTask;
            }

            if (!dragonRespawnTask.isFinished()) {
                setDebugState("Respawning the Ender Dragon for The End... Again...");
                return dragonRespawnTask;
            }

            if (!isItAPlaneTask.isFinished()) {
                setDebugState("Looking at the Ender Dragon through a spyglass for Is It a Plane?");
                return isItAPlaneTask;
            }

            if (!monstersHuntedTask.isFinished()) {
                setDebugState("Hunting every hostile mob for Monsters Hunted");
                return monstersHuntedTask;
            }

            setDebugState("Supported advancement objectives completed");
            if (!completionReported) {
                List<String> remaining = new ArrayList<>();
                if (!beatMinecraftTask.wasRemoteGetawaySuccessful()) remaining.add("Remote Getaway");
                if (!endCityTask.wasSuccessful()) remaining.add("The City at the End of the Game");
                if (!dragonRespawnTask.wasSuccessful()) remaining.add("The End... Again...");
                if (!greatViewTask.wasSuccessful()) remaining.add("Great View From Up Here");
                if (!isItABirdTask.wasSuccessful()) remaining.add("Is It a Bird?");
                if (!isItABalloonTask.wasSuccessful()) remaining.add("Is It a Balloon?");
                if (!aThrowawayJokeTask.wasSuccessful()) remaining.add("A Throwaway Joke");
                if (!takeAimTask.wasSuccessful()) remaining.add("Take Aim");
                if (!monstersHuntedTask.wasSuccessful()) remaining.add("Monsters Hunted");
                if (!postmortalTask.wasSuccessful()) remaining.add("Postmortal");
                if (!hiredHelpTask.wasSuccessful()) remaining.add("Hired Help");
                if (!starTraderTask.wasSuccessful()) remaining.add("Star Trader");
                if (!powerOfBooksTask.wasSuccessful()) remaining.add("The Power of Books");
                if (!whatADealTask.wasSuccessful()) remaining.add("What a Deal!");
                if (!craftingANewLookTask.wasSuccessful()) remaining.add("Crafting a New Look");
                if (!smithingWithStyleTask.wasSuccessful()) remaining.add("Smithing with Style");
                if (!stickySituationTask.wasSuccessful()) remaining.add("Sticky Situation");
                if (!olBetsyTask.wasSuccessful()) remaining.add("Ol' Betsy");
                if (!whosThePillagerNowTask.wasSuccessful()) remaining.add("Who's the Pillager Now?");
                if (!arbalisticTask.wasSuccessful()) remaining.add("Arbalistic");
                if (!twoBirdsOneArrowTask.wasSuccessful()) remaining.add("Two Birds, One Arrow");
                if (!surgeProtectorTask.wasSuccessful()) remaining.add("Surge Protector");
                if (!cavesAndCliffsTask.wasSuccessful()) remaining.add("Caves & Cliffs");
                if (!respectingTheRemnantsTask.wasSuccessful()) remaining.add("Respecting the Remnants");
                if (!carefulRestorationTask.wasSuccessful()) remaining.add("Careful Restoration");
                if (!adventuringTimeTask.wasSuccessful()) remaining.add("Adventuring Time");
                if (!soundOfMusicTask.wasSuccessful()) remaining.add("Sound of Music");
                if (!lightAsARabbitTask.wasSuccessful()) remaining.add("Light as a Rabbit");
                if (!isItAPlaneTask.wasSuccessful()) remaining.add("Is It a Plane?");
                if (!veryVeryFrighteningTask.wasSuccessful()) remaining.add("Very Very Frightening");
                if (!sniperDuelTask.wasSuccessful()) remaining.add("Sniper Duel");
                if (!bullseyeTask.wasSuccessful()) remaining.add("Bullseye");
                if (!isntItScuteTask.wasSuccessful()) remaining.add("Isn't It Scute?");
                if (!minecraftTrialsEditionTask.wasSuccessful()) remaining.add("Minecraft: Trial(s) Edition");
                if (!craftersCraftingCraftersTask.wasSuccessful()) remaining.add("Crafters Crafting Crafters");
                if (!countryLodeTakeMeHomeTask.wasSuccessful()) remaining.add("Country Lode, Take Me Home");
                if (!lightenUpTask.wasSuccessful()) remaining.add("Lighten Up");
                if (!whoNeedsRocketsTask.wasSuccessful()) remaining.add("Who Needs Rockets?");
                if (!underLockAndKeyTask.wasSuccessful()) remaining.add("Under Lock and Key");
                if (!revaultingTask.wasSuccessful()) remaining.add("Revaulting");
                if (!blowbackTask.wasSuccessful()) remaining.add("Blowback");
                if (!overOverkillTask.wasSuccessful()) remaining.add("Over-Overkill");
                if (!sneak100Task.wasSuccessful()) remaining.add("Sneak 100");
                if (!sweetDreamsTask.wasSuccessful()) remaining.add("Sweet Dreams");
                if (!itSpreadsTask.wasSuccessful()) remaining.add("It Spreads");
                if (!heroOfTheVillageTask.wasSuccessful()) remaining.add("Hero of the Village");
                if (!adventureTask.killedMonster() && !monsterHunterTask.isFinished()) {
                    remaining.add("Monster Hunter");
                }
                if (!remaining.isEmpty()) {
                    mod.logWarning("Finished game progression, but could not confirm these objectives: "
                            + String.join(", ", remaining) + ".");
                }
                completionReported = true;
            }
            return null;
        }

        @Override
        public boolean isFinished() {
            return gameProgressFinished && nextRequirement >= requirements.size()
                    && ironArmorEquipped && netheriteArmorEquipped && adventureObjectivesTask.isFinished()
                    && witheringHeightsTask.isFinished() && bringHomeBeaconTask.isFinished()
                    && furiousCocktailTask.isFinished() && enchanterTask.isFinished()
                    && zombieDoctorTask.isFinished() && endCityTask.isFinished()
                    && greatViewTask.isFinished() && dragonRespawnTask.isFinished()
                    && adventureTask.isFinished() && voluntaryExileTask.isFinished()
                    && heroOfTheVillageTask.isFinished()
                    && postmortalTask.isFinished()
                    && hiredHelpTask.isFinished()
                    && starTraderTask.isFinished()
                    && isItABirdTask.isFinished()
                    && isItABalloonTask.isFinished()
                    && aThrowawayJokeTask.isFinished()
                    && takeAimTask.isFinished()
                    && monstersHuntedTask.isFinished()
                    && powerOfBooksTask.isFinished()
                    && whatADealTask.isFinished()
                    && craftingANewLookTask.isFinished()
                    && smithingWithStyleTask.isFinished()
                    && stickySituationTask.isFinished()
                    && olBetsyTask.isFinished()
                    && whosThePillagerNowTask.isFinished()
                    && arbalisticTask.isFinished()
                    && twoBirdsOneArrowTask.isFinished()
                    && surgeProtectorTask.isFinished()
                    && cavesAndCliffsTask.isFinished()
                    && respectingTheRemnantsTask.isFinished()
                    && carefulRestorationTask.isFinished()
                    && adventuringTimeTask.isFinished()
                    && soundOfMusicTask.isFinished()
                    && lightAsARabbitTask.isFinished()
                    && isItAPlaneTask.isFinished()
                    && veryVeryFrighteningTask.isFinished()
                    && sniperDuelTask.isFinished()
                    && bullseyeTask.isFinished()
                    && isntItScuteTask.isFinished()
                    && minecraftTrialsEditionTask.isFinished()
                    && craftersCraftingCraftersTask.isFinished()
                    && countryLodeTakeMeHomeTask.isFinished()
                    && lightenUpTask.isFinished()
                    && whoNeedsRocketsTask.isFinished()
                    && underLockAndKeyTask.isFinished()
                    && revaultingTask.isFinished()
                    && blowbackTask.isFinished()
                    && overOverkillTask.isFinished()
                    && sneak100Task.isFinished()
                    && sweetDreamsTask.isFinished()
                    && itSpreadsTask.isFinished()
                    && (adventureTask.killedMonster() || monsterHunterTask.isFinished());
        }

        @Override
        protected void onStop(Task interruptTask) {
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof AdvancementItemTask;
        }

        @Override
        protected String toDebugString() {
            return "Collecting Minecraft advancement items";
        }

        private static List<Requirement> createRequirements() {
            List<Requirement> result = new ArrayList<>();
            add(result, "Minecraft", Items.CRAFTING_TABLE);
            add(result, "Stone Age", Items.COBBLESTONE, 3);
            add(result, "Getting an Upgrade", Items.STONE_PICKAXE);
            add(result, "Acquire Hardware", Items.IRON_INGOT);
            for (Item armor : new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE,
                    Items.IRON_LEGGINGS, Items.IRON_BOOTS}) {
                add(result, "Suit Up", armor);
            }
            add(result, "Hot Stuff", Items.LAVA_BUCKET);
            add(result, "Isn't It Iron Pick", Items.IRON_PICKAXE);
            add(result, "Not Today, Thank You", Items.SHIELD);
            add(result, "Oh Shiny", Items.GOLD_INGOT);
            add(result, "Diamonds!", Items.DIAMOND);
            add(result, "Ice Bucket Challenge / Bring Home the Beacon / Beaconator", Items.OBSIDIAN, 3);
            add(result, "Bring Home the Beacon / Beaconator", Items.GLASS, 5);
            add(result, "Hidden in the Depths", Items.ANCIENT_DEBRIS);
            add(result, "Who is Cutting Onions? / Not Quite \"Nine\" Lives", Items.CRYING_OBSIDIAN, 6);
            add(result, "Not Quite \"Nine\" Lives / A Furious Cocktail", Items.GLOWSTONE, 7);
            add(result, "Not Quite \"Nine\" Lives", Items.RESPAWN_ANCHOR);
            add(result, "Withering Heights / Bring Home the Beacon", Items.WITHER_SKELETON_SKULL, 3);
            add(result, "Withering Heights", Items.SOUL_SAND, 4);
            add(result, "Into Fire", Items.BLAZE_ROD);
            add(result, "A Furious Cocktail / Local Brewery", Items.BREWING_STAND);
            add(result, "A Furious Cocktail / Local Brewery", Items.BLAZE_POWDER);
            add(result, "A Furious Cocktail / Local Brewery", Items.NETHER_WART);
            add(result, "A Furious Cocktail / Local Brewery", Items.REDSTONE);
            add(result, "A Furious Cocktail / Local Brewery", Items.GLOWSTONE_DUST);
            add(result, "Local Brewery", Items.GLASS_BOTTLE);
            add(result, "Zombie Doctor", Items.BROWN_MUSHROOM);
            add(result, "Zombie Doctor", Items.COBBLESTONE, 25);
            add(result, "Zombie Doctor", Items.SUGAR);
            add(result, "Zombie Doctor", Items.SPIDER_EYE);
            add(result, "Zombie Doctor", Items.GUNPOWDER);
            add(result, "Zombie Doctor", Items.BLAZE_POWDER);
            add(result, "A Furious Cocktail / Zombie Doctor", Items.FERMENTED_SPIDER_EYE);
            add(result, "Zombie Doctor", Items.GOLDEN_APPLE);
            add(result, "Enchanter", Items.ENCHANTING_TABLE);
            add(result, "Enchanter", Items.LAPIS_LAZULI, 3);
            add(result, "Cover Me With Diamonds", Items.DIAMOND_HELMET);
            add(result, "The Next Generation", Items.DRAGON_EGG);
            add(result, "You Need a Mint", Items.DRAGON_BREATH);
            add(result, "Sky's the Limit", Items.ELYTRA);
            add(result, "The End... Again...", Items.GLASS, 28);
            add(result, "The End... Again...", Items.ENDER_EYE, 4);
            add(result, "The End... Again...", Items.GHAST_TEAR, 4);
            add(result, "The End... Again...", Items.END_CRYSTAL, 4);
            add(result, "This Boat Has Legs / Feels Like Home", Items.WARPED_FUNGUS_ON_A_STICK);
            return result;
        }

        private static void add(List<Requirement> requirements, String advancement, Item item) {
            add(requirements, advancement, item, 1);
        }

        private static void add(List<Requirement> requirements, String advancement, Item item, int count) {
            requirements.add(new Requirement(advancement, item, count));
        }
    }

    private static final class WitheringHeightsTask extends Task {
        private static final double RETREAT_DISTANCE = 20;
        private final TimerGame spawnCheckTimer = new TimerGame(8);
        private BlockPos floorCenter;
        private List<BlockPos> structurePositions;
        private List<net.minecraft.block.Block> structureBlocks;
        private int placementIndex;
        private PlaceBlockTask placeBlockTask;
        private boolean waitingForSpawn;
        private boolean witherSpawned;
        private boolean spawnWarningReported;
        private boolean finished;

        @Override
        protected void onStart() {
            floorCenter = null;
            structurePositions = null;
            structureBlocks = null;
            placementIndex = 0;
            placeBlockTask = null;
            waitingForSpawn = false;
            witherSpawned = false;
            spawnWarningReported = false;
            finished = false;
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (finished) {
                return null;
            }
            if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
                setDebugState("Moving to the Overworld before summoning the Wither");
                return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
            }

            if (floorCenter == null) {
                if (mod.getItemStorage().getItemCount(Items.SOUL_SAND, Items.SOUL_SOIL) < 4) {
                    return TaskCatalogue.getSquashedItemTask(
                            new ItemTarget(Items.SOUL_SAND, 4), new ItemTarget(Items.SOUL_SOIL, 4));
                }
                if (mod.getItemStorage().getItemCount(Items.WITHER_SKELETON_SKULL) < 3) {
                    return TaskCatalogue.getItemTask(Items.WITHER_SKELETON_SKULL, 3);
                }

                floorCenter = findSummoningSite(mod);
                if (floorCenter == null) {
                    setDebugState("Finding a clear, supported place to summon the Wither");
                    return new TimeoutWanderTask(4);
                }
                structurePositions = List.of(
                        floorCenter.up(),
                        floorCenter.add(-1, 2, 0),
                        floorCenter.up(2),
                        floorCenter.add(1, 2, 0),
                        floorCenter.add(-1, 3, 0),
                        floorCenter.add(1, 3, 0),
                        floorCenter.up(3));
                structureBlocks = List.of(
                        Blocks.SOUL_SAND,
                        Blocks.SOUL_SAND,
                        Blocks.SOUL_SAND,
                        Blocks.SOUL_SAND,
                        Blocks.WITHER_SKELETON_SKULL,
                        Blocks.WITHER_SKELETON_SKULL,
                        Blocks.WITHER_SKELETON_SKULL);
            }

            if (waitingForSpawn) {
                boolean witherNearby = mod.getEntityTracker().getTrackedEntities(WitherEntity.class).stream()
                        .anyMatch(wither -> wither.isAlive()
                                && wither.getPos().squaredDistanceTo(Vec3d.ofCenter(floorCenter)) < 24 * 24);
                boolean centerSkullConsumed = mod.getWorld().getBlockState(floorCenter.up(3)).isAir();
                if (witherNearby || centerSkullConsumed || spawnCheckTimer.elapsed()) {
                    witherSpawned = witherNearby || centerSkullConsumed;
                    if (!witherSpawned) {
                        if (!spawnWarningReported) {
                            mod.logWarning("The Wither has not spawned yet; waiting for the summon to complete.");
                            spawnWarningReported = true;
                        }
                        return null;
                    }
                    setDebugState("Wither summoned; retreating from its spawn explosion");
                    BlockPos retreatTarget = floorCenter.add((int) RETREAT_DISTANCE, 0, 0);
                    if (mod.getPlayer().getPos().squaredDistanceTo(Vec3d.ofCenter(floorCenter))
                            >= RETREAT_DISTANCE * RETREAT_DISTANCE) {
                        finished = true;
                        return null;
                    }
                    return new GetToBlockTask(retreatTarget);
                }
                return null;
            }

            while (placementIndex < structurePositions.size()
                    && mod.getWorld().getBlockState(structurePositions.get(placementIndex)).getBlock()
                    == structureBlocks.get(placementIndex)) {
                placementIndex++;
                placeBlockTask = null;
            }

            if (placementIndex >= structurePositions.size()) {
                waitingForSpawn = true;
                spawnCheckTimer.reset();
                return null;
            }

            if (placementIndex == structurePositions.size() - 1
                    && !mod.getPlayer().getBlockPos().isWithinDistance(floorCenter, 50)) {
                setDebugState("Moving within range before placing the final Wither skull");
                return new GetToBlockTask(floorCenter);
            }

            if (placeBlockTask == null) {
                net.minecraft.block.Block block = structureBlocks.get(placementIndex);
                if (block == Blocks.SOUL_SAND && !mod.getItemStorage().hasItem(Items.SOUL_SAND)) {
                    block = Blocks.SOUL_SOIL;
                }
                placeBlockTask = new PlaceBlockTask(structurePositions.get(placementIndex), block);
            }
            setDebugState("Building the Wither structure (" + (placementIndex + 1)
                    + "/" + structurePositions.size() + ")");
            return placeBlockTask;
        }

        private BlockPos findSummoningSite(AltoClef mod) {
            BlockPos player = mod.getPlayer().getBlockPos();
            BlockPos best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int y = -3; y <= 1; y++) {
                for (int x = -6; x <= 6; x++) {
                    for (int z = -6; z <= 6; z++) {
                        BlockPos floor = player.add(x, y - 1, z);
                        if (!mod.getChunkTracker().isChunkLoaded(floor)
                                || !WorldHelper.isSolidBlock(floor)) {
                            continue;
                        }
                        boolean clear = true;
                        for (int dx = -1; dx <= 1 && clear; dx++) {
                            for (int dy = 1; dy <= 3; dy++) {
                                BlockPos check = floor.add(dx, dy, 0);
                                if (!mod.getWorld().getBlockState(check).isReplaceable()) {
                                    clear = false;
                                    break;
                                }
                            }
                        }
                        if (!clear) {
                            continue;
                        }
                        double distance = floor.getSquaredDistance(player);
                        if (distance < bestDistance) {
                            best = floor.toImmutable();
                            bestDistance = distance;
                        }
                    }
                }
            }
            return best;
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
            return other instanceof WitheringHeightsTask;
        }

        @Override
        protected String toDebugString() {
            return "Summoning the Wither for Withering Heights";
        }
    }

    private static final class BringHomeBeaconTask extends Task {
        private static final int IRON_BLOCK_BATCH_SIZE = 32;
        private final WitheringHeightsTask initialSummonTask;
        private final TimerGame beaconActivationTimer = new TimerGame(3);
        private WitheringHeightsTask retrySummonTask;
        private WitherEntity witherTarget;
        private KillEntityTask killWitherTask;
        private PickupDroppedItemTask pickupStarTask;
        private boolean starObtained;
        private boolean beaconObtained;
        private BlockPos beaconPos;
        private List<BlockPos> structurePositions;
        private List<net.minecraft.block.Block> structureBlocks;
        private int placementIndex;
        private PlaceBlockTask placeBlockTask;
        private boolean waitingForActivation;
        private boolean finished;

        private BringHomeBeaconTask(WitheringHeightsTask initialSummonTask) {
            this.initialSummonTask = initialSummonTask;
        }

        @Override
        protected void onStart() {
            retrySummonTask = null;
            witherTarget = null;
            killWitherTask = null;
            pickupStarTask = null;
            starObtained = false;
            beaconObtained = false;
            beaconPos = null;
            structurePositions = null;
            structureBlocks = null;
            placementIndex = 0;
            placeBlockTask = null;
            waitingForActivation = false;
            finished = false;
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (finished) {
                return null;
            }
            if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
                setDebugState("Returning to the Overworld for the beacon objective");
                return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
            }

            if (!starObtained) {
                if (mod.getItemStorage().hasItem(Items.NETHER_STAR)) {
                    starObtained = true;
                    pickupStarTask = null;
                } else if (mod.getEntityTracker().itemDropped(Items.NETHER_STAR)) {
                    if (pickupStarTask == null) {
                        pickupStarTask = new PickupDroppedItemTask(Items.NETHER_STAR, 1, true);
                    }
                    setDebugState("Collecting the Nether Star");
                    return pickupStarTask;
                } else {
                    WitherEntity activeWither = mod.getEntityTracker().getTrackedEntities(WitherEntity.class)
                            .stream()
                            .filter(WitherEntity::isAlive)
                            .min((first, second) -> Double.compare(
                                    first.squaredDistanceTo(mod.getPlayer()),
                                    second.squaredDistanceTo(mod.getPlayer())))
                            .orElse(null);
                    if (activeWither != null) {
                        if (witherTarget == null || !witherTarget.equals(activeWither)) {
                            witherTarget = activeWither;
                            killWitherTask = new KillEntityTask(activeWither);
                        }
                        setDebugState("Defeating the Wither for its Nether Star");
                        return killWitherTask;
                    }

                    Task summonTask = retrySummonTask == null
                            ? initialSummonTask
                            : retrySummonTask;
                    if (summonTask.isFinished()) {
                        retrySummonTask = new WitheringHeightsTask();
                        summonTask = retrySummonTask;
                    }
                    setDebugState("Summoning a Wither to obtain a Nether Star");
                    return summonTask;
                }
            }

            if (!beaconObtained) {
                if (!mod.getItemStorage().hasItem(Items.BEACON)) {
                    if (!TaskCatalogue.taskExists(Items.BEACON)) {
                        mod.logWarning("Cannot craft a beacon: its recipe is unavailable in the resource catalogue.");
                        return null;
                    }
                    setDebugState("Crafting a beacon");
                    return TaskCatalogue.getItemTask(Items.BEACON, 1);
                }
                beaconObtained = true;
            }

            if (beaconPos == null) {
                BlockPos site = findBeaconSite(mod);
                if (site == null) {
                    setDebugState("Finding an open-sky 9x9 site for a full-power beacon");
                    return new TimeoutWanderTask(4);
                }
                structurePositions = new ArrayList<>(164);
                structureBlocks = new ArrayList<>(164);
                for (int layer = 1; layer <= 4; layer++) {
                    int radius = 5 - layer;
                    for (int x = -radius; x <= radius; x++) {
                        for (int z = -radius; z <= radius; z++) {
                            structurePositions.add(site.add(x, layer, z));
                            structureBlocks.add(Blocks.IRON_BLOCK);
                        }
                    }
                }
                beaconPos = site.up(5);
            }

            if (waitingForActivation) {
                if (mod.getWorld().getBlockEntity(beaconPos) instanceof BeaconBlockEntity beacon
                        && !beacon.getBeamSegments().isEmpty()) {
                    finished = true;
                    return null;
                }
                if (beaconActivationTimer.elapsed()) {
                    beaconActivationTimer.reset();
                    setDebugState("Waiting for the beacon beam to activate");
                }
                return null;
            }

            while (placementIndex < structurePositions.size()
                    && mod.getWorld().getBlockState(structurePositions.get(placementIndex)).getBlock()
                    == structureBlocks.get(placementIndex)) {
                placementIndex++;
                placeBlockTask = null;
            }

            if (placementIndex < structurePositions.size()) {
                int blocksToPlace = Math.min(IRON_BLOCK_BATCH_SIZE, structurePositions.size() - placementIndex);
                if (mod.getItemStorage().getItemCount(Items.IRON_BLOCK) < blocksToPlace) {
                    setDebugState("Collecting iron blocks for the full-power beacon pyramid ("
                            + (placementIndex + 1) + "/164)");
                    return TaskCatalogue.getItemTask(Items.IRON_BLOCK, blocksToPlace);
                }
            } else if (!mod.getWorld().getBlockState(beaconPos).isOf(Blocks.BEACON)) {
                if (!mod.getItemStorage().hasItem(Items.BEACON)) {
                    return TaskCatalogue.getItemTask(Items.BEACON, 1);
                }
                if (placeBlockTask == null) {
                    placeBlockTask = new PlaceBlockTask(beaconPos, Blocks.BEACON);
                }
                setDebugState("Placing the beacon on the completed full-power pyramid");
                return placeBlockTask;
            } else {
                waitingForActivation = true;
                beaconActivationTimer.reset();
                return null;
            }

            net.minecraft.block.Block block = structureBlocks.get(placementIndex);
            if (placeBlockTask == null) {
                placeBlockTask = new PlaceBlockTask(structurePositions.get(placementIndex), block);
            }
            setDebugState("Building the full-power iron pyramid (" + (placementIndex + 1) + "/164)");
            return placeBlockTask;
        }

        private BlockPos findBeaconSite(AltoClef mod) {
            BlockPos player = mod.getPlayer().getBlockPos();
            BlockPos best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int y = -2; y <= 1; y++) {
                for (int x = -8; x <= 8; x++) {
                    for (int z = -8; z <= 8; z++) {
                        BlockPos floor = player.add(x, y - 1, z);
                        if (!mod.getChunkTracker().isChunkLoaded(floor)
                                || !WorldHelper.isSolidBlock(floor)) {
                            continue;
                        }
                        boolean valid = true;
                        for (int dx = -4; dx <= 4 && valid; dx++) {
                            for (int dz = -4; dz <= 4 && valid; dz++) {
                                BlockPos basePos = floor.add(dx, 1, dz);
                                if (!mod.getChunkTracker().isChunkLoaded(basePos)
                                        || !mod.getChunkTracker().isChunkLoaded(basePos.down())
                                        || !WorldHelper.isSolidBlock(basePos.down())
                                        || !mod.getWorld().getBlockState(basePos).isReplaceable()) {
                                    valid = false;
                                    break;
                                }
                                for (int dy = 2; dy <= 4; dy++) {
                                    BlockPos upperSpace = floor.add(dx, dy, dz);
                                    if (!mod.getChunkTracker().isChunkLoaded(upperSpace)
                                            || !mod.getWorld().getBlockState(upperSpace).isReplaceable()) {
                                        valid = false;
                                        break;
                                    }
                                }
                            }
                        }
                        BlockPos candidateBeacon = floor.up(5);
                        if (!valid || !mod.getChunkTracker().isChunkLoaded(candidateBeacon)
                                || !mod.getWorld().getBlockState(candidateBeacon).isReplaceable()
                                || !mod.getWorld().isSkyVisible(candidateBeacon)) {
                            continue;
                        }
                        double distance = floor.getSquaredDistance(player);
                        if (distance < bestDistance) {
                            best = floor.toImmutable();
                            bestDistance = distance;
                        }
                    }
                }
            }
            return best;
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
            return other instanceof BringHomeBeaconTask;
        }

        @Override
        protected String toDebugString() {
            return "Defeating the Wither and activating a beacon";
        }
    }

    private static final class AdventureObjectivesTask extends Task {
        private final List<RegistryKey<Biome>> netherBiomes = List.of(
                BiomeKeys.BASALT_DELTAS,
                BiomeKeys.CRIMSON_FOREST,
                BiomeKeys.NETHER_WASTES,
                BiomeKeys.SOUL_SAND_VALLEY,
                BiomeKeys.WARPED_FOREST
        );
        private int biomeIndex;
        private BlockPos subspaceTarget;
        private boolean subspaceComplete;
        private final ReturnSenderTask returnSenderTask = new ReturnSenderTask();
        private boolean returnSenderComplete;
        private final OhShinyTask ohShinyTask = new OhShinyTask();
        private boolean ohShinyComplete;
        private final ThisBoatHasLegsTask thisBoatHasLegsTask = new ThisBoatHasLegsTask();
        private boolean thisBoatHasLegsComplete;
        private final FeelsLikeHomeTask feelsLikeHomeTask = new FeelsLikeHomeTask();
        private boolean feelsLikeHomeComplete;
        private final UneasyAllianceTask uneasyAllianceTask = new UneasyAllianceTask();
        private boolean uneasyAllianceComplete;
        private final WarPigsTask warPigsTask = new WarPigsTask();
        private boolean warPigsComplete;
        private final LocalBreweryTask localBreweryTask = new LocalBreweryTask();
        private boolean localBreweryComplete;
        private final ChargeRespawnAnchorTask chargeRespawnAnchorTask = new ChargeRespawnAnchorTask();
        private boolean chargeRespawnAnchorComplete;

        @Override
        protected void onStart() {
            AltoClef mod = AltoClef.getInstance();
            biomeIndex = 0;
            returnSenderComplete = false;
            ohShinyComplete = false;
            thisBoatHasLegsComplete = false;
            feelsLikeHomeComplete = false;
            uneasyAllianceComplete = false;
            warPigsComplete = false;
            localBreweryComplete = false;
            chargeRespawnAnchorComplete = false;
            BlockPos overworldOrigin = mod.getPlayer().getBlockPos();
            BlockPos originalNetherPortal = mod.getMiscBlockTracker()
                    .getLastUsedNetherPortal(Dimension.NETHER).orElse(null);
            if (originalNetherPortal != null) {
                overworldOrigin = new BlockPos(originalNetherPortal.getX() * 8,
                        originalNetherPortal.getY(), originalNetherPortal.getZ() * 8);
            }
            subspaceTarget = overworldOrigin.add(7000, 0, 0);
            subspaceComplete = false;
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (!ohShinyComplete) {
                if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
                    setDebugState("Traveling to the Nether to find an adult piglin");
                    return new DefaultGoToDimensionTask(Dimension.NETHER);
                }
                if (!ohShinyTask.isFinished()) {
                    setDebugState("Distracting an adult piglin with gold");
                    return ohShinyTask;
                }
                ohShinyComplete = true;
            }

            if (!thisBoatHasLegsComplete) {
                if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
                    setDebugState("Traveling to the Nether to find a strider");
                    return new DefaultGoToDimensionTask(Dimension.NETHER);
                }
                if (!thisBoatHasLegsTask.isFinished()) {
                    setDebugState("Riding and boosting a strider");
                    return thisBoatHasLegsTask;
                }
                thisBoatHasLegsComplete = true;
            }

            if (!feelsLikeHomeComplete) {
                if (!feelsLikeHomeTask.isFinished()) {
                    setDebugState("Riding a strider 50 blocks across lava in the Overworld");
                    return feelsLikeHomeTask;
                }
                feelsLikeHomeComplete = true;
            }

            if (!warPigsComplete) {
                if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
                    setDebugState("Traveling to the Nether to find a bastion chest");
                    return new DefaultGoToDimensionTask(Dimension.NETHER);
                }
                if (!warPigsTask.isFinished()) {
                    setDebugState("Finding and opening a chest in a bastion remnant");
                    return warPigsTask;
                }
                warPigsComplete = true;
            }

            if (!uneasyAllianceComplete) {
                if (!uneasyAllianceTask.isFinished()) {
                    setDebugState("Bringing a ghast through a large Nether portal and killing it in the Overworld");
                    return uneasyAllianceTask;
                }
                uneasyAllianceComplete = true;
            }

            if (!returnSenderComplete) {
                if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
                    setDebugState("Traveling to the Nether for a ghast");
                    return new DefaultGoToDimensionTask(Dimension.NETHER);
                }
                if (!returnSenderTask.isFinished()) {
                    setDebugState("Reflecting a ghast fireball back at its owner");
                    return returnSenderTask;
                }
                returnSenderComplete = true;
            }

            while (biomeIndex < netherBiomes.size()
                    && WorldHelper.getCurrentDimension() == Dimension.NETHER
                    && WorldVer.isBiomeAtPos(mod.getWorld(), netherBiomes.get(biomeIndex), mod.getPlayer().getBlockPos())) {
                biomeIndex++;
            }

            if (biomeIndex < netherBiomes.size()) {
                if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
                    setDebugState("Traveling to the Nether");
                    return new DefaultGoToDimensionTask(Dimension.NETHER);
                }
                setDebugState("Exploring Nether biome " + netherBiomes.get(biomeIndex).getValue());
                return new SearchWithinBiomeTask(netherBiomes.get(biomeIndex));
            }

            if (!localBreweryComplete) {
                if (!localBreweryTask.isFinished()) {
                    setDebugState("Retrieving an item from a brewing stand");
                    return localBreweryTask;
                }
                localBreweryComplete = true;
            }

            if (!chargeRespawnAnchorComplete) {
                if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
                    setDebugState("Traveling to the Nether to charge a respawn anchor");
                    return new DefaultGoToDimensionTask(Dimension.NETHER);
                }
                if (!chargeRespawnAnchorTask.isFinished()) {
                    setDebugState("Charging a respawn anchor four times");
                    return chargeRespawnAnchorTask;
                }
                chargeRespawnAnchorComplete = true;
            }

            if (WorldHelper.getCurrentDimension() == Dimension.OVERWORLD
                    && WorldHelper.inRangeXZ(mod.getPlayer(), subspaceTarget, 12)) {
                subspaceComplete = true;
                return null;
            }

            setDebugState("Traveling 7,000 blocks through the Nether");
            return new FastTravelTask(subspaceTarget, true);
        }

        @Override
        public boolean isFinished() {
            return subspaceComplete && localBreweryComplete && chargeRespawnAnchorComplete && ohShinyComplete
                    && thisBoatHasLegsComplete && feelsLikeHomeComplete && uneasyAllianceComplete && warPigsComplete;
        }

        @Override
        protected void onStop(Task interruptTask) {
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof AdventureObjectivesTask;
        }

        @Override
        protected String toDebugString() {
            return "Completing Oh Shiny, strider, ghast, bastion, respawn anchor, Local Brewery, Nether biome, and Subspace Bubble advancements";
        }
    }

    private static final class FeelsLikeHomeTask extends Task {
        private static final int REQUIRED_HORIZONTAL_DISTANCE = 50;
        private static final int LAVA_ROUTE_BLOCKS = 51;
        private static final int BOOST_INTERVAL_TICKS = 80;
        private final SearchChunkForBlockTask lavaSearcher = new SearchChunkForBlockTask(Blocks.LAVA);
        private final ThisBoatHasLegsTask striderTask = new ThisBoatHasLegsTask();
        private BlockPos routeStart;
        private BlockPos routeEnd;
        private List<BlockPos> lavaSourcesToPlace;
        private int nextLavaSource;
        private Task lavaPlacementTask;
        private Vec3d rideStart;
        private int ticksSinceBoost;
        private boolean finished;

        @Override
        protected void onStart() {
            routeStart = null;
            routeEnd = null;
            lavaSourcesToPlace = List.of();
            nextLavaSource = 0;
            lavaPlacementTask = null;
            rideStart = null;
            ticksSinceBoost = BOOST_INTERVAL_TICKS;
            finished = false;
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (finished) {
                return null;
            }

            if (!(mod.getPlayer().getVehicle() instanceof StriderEntity)) {
                if (!striderTask.isFinished()) {
                    setDebugState("Finding, saddling, and mounting a strider");
                    return striderTask;
                }
                return mountTrackedStrider(mod);
            }

            if (WorldHelper.getCurrentDimension() == Dimension.NETHER) {
                Optional<BlockPos> portal = mod.getMiscBlockTracker()
                        .getLastUsedNetherPortal(Dimension.NETHER);
                if (portal.isEmpty()) {
                    setDebugState("Locating a Nether portal while mounted");
                    return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
                }
                BlockPos portalPos = portal.get();
                if (portalPos.isWithinDistance(mod.getPlayer().getBlockPos(), 3)) {
                    setDebugState("Riding the strider through the Nether portal");
                    return new EnterNetherPortalTask(Dimension.OVERWORLD,
                            pos -> pos.isWithinDistance(portalPos, 3));
                }
                steer(mod, new Vec3d(portalPos.getX() + 0.5, portalPos.getY() + 1.0,
                        portalPos.getZ() + 0.5));
                setDebugState("Steering the strider toward the Overworld portal");
                return null;
            }

            if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
                return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
            }

            if (routeStart == null || routeEnd == null) {
                Optional<LavaRoute> route = findLavaRoute(mod);
                if (route.isEmpty()) {
                    setDebugState("Searching loaded Overworld chunks for a 50-block lava route");
                    return lavaSearcher;
                }
                routeStart = route.get().start;
                routeEnd = route.get().end;
                lavaSourcesToPlace = route.get().sourcesToPlace;
                if (mod.getPlayer().getBlockPos().getSquaredDistance(routeEnd)
                        < mod.getPlayer().getBlockPos().getSquaredDistance(routeStart)) {
                    BlockPos swap = routeStart;
                    routeStart = routeEnd;
                    routeEnd = swap;
                    Collections.reverse(lavaSourcesToPlace);
                }
            }

            while (nextLavaSource < lavaSourcesToPlace.size()
                    && mod.getWorld().getBlockState(lavaSourcesToPlace.get(nextLavaSource))
                    .getBlock() == Blocks.LAVA) {
                nextLavaSource++;
                lavaPlacementTask = null;
            }
            if (nextLavaSource < lavaSourcesToPlace.size()) {
                BlockPos lavaSource = lavaSourcesToPlace.get(nextLavaSource);
                if (!mod.getItemStorage().hasItem(Items.LAVA_BUCKET)) {
                    setDebugState("Collecting a lava bucket to extend the covered route");
                    return TaskCatalogue.getItemTask(Items.LAVA_BUCKET, 1);
                }
                if (lavaPlacementTask == null) {
                    lavaPlacementTask = new InteractWithBlockTask(
                            Items.LAVA_BUCKET, Direction.UP, lavaSource.down(), true);
                }
                setDebugState("Extending the covered lava route");
                return lavaPlacementTask;
            }

            BlockPos feetPos = mod.getPlayer().getBlockPos().down();
            if (mod.getWorld().getBlockState(feetPos).getBlock() != Blocks.LAVA
                    && !mod.getPlayer().isInLava()) {
                rideStart = null;
                BlockPos nearestRouteEnd = mod.getPlayer().getBlockPos().getSquaredDistance(routeStart)
                        <= mod.getPlayer().getBlockPos().getSquaredDistance(routeEnd) ? routeStart : routeEnd;
                steer(mod, Vec3d.ofCenter(nearestRouteEnd));
                setDebugState("Steering the strider onto the lava route");
                return null;
            }

            if (rideStart == null) {
                rideStart = mod.getPlayer().getPos();
            }
            double dx = mod.getPlayer().getX() - rideStart.x;
            double dz = mod.getPlayer().getZ() - rideStart.z;
            if (Math.sqrt(dx * dx + dz * dz) >= REQUIRED_HORIZONTAL_DISTANCE) {
                finished = true;
                releaseRideControls(mod);
                return null;
            }

            if (mod.getWorld().getBlockState(feetPos).getBlock() != Blocks.LAVA
                    && !mod.getPlayer().isInLava()) {
                rideStart = null;
            }
            steer(mod, Vec3d.ofCenter(routeEnd));
            if (++ticksSinceBoost >= BOOST_INTERVAL_TICKS
                    && mod.getSlotHandler().forceEquipItem(Items.WARPED_FUNGUS_ON_A_STICK)) {
                mod.getController().interactItem(mod.getPlayer(), Hand.MAIN_HAND);
                ticksSinceBoost = 0;
            }
            setDebugState("Riding across lava: " + (int) Math.sqrt(dx * dx + dz * dz) + "/50 blocks");
            return null;
        }

        private Task mountTrackedStrider(AltoClef mod) {
            Optional<StriderEntity> strider = mod.getEntityTracker().getTrackedEntities(StriderEntity.class)
                    .stream()
                    .filter(entity -> entity.isAlive() && entity.isSaddled())
                    .min((first, second) -> Double.compare(
                            first.squaredDistanceTo(mod.getPlayer()),
                            second.squaredDistanceTo(mod.getPlayer())));
            if (strider.isPresent()) {
                Hand hand = ThisBoatHasLegsTask.getEmptyInteractionHand(mod);
                if (hand != null) {
                    mod.getController().interactEntity(mod.getPlayer(), strider.get(), hand);
                }
                return null;
            }
            return new TimeoutWanderTask(true);
        }

        private Optional<LavaRoute> findLavaRoute(AltoClef mod) {
            BlockPos playerPos = mod.getPlayer().getBlockPos();
            LavaRoute closestRoute = null;
            double closestDistance = Double.POSITIVE_INFINITY;
            for (BlockPos lava : mod.getBlockScanner().getKnownLocations(Blocks.LAVA)) {
                if (!mod.getChunkTracker().isChunkLoaded(lava)
                        || mod.getWorld().getBlockState(lava).getBlock() != Blocks.LAVA) {
                    continue;
                }
                for (Direction direction : new Direction[]{
                        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    BlockPos end = lava.offset(direction, LAVA_ROUTE_BLOCKS - 1);
                    boolean valid = true;
                    List<BlockPos> sourcesToPlace = new ArrayList<>();
                    for (int step = 0; step < LAVA_ROUTE_BLOCKS; step++) {
                        BlockPos check = lava.offset(direction, step);
                        if (!mod.getChunkTracker().isChunkLoaded(check)
                                || !mod.getChunkTracker().isChunkLoaded(check.up())
                                || !mod.getChunkTracker().isChunkLoaded(check.up(2))) {
                            valid = false;
                            break;
                        }
                        var state = mod.getWorld().getBlockState(check);
                        if ((state.getBlock() != Blocks.LAVA && !state.isReplaceable())
                                || !WorldHelper.isSolidBlock(check.down())
                                || mod.getWorld().isSkyVisible(check)
                                || !mod.getWorld().getBlockState(check.up()).isAir()
                                || !mod.getWorld().getBlockState(check.up(2)).isAir()) {
                            valid = false;
                            break;
                        }
                        if (step % 7 == 0 && state.getBlock() != Blocks.LAVA) {
                            sourcesToPlace.add(check.toImmutable());
                        }
                    }
                    double distance = lava.getSquaredDistance(playerPos);
                    if (valid && distance < closestDistance) {
                        closestRoute = new LavaRoute(lava.toImmutable(), end, sourcesToPlace);
                        closestDistance = distance;
                    }
                }
            }
            return Optional.ofNullable(closestRoute);
        }

        private void steer(AltoClef mod, Vec3d target) {
            LookHelper.lookAt(mod, target, false);
            mod.getInputControls().hold(Input.MOVE_FORWARD);
            mod.getInputControls().release(Input.MOVE_BACK);
        }

        private void releaseRideControls(AltoClef mod) {
            mod.getInputControls().release(Input.MOVE_FORWARD);
            mod.getInputControls().release(Input.MOVE_BACK);
        }

        @Override
        protected void onStop(Task interruptTask) {
            releaseRideControls(AltoClef.getInstance());
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof FeelsLikeHomeTask;
        }

        @Override
        protected String toDebugString() {
            return "Riding a strider 50 blocks across lava in the Overworld";
        }

        private record LavaRoute(BlockPos start, BlockPos end, List<BlockPos> sourcesToPlace) {
        }
    }

    private static final class ChargeRespawnAnchorTask extends Task {
        private static final int MAX_CHARGES = 4;
        private BlockPos anchorPos;
        private PlaceBlockTask placeAnchorTask;

        @Override
        protected void onStart() {
            anchorPos = null;
            placeAnchorTask = null;
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
                setDebugState("Traveling to the Nether");
                return new DefaultGoToDimensionTask(Dimension.NETHER);
            }

            if (anchorPos == null) {
                anchorPos = findAnchorPosition(mod);
                if (anchorPos == null) {
                    setDebugState("Finding a safe place for a respawn anchor");
                    return new TimeoutWanderTask(2);
                }
                placeAnchorTask = new PlaceBlockTask(anchorPos, Blocks.RESPAWN_ANCHOR);
            }

            if (mod.getWorld().getBlockState(anchorPos).getBlock() != Blocks.RESPAWN_ANCHOR) {
                setDebugState("Placing respawn anchor");
                return placeAnchorTask;
            }

            int charges = mod.getWorld().getBlockState(anchorPos).get(RespawnAnchorBlock.CHARGES);
            if (charges >= MAX_CHARGES) {
                return null;
            }

            setDebugState("Charging respawn anchor (" + charges + "/" + MAX_CHARGES + ")");
            return new InteractWithBlockTask(Items.GLOWSTONE, anchorPos);
        }

        private BlockPos findAnchorPosition(AltoClef mod) {
            BlockPos playerPos = mod.getPlayer().getBlockPos();
            BlockPos best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (BlockPos candidate : BlockPos.iterate(
                    playerPos.add(-4, -1, -4), playerPos.add(4, 2, 4))) {
                if (!mod.getWorld().getBlockState(candidate).isReplaceable()
                        || !WorldHelper.isSolidBlock(candidate.down())) {
                    continue;
                }
                double distance = candidate.getSquaredDistance(playerPos);
                if (distance < bestDistance) {
                    best = candidate.toImmutable();
                    bestDistance = distance;
                }
            }
            return best;
        }

        @Override
        public boolean isFinished() {
            AltoClef mod = AltoClef.getInstance();
            return anchorPos != null
                    && WorldHelper.getCurrentDimension() == Dimension.NETHER
                    && mod.getWorld().getBlockState(anchorPos).getBlock() == Blocks.RESPAWN_ANCHOR
                    && mod.getWorld().getBlockState(anchorPos).get(RespawnAnchorBlock.CHARGES) >= MAX_CHARGES;
        }

        @Override
        protected void onStop(Task interruptTask) {
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof ChargeRespawnAnchorTask;
        }

        @Override
        protected String toDebugString() {
            return "Charging a respawn anchor";
        }
    }

    private static final class WarPigsTask extends Task {
        private static final double CHEST_MARKER_RANGE_SQUARED = 160 * 160;
        private final SearchChunkForBlockTask bastionSearcher =
                new SearchChunkForBlockTask(Blocks.GILDED_BLACKSTONE);
        private final Set<BlockPos> attemptedChests = new HashSet<>();
        private OpenBastionChestTask openChestTask;
        private EquipArmorTask goldHelmetTask;
        private boolean goldHelmetEquipped;
        private boolean finished;

        @Override
        protected void onStart() {
            attemptedChests.clear();
            openChestTask = null;
            goldHelmetTask = new EquipArmorTask(Items.GOLDEN_HELMET);
            goldHelmetEquipped = false;
            finished = false;
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (finished) {
                return null;
            }
            if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
                setDebugState("Traveling to the Nether to locate a bastion");
                return new DefaultGoToDimensionTask(Dimension.NETHER);
            }

            if (!goldHelmetEquipped) {
                if (!goldHelmetTask.isFinished()) {
                    setDebugState("Obtaining and wearing a golden helmet before entering the bastion");
                    return goldHelmetTask;
                }
                goldHelmetEquipped = true;
            }

            if (openChestTask != null) {
                if (!openChestTask.isFinished()) {
                    return openChestTask;
                }
                if (openChestTask.hasLoot()) {
                    finished = true;
                    return null;
                }
                attemptedChests.add(openChestTask.getChestPos());
                openChestTask = null;
            }

            Optional<BlockPos> chest = findUnopenedBastionChest(mod);
            if (chest.isPresent()) {
                openChestTask = new OpenBastionChestTask(chest.get());
                setDebugState("Opening bastion chest at " + chest.get().toShortString());
                return openChestTask;
            }

            setDebugState("Exploring Nether chunks for bastions and gilded blackstone");
            return bastionSearcher;
        }

        private Optional<BlockPos> findUnopenedBastionChest(AltoClef mod) {
            List<BlockPos> markers = mod.getBlockScanner().getKnownLocations(Blocks.GILDED_BLACKSTONE);
            return mod.getBlockScanner().getKnownLocations(Blocks.CHEST).stream()
                    .filter(pos -> !attemptedChests.contains(pos))
                    .filter(pos -> mod.getChunkTracker().isChunkLoaded(pos))
                    .filter(pos -> mod.getWorld().getBlockState(pos).getBlock() == Blocks.CHEST)
                    .filter(WorldHelper::isUnopenedChest)
                    .filter(chest -> markers.stream().anyMatch(marker ->
                            marker.getSquaredDistance(chest) <= CHEST_MARKER_RANGE_SQUARED))
                    .min((first, second) -> Double.compare(
                            first.getSquaredDistance(mod.getPlayer().getBlockPos()),
                            second.getSquaredDistance(mod.getPlayer().getBlockPos())));
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        protected void onStop(Task interruptTask) {
            StorageHelper.closeScreen();
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof WarPigsTask;
        }

        @Override
        protected String toDebugString() {
            return "Finding and opening an unopened bastion chest";
        }
    }

    private static final class OpenBastionChestTask extends Task {
        private final BlockPos chestPos;
        private boolean attempted;
        private boolean hasLoot;

        private OpenBastionChestTask(BlockPos chestPos) {
            this.chestPos = chestPos;
        }

        @Override
        protected void onStart() {
            attempted = false;
            hasLoot = false;
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (MinecraftClient.getInstance().player.currentScreenHandler
                    instanceof GenericContainerScreenHandler handler) {
                int chestSlots = handler.getRows() * 9;
                for (int i = 0; i < chestSlots; i++) {
                    if (!handler.getSlot(i).getStack().isEmpty()) {
                        hasLoot = true;
                        break;
                    }
                }
                attempted = true;
                StorageHelper.closeScreen();
                return null;
            }

            if (!mod.getChunkTracker().isChunkLoaded(chestPos)
                    || mod.getWorld().getBlockState(chestPos).getBlock() != Blocks.CHEST) {
                attempted = true;
                return null;
            }

            setDebugState("Interacting with bastion chest");
            return new InteractWithBlockTask(chestPos);
        }

        private BlockPos getChestPos() {
            return chestPos;
        }

        private boolean hasLoot() {
            return hasLoot;
        }

        @Override
        public boolean isFinished() {
            return attempted;
        }

        @Override
        protected void onStop(Task interruptTask) {
            StorageHelper.closeScreen();
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof OpenBastionChestTask task && task.chestPos.equals(chestPos);
        }

        @Override
        protected String toDebugString() {
            return "Opening chest at " + chestPos.toShortString();
        }
    }

    private static final class ThisBoatHasLegsTask extends AbstractDoToEntityTask {
        private final Set<BlockPos> checkedChests = new HashSet<>();
        private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(true);
        private LootContainerTask chestLootTask;
        private boolean boosted;

        private ThisBoatHasLegsTask() {
            super(3);
        }

        @Override
        protected void onStart() {
            super.onStart();
            checkedChests.clear();
            chestLootTask = null;
            boosted = false;
            AltoClef.getInstance().getBehaviour().push();
            AltoClef.getInstance().getBehaviour().addProtectedItems(
                    Items.SADDLE, Items.WARPED_FUNGUS_ON_A_STICK);
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (boosted) {
                return null;
            }
            if (mod.getPlayer().getVehicle() instanceof StriderEntity) {
                if (!mod.getSlotHandler().forceEquipItem(Items.WARPED_FUNGUS_ON_A_STICK)) {
                    return TaskCatalogue.getItemTask(Items.WARPED_FUNGUS_ON_A_STICK, 1);
                }
                ActionResult result = mod.getController().interactItem(mod.getPlayer(), Hand.MAIN_HAND);
                if (result.isAccepted()) {
                    boosted = true;
                }
                return null;
            }
            boolean hasSaddle = mod.getItemStorage().hasItem(Items.SADDLE);
            Optional<StriderEntity> saddledStrider = mod.getEntityTracker().getTrackedEntities(StriderEntity.class)
                    .stream()
                    .filter(StriderEntity::isSaddled)
                    .min((first, second) -> Double.compare(
                            first.squaredDistanceTo(mod.getPlayer()),
                            second.squaredDistanceTo(mod.getPlayer())));
            if (!hasSaddle && saddledStrider.isEmpty()) {
                if (TaskCatalogue.taskExists(Items.SADDLE)) {
                    return TaskCatalogue.getItemTask(Items.SADDLE, 1);
                }
                if (chestLootTask != null) {
                    if (!chestLootTask.isFinished()) {
                        setDebugState("Searching a Nether chest for a saddle");
                        return chestLootTask;
                    }
                    checkedChests.add(chestLootTask.chest);
                    chestLootTask = null;
                }
                Optional<BlockPos> chest = mod.getBlockScanner().getNearestBlock(
                        pos -> !checkedChests.contains(pos), Blocks.CHEST, Blocks.TRAPPED_CHEST);
                if (chest.isPresent()) {
                    chestLootTask = new LootContainerTask(chest.get(), List.of(Items.SADDLE));
                    setDebugState("Searching a Nether chest for a saddle");
                    return chestLootTask;
                }
                mod.getSlotHandler().forceEquipItem(Items.WARPED_FUNGUS_ON_A_STICK);
                setDebugState("Searching for striders and a saddle");
                return wanderTask;
            }

            mod.getSlotHandler().forceEquipItem(Items.WARPED_FUNGUS_ON_A_STICK);
            return super.onTick();
        }

        @Override
        protected Task onEntityInteract(AltoClef mod, Entity entity) {
            if (!(entity instanceof StriderEntity strider)) {
                return null;
            }
            if (!strider.isSaddled()) {
                if (!mod.getSlotHandler().forceEquipItem(Items.SADDLE)) {
                    return TaskCatalogue.taskExists(Items.SADDLE)
                            ? TaskCatalogue.getItemTask(Items.SADDLE, 1)
                            : null;
                }
                mod.getController().interactEntity(mod.getPlayer(), strider, Hand.MAIN_HAND);
                return null;
            }

            Hand mountHand = getEmptyInteractionHand(mod);
            if (mountHand != null) {
                mod.getController().interactEntity(mod.getPlayer(), strider, mountHand);
            }
            return null;
        }

        @Override
        protected Optional<Entity> getEntityTarget(AltoClef mod) {
            boolean hasSaddle = mod.getItemStorage().hasItem(Items.SADDLE);
            return mod.getEntityTracker().getClosestEntity(mod.getPlayer().getPos(),
                    entity -> entity instanceof StriderEntity strider
                            && strider.isAlive()
                            && (strider.isSaddled() || hasSaddle),
                    StriderEntity.class);
        }

        @Override
        protected boolean isSubEqual(AbstractDoToEntityTask other) {
            return other instanceof ThisBoatHasLegsTask;
        }

        @Override
        protected void onStop(Task interruptTask) {
            super.onStop(interruptTask);
            AltoClef.getInstance().getBehaviour().pop();
        }

        @Override
        public boolean isFinished() {
            return boosted;
        }

        @Override
        protected String toDebugString() {
            return "Riding and boosting a strider";
        }

        private static Hand getEmptyInteractionHand(AltoClef mod) {
            if (StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT).isEmpty()) {
                return Hand.OFF_HAND;
            }

            var inventory = mod.getPlayer().getInventory();
            for (int slot = 0; slot < 9; slot++) {
                if (inventory.getStack(slot).isEmpty()) {
                    inventory.selectedSlot = slot;
                    return Hand.MAIN_HAND;
                }
            }

            for (int slot = 9; slot < inventory.size(); slot++) {
                if (inventory.getStack(slot).isEmpty()) {
                    mod.getSlotHandler().clickSlot(Slot.getFromCurrentScreenInventory(slot),
                            inventory.selectedSlot, SlotActionType.SWAP);
                    return null;
                }
            }
            return null;
        }
    }

    private static final class UneasyAllianceTask extends Task {
        private static final int PORTAL_OBSIDIAN = 24;
        private final TimerGame ghastWaitTimer = new TimerGame(30);
        private final TimerGame overworldCheckTimer = new TimerGame(10);
        private BlockPos netherPortalOrigin;
        private BlockPos overworldPortalOrigin;
        private LargeGhastPortalTask netherPortalTask;
        private LargeGhastPortalTask overworldPortalTask;
        private KillEntityTask killGhastTask;
        private GhastEntity targetGhast;
        private GhastEntity killTargetGhast;
        private boolean netherPortalBuilt;
        private boolean overworldPortalBuilt;
        private boolean ghastWaitTimerStarted;
        private boolean overworldCheckTimerStarted;
        private boolean finished;

        @Override
        protected void onStart() {
            AltoClef mod = AltoClef.getInstance();
            netherPortalOrigin = null;
            overworldPortalOrigin = null;
            netherPortalTask = null;
            overworldPortalTask = null;
            killGhastTask = null;
            targetGhast = null;
            killTargetGhast = null;
            netherPortalBuilt = false;
            overworldPortalBuilt = false;
            ghastWaitTimerStarted = false;
            overworldCheckTimerStarted = false;
            finished = false;
            mod.getBehaviour().push();
            mod.getBehaviour().addProtectedItems(Items.OBSIDIAN, Items.FLINT_AND_STEEL,
                    Items.BOW, Items.ARROW);
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (finished) {
                return null;
            }

            if (WorldHelper.getCurrentDimension() == Dimension.NETHER) {
                if (netherPortalOrigin == null) {
                    BlockPos player = mod.getPlayer().getBlockPos();
                    netherPortalOrigin = new BlockPos(player.getX() - 3, player.getY(), player.getZ() + 4);
                }
                if (!netherPortalBuilt) {
                    if (mod.getItemStorage().getItemCount(Items.OBSIDIAN) < PORTAL_OBSIDIAN) {
                        setDebugState("Collecting obsidian for a ghast-sized Nether portal");
                        return TaskCatalogue.getItemTask(Items.OBSIDIAN, PORTAL_OBSIDIAN);
                    }
                    if (!mod.getItemStorage().hasItem(Items.FLINT_AND_STEEL)) {
                        return TaskCatalogue.getItemTask(Items.FLINT_AND_STEEL, 1);
                    }
                    if (netherPortalTask == null) {
                        netherPortalTask = new LargeGhastPortalTask(netherPortalOrigin);
                    }
                    if (!netherPortalTask.isFinished()) {
                        setDebugState("Building and lighting a ghast-sized Nether portal");
                        return netherPortalTask;
                    }
                    netherPortalBuilt = true;
                }

                if (!overworldPortalBuilt) {
                    setDebugState("Entering the large portal to build its Overworld counterpart");
                    return new EnterNetherPortalTask(Dimension.OVERWORLD,
                            pos -> isNearPortal(pos, netherPortalOrigin));
                }

                if (!ghastWaitTimerStarted) {
                    ghastWaitTimer.reset();
                    ghastWaitTimerStarted = true;
                }
                if (!ghastWaitTimer.elapsed()) {
                    BlockPos waitingPoint = netherPortalOrigin.add(3, 0, 3);
                    if (!waitingPoint.isWithinDistance(mod.getPlayer().getBlockPos(), 5)) {
                        setDebugState("Waiting near the Nether portal for a ghast");
                        return new GetToBlockTask(waitingPoint);
                    }
                    setDebugState("Luring Nether ghasts toward the large portal");
                    return null;
                }

                ghastWaitTimerStarted = false;
                overworldCheckTimerStarted = false;
                setDebugState("Checking the Overworld portal for a transferred ghast");
                return new EnterNetherPortalTask(Dimension.OVERWORLD,
                        pos -> isNearPortal(pos, netherPortalOrigin));
            }

            if (overworldPortalOrigin == null) {
                BlockPos player = mod.getPlayer().getBlockPos();
                overworldPortalOrigin = new BlockPos(player.getX() + 8, player.getY(), player.getZ() + 4);
            }
            if (!overworldPortalBuilt) {
                if (mod.getItemStorage().getItemCount(Items.OBSIDIAN) < PORTAL_OBSIDIAN) {
                    setDebugState("Collecting obsidian for the Overworld ghast portal");
                    return TaskCatalogue.getItemTask(Items.OBSIDIAN, PORTAL_OBSIDIAN);
                }
                if (overworldPortalTask == null) {
                    overworldPortalTask = new LargeGhastPortalTask(overworldPortalOrigin);
                }
                if (!overworldPortalTask.isFinished()) {
                    setDebugState("Building a ghast-sized Overworld portal");
                    return overworldPortalTask;
                }
                overworldPortalBuilt = true;
                ghastWaitTimerStarted = false;
            }

            if (killTargetGhast != null && !killTargetGhast.isAlive()) {
                finished = true;
                return null;
            }
            targetGhast = mod.getEntityTracker().getTrackedEntities(GhastEntity.class).stream()
                    .filter(Entity::isAlive)
                    .min((first, second) -> Double.compare(
                            first.squaredDistanceTo(mod.getPlayer()),
                            second.squaredDistanceTo(mod.getPlayer())))
                    .orElse(null);
            if (targetGhast != null) {
                if (killGhastTask == null || !targetGhast.equals(killTargetGhast)) {
                    killGhastTask = new KillEntityTask(targetGhast);
                    killTargetGhast = targetGhast;
                }
                setDebugState("Killing the ghast that reached the Overworld");
                return killGhastTask;
            }

            if (!overworldCheckTimerStarted) {
                overworldCheckTimer.reset();
                overworldCheckTimerStarted = true;
            }
            if (!overworldCheckTimer.elapsed()) {
                BlockPos waitingPoint = overworldPortalOrigin.add(3, 0, 3);
                if (!waitingPoint.isWithinDistance(mod.getPlayer().getBlockPos(), 5)) {
                    setDebugState("Waiting near the Overworld portal for a ghast");
                    return new GetToBlockTask(waitingPoint);
                }
                setDebugState("Watching the Overworld portal for a transferred ghast");
                return null;
            }

            overworldCheckTimerStarted = false;
            setDebugState("Returning to the Nether to lure a ghast through the portal");
            return new EnterNetherPortalTask(Dimension.NETHER,
                    pos -> isNearPortal(pos, overworldPortalOrigin));
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        protected void onStop(Task interruptTask) {
            AltoClef.getInstance().getBehaviour().pop();
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof UneasyAllianceTask;
        }

        @Override
        protected String toDebugString() {
            return "Bringing a ghast through a large portal and killing it in the Overworld";
        }

        private static boolean isNearPortal(BlockPos pos, BlockPos origin) {
            return Math.abs(pos.getX() - origin.getX()) <= 7
                    && Math.abs(pos.getY() - origin.getY()) <= 7
                    && Math.abs(pos.getZ() - origin.getZ()) <= 7;
        }
    }

    private static final class LargeGhastPortalTask extends Task {
        private final BlockPos origin;
        private final List<BlockPos> frame = new ArrayList<>();
        private final List<BlockPos> interior = new ArrayList<>();

        private LargeGhastPortalTask(BlockPos origin) {
            this.origin = origin;
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < 7; x++) {
                    if ((x == 0 || x == 6 || y == 0 || y == 6)
                            && !((x == 0 || x == 6) && (y == 0 || y == 6))) {
                        frame.add(origin.add(x, y, 0));
                    } else if (x > 0 && x < 6 && y > 0 && y < 6) {
                        interior.add(origin.add(x, y, 0));
                    }
                }
            }
        }

        @Override
        protected void onStart() {
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            for (BlockPos pos : interior) {
                if (!mod.getWorld().getBlockState(pos).isAir()
                        && mod.getWorld().getBlockState(pos).getBlock() != Blocks.NETHER_PORTAL) {
                    setDebugState("Clearing the large portal opening");
                    return new DestroyBlockTask(pos);
                }
            }

            for (BlockPos pos : frame) {
                if (mod.getWorld().getBlockState(pos).getBlock() == Blocks.OBSIDIAN) {
                    continue;
                }
                if (!mod.getWorld().getBlockState(pos).isAir()) {
                    return new DestroyBlockTask(pos);
                }
                setDebugState("Building the large obsidian portal frame");
                return new PlaceBlockTask(pos, Blocks.OBSIDIAN);
            }

            if (interior.stream().anyMatch(pos ->
                    mod.getWorld().getBlockState(pos).getBlock() == Blocks.NETHER_PORTAL)) {
                for (BlockPos pos : interior) {
                    if (mod.getWorld().getBlockState(pos).getBlock() == Blocks.NETHER_PORTAL) {
                        mod.getBlockScanner().addBlock(Blocks.NETHER_PORTAL, pos);
                    }
                }
                return null;
            }

            setDebugState("Lighting the ghast-sized portal");
            return new InteractWithBlockTask(new ItemTarget(Items.FLINT_AND_STEEL, 1),
                    Direction.UP, origin.add(3, -1, 0), true);
        }

        @Override
        public boolean isFinished() {
            AltoClef mod = AltoClef.getInstance();
            return frame.stream().allMatch(pos ->
                            mod.getWorld().getBlockState(pos).getBlock() == Blocks.OBSIDIAN)
                    && interior.stream().anyMatch(pos ->
                            mod.getWorld().getBlockState(pos).getBlock() == Blocks.NETHER_PORTAL);
        }

        @Override
        protected void onStop(Task interruptTask) {
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof LargeGhastPortalTask task && task.origin.equals(origin);
        }

        @Override
        protected String toDebugString() {
            return "Building a large ghast portal at " + origin.toShortString();
        }
    }

    private static final class OhShinyTask extends AbstractDoToEntityTask {
        private static final Item[] GOLDEN_ARMOR = {
                Items.GOLDEN_HELMET,
                Items.GOLDEN_CHESTPLATE,
                Items.GOLDEN_LEGGINGS,
                Items.GOLDEN_BOOTS
        };
        private boolean completed;

        private OhShinyTask() {
            super(3);
        }

        @Override
        protected void onStart() {
            super.onStart();
            AltoClef mod = AltoClef.getInstance();
            completed = false;
            mod.getBehaviour().push();
            mod.getBehaviour().addProtectedItems(Items.GOLD_INGOT);
            mod.getBehaviour().addForceFieldExclusion(entity -> entity instanceof PiglinEntity);
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (isWearingGoldenArmor()) {
                setDebugState("Removing golden armor before approaching a piglin");
                return new EquipArmorTask(Items.IRON_HELMET, Items.IRON_CHESTPLATE,
                        Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            }
            if (mod.getEntityTracker().getTrackedEntities(PiglinEntity.class).stream()
                    .anyMatch(piglin -> piglin.isAdult() && EntityHelper.isTradingPiglin(piglin))) {
                completed = true;
                return null;
            }
            if (completed) {
                return null;
            }
            return super.onTick();
        }

        @Override
        protected Task onEntityInteract(AltoClef mod, Entity entity) {
            if (EntityHelper.isTradingPiglin(entity)) {
                completed = true;
                return null;
            }
            if (!mod.getItemStorage().hasItem(Items.GOLD_INGOT)) {
                return TaskCatalogue.getItemTask(Items.GOLD_INGOT, 1);
            }
            if (mod.getSlotHandler().forceEquipItem(Items.GOLD_INGOT)) {
                mod.getController().interactEntity(mod.getPlayer(), entity, Hand.MAIN_HAND);
            }
            return null;
        }

        @Override
        protected Optional<Entity> getEntityTarget(AltoClef mod) {
            return mod.getEntityTracker().getClosestEntity(mod.getPlayer().getPos(),
                    entity -> entity instanceof PiglinEntity piglin
                            && piglin.isAdult()
                            && !EntityHelper.isTradingPiglin(piglin),
                    PiglinEntity.class);
        }

        @Override
        protected boolean isSubEqual(AbstractDoToEntityTask other) {
            return other instanceof OhShinyTask;
        }

        @Override
        protected void onStop(Task interruptTask) {
            super.onStop(interruptTask);
            AltoClef.getInstance().getBehaviour().pop();
        }

        @Override
        public boolean isFinished() {
            return completed;
        }

        @Override
        protected String toDebugString() {
            return "Distracting a piglin with gold";
        }

        private static boolean isWearingGoldenArmor() {
            for (Item armor : GOLDEN_ARMOR) {
                if (StorageHelper.isArmorEquipped(armor)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class LocalBreweryTask extends Task {
        private static final BrewingStandSlot[] POTION_SLOTS = {
                BrewingStandSlot.LEFT_POTION,
                BrewingStandSlot.MIDDLE_POTION,
                BrewingStandSlot.RIGHT_POTION
        };
        private boolean bottleRetrieved;
        private boolean finished;

        @Override
        protected void onStart() {
            bottleRetrieved = false;
            finished = false;
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (MinecraftClient.getInstance().player.currentScreenHandler instanceof BrewingStandScreenHandler) {
                for (BrewingStandSlot potionSlot : POTION_SLOTS) {
                    if (!StorageHelper.getItemStackInSlot(potionSlot).isEmpty()) {
                        mod.getSlotHandler().clickSlot(potionSlot, 0, SlotActionType.QUICK_MOVE);
                        if (StorageHelper.getItemStackInSlot(potionSlot).isEmpty()) {
                            finished = true;
                            StorageHelper.closeScreen();
                        }
                        return null;
                    }
                }

                if (bottleRetrieved) {
                    return null;
                }

                List<Slot> bottles = mod.getItemStorage()
                        .getSlotsWithItemPlayerInventory(false, Items.GLASS_BOTTLE);
                if (bottles.isEmpty()) {
                    return TaskCatalogue.getItemTask(Items.GLASS_BOTTLE, 1);
                }
                mod.getSlotHandler().clickSlot(bottles.getFirst(), 0, SlotActionType.QUICK_MOVE);
                bottleRetrieved = true;
                return null;
            }

            if (!mod.getBlockScanner().anyFound(Blocks.BREWING_STAND)) {
                setDebugState("Placing a brewing stand");
                return new PlaceBlockNearbyTask(Blocks.BREWING_STAND);
            }

            BlockPos stand = mod.getBlockScanner().getNearestBlock(Blocks.BREWING_STAND).orElseThrow(
                    () -> new IllegalStateException("Brewing stand disappeared from the block scanner."));
            setDebugState("Opening brewing stand at " + stand.toShortString());
            return new InteractWithBlockTask(stand);
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        protected void onStop(Task interruptTask) {
            StorageHelper.closeScreen();
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof LocalBreweryTask;
        }

        @Override
        protected String toDebugString() {
            return "Retrieving an item from a brewing stand";
        }
    }

    private static final class ReturnSenderTask extends Task {
        private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(true);
        private GhastEntity ghast;
        private float initialHealth;
        private boolean reflectedBallHit;
        private boolean reflectedTowardGhast;
        private final Set<FireballEntity> attackedFireballs =
                Collections.newSetFromMap(new IdentityHashMap<>());

        @Override
        protected void onStart() {
            ghast = null;
            initialHealth = 0;
            reflectedBallHit = false;
            reflectedTowardGhast = false;
            attackedFireballs.clear();
        }

        @Override
        protected Task onTick() {
            AltoClef mod = AltoClef.getInstance();
            if (ghast == null || !ghast.isAlive()) {
                ghast = mod.getEntityTracker().getTrackedEntities(GhastEntity.class).stream()
                        .min((first, second) -> Double.compare(
                                first.squaredDistanceTo(mod.getPlayer()),
                                second.squaredDistanceTo(mod.getPlayer())))
                        .orElse(null);
                if (ghast == null) {
                    setDebugState("Searching the Nether for a ghast");
                    return wanderTask;
                }
                initialHealth = ghast.getHealth();
                reflectedBallHit = false;
                reflectedTowardGhast = false;
                attackedFireballs.clear();
            }

            if (reflectedTowardGhast && ghast.getHealth() < initialHealth) {
                reflectedBallHit = true;
            }
            if (reflectedBallHit) {
                if (!ghast.isAlive()) return null;
                setDebugState("Finishing off the ghast hit by its reflected fireball");
                return new KillEntityTask(ghast);
            }

            if (ghast.squaredDistanceTo(mod.getPlayer()) > 12 * 12) {
                setDebugState("Getting within range of a ghast");
                return new GetToEntityTask(ghast, 10);
            }

            LookHelper.lookAt(mod, ghast.getEyePos());
            for (FireballEntity fireball : mod.getEntityTracker().getTrackedEntities(FireballEntity.class)) {
                if (fireball.getOwner() != ghast) {
                    continue;
                }

                Vec3d towardGhast = ghast.getEyePos().subtract(fireball.getPos());
                if (attackedFireballs.contains(fireball)
                        && fireball.getVelocity().dotProduct(towardGhast) > 0) {
                    reflectedTowardGhast = true;
                }

                if (fireball.squaredDistanceTo(mod.getPlayer()) <= 36) {
                    Vec3d towardPlayer = mod.getPlayer().getPos().subtract(fireball.getPos());
                    if (fireball.getVelocity().dotProduct(towardPlayer) > 0) {
                        mod.getControllerExtras().attack(fireball);
                        attackedFireballs.add(fireball);
                    }
                }
            }
            setDebugState("Waiting for and reflecting a ghast fireball");
            return null;
        }

        @Override
        public boolean isFinished() {
            return reflectedTowardGhast && ghast != null && !ghast.isAlive();
        }

        @Override
        protected void onStop(Task interruptTask) {
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof ReturnSenderTask;
        }

        @Override
        protected String toDebugString() {
            return "Reflecting a ghast fireball";
        }
    }

    private static final class Requirement {
        private final String advancement;
        private final Item item;
        private final int count;

        private Requirement(String advancement, Item item, int count) {
            this.advancement = advancement;
            this.item = item;
            this.count = count;
        }
    }
}
