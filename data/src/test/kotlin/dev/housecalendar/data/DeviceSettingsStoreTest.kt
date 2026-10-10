package dev.housecalendar.data

import dev.housecalendar.contracts.DeviceRole
import dev.housecalendar.contracts.InstantMs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalTime

/** SAD §3.5: settings that describe this device only, never synced, never backed up. */
class DeviceSettingsStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `a new device starts from the spec's defaults`() = runTest {
        val settings = DeviceSettingsStore(folder.root).current()
        assertEquals(ReminderChoice.MINE, settings.reminders)
        assertEquals(LocalTime.of(6, 30), settings.dayStarts)
        assertEquals(LocalTime.of(22, 0), settings.dayEnds)
        assertEquals(DisplayHome.WEEK, settings.displayHome)
        assertEquals(100, settings.sizePercent)
        assertEquals(null, settings.lastReminderFired)
        assertEquals(false, settings.pinningOffered)
    }

    @Test
    fun `updates persist across instances`() = runTest {
        val store = DeviceSettingsStore(folder.root)
        store.update { it.copy(reminders = ReminderChoice.EVERYONE, dayStarts = LocalTime.of(7, 0), lastReminderFired = InstantMs(42)) }
        val reopened = DeviceSettingsStore(folder.root).current()
        assertEquals(ReminderChoice.EVERYONE, reopened.reminders)
        assertEquals(LocalTime.of(7, 0), reopened.dayStarts)
        assertEquals(InstantMs(42), reopened.lastReminderFired)
    }

    @Test
    fun `the flow emits the latest settings`() = runTest {
        val store = DeviceSettingsStore(folder.root)
        store.update { it.copy(displayHome = DisplayHome.TODAY) }
        assertEquals(DisplayHome.TODAY, store.settings().first().displayHome)
    }

    @Test
    fun `an unreadable file falls back to the defaults and is replaced on the next update`() = runTest {
        File(folder.root, DeviceSettingsStore.FILE_NAME).writeText("{not json")
        val store = DeviceSettingsStore(folder.root)
        assertEquals(DeviceSettings(), store.current())
        store.update { it.copy(sizePercent = 120) }
        assertEquals(120, DeviceSettingsStore(folder.root).current().sizePercent)
    }

    @Test
    fun `a joining device keeps its chosen name and role until its first sync`() = runTest {
        val store = DeviceSettingsStore(folder.root)
        assertEquals(null, store.current().pendingJoin)
        store.update { it.copy(pendingJoin = PendingJoin("Kitchen", DeviceRole.Display)) }
        assertEquals(PendingJoin("Kitchen", DeviceRole.Display), DeviceSettingsStore(folder.root).current().pendingJoin)
        store.update { it.copy(pendingJoin = null) }
        assertEquals(null, DeviceSettingsStore(folder.root).current().pendingJoin)
    }

    @Test
    fun `fields written by a newer version are ignored`() = runTest {
        File(folder.root, DeviceSettingsStore.FILE_NAME).writeText("""{"sizePercent":90,"futureThing":true}""")
        assertEquals(90, DeviceSettingsStore(folder.root).current().sizePercent)
    }
}
