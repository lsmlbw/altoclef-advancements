package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.tasksystem.Task;

public class StatusCommand extends Command {
    public StatusCommand() {
        super("status", "Get status of currently executing command");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        boolean paused = mod.isPaused();
        Task currentTask = paused
                ? mod.getStoredTask()
                : mod.getUserTaskChain().getCurrentTask();
        if (currentTask == null) {
            mod.log("No tasks currently running.");
        } else {
            mod.log((paused ? "PAUSED TASK: " : "CURRENT TASK: ") + currentTask);
        }
        finish();
    }
}