package org.wisp.trophyplanet

import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin

class DummyFleetEntity : BaseCustomEntityPlugin() {
    var spriteScale: Float = 1f

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
            it.renderAtCenter(entity.location.x, entity.location.y)
        }
    }
}