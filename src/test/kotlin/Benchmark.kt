import org.junit.jupiter.api.Test
import org.lwjgl.util.Rectangle
import org.lwjgl.util.vector.Vector2f
import org.wisp.trophyplanet.packedcircle.PackedCircle
import org.wisp.trophyplanet.packedcircle.PackedCircleManager
import java.util.*

class Benchmark {
    @Test
    fun runBenchmark() {
        val start = Date().time

        val circles1 = mutableListOf<PackedCircle>()

        // Circles around station 1
        for (i in 1 until 500) {
            circles1 += PackedCircle(
                id = i.toString(),
                radius = 50f,
                position = Vector2f(500f, 500f),
                isPulledToCenter = true,
                isPinned = false,
                syncedEntity = null
            )
        }

        // Circles around station 2
        val circles2 = mutableListOf<PackedCircle>()
        for (i in 500 until 1000) {
            circles2 += PackedCircle(
                id = i.toString(),
                radius = 50f,
                position = Vector2f(6000f, 6000f),
                isPulledToCenter = true,
                isPinned = false,
                syncedEntity = null
            )
        }

        val allCirclesInSystem = circles1 + circles2

        // Station 1
        val bounds1 = Rectangle(500, 500, 5000, 5000)
        val pcm1 = PackedCircleManager(
//            bounds = null,
            bounds = bounds1,
            desiredTarget = Vector2f(bounds1.x.toFloat(), bounds1.y.toFloat())
        )
        // Station 2
        val bounds2 = Rectangle(6000, 6000, 5000, 5000)
        val pcm2 = PackedCircleManager(
//            bounds = null,
            bounds = bounds2,
            desiredTarget = Vector2f(bounds2.x.toFloat(), bounds2.y.toFloat())
        )

        // Add circles from both stations, but don't change station 2's circle positions
        circles1.forEach {
            pcm1.addCircle(it)
            pcm2.addCircle(it.copy(isPinned = true))
        }
        // Add circles from both stations, but don't change station 1's circle positions
        circles2.forEach {
            pcm1.addCircle(it.copy(isPinned = true))
            pcm2.addCircle(it)
        }

        for (i in 0 until 2500) {
            pcm1.updatePositions()
            pcm2.updatePositions()
        }

        println("Benchmark result: ${(Date().time - start) / 1000f} seconds")
        println("Station 1 position: ${pcm1.desiredTarget?.x}, ${pcm1.desiredTarget?.y}")
        println(
            "Average station 1 circle position: ${
                circles1.map { it.position.x }.average()
            }, ${circles1.map { it.position.y }.average()}"
        )
        println("Station 2 position: ${pcm2.desiredTarget?.x}, ${pcm2.desiredTarget?.y}")
        println(
            "Average station 2 circle position: ${
                circles2.map { it.position.x }.average()
            }, ${circles2.map { it.position.y }.average()}"
        )

        // No bounds
//        Benchmark result: 27.281 seconds
//        Station 1 position: 500.0, 500.0
//        Average station 1 circle position: 499.99992095587965, 499.99992095587965
//        Station 2 position: 6000.0, 6000.0
//        Average station 2 circle position: 6000.000001953125, 6000.000001953125

        // With bounds
//        Benchmark result: 13.799 seconds
//        Station 1 position: 500.0, 500.0
//        Average station 1 circle position: 500.00011882132185, -2050.0
//        Station 2 position: 6000.0, 6000.0
//        Average station 2 circle position: 6000.000025390625, 3450.0
    }
}