package org.wisp.trophyplanet.packedcircle

import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.ext.minus
import org.lwjgl.util.vector.Vector2f

/**
 * @param entity An optional entity whose location will be kept in sync with the circle
 */
class PackedCircle(
    val id: String,
    radius: Float,
    position: Vector2f,
    val isPulledToCenter: Boolean,
    val isPinned: Boolean,
    var syncedEntity: Entity?
) {
//    var radiusSquared: Float = radius * radius
//        private set
    var originalRadius: Float = radius
        private set

    var radius: Float = radius
        set(value) {
            field = value
//            radiusSquared = value * value
            originalRadius = value
        }

//    var previousPosition: Vector2f = position

    val position: Vector2f = position
//        set(value) {
//            previousPosition = this.position
//            field = value.copy()
//        }

//    fun distanceSquaredFromTargetPosition(): Boolean {
//        return MathUtils.getDistanceSquared(position, targetPosition) < radiusSquared
//    }

//    var targetPosition = Vector2f()
//    var positionWithOffset = Vector2f()
//    var previousPositionWithOffset = Vector2f()

//    fun delta() =
//        position - previousPosition

    class Entity(var location: Vector2f)
}

fun Vector2f.copy() = Vector2f(this.x, this.y)
fun Vector2f.multiplyInPlace(factor: Float) {
    this.x *= factor
    this.y *= factor
}