package org.wisp.trophyplanet

import com.fs.starfarer.api.EveryFrameScriptWithCleanup
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.campaign.SubmarketPlugin
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.impl.campaign.ids.Submarkets
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.lwjgl.util.vector.Vector2f
import org.wisp.trophyplanet.packedcircle.PackedCircle
import org.wisp.trophyplanet.packedcircle.PackedCircleManager
import wisp.questgiver.addPara
import kotlin.math.hypot

class TrophyPlanetScript : EveryFrameScriptWithCleanup {
    companion object {
        const val boundsSize = 2000f
        const val spriteScale = 0.5f
        private const val planetCircleId = "planet"
    }

    var planet: PlanetAPI? = null
    var settings: LifecyclePlugin.Settings? = null
    var hasScriptUpdatedMarkets = false

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

        if (!hasScriptUpdatedMarkets) {
            planetInner.market?.submarketsCopy?.forEach { it.plugin?.updateCargoPrePlayerInteraction() }
            hasScriptUpdatedMarkets = true
        }

        if (packedCircleManager == null) {
            packedCircleManager = PackedCircleManager(
                bounds = null,
                desiredTarget = planetInner.location
            )
        }

        val packedCircleManagerInner = packedCircleManager!!

        val circlesToShow = mutableListOf<CircleData>()

        circlesToShow += CircleData.Pinned(
            id = planetCircleId,
            displayName = null,
            factionId = null,
            radius = planetInner.radius,
            spriteToShow = null,
            position = planetInner.location,
            tooltipMaker = null
        )

        // Personal ships
        if (settingsInner.showStoredShips) {
            val market =
                planetInner.market?.submarketsCopy?.firstOrNull { it.specId == Submarkets.SUBMARKET_STORAGE }
            val mothballedShips =
                market
                    ?.cargo?.mothballedShips

            if (mothballedShips?.numMembers != null && mothballedShips.numMembers > 0) {
                circlesToShow += mothballedShips.membersListCopy
                    .map { ship ->
                        val sprite = Global.getSettings().getSprite(ship.hullSpec.spriteName)
                        CircleData.Unpinned(
                            id = ship.id,
                            displayName = ship.shipName,
                            factionId = market.faction?.id,
                            radius = (hypot(sprite.width, sprite.height) * spriteScale) / 2,
                            spriteToShow = sprite,
                            tooltipMaker = { tooltip, isExpanded ->
                                tooltip.addPara { ship.hullSpec.nameWithDesignationWithDashClass }
                            }
                        )
                    }
            }
        }

