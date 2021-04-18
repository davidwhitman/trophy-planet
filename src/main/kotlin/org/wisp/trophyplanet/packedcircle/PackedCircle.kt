package org.wisp.trophyplanet.packedcircle

import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.ext.minus
import org.lwjgl.util.vector.Vector2f

class PackedCircle(
    val id: String,
    radius: Float,
    position: Vector2f,
    val isPulledToCenter: Boolean,
    val isPinned: Boolean
) {
    var radiusSqared: Float = radius * radius
        private set
    var originalRadius: Float = radius
        private set

    var radius: Float = radius
        set(value) {
            field = value
            radiusSqared = value * value
            originalRadius = value
        }

    var previousPosition: Vector2f = position
        private set

    var position: Vector2f = position
        set(value) {
            previousPosition = this.position
            field = value.copy()
        }

    fun distanceSquaredFromTargetPosition(): Boolean {
        return MathUtils.getDistanceSquared(position, targetPosition) < radiusSqared
    }

    var targetPosition = Vector2f()
    var positionWithOffset = Vector2f()
    var previousPositionWithOffset = Vector2f()

    fun delta() =
        position - previousPosition
}

fun Vector2f.copy() = Vector2f(this.x, this.y)
fun Vector2f.multiplyInPlace(factor: Float) {
    this.x *= factor
    this.y *= factor
}