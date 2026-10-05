package com.storepos.app.update

import com.storepos.app.data.model.AppVersion

// Fail closed if a shared-backend response has missing or foreign product identity.
internal fun latestStorePosVersion(versions: List<AppVersion>): AppVersion? =
    versions.filter { it.appCode == "storepos" && it.isPublished }
        .maxByOrNull { it.versionCode }
