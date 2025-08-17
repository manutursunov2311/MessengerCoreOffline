package tj.messengercoreoffline.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
import tj.messengercoreoffline.common.DispatcherProvider
import tj.messengercoreoffline.ui.ChatViewModel

val uiModule = module {
    single {
        ChatViewModel(
            sendMessageUseCase = get(),
            observeMessagesUseCase = get(),
            networkMonitor = get(),
            scope = CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default)
        )
    }
}