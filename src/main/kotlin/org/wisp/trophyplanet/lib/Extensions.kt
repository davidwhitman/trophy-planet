package org.wisp.trophyplanet.lib

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator
import com.fs.starfarer.api.util.Misc
import org.apache.log4j.Priority
import org.json.JSONArray
import org.json.JSONObject
import org.lazywizard.lazylib.ext.json.getFloat
import org.lwjgl.util.vector.Vector2f
import kotlin.math.pow
import kotlin.random.Random


/**
 * How far the token's system is from the center of the sector.
 */
val SectorEntityToken.distanceFromCenterOfSector: Float
    get() = this.starSystem.distanceFromCenterOfSector


/**
 * How far the system is from another system.
 */
fun StarSystemAPI.distanceFrom(other: StarSystemAPI): Float =
    Misc.getDistanceLY(
        this.location,
        other.location
    )

/**
 * How far the token is from another token, in hyperspace.
 */
fun SectorEntityToken.distanceFrom(other: SectorEntityToken): Float =
    Misc.getDistanceLY(
        this.locationInHyperspace,
        other.locationInHyperspace
    )

/**
 * How far the system is from the center of the sector.
 */
val StarSystemAPI.distanceFromCenterOfSector: Float
    get() = Misc.getDistanceLY(
        this.location,
        Global.getSector().hyperspace.location
    )

/**
 * How far the token's system is from the player's fleet, in LY.
 */
val SectorEntityToken.distanceFromPlayerInHyperspace: Float
    get() = this.starSystem.distanceFromPlayerInHyperspace

/**
 * How far the system is from the player's fleet, in LY.
 */
val StarSystemAPI.distanceFromPlayerInHyperspace: Float
    get() = Misc.getDistanceLY(
        this.location,
        Global.getSector().playerFleet.locationInHyperspace
    )

/**
 * How far the point is from the player's fleet, in LY.
 */
val Vector2f.distanceFromPlayerInHyperspace: Float
    get() = Misc.getDistanceLY(
        this,
        Global.getSector().playerFleet.locationInHyperspace
    )

/**
 * Empty string, `""`.
 */
val String.Companion.empty
    get() = ""

/**
 * Creates a token for the fleet at its current location.
 */
fun CampaignFleetAPI.createToken(): SectorEntityToken = this.containingLocation.createToken(this.location)

/**
 * Whether the point is inside the circle.
 */
fun isPointInsideCircle(
    point: Vector2f,
    circleCenter: Vector2f,
    circleRadius: Float
): Boolean = (point.x - circleCenter.x).pow(2) +
        (point.y - circleCenter.y).pow(2) < circleRadius.pow(2)

/**
 * @see [isPointInsideCircle]
 */
fun Vector2f.isInsideCircle(
    center: Vector2f,
    radius: Float
) = isPointInsideCircle(this, center, radius)

/**
 * Displays the dialog as an interaction with [targetEntity].
 */
fun InteractionDialogPlugin.show(campaignUIAPI: CampaignUIAPI, targetEntity: SectorEntityToken) =
    campaignUIAPI.showInteractionDialog(this, targetEntity)

/**
 * Gets the first intel of the given type.
 */
fun <T : IntelInfoPlugin> IntelManagerAPI.findFirst(intelClass: Class<T>): T? =
    this.getFirstIntel(intelClass) as? T

/**
 * The player's first name. Falls back to their full name, and then to "No-Name" if they have no name.
 */
val PersonAPI.firstName: String
    get() = this.name?.first?.ifBlank { null }
        ?: this.nameString
        ?: "No-Name"

/**
 * The player's last name. Falls back to their full name, and then to "No-Name" if they have no name.
 */
val PersonAPI.lastName: String
    get() = this.name?.last?.ifBlank { null }
        ?: this.nameString
        ?: "No-Name"

/**
 * Removes a [BaseBarEventCreator] immediately.
 */
fun <T : BaseBarEventCreator> BarEventManager.removeBarEventCreator(barEventCreatorClass: Class<T>) {
    this.setTimeout(barEventCreatorClass, 0f)
    this.creators.removeAll { it::class.java == barEventCreatorClass }
}

