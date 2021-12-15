package com.trendyol.android.devtools.core.io

import android.content.Context

object FileReader {

    fun readAssetFile(context: Context, name: String): String? {
        return runCatching {
            context.assets.open(name)
                .bufferedReader()
                .use { it.readText() }
        }.getOrNull()
    }
}
