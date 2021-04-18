package org.wisp.trophyplanet

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.OrbitAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.impl.campaign.ids.Submarkets
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator
import com.thoughtworks.xstream.XStream
import wisp.questgiver.wispLib.isSolidPlanet
import kotlin.math.hypot

class LifecyclePlugin : BaseModPlugin() {
    companion object {
        const val TROPHY_ENTITY_TAG = "wisp_trophyEntity"
    }

    override fun onGameLoad(newGame: Boolean) {
        super.onGameLoad(newGame)


        Global.getSector().starSystems
            .flatMap { it.planets }
            .filter { it.isSolidPlanet }//&& it.faction.isPlayerFaction }
            .also {
                it.forEach { planet ->
                    planet.containingLocation.getCustomEntitiesWithTag(TROPHY_ENTITY_TAG)
                        .forEach { planet.containingLocation.removeEntity(it) }
                }
            }
            .forEach { planet ->

                val storage = planet.market?.submarketsCopy?.firstOrNull() { it.specId == Submarkets.SUBMARKET_STORAGE }
                    ?: return@forEach

                if (storage.cargo.mothballedShips.membersListCopy.isEmpty()) return@forEach

                storage.cargo.mothballedShips.membersListCopy.forEachIndexed { index, ship ->
                    val sprite = Global.getSettings().getSprite(ship.hullSpec.spriteName)
                    val spriteSize = hypot(sprite.width, sprite.height)

                    val customEntity = planet.containingLocation.addCustomEntity(
                        null,
                        "",
                        "Trophy_Planet-DummyFleet",
                        null,
                        null
                    )
                        .apply {
                            this.addTag(TROPHY_ENTITY_TAG)
                            this.setLocation(
                                planet.location.x,
                                planet.location.y
                            )
                            this.orbit = createOrbit(
                                radius = planet.radius + 80f + (15f * index) + (spriteSize / 50f),
                                offsetAngle = 35f * index * (spriteSize / 360),
                                orbitCenter = planet
                            )
                        }

                    (customEntity.customPlugin as DummyFleetEntity).apply {
                        this.addSprite(sprite)
                    }
//
//                    val fleet = FleetFactoryV3.createEmptyFleet(Factions.DERELICT, FleetTypes.PATROL_SMALL, null)
//                        .apply {
//                            this.isTransponderOn = false
//                            this.setLocation(planet.location.x, planet.location.y)
//                            this.name = "dummy fleet"
//                            this.setNoEngaging(Float.POSITIVE_INFINITY)
//                        }
//
//                    ship.spriteOverride = ship.hullSpec.spriteName
//                    val sprite = Global.getSettings().getSprite(ship.hullSpec.spriteName)
//                    ship.overrideSpriteSize = Vector2f(sprite.width * spriteScale, sprite.height * spriteScale)
//                    fleet.fleetData.addFleetMember(ship)
//
//                    planet.starSystem.addEntity(fleet)
//
//                    fleet.setCircularOrbit(
//                        planet,
//                        Misc.getAngleInDegrees(fleet.location, planet.location) + (index * 20),
//                        planet.radius + 150f,
//                        2f
//                    )
                }
            }
    }

    fun createOrbit(
        radius: Float,
        offsetAngle: Float,
        orbitCenter: SectorEntityToken
    ): OrbitAPI {

        return Global.getFactory().createCircularOrbit(
            orbitCenter,
            offsetAngle,
            radius,
            radius / (20f + StarSystemGenerator.random.nextFloat() * 5f) // taken from StarSystemGenerator:1655
        )
    }

    /**
     * Tell the XML serializer to use custom naming, so that moving or renaming classes doesn't break saves.
     */
    override fun configureXStream(x: XStream) {
        super.configureXStream(x)
    }
}