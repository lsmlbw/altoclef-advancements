package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.entity.KillEntityTask;
import adris.altoclef.tasks.movement.FollowPlayerTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Optional;

public class PunkCommand extends Command {
    public PunkCommand() {
        super("punk", "Repeatedly targets a player by username",
                new StringArg("username"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String username = parser.get(String.class);
        if (username.equalsIgnoreCase(mod.getPlayer().getName().getString())) {
            mod.logWarning("You cannot target yourself.");
            finish();
            return;
        }
        mod.runUserTask(new PunkPlayerTask(username), this::finish);
    }

    private static final class PunkPlayerTask extends Task {
        private final String username;

        private PunkPlayerTask(String username) {
            this.username = username;
        }

        @Override
        protected void onStart() {
        }

        @Override
        protected Task onTick() {
            Optional<PlayerEntity> player = AltoClef.getInstance().getEntityTracker().getPlayerEntity(username);
            if (player.isPresent()) {
                return new KillEntityTask(player.get());
            }
            return new FollowPlayerTask(username);
        }

        @Override
        protected void onStop(Task interruptTask) {
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof PunkPlayerTask && ((PunkPlayerTask) other).username.equals(username);
        }

        @Override
        protected String toDebugString() {
            return "Repeatedly targeting player " + username;
        }
    }
}
