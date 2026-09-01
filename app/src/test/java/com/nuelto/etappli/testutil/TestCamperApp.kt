package com.nuelto.etappli.testutil

import com.nuelto.etappli.AppContainer
import com.nuelto.etappli.CamperApp

/**
 * Test application: deterministic in-memory container regardless of whether
 * google-services.json is present in the local build. Tests that need custom
 * repositories assign `container` before composing anything.
 */
class TestCamperApp : CamperApp() {
    public override fun createContainer(): AppContainer = AppContainer.inMemory()
}
