package org.wisp.trophyplanet

import com.fs.starfarer.api.EveryFrameScriptWithCleanup
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.SubmarketPlugin
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.impl.campaign.ids.Submarkets
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.HintPanelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.lwjgl.util.vector.Vector2f
import org.wisp.trophyplanet.packedcircle.PackedCircle
import org.wisp.trophyplanet.packedcircle.PackedCircleManager
import kotlin.math.hypot

class TrophyPlanetScript : EveryFrameScriptWithCleanup, CampaignInputListener {
    companion object {
        const val boundsSize = 2000f
        private const val planetCircleId = "planet"
    }

    var market: MarketAPI? = null
    var settings: LifecyclePlugin.Settings? = null
    var hasScriptUpdatedMarkets = false

    val marketLocation: SectorEntityToken?
        get() = market?.primaryEntity

    var done = false

    private var packedCircleManager: PackedCircleManager? = null
    private var isHidden = false

    /**
     * The entities created by this mod that are currently extant.
     */
    private val trackedEntitiesInSystem = mutableMapOf<String, SectorEntityToken>()

    /**
     * Stations, planets, mirrors, etc.
     */
    private val permanentEntitiesToAvoid: List<SectorEntityToken>? by lazy {
        market?.containingLocation?.getEntitiesWithTag(Tags.STATION).orEmpty() +
                market?.containingLocation?.planets.orEmpty()
    }

    override fun isDone(): Boolean = done

    // Needs to run on pause so that sprites fade when scrolling while paused
    override fun runWhilePaused() = true

