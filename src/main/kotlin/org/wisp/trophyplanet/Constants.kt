package org.wisp.trophyplanet

import com.fs.starfarer.api.campaign.SectorEntityToken

object Constants {
    const val MOD_ID = "wisp_trophy-planet"

    const val TROPHY_ENTITY_TAG_PREFIX = "wisp_trophyEntity"

    fun getTagForEntities(token: SectorEntityToken) = TROPHY_ENTITY_TAG_PREFIX + "_" + token.id
}