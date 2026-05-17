package com.nyasa.notifybridge.data.di

import com.nyasa.notifybridge.data.mqtt.HiveMqClientManager
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module @InstallIn(SingletonComponent::class)
abstract class MqttModule {
    @Binds abstract fun mqtt(i: HiveMqClientManager): MqttClientManager
}
