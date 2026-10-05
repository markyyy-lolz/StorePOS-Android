package com.storepos.app.update

import com.storepos.app.data.model.AppVersion
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorePosVersionsTest {
    @Test fun sharedBackendDoesNotOfferMotoPosWithHigherVersionCode() {
        val versions = Json.decodeFromString<List<AppVersion>>("""[
            {"app_code":"motopos","is_published":true,"version_code":25,"version_name":"2.2.3"},
            {"app_code":"storepos","is_published":true,"version_code":1,"version_name":"1.0.0"},
            {"app_code":"storepos","is_published":true,"version_code":2,"version_name":"1.0.1"},
            {"app_code":"storepos","is_published":false,"version_code":3,"version_name":"1.0.2"}
        ]""")
        assertEquals("1.0.1", latestStorePosVersion(versions)?.versionName)
    }

    @Test fun missingIdentityOrPublicationCannotBecomeAnUpdate() {
        val versions = Json.decodeFromString<List<AppVersion>>("""[
            {"version_code":99,"version_name":"unknown","is_published":true},
            {"app_code":"storepos","version_code":100,"version_name":"draft"}
        ]""")
        assertNull(latestStorePosVersion(versions))
    }

    @Test fun noStorePosReleaseReturnsNoUpdate() {
        assertNull(latestStorePosVersion(emptyList()))
        assertNull(latestStorePosVersion(listOf(AppVersion(
            appCode = "motopos", isPublished = true, versionCode = 25, versionName = "2.2.3"
        ))))
    }
}
