package chat.sphinx.activitymain.di

import android.content.Context
import android.widget.ImageView
import chat.sphinx.concept_connectivity_helper.ConnectivityHelper
import chat.sphinx.concept_grapheneos_manager.GrapheneOsManager
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_signer_manager.SignerManager
import chat.sphinx.concept_user_colors_helper.UserColorsHelper
import chat.sphinx.connectivity_helper.ConnectivityHelperImpl
import chat.sphinx.grapheneos_manager.GrapheneOsManagerImpl
import chat.sphinx.signer_manager.SignerManagerImpl
import chat.sphinx.user_colors_helper.UserColorsHelperImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import javax.inject.Singleton

@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {

    @Provides
    @ActivityScoped
    fun provideUserColorsImpl(
        @ApplicationContext appContext: Context,
        dispatchers: CoroutineDispatchers
    ): UserColorsHelperImpl =
        UserColorsHelperImpl(appContext, dispatchers)

    @Provides
    fun provideUserColors(
        userColorsHelperImpl: UserColorsHelperImpl
    ): UserColorsHelper =
        userColorsHelperImpl

    @Provides
    @ActivityScoped
    fun provideConnectivityHelperImpl(
        @ApplicationContext appContext: Context,
    ): ConnectivityHelperImpl =
        ConnectivityHelperImpl(appContext)

    @Provides
    fun provideConnectivityHelper(
        connectivityHelperImpl: ConnectivityHelperImpl
    ): ConnectivityHelper =
        connectivityHelperImpl

    @Provides
    @ActivityScoped
    fun provideSignerManagerImpl(
        @ApplicationContext appContext: Context,
        dispatchers: CoroutineDispatchers,
    ): SignerManagerImpl =
        SignerManagerImpl(appContext, dispatchers)

    @Provides
    fun provideSignerManager(
        signerManagerImpl: SignerManagerImpl
    ): SignerManager =
        signerManagerImpl

    @Provides
    @ActivityScoped
    fun provideGrapheneOsManagerImpl(
        @ApplicationContext appContext: Context,
        dispatchers: CoroutineDispatchers,
        imageLoader: ImageLoader<ImageView>
    ): GrapheneOsManagerImpl =
        GrapheneOsManagerImpl(appContext, imageLoader, dispatchers)

    @Provides
    fun provideGrapheneOsManager(
        grapheneOsManagerImpl: GrapheneOsManagerImpl
    ): GrapheneOsManager =
        grapheneOsManagerImpl

}
