package org.wisp.trophyplanet.commands

import com.fs.starfarer.api.Global
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.Console
import org.wisp.trophyplanet.LifecyclePlugin

class ReloadSettings : BaseCommand {
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (!context.isInCampaign || Global.getSector().playerFleet.isInHyperspace) {
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }

        LifecyclePlugin.reload()

        Console.showMessage("Reloaded")
        return BaseCommand.CommandResult.SUCCESS
    }
}