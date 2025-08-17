package tj.messengercoreoffline.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
import tj.messengercoreoffline.chat.datasource.InMemoryLocalDataSource
import tj.messengercoreoffline.chat.datasource.LocalDataSource
import tj.messengercoreoffline.chat.repository.ChatRepository
import tj.messengercoreoffline.chat.repository.ChatRepositoryImpl
import tj.messengercoreoffline.chat.transport.FakeTransport
import tj.messengercoreoffline.chat.transport.Transport
import tj.messengercoreoffline.chat.usecase.ObserveMessagesUseCase
import tj.messengercoreoffline.chat.usecase.SendMessageUseCase
import tj.messengercoreoffline.common.DefaultDispatcherProvider
import tj.messengercoreoffline.common.DispatcherProvider
import tj.messengercoreoffline.common.SystemTimeProvider
import tj.messengercoreoffline.common.TimeProvider
import tj.messengercoreoffline.connectivity.DefaultNetworkMonitor
import tj.messengercoreoffline.connectivity.NetworkMonitor

val appModule = module {

    //Core
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<TimeProvider> { SystemTimeProvider() }
    single<NetworkMonitor> { DefaultNetworkMonitor() }

    //Transport and Data
    single<Transport> { FakeTransport(get()) }
    single<LocalDataSource> { InMemoryLocalDataSource() }

    //Repository
    single<ChatRepository> {
        ChatRepositoryImpl(
            localDataSource = get(),
            transport = get(),
            networkMonitor = get(),
            timeProvider = get(),
            dispatchers = get(),
            scope = CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default)
        )
    }

    //Use Cases
    single { SendMessageUseCase(get()) }
    single { ObserveMessagesUseCase(get()) }
}