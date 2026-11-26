package org.commcare.android.database.connect.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.robolectric.util.ReflectionHelpers
import java.util.Date

class ConnectUserRecordMigrationTest {
    @Test
    fun `fromV30 keeps the existing user and leaves the demographic fields unset`() {
        val passwordDate = Date(1_700_000_000_000)
        val oldRecord =
            ConnectUserRecordV30().apply {
                ReflectionHelpers.setField(this, "userId", "user-1")
                ReflectionHelpers.setField(this, "password", "secret")
                ReflectionHelpers.setField(this, "name", "Ada Lovelace")
                ReflectionHelpers.setField(this, "primaryPhone", "+11234567890")
                ReflectionHelpers.setField(this, "lastPasswordDate", passwordDate)
                ReflectionHelpers.setField(this, "pin", "1234")
                ReflectionHelpers.setField(this, "photo", "base64photo")
                ReflectionHelpers.setField(this, "email", "ada@example.com")
            }

        val record = ConnectUserRecord.fromV30(oldRecord)

        assertEquals("user-1", record.userId)
        assertEquals("secret", record.password)
        assertEquals("Ada Lovelace", record.name)
        assertEquals("+11234567890", record.primaryPhone)
        assertEquals(passwordDate, ReflectionHelpers.getField<Date>(record, "lastPasswordDate"))
        assertEquals("1234", record.pin)
        assertEquals("base64photo", record.photo)
        assertEquals("ada@example.com", record.email)
        assertNull(record.gender)
        assertNull(record.dateOfBirth)
        assertNull(record.languages)
        assertNull(record.education)
        assertNull(record.chwExperience)
        assertNull(record.techProficiency)
    }
}
