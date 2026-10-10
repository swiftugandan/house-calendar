package dev.housecalendar.app

import dev.housecalendar.app.calendar.CalendarActions
import dev.housecalendar.app.calendar.CalendarRepository
import dev.housecalendar.app.calendar.CalendarSnapshot
import dev.housecalendar.app.calendar.Clock
import dev.housecalendar.app.calendar.NewMember
import dev.housecalendar.app.sync.PairingCode
import dev.housecalendar.app.ui.theme.DarkColors
import dev.housecalendar.app.ui.theme.LightColors
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.DeviceUpsert
import dev.housecalendar.contracts.EventId
import dev.housecalendar.contracts.FamilyCreated
import dev.housecalendar.contracts.FamilyId
import dev.housecalendar.contracts.MemberId
import dev.housecalendar.contracts.MemberKind
import dev.housecalendar.contracts.MemberUpsert
import dev.housecalendar.contracts.Owner
import dev.housecalendar.contracts.Who
import dev.housecalendar.schedule.Projection
import dev.housecalendar.sync.FamilyKey
import dev.housecalendar.sync.InMemoryOpLog
import dev.housecalendar.sync.OpFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The repository, family actions, pairing codes and the colour tokens. */
class FoundationsTest {
    private val clock = Clock { Instant.parse("2026-10-10T12:00:00Z") }
    private val device = TestFamily().phone
    private val sam = MemberId(UUID.fromString("11111111-1111-4111-8111-111111111111"))
    private val ada = MemberId(UUID.fromString("22222222-2222-4222-8222-222222222222"))

    @Test
    fun `creating a family records the family, its members in order, and this device`() {
        val payloads = CalendarActions.createFamily(
            "The Okellos", LONDON, DayOfWeek.MONDAY,
            members = listOf(NewMember(sam, "Sam", MemberKind.Adult), NewMember(ada, "Ada", MemberKind.Child)),
            device = device, deviceName = "Sam's phone", role = DeviceRole.Phone, owner = sam,
        )
        assertEquals(FamilyCreated("The Okellos", LONDON, DayOfWeek.MONDAY), payloads.first())
        assertEquals(
            listOf(MemberUpsert(sam, "Sam", 0, MemberKind.Adult, false), MemberUpsert(ada, "Ada", 1, MemberKind.Child, false)),
            payloads.filterIsInstance<MemberUpsert>(),
        )
        assertEquals(DeviceUpsert(device, "Sam's phone", DeviceRole.Phone, Owner.Member(sam)), payloads.last())
    }

    @Test
    fun `a new member gets the next colour`() {
        val family = TestFamily()
        val bea = MemberId(UUID.fromString("33333333-3333-4333-8333-333333333333"))
        assertEquals(MemberUpsert(bea, "Bea", 4, MemberKind.Child, false), CalendarActions.addMember(family.state(), bea, " Bea ", MemberKind.Child))
    }

    @Test
    fun `the repository goes from no family to ready, and applies only what is new`() = runTest {
        val log = InMemoryOpLog()
        val repository = CalendarRepository(log, device, clock, Dispatchers.Unconfined)
        assertEquals(CalendarSnapshot.NoFamily, repository.refreshNow())

        val family = FamilyId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
        repository.createFamily(family, CalendarActions.createFamily("Okellos", LONDON, DayOfWeek.MONDAY, listOf(NewMember(sam, "Sam", MemberKind.Adult)), device, "Phone", DeviceRole.Phone, sam))
        val ready = repository.refreshNow() as CalendarSnapshot.Ready
        assertEquals(family, ready.familyId)
        assertEquals("Okellos", ready.state.familyName)

        val event = EventId(UUID.fromString("44444444-4444-4444-8444-444444444444"))
        repository.record(listOf(dev.housecalendar.contracts.EventUpsert(event, title = "Dentist", who = Who.Everyone)))
        val otherDevice = dev.housecalendar.contracts.DeviceId(UUID.fromString("0bbbbbbb-0000-4000-8000-000000000002"))
        val other = OpFactory(family, otherDevice, log) { dev.housecalendar.contracts.InstantMs(1) }
        other.record(listOf(dev.housecalendar.contracts.EventUpsert(event, place = "Smile Dental")))

        val after = repository.refreshNow() as CalendarSnapshot.Ready
        assertEquals("Smile Dental", after.state.events.getValue(event).place?.value)
        assertEquals(Projection.fold(log.all()), after.state)
    }

    @Test
    fun `pairing codes round-trip and refuse other apps' codes`() {
        val family = FamilyId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
        val key = FamilyKey.generate()
        val code = PairingCode.encode(family, key)
        assertTrue(code.startsWith("hc1:"))
        val decoded = requireNotNull(PairingCode.decode("  $code\n"))
        assertEquals(family, decoded.first)
        assertTrue(decoded.second.bytes().contentEquals(key.bytes()))
        assertEquals(null, PairingCode.decode(code.replace("hc1:", "hp1:")))
        assertEquals(null, PairingCode.decode("hc1:short"))
    }

    @Test
    fun `every colour pairing in DESIGN dot md meets its WCAG contrast`() {
        listOf(LightColors, DarkColors).forEach { colors ->
            val grounds = listOf(colors.ground, colors.surface, colors.sunk, colors.band)
            grounds.forEach { ground ->
                assertAtLeast(4.5, colors.ink.toArgb(), ground.toArgb())
                assertAtLeast(4.5, colors.inkMuted.toArgb(), ground.toArgb())
                assertAtLeast(4.5, colors.action.toArgb(), ground.toArgb())
                assertAtLeast(4.5, colors.today.toArgb(), ground.toArgb())
            }
            assertAtLeast(4.5, colors.onAction.toArgb(), colors.action.toArgb())
            assertAtLeast(4.5, colors.onToday.toArgb(), colors.today.toArgb())
            colors.members.forEach { member ->
                assertAtLeast(4.5, member.onFill.toArgb(), member.fill.toArgb())
                // Text on a person's card is always ink; the tint never decides legibility.
                assertAtLeast(4.5, colors.ink.toArgb(), member.tint.toArgb())
                assertAtLeast(4.5, colors.inkMuted.toArgb(), member.tint.toArgb())
                // Names in the member's colour, on white and on their own tint.
                assertAtLeast(4.5, member.accent.toArgb(), colors.surface.toArgb())
                assertAtLeast(4.5, member.accent.toArgb(), member.tint.toArgb())
            }
            assertEquals(8, colors.members.size)
        }
    }

    @Test
    fun `member tints are the fill blended over the card surface`() {
        listOf(LightColors to 0.13f, DarkColors to 0.16f).forEach { (colors, alpha) ->
            colors.members.forEach { member ->
                val expected = member.fill.copy(alpha = alpha).compositeOver(colors.surface).toArgb()
                assertEquals(Integer.toHexString(expected), Integer.toHexString(member.tint.toArgb()))
            }
        }
    }

    private fun assertAtLeast(minimum: Double, a: Int, b: Int) {
        val ratio = contrast(a, b)
        assertTrue("contrast ${"%.2f".format(ratio)} below $minimum for #${Integer.toHexString(a)} on #${Integer.toHexString(b)}", ratio >= minimum)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
