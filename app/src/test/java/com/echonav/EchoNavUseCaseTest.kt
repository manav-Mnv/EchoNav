package com.echonav

import android.graphics.RectF
import com.echonav.data.model.AppMode
import com.echonav.data.model.DetectedObject
import com.echonav.domain.usecase.BuildAnnouncementUseCase
import com.echonav.domain.usecase.DirectionUseCase
import com.echonav.domain.usecase.DistanceUseCase
import com.echonav.domain.usecase.FeedbackFilterUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for domain use cases.
 *
 * These run on the JVM (no Android emulator needed) because the use cases
 * are pure Kotlin — no Android framework dependencies.
 *
 * Run with: ./gradlew test
 */
class EchoNavUseCaseTest {

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Creates a DetectedObject with a bounding box centered at (cx, cy) with given size */
    private fun makeObject(
        label: String,
        cx: Float,
        cy: Float = 0.5f,
        width: Float = 0.2f,
        height: Float = 0.2f,
        confidence: Float = 0.9f
    ): DetectedObject {
        val half = width / 2f
        val halfH = height / 2f
        return DetectedObject(
            label       = label,
            confidence  = confidence,
            boundingBox = RectF(cx - half, cy - halfH, cx + half, cy + halfH)
        )
    }

    // ── DirectionUseCase ──────────────────────────────────────────────────

    private val directionUseCase = DirectionUseCase()

    @Test
    fun `direction - left zone returns on your left`() {
        val obj = makeObject("person", cx = 0.2f)
        assertEquals("on your left", directionUseCase.execute(obj))
    }

    @Test
    fun `direction - center zone returns ahead`() {
        val obj = makeObject("chair", cx = 0.5f)
        assertEquals("ahead", directionUseCase.execute(obj))
    }

    @Test
    fun `direction - right zone returns on your right`() {
        val obj = makeObject("door", cx = 0.8f)
        assertEquals("on your right", directionUseCase.execute(obj))
    }

    @Test
    fun `direction - left boundary (0,38) is still on your left`() {
        val obj = makeObject("person", cx = 0.37f)
        assertEquals("on your left", directionUseCase.execute(obj))
    }

    @Test
    fun `direction - right boundary (0,62) is still on your right`() {
        val obj = makeObject("person", cx = 0.63f)
        assertEquals("on your right", directionUseCase.execute(obj))
    }

    // ── DistanceUseCase ───────────────────────────────────────────────────

    private val distanceUseCase = DistanceUseCase()

    @Test
    fun `distance - large bbox is very close`() {
        // 0.6 * 0.6 = 0.36 area — above 0.25 threshold
        val obj = makeObject("person", cx = 0.5f, width = 0.6f, height = 0.6f)
        assertEquals("very close", distanceUseCase.execute(obj))
    }

    @Test
    fun `distance - medium bbox is nearby`() {
        // 0.3 * 0.3 = 0.09 area — above 0.08 threshold
        val obj = makeObject("chair", cx = 0.5f, width = 0.3f, height = 0.3f)
        assertEquals("nearby", distanceUseCase.execute(obj))
    }

    @Test
    fun `distance - small bbox is ahead`() {
        // 0.1 * 0.1 = 0.01 area — below 0.08 threshold
        val obj = makeObject("door", cx = 0.5f, width = 0.1f, height = 0.1f)
        assertEquals("ahead", distanceUseCase.execute(obj))
    }

    @Test
    fun `isDanger returns true for very close objects`() {
        val obj = makeObject("person", cx = 0.5f, width = 0.6f, height = 0.6f)
        assertTrue(distanceUseCase.isDanger(obj))
    }

    @Test
    fun `isDanger returns false for nearby objects`() {
        val obj = makeObject("person", cx = 0.5f, width = 0.3f, height = 0.3f)
        assertFalse(distanceUseCase.isDanger(obj))
    }

    // ── FeedbackFilterUseCase ─────────────────────────────────────────────

    private lateinit var feedbackFilter: FeedbackFilterUseCase

    @Before
    fun setupFilter() {
        feedbackFilter = FeedbackFilterUseCase()
    }

    @Test
    fun `filter - new object should announce`() {
        val obj = makeObject("person", cx = 0.5f)
        assertTrue(feedbackFilter.shouldAnnounce(obj, "nearby"))
    }