        if (settingsInner.showShipsForSale) {
            val marketShips = planetInner.market?.submarketsCopy
                ?.flatMap { subMarket ->
                    val ships = subMarket?.cargo?.mothballedShips?.membersListCopy

                    if (subMarket != null
                        && ships?.any() == true
                        && subMarket.plugin?.isIllegalOnSubmarket(
                            ships.firstOrNull(),
                            SubmarketPlugin.TransferAction.PLAYER_BUY
                        ) != true
                    ) {
                        return@flatMap ships.map { subMarket to it }
                    } else return@flatMap emptyList<Pair<SubmarketAPI, FleetMemberAPI>>()
                } ?: emptyList()

            if (marketShips.count() > 0) {
                circlesToShow += marketShips
                    .map { (market, ship) ->
                        val sprite = Global.getSettings().getSprite(ship.hullSpec.spriteName)
                        CircleData.Unpinned(
                            id = ship.id,
                            displayName = ship.hullSpec.nameWithDesignationWithDashClass,
                            factionId = market?.faction?.id,
                            radius = (hypot(sprite.width, sprite.height) * spriteScale) / 2,
                            spriteToShow = sprite,
                            tooltipMaker = { tooltip, isExpanded ->
                                tooltip.showShips(listOf(ship), 1, false, 16f)
                                tooltip.addPara(textColor = market.faction.color) { market.nameOneLine }
                            }
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
                    factionId = null,
                    radius = it.radius,
                    spriteToShow = null,
                    position = it.location,
                    tooltipMaker = null
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

    private fun shouldShowShips(
        market: SubmarketAPI?,
        ships: MutableList<FleetMemberAPI>?
    ) = market != null
            && ships?.any() == true
            && market.plugin?.isIllegalOnSubmarket(
        ships.firstOrNull(),
        SubmarketPlugin.TransferAction.PLAYER_BUY
    ) != true

    private fun setShipsAroundPlanet(
        circles: List<CircleData>,
        packedCircleManager: PackedCircleManager,
        planet: PlanetAPI
    ) {
        val circleKeys = packedCircleManager.circles.keys

        // Create entities and circles for each stored ship if they don't exist already
        val circlesToAddSpritesFor = circles.filter { it.id !in circleKeys }
        circlesToAddSpritesFor.forEachIndexed { index, circleData ->
            val customEntity = if (circleData.spriteToShow != null) {
                planet.containingLocation.addCustomEntity(
                    circleData.id,
                    circleData.displayName,
                    "Trophy_Planet-DummyFleet",
                    circleData.factionId,
                    null
                )
                    .apply {
                        this.radius = circleData.radius
                        this.addTag(Constants.getTagForEntities(planet))

                        (this.customPlugin as DummyFleetEntity).apply {
                            this.spriteScale = TrophyPlanetScript.spriteScale
                            this.addSprite(circleData.spriteToShow)
                            this.tooltipMaker = circleData.tooltipMaker
                        }
                    }
            } else {
                null
            }

            if (!packedCircleManager.circles.containsKey(circleData.id)) {
                packedCircleManager.circles[circleData.id] = PackedCircle(
                    id = circleData.id,
                    radius = circleData.radius,
                    position =
                    when (circleData) {
                        is CircleData.Unpinned -> Vector2f(planet.location.x + index, planet.location.y + index)
                        is CircleData.Pinned -> circleData.position
                    },
                    isPulledToCenter = circleData.shouldModifyPosition,
                    isPinned = !circleData.shouldModifyPosition,
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
            ?.getCustomEntitiesWithTag(Constants.getTagForEntities(planetInner))
            ?.forEach { entity ->
                (entity.customPlugin as? DummyFleetEntity)?.onDestroy()
                planetInner.containingLocation?.removeEntity(entity)
            }

        // Wipe field variables, probably not needed but out of an abundance of caution
        packedCircleManager?.circles?.clear()
        packedCircleManager = null
        done = true
    }

    private sealed class CircleData(
        val id: String,
        val displayName: String?,
        val factionId: String?,
        val radius: Float,
        val spriteToShow: SpriteAPI?,
        val tooltipMaker: ((tooltip: TooltipMakerAPI, expanded: Boolean) -> Unit)?,
        val shouldModifyPosition: Boolean
    ) {
        class Pinned(
            id: String,
            displayName: String?,
            factionId: String?,
            radius: Float,
            spriteToShow: SpriteAPI?,
            tooltipMaker: ((tooltip: TooltipMakerAPI, expanded: Boolean) -> Unit)?,
            val position: Vector2f
        ) : CircleData(
            id = id,
            displayName = displayName,
            factionId = factionId,
            radius = radius,
            spriteToShow = spriteToShow,
            tooltipMaker = tooltipMaker,
            shouldModifyPosition = false
        )

        class Unpinned(
            id: String,
            displayName: String?,
            factionId: String?,
            radius: Float,
            spriteToShow: SpriteAPI?,
            tooltipMaker: ((tooltip: TooltipMakerAPI, expanded: Boolean) -> Unit)?
        ) : CircleData(
            id = id,
            displayName = displayName,
            factionId = factionId,
            radius = radius,
            spriteToShow = spriteToShow,
            tooltipMaker = tooltipMaker,
            shouldModifyPosition = true
        )
    }
}