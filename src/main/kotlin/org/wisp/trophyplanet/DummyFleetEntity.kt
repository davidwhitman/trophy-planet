package org.wisp.trophyplanet

import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin
import com.fs.starfarer.campaign.fleet.CampaignFleet
import com.fs.starfarer.campaign.fleet.CampaignFleetMemberView
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.campaign.util.CollectionView

class DummyFleetEntity : BaseCustomEntityPlugin(), CollectionView.CollectionViewDelegate<CampaignFleetMemberView> {
    var trophyFleet: CampaignFleet? = null

    override fun init(entity: SectorEntityToken?, pluginParams: Any?) {
        super.init(entity, pluginParams)

//        if (pluginParams !is JumpAnimation) {
//            throw ClassCastException("pluginParams must be a JumpAnimation")
//        }
    }

    override fun advance(amount: Float) {
        super.advance(amount)
    }

    override fun render(layer: CampaignEngineLayers, viewport: ViewportAPI) {
        super.render(layer, viewport)

    }

    override fun createItemView(var1: Any?): CampaignFleetMemberView? {
        return if (var1 is FleetMember) {
//            CampaignFleetMemberView(this.fleet, var1 as FleetMember?)
            null
        } else {
            null
        }
    }

    override fun shouldCreateViewFor(p0: Any?): Boolean {
        TODO("Not yet implemented")
    }
}