    @Test
    fun `filter - same object same distance within cooldown should suppress`() {
        val obj = makeObject("person", cx = 0.5f)
        feedbackFilter.shouldAnnounce(obj, "nearby")          // first → true
        val second = feedbackFilter.shouldAnnounce(obj, "nearby") // within cooldown → false
        assertFalse(second)
    }

    @Test
    fun `filter - same object different distance should announce despite cooldown`() {
        val obj = makeObject("person", cx = 0.5f)
        feedbackFilter.shouldAnnounce(obj, "ahead")           // first pass
        val changed = feedbackFilter.shouldAnnounce(obj, "very close") // distance changed
        assertTrue(changed)
    }

    @Test
    fun `filter - different labels are tracked independently`() {
        val person = makeObject("person", cx = 0.5f)
        val chair  = makeObject("chair",  cx = 0.3f)

        feedbackFilter.shouldAnnounce(person, "nearby")  // person: announced
        // chair hasn't been seen — should announce
        assertTrue(feedbackFilter.shouldAnnounce(chair, "ahead"))
    }

    @Test
    fun `filter - reset clears all state`() {
        val obj = makeObject("person", cx = 0.5f)
        feedbackFilter.shouldAnnounce(obj, "nearby")  // first
        feedbackFilter.reset()
        // After reset, should be treated as new
        assertTrue(feedbackFilter.shouldAnnounce(obj, "nearby"))
    }

    // ── BuildAnnouncementUseCase ──────────────────────────────────────────

    private val buildAnnouncement = BuildAnnouncementUseCase(directionUseCase, distanceUseCase)

    @Test
    fun `announcement - very close person ahead includes caution`() {
        // Large bbox — very close + centered
        val obj = makeObject("person", cx = 0.5f, width = 0.6f, height = 0.6f)
        val result = buildAnnouncement.execute(obj)
        assertTrue(result.contains("Person"))
        assertTrue(result.contains("ahead"))
        assertTrue(result.contains("very close"))
        assertTrue(result.contains("Caution"))
    }

    @Test
    fun `announcement - nearby chair on left is correct`() {
        val obj = makeObject("chair", cx = 0.2f, width = 0.3f, height = 0.3f)
        val result = buildAnnouncement.execute(obj)
        assertTrue(result.contains("Chair"))
        assertTrue(result.contains("on your left"))
        assertFalse(result.contains("Caution"))
    }

    @Test
    fun `announcement - distant door ahead has no caution`() {
        val obj = makeObject("door", cx = 0.5f, width = 0.05f, height = 0.1f)
        val result = buildAnnouncement.execute(obj)
        assertTrue(result.contains("Door"))
        assertFalse(result.contains("Caution"))
    }

    @Test
    fun `announcement - car on right nearby`() {
        val obj = makeObject("car", cx = 0.75f, width = 0.3f, height = 0.3f)
        val result = buildAnnouncement.execute(obj)
        assertTrue(result.contains("Car"))
        assertTrue(result.contains("on your right"))
    }

    // ── AppMode ───────────────────────────────────────────────────────────

    @Test
    fun `mode cycle - Explore next is Focus`() {
        assertEquals(AppMode.FOCUS, AppMode.EXPLORE.next())
    }

    @Test
    fun `mode cycle - Focus next is Silent`() {
        assertEquals(AppMode.SILENT, AppMode.FOCUS.next())
    }

    @Test
    fun `mode cycle - Silent next wraps to Explore`() {
        assertEquals(AppMode.EXPLORE, AppMode.SILENT.next())
    }

    @Test
    fun `mode announcements are non-empty`() {
        AppMode.values().forEach { mode ->
            assertTrue(
                "Mode ${mode.name} has empty announcement",
                mode.announcement().isNotEmpty()
            )
        }
    }

    // ── DetectedObject helpers ────────────────────────────────────────────

    @Test
    fun `centerX is midpoint of bounding box`() {
        val obj = DetectedObject(
            label       = "test",
            confidence  = 0.9f,
            boundingBox = RectF(0.2f, 0.2f, 0.8f, 0.8f)
        )
        assertEquals(0.5f, obj.centerX, 0.001f)
    }

    @Test
    fun `normalizedArea is width times height`() {
        val obj = DetectedObject(
            label       = "test",
            confidence  = 0.9f,
            boundingBox = RectF(0.0f, 0.0f, 0.5f, 0.4f)
        )
        // width=0.5, height=0.4 → area=0.2
        assertEquals(0.2f, obj.normalizedArea, 0.001f)
    }
}
