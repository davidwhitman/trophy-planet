package org.wisp.trophyplanet

import com.fs.starfarer.api.campaign.PlanetAPI

object TrophyPlanetTags {
    private const val TROPHY_ENTITY_TAG = "wisp_trophyEntity"

    fun getTagForPlanetEntities(planetAPI: PlanetAPI) = TROPHY_ENTITY_TAG + "_" + planetAPI.id
}