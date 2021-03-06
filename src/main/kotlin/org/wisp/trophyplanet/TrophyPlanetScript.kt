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
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.ui.HintPanelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.lazywizard.lazylib.ext.logging.w
import org.lwjgl.util.vector.Vector2f
import org.wisp.trophyplanet.lib.asList
import org.wisp.trophyplanet.packedcircle.PackedCircle
import org.wisp.trophyplanet.packedcircle.PackedCircleManager
import kotlin.math.hypot

internal class TrophyPlanetScript : EveryFrameScriptWithCleanup, CampaignInputListener {
    companion object {
        const val boundsSize = 2000f
        private const val planetCircleId = "planet"
        val logger = Global.getLogger(TrophyPlanetScript::class.java)
    }

    var market: MarketAPI? = null
    var settings: LifecyclePlugin.Settings? = null
    var hasScriptUpdatedMarkets = false

    val marketLocation: SectorEntityToken?
        get() = market?.primaryEntity

    var done = false

    private var packedCircleManager: PackedCircleManager? = null
    private var isHidden = false

    val recalculationPeriod = 5000f
    var timeSinceLastRecalculation = 0f
    var spriteRadii: List<Float>? = emptyList()
    var desiredMinShipRadius: Float = 60f
    var desiredMaxShipRadius: Float = 120f
    var minShipRadius: Float = 0f
    var maxShipRadius: Float = 0f
    var shipAverageRadius: Float = 0f
    val tooltipPadding = 10f

    /**
     * The entities created by this mod that are currently extant.
     */
    private val trackedEntitiesInSystem = mutableMapOf<String, SectorEntityToken>()

    private val spriteInfoCache = mutableMapOf<String, SpriteInfo>()

    data class SpriteInfo(val spriteAPI: SpriteAPI, val radius: Float)

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
        timeSinceLastRecalculation -= recalculationPeriod

        // If script is done, market's entity is gone, or player is not in this script's planet's system, destroy self.
        if (isDone
            || market?.primaryEntity?.isExpired != false
            || Global.getSector().currentLocation != market?.primaryEntity?.containingLocation
        ) {
            cleanup()
            return
        }

        val planetInner = market?.primaryEntity ?: return
        val settingsInner = settings ?: return
        permanentEntitiesToAvoid ?: return

        if (market?.primaryEntity?.isVisibleToPlayerFleet == false) return

        if (!settingsInner.showShipsForSale && !settingsInner.showStoredShips) {
            return
        }

        desiredMinShipRadius = settingsInner.shipMinSize
        desiredMaxShipRadius = settingsInner.shipMaxSize

        if (timeSinceLastRecalculation <= 0f) {
            recalculatePlanetSpriteSizes(planetInner)
            timeSinceLastRecalculation = recalculationPeriod
        }

        inputCooldown = (inputCooldown - amount).coerceAtLeast(-1f)
        spriteInfoCache.clear()

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
            val submarket =
                planetInner.market?.submarketsCopy?.firstOrNull { it.specId == Submarkets.SUBMARKET_STORAGE }
            val mothballedShips =
                submarket
                    ?.cargo?.mothballedShips

