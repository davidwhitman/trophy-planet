package org.wisp.trophyplanet

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.Submarkets
import com.fs.starfarer.api.util.Misc
import com.thoughtworks.xstream.XStream
import org.lwjgl.util.vector.Vector2f
import wisp.questgiver.wispLib.isSolidPlanet

class LifecyclePlugin : BaseModPlugin() {

    override fun onGameLoad(newGame: Boolean) {
        super.onGameLoad(newGame)

        val spriteScale = 0.5f
        Global.getSector().starSystems
            .flatMap { it.planets }
            .filter { it.isSolidPlanet && it.faction.isPlayerFaction }
            .forEach { planet ->
                val storage = planet.market?.submarketsCopy?.firstOrNull() { it.specId == Submarkets.SUBMARKET_STORAGE }
                    ?: return@forEach
                if (storage.cargo.mothballedShips.membersListCopy.isEmpty()) return@forEach

                storage.cargo.mothballedShips.membersListCopy.forEachIndexed { index, ship ->

                    val fleet = FleetFactoryV3.createEmptyFleet(Factions.DERELICT, FleetTypes.PATROL_SMALL, null)
                        .apply {
                            this.isTransponderOn = false
                            this.setLocation(planet.location.x, planet.location.y)
                            this.name = "dummy fleet"
                            this.setNoEngaging(Float.POSITIVE_INFINITY)
                        }

                    ship.spriteOverride = ship.hullSpec.spriteName
                    val sprite = Global.getSettings().getSprite(ship.hullSpec.spriteName)
                    ship.overrideSpriteSize = Vector2f(sprite.width * spriteScale, sprite.height * spriteScale)
                    fleet.fleetData.addFleetMember(ship)

                    planet.starSystem.addEntity(fleet)

                    fleet.setCircularOrbit(
                        planet,
                        Misc.getAngleInDegrees(fleet.location, planet.location) + (index * 20),
                        planet.radius + 150f,
                        2f
                    )
                }
            }
    }

    /**
     * Tell the XML serializer to use custom naming, so that moving or renaming classes doesn't break saves.
     */
    override fun configureXStream(x: XStream) {
        super.configureXStream(x)
    }
}