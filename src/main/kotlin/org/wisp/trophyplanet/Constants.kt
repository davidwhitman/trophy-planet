package org.wisp.trophyplanet

import com.fs.starfarer.api.campaign.PlanetAPI

object Constants {
    const val MOD_ID = "wisp_trophy-planet"

    private const val TROPHY_ENTITY_TAG = "wisp_trophyEntity"

    fun getTagForPlanetEntities(planetAPI: PlanetAPI) = TROPHY_ENTITY_TAG + "_" + planetAPI.id
}