package com.nuelto.etappli.testutil

import android.content.Context
import com.nuelto.etappli.data.AuthRepository
import com.nuelto.etappli.data.AuthUser
import com.nuelto.etappli.data.SettingsRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.UserSettings
import com.nuelto.etappli.domain.PlaceSearch
import com.nuelto.etappli.domain.PlaceSuggestion
import com.nuelto.etappli.location.PlaceNameResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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

/** Records every (query, bias, preference); [result] decides what comes back. */
class FakePlaceSearch : PlaceSearch {
    val requests = mutableListOf<Pair<String, LatLng?>>()
    val preferences = mutableListOf<StopKind?>()
    val gates = mutableListOf<CompletableDeferred<Unit>>()
    var gated = false
    var result: List<PlaceSuggestion>? = listOf(
        PlaceSuggestion("Lauterbrunnen", "Bern, Switzerland", LatLng(46.5939043, 7.9078016)),
    )

    /** Whatever [resolve] should hand back; by default the hit itself. */
    var resolved: (PlaceSuggestion) -> PlaceSuggestion? = { it }

    override suspend fun resolve(suggestion: PlaceSuggestion): PlaceSuggestion? = resolved(suggestion)

    val finds = mutableListOf<Pair<String, LatLng?>>()

    /** What a submitted search locates; by default nothing is out there. */
    var found: List<PlaceSuggestion>? = emptyList()

    override suspend fun find(query: String, near: LatLng?): List<PlaceSuggestion>? {
        finds += query to near
        if (gated) {
            val gate = CompletableDeferred<Unit>()
            gates += gate
            gate.await()
        }
        return found
    }

    override suspend fun search(
        query: String,
        near: LatLng?,
        prefer: StopKind?,
    ): List<PlaceSuggestion>? {
        requests += query to near
        preferences += prefer
        if (gated) {
            val gate = CompletableDeferred<Unit>()
            gates += gate
            gate.await()
        }
        return result
    }
}

/** Follows a shared short link on demand; [gate] holds the answer for mid-flight assertions. */
class FakeShareLinkResolver(var result: String? = null) {
    val requests = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null

    suspend fun expand(url: String): String? {
        requests += url
        gate?.await()
        return result
    }
}

/** Settings that arrive only once [gate] completes — for what a second tap does mid-write. */
class GatedSettingsRepository : SettingsRepository {
    val gate = CompletableDeferred<Unit>()
    override fun settings(): Flow<UserSettings> = flow {
        gate.await()
        emit(UserSettings())
    }
    override suspend fun update(settings: UserSettings) = Unit
}
