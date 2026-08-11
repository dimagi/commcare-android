package org.commcare.connect.database

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.network.LoginInvalidatedException
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Covers Connect storage being reached around sign-out, which an in-flight request can still do
 * because the sign-in check and the storage access aren't atomic.
 *
 * The invariant under test is that losing that race stays survivable: it must never flag the DB as
 * broken or raise [LoginInvalidatedException], because reaching the uncaught handler with that wipes
 * the account and restarts the process.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectDatabaseHelperTest {
    private val context: Context = CommCareTestApplication.instance()

    @After
    fun tearDown() {
        ConnectUserDatabaseUtil.forgetUser()
    }

    private fun readUserStorage() =
        ConnectDatabaseHelper
            .getConnectStorage(context, ConnectUserRecord::class.java)
            .getRecordsForValues(emptyArray(), emptyArray())

    @Test
    fun testForgetUserLeavesNoPassphraseAndNoBrokenFlag() {
        ConnectUserDatabaseUtil.forgetUser()

        //  the passphrase going away is what tells the storage layer there's no account to open a
        //  DB for, and it has to land together with the deletion rather than partway through it
        assertNull(ConnectDatabaseUtils.getKeyRecord())
        assertFalse(ConnectDatabaseHelper.dbExists())
        assertFalse(ConnectDatabaseHelper.isDbBroken())
    }

    @Test
    fun testGetUserAfterForgetUserReturnsNullWithoutFlaggingDb() {
        ConnectUserDatabaseUtil.forgetUser()

        //  the ordinary "do I have an account" check treats an absent DB as no account, so it stays
        //  cheap and side-effect free
        assertNull(ConnectUserDatabaseUtil.getUser(context))
        assertFalse(ConnectDatabaseHelper.isDbBroken())
    }

    @Test
    fun testStorageReadRacingSignOutNeverInvalidatesLogin() {
        val readerReady = CountDownLatch(1)
        val signOutDone = CountDownLatch(1)
        val fatal = AtomicReference<Throwable?>(null)

        val reader =
            Thread {
                readerReady.countDown()
                //  keep hammering storage across the sign-out so at least one read lands on either
                //  side of it, and ideally one lands mid-teardown
                repeat(200) {
                    try {
                        readUserStorage()
                    } catch (expected: ConnectDatabaseUnavailableException) {
                        //  the read can't succeed once the account is gone, it just has to fail
                        //  survivably
                    } catch (t: Throwable) {
                        if (t is LoginInvalidatedException) {
                            fatal.compareAndSet(null, t)
                        }
                    }
                }
                signOutDone.countDown()
            }

        reader.start()
        assertTrue(readerReady.await(5, TimeUnit.SECONDS))
        ConnectUserDatabaseUtil.forgetUser()
        reader.join(TimeUnit.SECONDS.toMillis(30))

        assertNull("A read racing sign-out invalidated the login", fatal.get())
        assertFalse("A read racing sign-out flagged the Connect DB as broken", ConnectDatabaseHelper.isDbBroken())
    }
}