/**
 * Adds the [BaseBarEventCreator] to the [BarEventManager] if it isn't already present and if the [predicate] returns true.
 */
inline fun <reified T : BaseBarEventCreator> BarEventManager.addBarEventCreatorIf(
    barEventCreator: T = T::class.java.newInstance(),
    predicate: () -> Boolean
) {
    if (!this.hasEventCreator(barEventCreator::class.java) && predicate()) {
        this.addEventCreator(barEventCreator)
    }
}

/**
 * True if any of the arguments are equal; false otherwise.
 */
fun Any.equalsAny(vararg other: Any): Boolean = arrayOf(*other).any { this == it }

/**
 * Returns `primaryEntity` if non-null, or the first item in `connectedEntities` otherwise. Returns `null` if `connectedEntities` is empty.
 */
val MarketAPI.preferredConnectedEntity: SectorEntityToken?
    get() = this.primaryEntity ?: this.connectedEntities.firstOrNull()

fun List<PlanetAPI>.getNonHostileOnlyIfPossible(): List<PlanetAPI> {
    val nonHostile = this.filter { it.market?.faction?.isHostileTo(Global.getSector().playerFaction.id) == true }
    return if (nonHostile.isNotEmpty()) nonHostile else this
}

/**
 * Returns items matching the predicate or, if none are matching, returns the original [List].
 */
fun <T> List<T>.prefer(predicate: (item: T) -> Boolean): List<T> =
    this.filter { predicate(it) }
        .ifEmpty { this }

fun BaseIntelPlugin.endAndNotifyPlayer(delayBeforeEndingInDays: Float = 3f) {
    this.endAfterDelay(delayBeforeEndingInDays)
    this.sendUpdateIfPlayerHasIntel(null, false)
}

val LocationAPI.actualPlanets: List<PlanetAPI>
    get() = this.planets.filter { !it.isStar }

val LocationAPI.solidPlanets: List<PlanetAPI>
    get() = this.planets.filter { it.isSolidPlanet }

fun SectorEntityToken.hasSameMarketAs(other: SectorEntityToken?) =
    this.market != null && this.market.id == other?.market?.id

val PlanetAPI.isSolidPlanet: Boolean
    get() = !this.isStar && !this.isGasGiant

fun ClosedFloatingPointRange<Float>.random(): Float =
    (this.start + (this.endInclusive - this.start) * Random.nextFloat())

/**
 * Returns true if the two circles have any overlap, false otherwise.
 * (x1 - x0)^2 + (y1 - y0)^2 >= (r1 + r0)^2 (thanks, Greg)
 */
fun doCirclesIntersect(centerA: Vector2f, radiusA: Float, centerB: Vector2f, radiusB: Float) =
    (centerB.x - centerA.x).pow(2) + (centerB.y - centerA.y).pow(2) >= (radiusA + radiusB).pow(2)

operator fun Priority.compareTo(other: Priority) = this.toInt().compareTo(other.toInt())

fun JSONArray.toStringList(): List<String> {
    return MutableList(this.length()) {
        this.getString(it)
    }
        .filterNotNull()
}

fun JSONArray.toLongList(): List<Long> {
    return MutableList(this.length()) {
        this.getLong(it)
    }
}

fun JSONObject.tryGetString(key: String, default: () -> String): String =
    kotlin.runCatching { this.getString(key) }
        .getOrDefault(default())

fun JSONObject.tryGetBoolean(key: String, default: () -> Boolean): Boolean =
    kotlin.runCatching { this.getBoolean(key) }
        .getOrDefault(default())

fun JSONObject.tryGetFloat(key: String, default: () -> Float): Float =
    kotlin.runCatching { this.getFloat(key) }
        .getOrDefault(default())

fun JSONObject.tryGetInt(key: String, default: () -> Int): Int =
    kotlin.runCatching { this.getInt(key) }
        .getOrDefault(default())

fun <T> T?.asList(): List<T> = if (this == null) emptyList() else listOf(this)