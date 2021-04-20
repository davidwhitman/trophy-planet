package org.wisp.trophyplanet.packedcircle

import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.Rectangle
import org.lwjgl.util.vector.Vector2f
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * https://github.com/snorpey/circlepacker/blob/master/src/PackedCircleManager.js
 *
 * @param bounds Set the boundary rectangle for the circle packing.
 * This is used to locate the 'center'
 * https://github.com/snorpey/circlepacker/blob/master/src/PackedCircleManager.js#L30
 */
class PackedCircleManager(
    val circles: MutableMap<String, PackedCircle> = mutableMapOf(),
    val pinnedCircleIds: MutableList<Int> = mutableListOf(),
    var bounds: Rectangle?,
    var desiredTarget: Vector2f?
) {
    companion object {
        const val damping: Float = 0.025f

        // Number of passes for the centering and collision
        // algorithms - it's (O)logN^2 so use increase at your own risk!
        // Play with these numbers - see what works best for your project
        const val numberOfCenteringPasses = 1
        const val numberOfCollisionPasses = 3
    }

    var isCenterPullActive = true

    /**
     * Force a certain circle to be the 'draggedCircle'.
     * Can be used to undrag a circle by setting to null
     * https://github.com/snorpey/circlepacker/blob/master/src/PackedCircleManager.js#L267
     */
    var draggedCircle: PackedCircle? = null
        set(value) {
            // Setting to null, and we had a circle before.
            // Restore the radius of the circle as it was previously
            if (field != null && field != value) {
                field!!.radius = field!!.originalRadius
            }

            field = value
        }

    fun addCircle(circle: PackedCircle) {
        circles += circle.id to circle
//        circle.targetPosition = this.desiredTarget.copy()
    }

    fun removeCircle(circleId: String) {
        circles.remove(circleId)
    }

    fun updatePositions() {
        // Choosing not to copy anything, we'll see what happens
//        val previousPositions = circles.values.forEach {
//            it.previousPosition = it.position.copy()
//        }

        val desiredTargetInner = this.desiredTarget

        if (desiredTargetInner != null && this.isCenterPullActive) {
            this.pushAllCirclesTowardTarget(desiredTargetInner)
        }

        this.handleCollisions()

        if (bounds != null) {
            this.circles.values
                .filter { !it.isPinned }
                .forEach { this.handleBoundaryForCircle(it, bounds!!) }
        }

        this.circles.values.forEach {
            it.syncedEntity?.location?.x = it.position.x
            it.syncedEntity?.location?.y = it.position.y
        }
    }

    fun pushAllCirclesTowardTarget(target: Vector2f) {
        val point = Vector2f()

        for (n in (0 until PackedCircleManager.numberOfCenteringPasses)) {
            for (circle in circles.values) {
                if (circle.isPulledToCenter) {
                    // Kinematic circles can't be pushed around.
                    val isCircleKinematic =
                        circle == draggedCircle || circle.isPinned

                    if (isCircleKinematic)
                        continue

                    point.x = circle.position.x - target.x
                    point.y = circle.position.y - target.y
                    point.x = point.x * damping
                    point.y = point.y * damping

                    circle.position.x -= point.x
                    circle.position.y -= point.y
                }
            }
        }
    }

    /**
     * Packs the circles towards the center of the bounds.
     * Each circle will have it's own 'targetPosition' later on
     */
    fun handleCollisions() {
        val force = Vector2f()
        val circleEntries = circles.values

        for (n in (0 until numberOfCollisionPasses)) {
            for (circleA in circleEntries) {
                // Kinematic circles can't be pushed around.
                val isCircleAKinematic = (circleA == draggedCircle) || circleA.isPinned

                for (circleB in circleEntries) {
                    val isCircleBKinematic = (circleB == draggedCircle) || circleB.isPinned

                    if (circleA == circleB || (isCircleAKinematic && isCircleBKinematic)) {
                        continue
                    }

                    val dx = circleB.position.x - circleA.position.x
                    val dy = circleB.position.y - circleA.position.y

                    // The distance between the two circles radii,
                    // but we're also gonna pad it a tiny bit
                    val r = (circleA.radius + circleB.radius) * 1.08f
                    var d = MathUtils.getDistanceSquared(circleA.position, circleB.position)

                    // Add a little bit of distance to create an inverse force
                    if (d == 0f) {
                        d = Random.nextFloat()
                    }

                    if (d < (r * r) - 0.02f) {
                        force.x = dx
                        force.y = dy

                        if (force.length() > 0f) {
                            force.normalise()
                        } else {
                            // If force is 0, add a random force
                            force.x = Random.nextFloat()
                            force.y = Random.nextFloat()
                        }

                        val inverseForce = (r - sqrt(d)) * 0.5f
                        force.multiplyInPlace(inverseForce)

                        if (!isCircleBKinematic) {
                            if (isCircleAKinematic) {
                                // Double inverse force to make up
                                // for the fact that the other object is fixed
                                force.multiplyInPlace(2.2f)
                            }

                            circleB.position.x += force.x
                            circleB.position.y += force.y
                        }

                        if (!isCircleAKinematic) {
                            if (isCircleBKinematic) {
                                // Double inverse force to make up
                                // for the fact that the other object is fixed
                                force.multiplyInPlace(2.2f)
                            }

                            circleA.position.x -= force.x
                            circleA.position.y -= force.y
                        }
                    }
                }
            }
        }
    }

    /**
     * https://github.com/snorpey/circlepacker/blob/master/src/PackedCircleManager.js#L233
     */
    fun handleBoundaryForCircle(circle: PackedCircle, bounds: Rectangle) {
        val x = circle.position.x
        val y = circle.position.y
        val radius = circle.radius

        var overEdge = false

        // Wisp note: the y and height params might be swapped
        if (x + radius >= bounds.right) {
            circle.position.x = bounds.right - radius
            overEdge = true
        } else if (x - radius < bounds.left) {
            circle.position.x = bounds.left + radius
            overEdge = true
        }

        if (y + radius > bounds.bottom) {
            circle.position.y = bounds.bottom - radius
            overEdge = true
        } else if (y - radius < bounds.top) {
            circle.position.y = bounds.top + radius
            overEdge = true
        }

        // end dragging if user dragged over edge
        if (overEdge && circle === this.draggedCircle) {
            this.draggedCircle = null
        }
    }
}

val Rectangle.right: Int
    get() = this.x + this.width

val Rectangle.left: Int
    get() = this.x

val Rectangle.bottom: Int
    get() = this.y - this.height

val Rectangle.top: Int
    get() = this.y// - this.height