package com.dangerfield.drop2048.libraries.config.impl.data

import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.core.Catching

interface RemoteConfigDataSource {
    suspend fun getConfig(): Catching<AppConfigMap>
}
