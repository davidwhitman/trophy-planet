package org.wisp.trophyplanet

import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin
import com.fs.starfarer.api.ui.TooltipMakerAPI

class DummyFleetEntity : BaseCustomEntityPlugin() {
    var spriteScale: Float = 1f
    var spriteAlpha: Float = 1f

    @Transient
    var tooltipMaker: ((tooltip: TooltipMakerAPI, isExpanded: Boolean) -> TooltipMakerAPI?)? = null

    @Transient
    private var sprites = mutableListOf<SpriteAPI>()

    override fun init(entity: SectorEntityToken?, pluginParams: Any?) {
        super.init(entity, pluginParams)
    }

    fun readResolve() {
        sprites = mutableListOf()
    }

    fun addSprite(sprite: SpriteAPI) {
        sprite.width = sprite.width * spriteScale
        sprite.height = sprite.height * spriteScale
        sprites.add(sprite)
    }

    override fun advance(amount: Float) {
        super.advance(amount)
    }

    override fun render(layer: CampaignEngineLayers, viewport: ViewportAPI) {
        super.render(layer, viewport)

        sprites.forEach {
            it.alphaMult = this.spriteAlpha
            it.renderAtCenter(entity.location.x, entity.location.y)
        }
    }

    override fun hasCustomMapTooltip(): Boolean = true

    override fun isMapTooltipExpandable(): Boolean = false

    override fun createMapTooltip(tooltip: TooltipMakerAPI?, expanded: Boolean) {
        super.createMapTooltip(tooltip, expanded)
        tooltip ?: return
        tooltipMaker?.invoke(tooltip, expanded)
    }

    override fun appendToCampaignTooltip(tooltip: TooltipMakerAPI?, level: SectorEntityToken.VisibilityLevel?) {
        super.appendToCampaignTooltip(tooltip, level)
        tooltip ?: return
        tooltipMaker?.invoke(tooltip, false)
    }

    /**
     * Call this when entity is being removed to avoid memory leaks.
     */
    fun onDestroy() {
        tooltipMaker = null
    }
}