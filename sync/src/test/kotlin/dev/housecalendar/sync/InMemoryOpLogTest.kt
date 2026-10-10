package dev.housecalendar.sync

class InMemoryOpLogTest : OpLogContract() {
    override fun newLog(): OpLog = InMemoryOpLog()
}
