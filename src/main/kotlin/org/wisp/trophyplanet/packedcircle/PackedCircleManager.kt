package org.wisp.trophyplanet.packedcircle

import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.Rectangle
import org.lwjgl.util.vector.Vector2f
import kotlin.math.sqrt

class PackedCircleManager(
    val circles: MutableList<PackedCircle> = mutableListOf(),
    val pinnedCircleIds: MutableList<Int> = mutableListOf(),
    var desiredTarget: Vector2f = Vector2f()
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
            if (draggedCircle != null && draggedCircle != value) {
                draggedCircle!!.radius = draggedCircle!!.originalRadius
            }

            field = value
        }

    /**
     * Set the boundary rectangle for the circle packing.
     * This is used to locate the 'center'
     * https://github.com/snorpey/circlepacker/blob/master/src/PackedCircleManager.js#L30
     */
    var bounds: Rectangle = Rectangle(0, 0, 0, 0)

    fun addCircle(circle: PackedCircle) {
        circles += circle
        circle.targetPosition = this.desiredTarget.copy()
    }

    fun removeCircle(circleId: String) {
        circles.removeIf { it.id == circleId }
    }

    fun updatePositions() {
        // Choosing not to copy anything, we'll see what happens
//        val previousPositions = circles.map { PackedCircle }
//
        if (this.desiredTarget != null && this.isCenterPullActive) {
            this.pushAllCirclesTowardTarget(desiredTarget)
        }

        this.handleCollisions()

        this.circles.forEach { this.handleBoundaryForCircle(it) }
    }

    fun pushAllCirclesTowardTarget(target: Vector2f) {
        val point = Vector2f()


        for (n in (0 until PackedCircleManager.numberOfCenteringPasses)) {
            circles.forEach { circle ->
                if (circle.isPulledToCenter) {
                    // Kinematic circles can't be pushed around.
                    val isCircleKinematic =
                        circle == draggedCircle
                                || circle.isPinned

                    if (isCircleKinematic)
                        return@forEach

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

        for (n in (0 until numberOfCollisionPasses)) {
            for (circleA in circles) {
                for (circleB in circles) {
                    val isCircleAKinematic = (circleA == draggedCircle) || circleA.isPinned
                    val isCircleBKinematic = (circleB == draggedCircle) || circleB.isPinned

                    if (circleA == circleB || (isCircleAKinematic && isCircleBKinematic)) {
                        continue
                    }

                    val dx = circleB.position.x - circleA.position.x
                    val dy = circleB.position.y - circleA.position.y

                    // The distance between the two circles radii,
                    // but we're also gonna pad it a tiny bit
                    val r = (circleA.radius + circleB.radius) * 1.08f
                    val d = MathUtils.getDistanceSquared(circleA.position, circleB.position)

                    if (d < (r * r) - 0.02f) {
                        force.x = dx
                        force.y = dy
                        force.normalise()

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
    fun handleBoundaryForCircle(circle: PackedCircle) {
        val x = circle.position.x
        val y = circle.position.y
        val radius = circle.radius

        var overEdge = false

        // Wisp note: the y and height params might be swapped
        if (x + radius >= this.bounds.right) {
            circle.position.x = this.bounds.right - radius
            overEdge = true
        } else if (x - radius < this.bounds.x) {
            circle.position.x = this.bounds.x + radius
            overEdge = true
        }

        if (y + radius > this.bounds.bottom) {
            circle.position.y = this.bounds.bottom - radius
            overEdge = true
        } else if (y - radius < this.bounds.height) {
            circle.position.y = this.bounds.height + radius
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

val Rectangle.bottom: Int
    get() = this.y + this.height