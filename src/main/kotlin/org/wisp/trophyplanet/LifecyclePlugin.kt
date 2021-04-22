package org.wisp.trophyplanet

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.thoughtworks.xstream.XStream
import org.wisp.trophyplanet.Constants.MOD_ID
import wisp.questgiver.wispLib.isSolidPlanet

class LifecyclePlugin : BaseModPlugin() {
    override fun onGameLoad(newGame: Boolean) {
        super.onGameLoad(newGame)

        val settings =
            Global.getSettings().getMergedJSONForMod("data/config/modSettings.json", MOD_ID)
                .getJSONObject("wisp_trophy-planet")
                .let { settings ->
                    Settings(
                        showStoredShips = settings.getBoolean("showShipsInStorage"),
                        showShipsForSale = settings.getBoolean("showShipsForSale")
                    )
                }

        Global.getSector().addTransientListener(object : BaseCampaignEventListener(false) {
            override fun reportFleetJumped(
                fleet: CampaignFleetAPI?,
                from: SectorEntityToken?,
                to: JumpPointAPI.JumpDestination?
            ) {
                super.reportFleetJumped(fleet, from, to)

                if (fleet != Global.getSector().playerFleet) return

                to?.destination?.starSystem ?: return

                addScriptForSystem(to.destination.starSystem, settings)
            }
        })

        if ((Global.getSector().currentLocation as? StarSystemAPI) != null) {
            addScriptForSystem(Global.getSector().currentLocation as StarSystemAPI, settings)
        }
    }

    private fun addScriptForSystem(starSystemAPI: StarSystemAPI, settings: Settings) {
        starSystemAPI.planets
            .filter { it.isSolidPlanet }//&& it.faction.isPlayerFaction }
            .forEach { planet ->
                Global.getSector().addTransientScript(TrophyPlanetScript().apply {
                    this.planet = planet
                    this.settings = settings
                })
            }
    }

    /**
     * Tell the XML serializer to use custom naming, so that moving or renaming classes doesn't break saves.
     */
    override fun configureXStream(x: XStream) {
        super.configureXStream(x)
    }

    data class Settings(
        val showStoredShips: Boolean,
        val showShipsForSale: Boolean
    )
}