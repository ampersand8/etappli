package com.nuelto.camperexperience.testutil

import android.content.Context
import com.nuelto.camperexperience.data.AuthRepository
import com.nuelto.camperexperience.data.AuthUser
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.domain.PlaceSearch
import com.nuelto.camperexperience.domain.PlaceSuggestion
import com.nuelto.camperexperience.location.PlaceNameResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Replaces Dispatchers.Main for viewModelScope in JVM tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class FakeAuthRepository(
    initialUser: AuthUser? = null,
    private val signInResult: String? = null,
) : AuthRepository {
    val state = MutableStateFlow(initialUser)
    var signInCalls = 0
    var signOutCalls = 0

    /** When set, signInWithGoogle suspends until the deferred completes. */
    var gate: CompletableDeferred<Unit>? = null

    override val authState: Flow<AuthUser?> = state
    override val currentUser: AuthUser? get() = state.value

    override suspend fun signInWithGoogle(activityContext: Context): String? {
        signInCalls++
        gate?.await()
        if (signInResult == null) state.value = AuthUser("test-uid")
        return signInResult
    }

    override fun signOut() {
        signOutCalls++
        state.value = null
    }
}

/** Resolves "Place@<lat>"; each call can be individually gated for late-result tests. */
class FakePlaceNameResolver(context: Context) : PlaceNameResolver(context) {
    val gates = mutableListOf<CompletableDeferred<Unit>>()
    val requests = mutableListOf<LatLng>()
    var gated = false
    var result: (LatLng) -> String? = { "Place@${it.latitude}" }

    override suspend fun placeName(location: LatLng): String? {
        requests += location
        if (gated) {
            val gate = CompletableDeferred<Unit>()
            gates += gate
            gate.await()
        }
        return result(location)
    }
}

/** Records every (query, bias); [result] decides what comes back (null = lookup failed). */
class FakePlaceSearch : PlaceSearch {
    val requests = mutableListOf<Pair<String, LatLng?>>()
    val gates = mutableListOf<CompletableDeferred<Unit>>()
    var gated = false
    var result: List<PlaceSuggestion>? = listOf(
        PlaceSuggestion("Lauterbrunnen", "Bern, Switzerland", LatLng(46.5939043, 7.9078016)),
    )

    override suspend fun search(query: String, near: LatLng?): List<PlaceSuggestion>? {
        requests += query to near
        if (gated) {
            val gate = CompletableDeferred<Unit>()
            gates += gate
            gate.await()
        }
        return result
    }
}