            if (mothballedShips?.numMembers != null && mothballedShips.numMembers > 0) {
                circlesToShow += mothballedShips.membersListCopy.let { ships ->
                    ships.map { ship ->
                        val cachedSpriteInfo = getOrCalculateCachedSpriteInfo(submarket, ship.hullSpec.spriteName)
                        val scalingFactor = mapToNewScale(
                            sprite = cachedSpriteInfo,
                            oldScaleMin = minShipRadius,
                            oldScaleMax = maxShipRadius,
                            newScaleMin = desiredMinShipRadius,
                            newScaleMax = desiredMaxShipRadius
                        ) / cachedSpriteInfo.radius
                        val spriteToShow = cachedSpriteInfo.spriteAPI
                            .apply {
                                this.alphaMult = when {
                                    settingsInner.shouldFadeOnZoomInForStorage -> {
                                        calculateFadeMultiplierBasedOnZoom(
                                            settingsInner,
                                            kotlin.runCatching { Global.getSector()?.campaignUI?.zoomFactor ?: 1f }
                                                .getOrDefault(1f))
                                    }
                                    else -> 1f
                                }
                            }
                        EntityData(
                            id = ship.id,
                            customEntityInfo = CustomEntityInfo(
                                displayName = "${ship.shipName}, ${ship.hullSpec.nameWithDesignationWithDashClass}",
                                factionId = submarket.faction?.id,
                                scalingFactor = scalingFactor,
                                spriteToShow = spriteToShow,
                                tooltipMaker = { tooltip, isExpanded ->
                                    if (!isHidden) {
                                        addHullmodsToTooltip(ship, tooltip)
                                        tooltip.addSpacer(tooltipPadding*3)
                                        tooltip.addPara(
                                            "In ${submarket.nameOneLine}",
                                            submarket.faction.color,
                                            tooltipPadding
                                        )
                                            .position.inBL(tooltipPadding, tooltipPadding)
                                        tooltip
                                    } else null
                                }
                            ),
                            radius = cachedSpriteInfo.radius,
                            kineticsInfo = KineticsInfo.Unpinned()
                        )
                    }
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

            if (marketShips.isNotEmpty()) {
                circlesToShow += marketShips.let { marketsAndShips ->
                    marketsAndShips.map { (submarket, ship) ->
                        val cachedSpriteInfo = getOrCalculateCachedSpriteInfo(submarket, ship.hullSpec.spriteName)
                        val sprite = cachedSpriteInfo.spriteAPI
                            .apply {
                                this.alphaMult = when {
                                    settingsInner.shouldFadeOnZoomInForSale -> {
                                        calculateFadeMultiplierBasedOnZoom(
                                            settingsInner,
                                            kotlin.runCatching { Global.getSector()?.campaignUI?.zoomFactor ?: 1f }
                                                .getOrDefault(1f))
                                    }
                                    else -> 1f
                                }
                            }
                        val scalingFactor = mapToNewScale(
                            sprite = cachedSpriteInfo,
                            oldScaleMin = minShipRadius,
                            oldScaleMax = maxShipRadius,
                            newScaleMin = desiredMinShipRadius,
                            newScaleMax = desiredMaxShipRadius
                        ) / cachedSpriteInfo.radius
                        EntityData(
                            id = ship.id,
                            customEntityInfo = CustomEntityInfo(
                                displayName = ship.hullSpec.nameWithDesignationWithDashClass,
                                factionId = submarket.faction?.id,
                                scalingFactor = scalingFactor,
                                spriteToShow = sprite,
                                tooltipMaker = { tooltip, isExpanded ->
                                    if (!isHidden) {
                                        addHullmodsToTooltip(ship, tooltip)
                                        tooltip.addSpacer(tooltipPadding*3)
                                        tooltip.addPara(
                                            "Available from the %s for %s credits.",
                                            tooltipPadding,
                                            arrayOf(submarket.faction.color, Misc.getHighlightColor()),
                                            submarket.nameOneLine,
                                            Misc.getWithDGS(ship.baseBuyValue + (ship.baseBuyValue * submarket.tariff))
                                        )
                                            .position.inBL(tooltipPadding, tooltipPadding)

                                        tooltip
                                    } else null
                                }
                            ),
                            radius = cachedSpriteInfo.radius,
                            kineticsInfo = KineticsInfo.Unpinned()
                        )
                    }
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

    private data class ShipHullMod(
        val spec: HullModSpecAPI,
        val isSMod: Boolean,
        val isDmod: Boolean
    )

//    Now wrap both with another panel and element or try setting the panels height so it doesn't create that empty space below

    /**
     * Adds armaments and hullmods to the provided tooltip.
     * Not idempotent.
     * Vanilla stat datasheet
     * com.fs.starfarer.campaign.ui.trade.OoOO
     */
    private fun addHullmodsToTooltip(
        ship: FleetMemberAPI,
        tooltip: TooltipMakerAPI
    ) {
        val panelWidth = tooltip.position.width - tooltipPadding * 2
        val panelInitialHeight = 500f
        val labelWidth = 85f
        val armamentsLabel = "Armaments:"
        val hullmodLabel = "Hull mods:"

        // Armament panel
        val armPanel = Global.getSettings().createCustom(panelWidth, panelInitialHeight, null).also { armPanel ->
            // Armament label
            armPanel.createUIElement(labelWidth, 40f, false).apply {
                this.addPara(armamentsLabel, 0f)
            }.also {
                armPanel.addUIElement(it).inTL(0f, 0f)
            }

            // Armament values
            armPanel.createUIElement(panelWidth - labelWidth, 40f, false).apply {
                val arms = ship.variant.fittedWeaponSlots
                    .mapNotNull { slotId ->
                        kotlin.runCatching { ship.variant.getWeaponSpec(slotId) }
                            .onFailure { logger.w({ "Unable to find weapon id '$slotId'." }, it) }
                            .getOrNull()
                    }
                    .groupBy { it.weaponName }
                    .mapNotNull { (weaponName, list) ->
                        weaponName to list.count()
                    }

                if (arms.any()) {
                    addPara(
                        buildString {
                            append(arms.joinToString { "%s %s" })
                        },
                        0f,
                        arms.flatMap { listOf(Misc.getHighlightColor(), Misc.getTextColor()) }
                            .toTypedArray(),
                        *arms.flatMap { listOf("${it.second}x", it.first) }
                            .toTypedArray()
                    )
                } else {
                    addPara("None", 0f)
                }
            }.also {
                armPanel.addUIElement(it).inTL(labelWidth, 0f)
                armPanel.position.setSize(armPanel.position.width, it.position.height)
            }
        }.also { tooltip.addCustom(it, 5f) }
        market?.starSystem?.createToken()

        // Hullmod panel
        // Make these separate panels so that the second can be positioned below the first's content.
        Global.getSettings().createCustom(panelWidth, panelInitialHeight, null).also { hmodPanel ->
            // Hullmod label
            hmodPanel.createUIElement(labelWidth, 40f, false).apply {
                this.addPara(hullmodLabel, 0f)
            }.also {
                hmodPanel.addUIElement(it).inTL(0f, 0f)
            }

            // Hullmod values
            hmodPanel.createUIElement(panelWidth - labelWidth, 40f, false).apply {
                val mods = ship.variant.sortedMods
                    .distinct()
                    .map {
                        val spec = Global.getSettings().getHullModSpec(it)
                        ShipHullMod(
                            spec,
                            spec.id in ship.variant.sMods,
                            spec.hasTag(Tags.HULLMOD_DMOD)
                        )
                    }.ifEmpty { null }

                if (mods != null) {
                    addPara(
                        buildString {
                            append(mods.joinToString {
                                when {
                                    it.isDmod -> "%s %s"
                                    else -> "%s"
                                }
                            })
                        },
                        0f,
                        mods.flatMap {
                            when {
                                it.isSMod -> Misc.getStoryOptionColor().asList()
                                it.isDmod -> listOf(
                                    Misc.getGrayColor(),
                                    Misc.getNegativeHighlightColor()
                                )
                                else -> Misc.getTextColor().asList()
                            }
                        }
                            .toTypedArray(),
                        *mods.flatMap {
                            when {
                                it.isDmod -> listOf(it.spec.displayName, "(D)")
                                else -> it.spec.displayName.asList()
                            }
                        }
                            .toTypedArray()
                    )
                }
            }.also {
                hmodPanel.addUIElement(it).inTL(labelWidth, 0f)
                hmodPanel.position.setSize(hmodPanel.position.width, it.position.height)
            }
        }.also { tooltip.addCustom(it, 0f).apply { position.belowLeft(armPanel, 5f) } }
    }

    private fun recalculatePlanetSpriteSizes(planet: SectorEntityToken) {
        spriteRadii = planet.market?.submarketsCopy
            ?.flatMap { submarket ->
                submarket.cargo?.mothballedShips?.membersListCopy?.map { submarket to it } ?: emptyList()
            }
            ?.map { (submarket, ship) ->
                getOrCalculateCachedSpriteInfo(
                    submarket,
                    ship.hullSpec.spriteName
                )
            }
            ?.map { it.radius }

        minShipRadius = spriteRadii
            ?.minOrNull() ?: desiredMinShipRadius
        maxShipRadius = spriteRadii
            ?.maxOrNull() ?: desiredMaxShipRadius
        shipAverageRadius =
            spriteRadii
                ?.average()?.toFloat() ?: listOf(desiredMinShipRadius, desiredMaxShipRadius).average().toFloat()
    }

    private fun calculateFadeMultiplierBasedOnZoom(
        settingsInner: LifecyclePlugin.Settings,
        zoomMult: Float
    ): Float {
        val zoomedIn = 0.5f
        val zoomedOut = 3.0f
        // Thank you AlexAtheos for putting me on the right path
        // y = y1 + ((y2 - y1)/(x2 - x1))*(x - x1)
        val mult =
            kotlin.runCatching { 1f + ((settingsInner.alphaMult - 1f) / (zoomedOut - zoomedIn)) * (zoomMult - zoomedIn) }
                .getOrDefault(settingsInner.alphaMult)
        return mult
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
        spriteInfoCache.clear()
        done = true

        Global.getSector().removeTransientScript(this)
    }

    private fun mapToNewScale(
        sprite: SpriteInfo,
        oldScaleMin: Float,
        oldScaleMax: Float,
        newScaleMin: Float,
        newScaleMax: Float
    ): Float {
        val oldScaleRange = (oldScaleMax - oldScaleMin)
        val newScaleRange = (newScaleMax - newScaleMin)

        return (((sprite.radius - oldScaleMin) * newScaleRange / oldScaleRange) + newScaleMin)
//            .coerceAtMost(sprite.radius)
        // Don't stretch a sprite bigger than actually is, only downscale large sprites.
    }

    private fun getOrCalculateCachedSpriteInfo(submarket: SubmarketAPI, spriteName: String): SpriteInfo {
        val key = (submarket.spec?.id ?: "") + spriteName

        if (!spriteInfoCache.containsKey(key)) {
            val sprite = Global.getSettings().getSprite(spriteName)
            spriteInfoCache[key] = SpriteInfo(
                spriteAPI = sprite,
                radius = hypot(sprite.width, sprite.height) / 2
            )
        }

        return spriteInfoCache[key]!!
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
        val tooltipMaker: ((tooltip: TooltipMakerAPI, expanded: Boolean) -> TooltipMakerAPI?)
    )
}