package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.entity.ShootArrowSimpleProjectileTask;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;

import java.util.Comparator;
import java.util.Locale;

public class ShootCommand extends Command {
    public ShootCommand() {
        super("shoot", "Shoots an arrow at a nearby entity or named target",
                new StringArg("entity", null));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String targetName = parser.get(String.class);
        Entity target;
        if (targetName == null) {
            target = mod.getEntityTracker().getHostiles().stream()
                    .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mod.getPlayer())))
                    .orElse(null);
        } else {
            String query = targetName.toLowerCase(Locale.ROOT);
            target = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (Entity entity : mod.getWorld().getEntities()) {
                if (!entity.isAlive() || !(entity instanceof LivingEntity) || entity == mod.getPlayer()) {
                    continue;
                }
                if (!entity.getName().getString().equalsIgnoreCase(targetName)
                        && !entity.getType().getTranslationKey().toLowerCase(Locale.ROOT).endsWith(query)) {
                    continue;
                }
                double distance = entity.squaredDistanceTo(mod.getPlayer());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    target = entity;
                }
            }
        }

        if (target == null) {
            mod.logWarning(targetName == null
                    ? "No nearby hostile entity to shoot at."
                    : "No loaded entity matched \"" + targetName + "\".");
            finish();
            return;
        }
        if (!mod.getItemStorage().hasItem(Items.BOW)
                || !(mod.getItemStorage().hasItem(Items.ARROW)
                || mod.getItemStorage().hasItem(Items.SPECTRAL_ARROW)
                || mod.getItemStorage().hasItem(Items.TIPPED_ARROW))) {
            mod.logWarning("Shooting requires a bow and at least one arrow.");
            finish();
            return;
        }

        mod.runUserTask(new ShootArrowSimpleProjectileTask(target), this::finish);
    }
}
