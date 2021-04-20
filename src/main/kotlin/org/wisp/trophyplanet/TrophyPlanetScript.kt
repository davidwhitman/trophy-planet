package org.wisp.trophyplanet

import com.fs.starfarer.api.EveryFrameScriptWithCleanup
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.impl.campaign.ids.Submarkets
import org.lwjgl.util.vector.Vector2f
import org.wisp.trophyplanet.packedcircle.PackedCircle
import org.wisp.trophyplanet.packedcircle.PackedCircleManager
import kotlin.math.hypot

class TrophyPlanetScript : EveryFrameScriptWithCleanup {
    companion object {
        const val boundsSize = 2000f
        const val spriteScale = 0.5f
    }

    var planet: PlanetAPI? = null

    var done = false

    private var packedCircleManager: PackedCircleManager? = null

    override fun isDone(): Boolean = done

    override fun runWhilePaused() = false

    override fun advance(amount: Float) {
        if (isDone) return

        val planetInner = planet ?: return

        // If player is not in this script's planet's system, destroy self.
        if (Global.getSector().currentLocation != planetInner.containingLocation) {
            done = true
            return
        }

        val storage =
            planetInner.market?.submarketsCopy?.firstOrNull() { it.specId == Submarkets.SUBMARKET_STORAGE }
                ?: return

        if (storage.cargo.mothballedShips.numMembers == 0) return

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
        val planetCircleId = "planet"

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

        val ships = storage.cargo.mothballedShips.membersListCopy
        val circleKeys = packedCircleManagerInner.circles.keys

        // Create entities and circles for each stored ship if they don't exist already
        val shipsToAddEntitiesFor = ships.filter { it.id !in circleKeys }
        shipsToAddEntitiesFor.forEachIndexed { index, ship ->
            val sprite = Global.getSettings().getSprite(ship.hullSpec.spriteName)
            val spriteDiameter = hypot(sprite.width, sprite.height) * TrophyPlanetScript.spriteScale

            val customEntity = planetInner.containingLocation.addCustomEntity(
                ship.id,
                null,
                "Trophy_Planet-DummyFleet",
                null,
                null
            )
                .apply {
                    this.addTag(TrophyPlanetTags.getTagForPlanetEntities(planetInner))
                }

            (customEntity.customPlugin as DummyFleetEntity).apply {
                this.spriteScale = TrophyPlanetScript.spriteScale
                this.addSprite(sprite)
            }

            if (!packedCircleManagerInner.circles.containsKey(ship.id)) {
                packedCircleManagerInner.circles[ship.id] = PackedCircle(
                    id = ship.id,
                    radius = spriteDiameter / 2,
                    position = Vector2f(planetInner.location.x + index, planetInner.location.y + index),
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

            packedCircleManagerInner.circles.remove(shipId)
            planetInner.containingLocation.removeEntity(planetInner.containingLocation.getEntityById(shipId))
        }

        // Keeps desired location synced, since planet will be orbiting and ships should follow it
        packedCircleManagerInner.desiredTarget = planetInner.location
        packedCircleManagerInner.updatePositions()
    }

    override fun cleanup() {
        val planetInner = planet

        // Remove ships around this script's planet
        planetInner?.containingLocation
            ?.getCustomEntitiesWithTag(TrophyPlanetTags.getTagForPlanetEntities(planetInner))
            ?.forEach { entity -> planetInner.containingLocation?.removeEntity(entity) }

        // Wipe field variables, probably not needed but out of an abundance of caution
        packedCircleManager?.circles?.clear()
        packedCircleManager = null
        done = true
    }
}