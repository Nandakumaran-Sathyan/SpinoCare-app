package com.example.modicanalyzer.data.repository

import com.example.modicanalyzer.data.local.dao.UserDao
import com.example.modicanalyzer.data.local.entity.UserEntity
import com.example.modicanalyzer.data.model.SyncStatus
import com.example.modicanalyzer.data.remote.FirestoreDataSource
import com.example.modicanalyzer.util.EncryptionUtil
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.UUID

class AuthRepositoryTest {

    private lateinit var userDao: UserDao
    private lateinit var firestoreDataSource: FirestoreDataSource
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var authRepository: AuthRepository

    @Before
    fun setup() {
        userDao = mock()
        firestoreDataSource = mock()
        firebaseAuth = mock()

        authRepository = AuthRepository(
            userDao = userDao,
            firestoreDataSource = firestoreDataSource,
            firebaseAuth = firebaseAuth
        )
    }

    @After
    fun tearDown() {
        // nothing for now
    }

    @Test
    fun `signUp offline stores pending user and emits success`() = runTest {
        val email = "offline@example.com"
        val password = "Password123!"

        // userDao.getUserByEmail returns null (no existing user)
        Mockito.`when`(userDao.getUserByEmail(email)).thenReturn(null)

        val emissions = authRepository.signUp(
            email = email,
            password = password,
            displayName = "Offline User",
            isOnline = false
        ).toList()

        // Expect two emissions: Loading, Success
        // The last emission should be Success with isFirebaseAuth=false
        val last = emissions.last()
        assertEquals(true, last is com.example.modicanalyzer.data.model.AuthState.Success)
        val success = last as com.example.modicanalyzer.data.model.AuthState.Success
        assertEquals(email, success.email)
        assertEquals(false, success.isFirebaseAuth)

        // Capture the inserted user and assert fields
        val captor = argumentCaptor<UserEntity>()
        verify(userDao, times(1)).insertUser(captor.capture())
        val inserted = captor.firstValue
        assertEquals(email, inserted.email)
        // Check sync status is PENDING
        assertEquals(SyncStatus.PENDING, inserted.syncStatus)
        // Encrypted password should be present for offline users
        assert(inserted.encryptedPassword != null && inserted.encryptedPassword!!.isNotBlank())

        // Verify stored password hash matches SHA-256 of original password
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val expectedHash = md.digest(password.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(expectedHash, inserted.passwordHash)
    }

    @Test
    fun `syncOfflineUsers migrates offline user to firebase and returns uid`() = runTest {
        // Prepare an offline user with encrypted password
        val tempUserId = UUID.randomUUID().toString()
        val email = "migrate@example.com"
        val plainPassword = "SecretPass!"
        val encrypted = EncryptionUtil.encryptPassword(plainPassword)

        val offlineUser = UserEntity(
            userId = tempUserId,
            email = email,
            passwordHash = "irrelevant",
            encryptedPassword = encrypted,
            displayName = "Migrating User",
            isFirebaseAuth = false,
            syncStatus = SyncStatus.PENDING
        )

        // Mock DAO to return this unsynced user
        Mockito.`when`(userDao.getUnsyncedUsers()).thenReturn(listOf(offlineUser))

        // Prepare Firebase mocks: AuthResult and FirebaseUser
        val firebaseUser = mock<FirebaseUser> {
            on { uid } doReturn "firebase-uid-123"
        }

        val authResult = mock<AuthResult> {
            on { user } doReturn firebaseUser
        }

        // When firebaseAuth.createUserWithEmailAndPassword called, return a completed Task<AuthResult>
        Mockito.`when`(firebaseAuth.createUserWithEmailAndPassword(anyString(), anyString()))
            .thenReturn(Tasks.forResult(authResult))

        // Firestore sync returns success
        Mockito.`when`(firestoreDataSource.syncUserProfile(anyString(), anyString(), Mockito.anyOrNull()))
            .thenReturn(Result.success(Unit))

        // Stub DAO update & insert & delete to do nothing
        Mockito.`when`(userDao.updateSyncStatus(Mockito.anyString(), Mockito.any(), Mockito.anyOrNull())).thenReturn(Unit)
        Mockito.`when`(userDao.insertUser(Mockito.any())).thenReturn(Unit)
        Mockito.`when`(userDao.deleteUser(Mockito.anyString())).thenReturn(Unit)

        val result = authRepository.syncOfflineUsers()

        // Expect the new firebase uid returned
        assertEquals(1, result.size)
        assertEquals("firebase-uid-123", result[0])

        // Verify that Firebase create method was invoked
        verify(firebaseAuth, times(1)).createUserWithEmailAndPassword(anyString(), anyString())

        // Verify that Firestore profile sync was called with new uid
        verify(firestoreDataSource, times(1)).syncUserProfile("firebase-uid-123", email, "Migrating User")
    }

    @Test
    fun `syncOfflineUsers marks user failed when firebase creation errors`() = runTest {
        val tempUserId = UUID.randomUUID().toString()
        val email = "fail@example.com"
        val plainPassword = "SecretFail!"
        val encrypted = EncryptionUtil.encryptPassword(plainPassword)

        val offlineUser = UserEntity(
            userId = tempUserId,
            email = email,
            passwordHash = "irrelevant",
            encryptedPassword = encrypted,
            displayName = "Failing User",
            isFirebaseAuth = false,
            syncStatus = SyncStatus.PENDING
        )

        Mockito.`when`(userDao.getUnsyncedUsers()).thenReturn(listOf(offlineUser))

        // Make firebase return an exception (email collision for example)
        val exc = Exception("The email address is already in use by another account.")
        Mockito.`when`(firebaseAuth.createUserWithEmailAndPassword(anyString(), anyString()))
            .thenReturn(Tasks.forException(exc))

        // Run sync
        val result = authRepository.syncOfflineUsers()

        // No synced users expected
        assertEquals(0, result.size)

        // Verify we updated sync status to FAILED for this user
        verify(userDao, times(1)).updateSyncStatus(tempUserId, SyncStatus.FAILED, null)
    }
}
