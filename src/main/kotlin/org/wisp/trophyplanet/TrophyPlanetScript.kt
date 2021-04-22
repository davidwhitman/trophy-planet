package org.wisp.trophyplanet

import com.fs.starfarer.api.EveryFrameScriptWithCleanup
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.campaign.SubmarketPlugin
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Submarkets
import com.fs.starfarer.api.util.Misc
import org.lwjgl.util.vector.Vector2f
import org.wisp.trophyplanet.packedcircle.PackedCircle
import org.wisp.trophyplanet.packedcircle.PackedCircleManager
import kotlin.math.hypot

class TrophyPlanetScript : EveryFrameScriptWithCleanup {
    companion object {
        const val boundsSize = 2000f
        const val spriteScale = 0.5f
        private const val planetCircleId = "planet"
    }

    var planet: PlanetAPI? = null
    var settings: LifecyclePlugin.Settings? = null

    var done = false

    private var packedCircleManager: PackedCircleManager? = null

    override fun isDone(): Boolean = done

    override fun runWhilePaused() = false

    override fun advance(amount: Float) {
        if (isDone) return

        val planetInner = planet ?: return
        val settingsInner = settings ?: return

        // If player is not in this script's planet's system, destroy self.
        if (Global.getSector().currentLocation != planetInner.containingLocation) {
            done = true
            return
        }

        if (packedCircleManager == null) {
            packedCircleManager = PackedCircleManager(
                bounds = null,
//                bounds = Rectangle(
//                    (planetInner.location.x - boundsSize).toInt(),
//                    (planetInner.location.y + boundsSize).toInt(),
//                    boundsSize.toInt(),
//                    boundsSize.toInt()
//                ),
                desiredTarget = planetInner.location
            )
        }

        val packedCircleManagerInner = packedCircleManager!!

        if (!packedCircleManagerInner.circles.containsKey(planetCircleId)) {
            packedCircleManagerInner.circles[planetCircleId] =
                PackedCircle(
                    id = planetCircleId,
                    radius = planetInner.radius,
                    position = planetInner.location,
                    isPulledToCenter = false,
                    isPinned = true,
                    syncedEntity = null
                )
        }

        val shipsToShow = mutableListOf<ShipData>()

        if (settingsInner.showStoredShips) {
            val mothballedShips =
                planetInner.market?.submarketsCopy?.firstOrNull { it.specId == Submarkets.SUBMARKET_STORAGE }
                    ?.cargo?.mothballedShips

            if (mothballedShips?.numMembers != null && mothballedShips.numMembers > 0) {
                shipsToShow += mothballedShips.membersListCopy
                    .map { ship -> ShipData(ship, ship.shipName) }
            }
        }

        if (settingsInner.showShipsForSale) {
            val marketShips = mutableListOf<FleetMemberAPI>()
            val openMarketShips =
                planetInner.market?.submarketsCopy?.firstOrNull { it.specId == Submarkets.SUBMARKET_OPEN }
                    ?.cargo?.mothballedShips?.membersListCopy

            if (openMarketShips != null) {
                marketShips.addAll(openMarketShips)
            }


            val militaryMarket =
                planetInner.market?.submarketsCopy?.firstOrNull { it.specId == Submarkets.GENERIC_MILITARY }
            val militaryMarketShips =
                militaryMarket
                    ?.cargo?.mothballedShips?.membersListCopy
            if (militaryMarketShips?.any() == true
                && militaryMarket.plugin?.isIllegalOnSubmarket(
                    militaryMarketShips.firstOrNull(),
                    SubmarketPlugin.TransferAction.PLAYER_BUY
                ) != true
            ) {
                marketShips.addAll(militaryMarketShips)
            }


            val blackMarketShips =
                planetInner.market?.submarketsCopy?.firstOrNull { it.specId == Submarkets.SUBMARKET_BLACK }
                    ?.cargo?.mothballedShips?.membersListCopy

            if (blackMarketShips != null) {
                marketShips.addAll(blackMarketShips)
            }

            if (marketShips.count() > 0) {
                shipsToShow += marketShips
                    .map { ship -> ShipData(ship, "${ship.hullSpec.nameWithDesignationWithDashClass} (${Misc.getDGSCredits(ship.baseBuyValue)})") }
            }
        }

        setShipsAroundPlanet(
            ships = shipsToShow,
            packedCircleManager = packedCircleManagerInner,
            planet = planetInner
        )

        // Keeps desired location synced, since planet will be orbiting and ships should follow it
        packedCircleManagerInner.desiredTarget = planetInner.location
        packedCircleManagerInner.updatePositions()
    }

    data class ShipData(
        val fleetMember: FleetMemberAPI,
        val displayName: String
    ) {
        val id = fleetMember.id
    }

    private fun setShipsAroundPlanet(
        ships: List<ShipData>,
        packedCircleManager: PackedCircleManager,
        planet: PlanetAPI
    ) {
        val circleKeys = packedCircleManager.circles.keys

        // Create entities and circles for each stored ship if they don't exist already
        val shipsToAddEntitiesFor = ships.filter { it.id !in circleKeys }
        shipsToAddEntitiesFor.forEachIndexed { index, ship ->
            val sprite = Global.getSettings().getSprite(ship.fleetMember.hullSpec.spriteName)
            val spriteDiameter = hypot(sprite.width, sprite.height) * spriteScale

            val customEntity = planet.containingLocation.addCustomEntity(
                ship.id,
                ship.displayName,
                "Trophy_Planet-DummyFleet",
                null,
                null
            )
                .apply {
                    this.addTag(Constants.getTagForPlanetEntities(planet))
                }

            (customEntity.customPlugin as DummyFleetEntity).apply {
                this.spriteScale = TrophyPlanetScript.spriteScale
                this.addSprite(sprite)
            }

            if (!packedCircleManager.circles.containsKey(ship.id)) {
                packedCircleManager.circles[ship.id] = PackedCircle(
                    id = ship.id,
                    radius = spriteDiameter / 2,
                    position = Vector2f(planet.location.x + index, planet.location.y + index),
                    isPulledToCenter = true,
                    isPinned = false,
                    syncedEntity = PackedCircle.Entity(customEntity.location)
                )
            }
        }

        // Remove any circles and entities whose ships are no longer in storage
        val shipIds = ships.map { it.id }
        val entityIdsToRemove = circleKeys - shipIds
        entityIdsToRemove.forEach { shipId ->
            if (shipId == planetCircleId) return@forEach

            packedCircleManager.circles.remove(shipId)
            planet.containingLocation.removeEntity(planet.containingLocation.getEntityById(shipId))
        }
    }

    override fun cleanup() {
        val planetInner = planet

        // Remove ships around this script's planet
        planetInner?.containingLocation
            ?.getCustomEntitiesWithTag(Constants.getTagForPlanetEntities(planetInner))
            ?.forEach { entity -> planetInner.containingLocation?.removeEntity(entity) }

        // Wipe field variables, probably not needed but out of an abundance of caution
        packedCircleManager?.circles?.clear()
        packedCircleManager = null
        done = true
    }
}