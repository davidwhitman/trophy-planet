package org.wisp.trophyplanet

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.util.Misc
import com.thoughtworks.xstream.XStream
import org.lwjgl.input.Keyboard
import org.wisp.trophyplanet.Constants.MOD_ID
import org.wisp.trophyplanet.lib.tryGetBoolean
import org.wisp.trophyplanet.lib.tryGetFloat

class LifecyclePlugin : BaseModPlugin() {
    private val entitiestoKeepAcrossSavingProcess = mutableListOf<Pair<StarSystemAPI, SectorEntityToken>>()

    companion object {
        var settings: Settings? = null

        fun reload() {
            Global.getSector().starSystems
                .forEach { sys ->
                    sys.allEntities
                        .filter { it.tags.any { it.startsWith(Constants.TROPHY_ENTITY_TAG_PREFIX) } }
                        .toList()
                        .forEach { sys.removeEntity(it) }
                }


            settings =
                Global.getSettings().getMergedJSONForMod("data/config/modSettings.json", MOD_ID)
                    .getJSONObject("wisp_trophy-planet")
                    .let { settings ->
                        Settings(
                            showStoredShips = settings.tryGetBoolean("showShipsInStorage") { true },
                            showShipsForSale = settings.tryGetBoolean("showShipsForSale") { true },
                            normalizingAmount = settings.tryGetFloat("normalizingAmount") { 0.8f },
                            storedSpriteScaleModifier = settings.tryGetFloat("storedSpriteScaleModifier") { 0.5f },
                            forSaleSpriteScaleModifier = settings.tryGetFloat("forSaleSpriteScaleModifier") { 0.8f },
                            alphaMult = settings.tryGetFloat("alphaMult") { 0.2f },
                            shouldFadeOnZoomInForStorage = settings.tryGetBoolean("shouldFadeOnZoomInForStorage") { true },
                            shouldFadeOnZoomInForSale = settings.tryGetBoolean("shouldFadeOnZoomInForSale") { true },
                            toggleHotkey = kotlin.runCatching { Keyboard.getKeyIndex(settings.getString("toggleHotkey")) }
                                .getOrDefault(Keyboard.KEY_P)
                        )
                    }

            Global.getSector().removeTransientScriptsOfClass(
                TrophyPlanetScript::
                class.java
            )

            if ((Global.getSector().currentLocation as? StarSystemAPI) != null) {
                addScriptForSystem(Global.getSector().currentLocation as StarSystemAPI, settings!!)
            }
        }

        private fun addScriptForSystem(starSystemAPI: StarSystemAPI, settings: Settings) {
            Misc.getMarketsInLocation(starSystemAPI)
                .forEach { marketInsystem ->
                    val script = TrophyPlanetScript().apply {
                        this.market = marketInsystem
                        this.settings = settings
                    }
                    Global.getSector().addTransientScript(script)
                    Global.getSector().listenerManager.addListener(script, true)
                }
        }
    }

    override fun onGameLoad(newGame: Boolean) {
        super.onGameLoad(newGame)

        // Remove all entities on load, just in case something wasn't cleaned up
        reload()
        settings ?: return

        Global.getSector().addTransientListener(object : BaseCampaignEventListener(false) {
            override fun reportFleetJumped(
                fleet: CampaignFleetAPI?,
                from: SectorEntityToken?,
                to: JumpPointAPI.JumpDestination?
            ) {
                super.reportFleetJumped(fleet, from, to)

                if (fleet != Global.getSector().playerFleet) return

                to?.destination?.starSystem ?: return

                addScriptForSystem(to.destination.starSystem, settings!!)
            }
        })
    }

    override fun beforeGameSave() {
        super.beforeGameSave()
        entitiestoKeepAcrossSavingProcess.clear()

        // Don't put the entities in the save file, they should be regenerated each time
        // and this will help prevent save incompatibility.
        Global.getSector().starSystems
            .forEach { sys ->
                sys.allEntities
                    .filter { it.tags.any { it.startsWith(Constants.TROPHY_ENTITY_TAG_PREFIX) } }
                    .toList()
                    .forEach {
                        entitiestoKeepAcrossSavingProcess.add(sys to it)
                        sys.removeEntity(it)
                    }
            }
    }

    override fun afterGameSave() {
        super.afterGameSave()

        entitiestoKeepAcrossSavingProcess
            .forEach { (sys, entity) ->
                sys.addEntity(entity)
            }
        entitiestoKeepAcrossSavingProcess.clear()
    }

    /**
     * Tell the XML serializer to use custom naming, so that moving or renaming classes doesn't break saves.
     */
    override fun configureXStream(x: XStream) {
        super.configureXStream(x)
    }

    data class Settings(
        val showStoredShips: Boolean,
        val showShipsForSale: Boolean,
        val normalizingAmount: Float,
        val storedSpriteScaleModifier: Float,
        val forSaleSpriteScaleModifier: Float,
        val alphaMult: Float,
        val shouldFadeOnZoomInForStorage: Boolean,
        val shouldFadeOnZoomInForSale: Boolean,
        val toggleHotkey: Int
    )
}