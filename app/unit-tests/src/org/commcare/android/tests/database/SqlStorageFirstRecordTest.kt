package org.commcare.android.tests.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.util.TestUtils
import org.commcare.models.database.SqlStorage
import org.javarosa.core.storage.Shoe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class SqlStorageFirstRecordTest {
    private lateinit var storage: SqlStorage<Shoe>

    @Before
    fun setUp() {
        TestUtils.initializeStaticTestStorage()
        storage = TestUtils.getStorage("ShoeStorage", Shoe::class.java)
    }

    @Test
    fun `returns null when the table is empty`() {
        assertNull(storage.getFirstRecord())
    }

    @Test
    fun `returns the record with the lowest id`() {
        val first = writeShoe("nike")
        writeShoe("adidas")
        writeShoe("puma")

        val record = storage.getFirstRecord()!!
        assertEquals(first.id, record.id)
        assertEquals("nike", record.getMetaData(Shoe.META_BRAND))
    }

    @Test
    fun `moves on to the next record once the first is removed`() {
        val first = writeShoe("nike")
        val second = writeShoe("adidas")

        storage.remove(first.id)

        assertEquals(second.id, storage.getFirstRecord()!!.id)
    }

    private fun writeShoe(brand: String): Shoe = Shoe(brand, "running", "10").also { storage.write(it) }
}
