package org.wisp.trophyplanet

import com.fs.starfarer.api.EveryFrameScriptWithCleanup
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.impl.campaign.ids.Submarkets
import org.wisp.trophyplanet.packedcircle.PackedCircle
import org.wisp.trophyplanet.packedcircle.PackedCircleManager
import kotlin.math.hypot

class TrophyPlanetScript : EveryFrameScriptWithCleanup {
    var planet: PlanetAPI? = null
    var done = false

    var packedCircleManager = PackedCircleManager()

    override fun isDone(): Boolean = done

    override fun runWhilePaused() = false

    override fun advance(amount: Float) {
        val planetInner = planet ?: return

        val storage =
            planetInner.market?.submarketsCopy?.firstOrNull() { it.specId == Submarkets.SUBMARKET_STORAGE }
                ?: return

        if (storage.cargo.mothballedShips.numMembers == 0) return

        storage.cargo.mothballedShips.membersListCopy.forEachIndexed { index, ship ->
            val sprite = Global.getSettings().getSprite(ship.hullSpec.spriteName)
            val spriteDiameter = hypot(sprite.width, sprite.height)

            val customEntity = planetInner.containingLocation.addCustomEntity(
                null,
                "",
                "Trophy_Planet-DummyFleet",
                null,
                null
            )
                .apply {
                    this.addTag(LifecyclePlugin.TROPHY_ENTITY_TAG)
                    this.setLocation(
                        planetInner.location.x,
                        planetInner.location.y
                    )
                }

            (customEntity.customPlugin as DummyFleetEntity).apply {
                this.addSprite(sprite)
            }

            packedCircleManager.circles += PackedCircle(
                id = ship.id,
                radius = spriteDiameter / 2,
                position = planetInner.location,
                isPulledToCenter = true,
                isPinned = false
            )
        }
    }

    override fun cleanup() {

    }
}