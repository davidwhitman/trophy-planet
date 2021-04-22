package org.wisp.trophyplanet

import com.fs.starfarer.api.EveryFrameScriptWithCleanup
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.campaign.SubmarketPlugin
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.graphics.SpriteAPI
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
        if (isDone) {
            cleanup()
            return
        }

        val planetInner = planet ?: return
        val settingsInner = settings ?: return

        // If player is not in this script's planet's system, destroy self.
        if (Global.getSector().currentLocation != planetInner.containingLocation) {
            cleanup()
            done = true
            Global.getSector().removeTransientScript(this)
            return
        }

        if (!settingsInner.showShipsForSale && !settingsInner.showStoredShips) {
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

        val circlesToShow = mutableListOf<CircleData>()

        circlesToShow += CircleData.Pinned(
            id = planetCircleId,
            displayName = null,
            radius = planetInner.radius,
            spriteToShow = null,
            position = planetInner.location
        )

//        if (!packedCircleManagerInner.circles.containsKey(planetCircleId)) {
//            packedCircleManagerInner.circles[planetCircleId] =
//                PackedCircle(
//                    id = planetCircleId,
//                    radius = planetInner.radius,
//                    position = planetInner.location,
//                    isPulledToCenter = false,
//                    isPinned = true,
//                    syncedEntity = null
//                )
//        }

        if (settingsInner.showStoredShips) {
            val mothballedShips =
                planetInner.market?.submarketsCopy?.firstOrNull { it.specId == Submarkets.SUBMARKET_STORAGE }
                    ?.cargo?.mothballedShips

            if (mothballedShips?.numMembers != null && mothballedShips.numMembers > 0) {
                circlesToShow += mothballedShips.membersListCopy
                    .map { ship ->
                        val sprite = Global.getSettings().getSprite(ship.hullSpec.spriteName)
                        CircleData.Unpinned(
                            id = ship.id,
                            displayName = ship.shipName,
                            radius = (hypot(sprite.width, sprite.height) * spriteScale) / 2,
                            spriteToShow = sprite
                        )
                    }
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
                circlesToShow += marketShips
                    .map { ship ->
                        val sprite = Global.getSettings().getSprite(ship.hullSpec.spriteName)
                        CircleData.Unpinned(
                            id = ship.id,
                            displayName = "${ship.hullSpec.nameWithDesignationWithDashClass} (${Misc.getDGSCredits(ship.baseBuyValue)})",
                            radius = (hypot(sprite.width, sprite.height) * spriteScale) / 2,
                            spriteToShow = sprite
                        )
                    }
            }
        }

        // Fleets in system
        if (planetInner.starSystem != null) {
            circlesToShow += planetInner.starSystem.fleets.map {
                CircleData.Pinned(
                    id = it.id,
                    displayName = null,
                    radius = it.radius,
                    spriteToShow = null,
                    position = it.location
                )
            }
        }

        setShipsAroundPlanet(
            circles = circlesToShow,
            packedCircleManager = packedCircleManagerInner,
            planet = planetInner
        )

        // Keeps desired location synced, since planet will be orbiting and ships should follow it
        packedCircleManagerInner.desiredTarget = planetInner.location
        packedCircleManagerInner.updatePositions()
    }

    private fun setShipsAroundPlanet(
        circles: List<CircleData>,
        packedCircleManager: PackedCircleManager,
        planet: PlanetAPI
    ) {
        val circleKeys = packedCircleManager.circles.keys

        // Create entities and circles for each stored ship if they don't exist already
        val shipsToAddEntitiesFor = circles.filter { it.id !in circleKeys }
        shipsToAddEntitiesFor.forEachIndexed { index, ship ->
            val customEntity = if (ship.spriteToShow != null) {
                planet.containingLocation.addCustomEntity(
                    ship.id,
                    ship.displayName,
                    "Trophy_Planet-DummyFleet",
                    null,
                    null
                )
                    .apply {
                        this.addTag(Constants.getTagForPlanetEntities(planet))

                        (this.customPlugin as DummyFleetEntity).apply {
                            this.spriteScale = TrophyPlanetScript.spriteScale
                            this.addSprite(ship.spriteToShow)
                        }
                    }
            } else {
                null
            }

            if (!packedCircleManager.circles.containsKey(ship.id)) {
                packedCircleManager.circles[ship.id] = PackedCircle(
                    id = ship.id,
                    radius = ship.radius,
                    position =
                    when (ship) {
                        is CircleData.Unpinned -> Vector2f(planet.location.x + index, planet.location.y + index)
                        is CircleData.Pinned -> ship.position
                    },
                    isPulledToCenter = ship.shouldModifyPosition,
                    isPinned = !ship.shouldModifyPosition,
                    syncedEntity = customEntity?.let { PackedCircle.Entity(customEntity.location) }
                )
            }
        }

        // Remove any circles and entities whose ships are no longer in storage
        val shipIds = circles.map { it.id }
        val entityIdsToRemove = circleKeys - shipIds
        entityIdsToRemove.forEach { shipId ->
            if (shipId == planetCircleId) return@forEach

            packedCircleManager.circles.remove(shipId)
            planet.containingLocation.removeEntity(planet.containingLocation.getEntityById(shipId))
        }
    }

    /**
     * Original doc: Called when an entity that has this script attached to it is removed from the campaign engine.
     *------------------
     * `EveryFrameScriptWithCleanup`'s cleanup method is only called when the entity the script is attached to is removed,
     * so it'll only get automatically called when the game is reloaded.
     */
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

    private sealed class CircleData(
        val id: String,
        val displayName: String?,
        val radius: Float,
        val spriteToShow: SpriteAPI?,
        val shouldModifyPosition: Boolean
    ) {
        class Pinned(
            id: String,
            displayName: String?,
            radius: Float,
            spriteToShow: SpriteAPI?,
            val position: Vector2f
        ) : CircleData(
            id,
            displayName,
            radius,
            spriteToShow,
            shouldModifyPosition = false
        )

        class Unpinned(
            id: String,
            displayName: String?,
            radius: Float,
            spriteToShow: SpriteAPI?
        ) : CircleData(
            id,
            displayName,
            radius,
            spriteToShow,
            shouldModifyPosition = true
        )
    }
}