    override fun advance(amount: Float) {
        if (isDone || market?.primaryEntity?.isExpired != false) {
            cleanup()
            return
        }

        // If player is not in this script's planet's system, destroy self.
        if (Global.getSector().currentLocation != market?.primaryEntity?.containingLocation) {
            cleanup()
            done = true
            Global.getSector().removeTransientScript(this)
            return
        }

        val planetInner = market?.primaryEntity ?: return
        val settingsInner = settings ?: return
        permanentEntitiesToAvoid ?: return

        if (market?.primaryEntity?.isVisibleToPlayerFleet == false) return

        if (!settingsInner.showShipsForSale && !settingsInner.showStoredShips) {
            return
        }

        inputCooldown = (inputCooldown - amount).coerceAtLeast(-1f)

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

        val circlesToShow = mutableListOf<EntityData>()

        circlesToShow += EntityData(
            id = planetCircleId,
            radius = planetInner.radius,
            kineticsInfo = KineticsInfo.Pinned(
                position = planetInner.location
            )
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
                        val radius = hypot(sprite.width, sprite.height) / 2
                        EntityData(
                            id = ship.id,
                            customEntityInfo = CustomEntityInfo(
                                displayName = ship.shipName,
                                factionId = market.faction?.id,
                                scalingFactor = getScalingFactor(
                                    radius,
                                    settingsInner,
                                    settingsInner.storedSpriteScaleModifier
                                ),
                                spriteToShow = sprite,
                                tooltipMaker = { tooltip, isExpanded ->
                                    if (!isHidden) {
                                        tooltip.addPara("In ${market.nameOneLine}", market.faction.color, 10f)
                                    }
                                }
                            ),
                            radius = radius,
                            kineticsInfo = KineticsInfo.Unpinned()
                        )
                    }
            }
        }

        // Ships for sale in markets
        if (settingsInner.showShipsForSale) {
            val marketShips = planetInner.market?.submarketsCopy
                ?.filter { it.specId != Submarkets.SUBMARKET_STORAGE }
                ?.flatMap { subMarket ->
                    val ships = subMarket?.cargo?.mothballedShips?.membersListCopy

                    if (subMarket != null
                        && ships?.any() == true
                        && shouldShowShips(subMarket, ships)
                    ) {
                        return@flatMap ships.map { subMarket to it }
                    } else return@flatMap emptyList<Pair<SubmarketAPI, FleetMemberAPI>>()
                } ?: emptyList()

            if (marketShips.count() > 0) {
                circlesToShow += marketShips
                    .map { (market, ship) ->
                        val sprite = Global.getSettings().getSprite(ship.hullSpec.spriteName)
                            .apply {
                                val zoomMult = kotlin.runCatching { Global.getSector()?.campaignUI?.zoomFactor ?: 1f }
                                    .getOrDefault(1f)
                                this.alphaMult = when {
                                    settingsInner.shouldFadeOnZoomIn -> {
                                        val zoomedIn = 0.5f
                                        val zoomedOut = 3.0f
                                        // Thank you AlexAtheos for putting me on the right path
                                        // y = y1 + ((y2 - y1)/(x2 - x1))*(x - x1)
                                        val mult =
                                            kotlin.runCatching { 1f + ((settingsInner.alphaMult - 1f) / (zoomedOut - zoomedIn)) * (zoomMult - zoomedIn) }
                                                .getOrDefault(settingsInner.alphaMult)
                                        mult
                                    }
                                    else -> {
                                        zoomMult
                                    }
                                }
                            }
                        val radius = hypot(sprite.width, sprite.height) / 2
                        EntityData(
                            id = ship.id,
                            customEntityInfo = CustomEntityInfo(
                                displayName = ship.hullSpec.nameWithDesignationWithDashClass,
                                factionId = market.faction?.id,
                                scalingFactor = getScalingFactor(
                                    radius,
                                    settingsInner,
                                    settingsInner.forSaleSpriteScaleModifier
                                ),
                                spriteToShow = sprite,
                                tooltipMaker = { tooltip, isExpanded ->
                                    if (!isHidden) {
                                        tooltip.addPara(
                                            "Available from the %s for %s credits.",
                                            10f,
                                            arrayOf(market.faction.color, Misc.getHighlightColor()),
                                            market.nameOneLine,
                                            Misc.getWithDGS(ship.baseBuyValue + (ship.baseBuyValue * market.tariff))
                                        )
                                    }
                                }
                            ),
                            radius = radius,
                            kineticsInfo = KineticsInfo.Unpinned()
                        )
                    }
            }
        }

        // Fleets and stations in system
        if (planetInner.starSystem != null) {
            circlesToShow += (planetInner.starSystem.fleets + permanentEntitiesToAvoid!!)
                .map {
                    EntityData(
                        id = it.id,
                        radius = it.radius,
                        kineticsInfo = KineticsInfo.Pinned(
                            position = it.location
                        )
                    )
                }
        }

        setShipsAroundMarketLocation(
            entities = circlesToShow,
            packedCircleManager = packedCircleManagerInner,
            marketLocation = planetInner
        )

        if (!isHidden && !Global.getSector().isPaused) {
            // Keeps desired location synced, since planet will be orbiting and ships should follow it
            packedCircleManagerInner.desiredTarget = planetInner.location
            packedCircleManagerInner.updatePositions()
        }
    }

    var inputCooldown = 0f

    override fun getListenerInputPriority(): Int = 1

    override fun processCampaignInputPreCore(events: List<InputEventAPI>?) {
        if (inputCooldown > 0f || isDone || events == null) {
            return
        }

        for (e in events) {
            if (!e.isConsumed && e.isKeyDownEvent && e.eventValue == settings?.toggleHotkey && inputCooldown <= 0) {
                this.isHidden = !this.isHidden

                inputCooldown = 0.1f
            }
        }
    }

    override fun processCampaignInputPreFleetControl(p0: MutableList<InputEventAPI>?) {}

    override fun processCampaignInputPostCore(p0: MutableList<InputEventAPI>?) {}

    private fun shouldShowShips(
        market: SubmarketAPI?,
        ships: MutableList<FleetMemberAPI>?
    ) = market != null
            && ships?.any() == true
            && market.plugin?.isIllegalOnSubmarket(
        ships.firstOrNull(),
        SubmarketPlugin.TransferAction.PLAYER_BUY
    ) != true
            && market.plugin?.isEnabled(object : CoreUIAPI {
        override fun getTradeMode(): CampaignUIAPI.CoreUITradeMode = CampaignUIAPI.CoreUITradeMode.OPEN

        override fun getHintPanel(): HintPanelAPI? = null

    }) != false

    private fun setShipsAroundMarketLocation(
        entities: List<EntityData>,
        packedCircleManager: PackedCircleManager,
        marketLocation: SectorEntityToken
    ) {
        val circleKeys = packedCircleManager.circles.keys

        // Create entities and circles for each stored ship if they don't exist already
        val circlesToAddSpritesFor = entities//.filter { it.id !in circleKeys }
        circlesToAddSpritesFor.forEachIndexed { index, circleData ->
            val shouldShowEntitiesThatWereHidden = trackedEntitiesInSystem[circleData.id] == null && !isHidden

            val customEntity =
                if (circleData.customEntityInfo != null
                    && shouldShowEntitiesThatWereHidden
                ) {
                    marketLocation.containingLocation.addCustomEntity(
                        circleData.id,
                        circleData.customEntityInfo.displayName,
                        "Trophy_Planet-DummyFleet",
                        null,
                        null
                    )
                        .apply {
                            this.radius = circleData.radius * circleData.customEntityInfo.scalingFactor
                            this.addTag(Constants.getTagForEntities(marketLocation))

                            (this.customPlugin as DummyFleetEntity).apply {
                                this.spriteScale = circleData.customEntityInfo.scalingFactor
                                this.spriteAlpha = circleData.customEntityInfo.spriteToShow.alphaMult
                                this.addSprite(circleData.customEntityInfo.spriteToShow)
                                this.tooltipMaker = circleData.customEntityInfo.tooltipMaker
                            }
                            trackedEntitiesInSystem[circleData.id] = this
                        }
                } else {
                    null
                }

            if (!packedCircleManager.circles.containsKey(circleData.id)) {
                packedCircleManager.circles[circleData.id] = PackedCircle(
                    id = circleData.id,
                    radius = circleData.radius * (circleData.customEntityInfo?.scalingFactor ?: 1f),
                    position =
                    when (circleData.kineticsInfo) {
                        is KineticsInfo.Unpinned -> Vector2f(
                            marketLocation.location.x + index,
                            marketLocation.location.y + index
                        )
                        is KineticsInfo.Pinned -> circleData.kineticsInfo.position
                    },
                    isPulledToCenter = circleData.shouldModifyPosition,
                    isPinned = !circleData.shouldModifyPosition,
                    syncedEntity = customEntity?.let { PackedCircle.Entity(customEntity.location) }
                )
            } else if (customEntity != null) {
                val syncedEntity = packedCircleManager.circles[circleData.id]?.syncedEntity
                packedCircleManager.circles[circleData.id]?.syncedEntity =
                    PackedCircle.Entity(customEntity.location)
            }

        }

        // Update entities that are already being shown
        val circlesToUpdate = entities
            .filter { it.id in circleKeys && it.customEntityInfo != null }
        circlesToUpdate.forEach { data ->
            val entity = trackedEntitiesInSystem[data.id]
            (entity?.customPlugin as? DummyFleetEntity)?.spriteAlpha =
                data.customEntityInfo?.spriteToShow?.alphaMult ?: 1f

            if (isHidden && entity != null) {
                marketLocation.containingLocation?.removeEntity(entity)
                trackedEntitiesInSystem.remove(entity.id)
            }
        }

        // Remove any circles and entities whose ships are no longer in storage
        val shipIds = entities.map { it.id }
        val entityIdsToRemove = circleKeys - shipIds
        entityIdsToRemove.forEach { shipId ->
            if (shipId == planetCircleId) return@forEach

            packedCircleManager.circles.remove(shipId)
            marketLocation.containingLocation.removeEntity(trackedEntitiesInSystem[shipId])
        }
    }

    /**
     * Original doc: Called when an entity that has this script attached to it is removed from the campaign engine.
     *------------------
     * `EveryFrameScriptWithCleanup`'s cleanup method is only called when the entity the script is attached to is removed,
     * so it'll only get automatically called when the game is reloaded.
     */
    override fun cleanup() {
        val planetInner = marketLocation

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
        market = null
        done = true
    }


    private fun getScalingFactor(
        radius: Float,
        settings: LifecyclePlugin.Settings,
        preNormalizationScalingFactor: Float
    ): Float {
        val normalizingTargetSize = settings.normalizingTargetSize
        val normalizingAmount = settings.normalizingAmount
        val adjustedRadius = preNormalizationScalingFactor * radius

        return (((normalizingTargetSize * normalizingAmount) +
                adjustedRadius) / (2f + normalizingAmount).coerceAtLeast(0.01f))
            .div(adjustedRadius)
    }

    private data class EntityData(
        val id: String,
        val radius: Float,
        val kineticsInfo: KineticsInfo,
        val customEntityInfo: CustomEntityInfo? = null
    ) {
        val shouldModifyPosition = kineticsInfo is KineticsInfo.Unpinned
    }

    private sealed class KineticsInfo {
        data class Pinned(
            val position: Vector2f
        ) : KineticsInfo()

        class Unpinned : KineticsInfo()
    }

    private data class CustomEntityInfo(
        val spriteToShow: SpriteAPI,
        val scalingFactor: Float,
        val displayName: String,
        val factionId: String?,
        val tooltipMaker: ((tooltip: TooltipMakerAPI, expanded: Boolean) -> Unit)
    )
}