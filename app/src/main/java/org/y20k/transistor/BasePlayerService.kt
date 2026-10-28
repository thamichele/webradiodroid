/*
 * BasePlayerService.kt
 * Implements the BasePlayerService abstract class
 * BasePlayerService is Transistor's foreground service that plays radio station audio.
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor

import android.content.pm.PackageManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import org.y20k.transistor.core.Collection
import org.y20k.transistor.helpers.AudioHelper
import org.y20k.transistor.helpers.CollectionHelper
import org.y20k.transistor.helpers.FileHelper
import org.y20k.transistor.helpers.PreferencesHelper
import java.util.Date


/*
 * BasePlayerService class
 */
@UnstableApi
abstract class BasePlayerService: MediaLibraryService() {


    /* Define log tag */
    private val TAG: String = BasePlayerService::class.java.simpleName


    /* Main class variables */
    abstract val player: Player
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var sleepTimer: CountDownTimer
    private var sleepTimerTimeRemaining: Long = 0L
    private var sleepTimerRunning: Boolean = false
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val librarySessionCallback = CustomMediaLibrarySessionCallback()
    private var collection: Collection = Collection()
    private lateinit var metadataHistory: MutableList<String>
    private var playbackRestartCounter: Int = 0
    private var playLastStation: Boolean = false
    var bufferSizeMultiplier: Int = PreferencesHelper.loadBufferSizeMultiplier()


    /* Overrides onCreate from Service */
    override fun onCreate() {
        super.onCreate()
        // fetch the metadata history
        metadataHistory = PreferencesHelper.loadMetadataHistory()
        // load collection
        collection = FileHelper.readCollection(this)
        // create and register collection changed receiver
        LocalBroadcastManager.getInstance(application).registerReceiver(collectionChangedReceiver, IntentFilter(Keys.ACTION_COLLECTION_CHANGED))
        // initialize player
        initializePlayer()
        // initialize media session
        initializeSession()
        // initialize media items
        initializeMediaItems()
        // set up notification provider
        val notificationProvider: DefaultMediaNotificationProvider = CustomNotificationProvider()
        notificationProvider.setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp)
        setMediaNotificationProvider(notificationProvider)
    }


    /* Overrides onDestroy from Service */
    override fun onDestroy() {
        super.onDestroy()
        releaseSession()
    }


//    /* Overrides onTaskRemoved from Service */
//    override fun onTaskRemoved(rootIntent: Intent?) {
//        releaseSession()
//        pauseAllPlayersAndStopSelf()
//    }


    /* Overrides onGetSession from MediaSessionService */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        // todo implement a package validator (see: https://github.com/android/uamp/blob/ef5076bf4279adfccafa746c92da6ec86607f284/common/src/main/java/com/example/android/uamp/media/MusicService.kt#L217)
        return mediaLibrarySession
    }
    
    
    /* Initialize the player - override in implementation */
    abstract fun initializePlayer()


    /* Initializes the MediaSession */
    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntent(intent)
            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, librarySessionCallback).apply {
            setSessionActivity(pendingIntent)
        }.build()
    }


    /* Releases the MediaSession */
    private fun releaseSession() {
        mediaLibrarySession.run {
            release()
            if (player.playbackState != Player.STATE_IDLE) {
                player.removeListener(playerListener)
                player.release()
            }
        }
    }


    /* Adds stations as media items to the player */
    private fun initializeMediaItems() {
        val stations: MutableList<MediaItem> = CollectionHelper.getStationsAsMediaItems(this, collection)
        // store current item
        val currentItem: MediaItem? = player.currentMediaItem
        // set the new media items
        player.setMediaItems(stations)
        // get the correct index ( = position in station list)
        val index: Int
        if (currentItem != null) {
            index = stations.indexOf(currentItem)
        } else {
            index = PreferencesHelper.loadLastPlayedStationPosition()
        }
        // seek to correct position
        if (index != -1 && index < stations.size) {
            player.seekToDefaultPosition(index)
        } else {
            player.seekToDefaultPosition()
        }
    }


//    /* Creates a LoadControl - increase buffer size by given factor */
//    private fun createDefaultLoadControl(factor: Int): DefaultLoadControl {
//        val builder = DefaultLoadControl.Builder()
//        builder.setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
//        builder.setBufferDurationsMs(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * factor, DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * factor, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS * factor, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS * factor)
//        return builder.build()
//    }



    /* Starts sleep timer / adds default duration to running sleeptimer */
    private fun startSleepTimer(continueWithPreviousTimerDuration: Boolean) {
        // stop running timer
        if (sleepTimerTimeRemaining > 0L && this::sleepTimer.isInitialized) {
            handler.removeCallbacksAndMessages(null)
            sleepTimer.cancel()
        }
        // initialize timer
        val duration: Long
        if (continueWithPreviousTimerDuration) {
            duration = sleepTimerTimeRemaining
        } else {
            duration = Keys.SLEEP_TIMER_DURATION + sleepTimerTimeRemaining
        }
        sleepTimer = object: CountDownTimer(duration, Keys.SLEEP_TIMER_INTERVAL) {
            override fun onFinish() {
                Log.v(TAG, "Sleep timer finished. Sweet dreams.")
                sleepTimerTimeRemaining = 0L
                sleepTimerRunning = false
                player.pause() // todo may use player.stop() here
            }
            override fun onTick(millisUntilFinished: Long) {
                sleepTimerRunning = true
                sleepTimerTimeRemaining = millisUntilFinished
            }
        }
        // start timer
        sleepTimer.start()
        // store timer state
        PreferencesHelper.saveSleepTimerRunning(isRunning = true)
    }


    /* Cancels sleep timer */
    private fun cancelSleepTimer(delayedReset: Boolean) {
        if (this::sleepTimer.isInitialized && sleepTimerTimeRemaining > 0L) {
            if (delayedReset) {
                handler.postDelayed({
                    sleepTimerTimeRemaining = 0L
                    sleepTimerRunning = false
                }, 2500L)
            } else {
                sleepTimerTimeRemaining = 0L
                sleepTimerRunning = false
            }
            sleepTimer.cancel()
        }
        // store timer state
        PreferencesHelper.saveSleepTimerRunning(isRunning = false)
    }


    /* Updates metadata */
    private fun updateMetadata(metadata: String = String()) {
        // get metadata string
        val metadataStringEncoded: String
        if (metadata.isNotEmpty()) {
            metadataStringEncoded = metadata
        } else {
            metadataStringEncoded = player.currentMediaItem?.mediaMetadata?.albumTitle.toString()
        }
        // remove HTML encoding
        val metadataString: String = Html.fromHtml(metadataStringEncoded, Html.FROM_HTML_MODE_LEGACY).toString()
        // remove duplicates
        if (metadataHistory.contains(metadataString)) {
            metadataHistory.removeIf { it == metadataString }
        }
        // append metadata to metadata history
        metadataHistory.add(metadataString)
        // trim metadata list
        if (metadataHistory.size > Keys.DEFAULT_SIZE_OF_METADATA_HISTORY) {
            metadataHistory.removeAt(0)
        }
        // save history
        PreferencesHelper.saveMetadataHistory(metadataHistory)
    }


    /* Try to restart Playback */
    private fun tryToRestartPlayback() {
        // restart playback for up to five times
        if (playbackRestartCounter < 5) {
            playbackRestartCounter++
            player.play()
        } else {
            player.stop()
            Toast.makeText(this, this.getString(R.string.toast_message_error_restart_playback_failed), Toast.LENGTH_LONG).show()
        }
    }


    /* Reads collection of stations from storage using GSON */
    private fun loadCollection(context: Context) {
        Log.v(TAG, "Loading collection of stations from storage")
        CoroutineScope(Main).launch {
            // load collection
            collection = FileHelper.readCollection(context)
            // re-initialize player
            initializeMediaItems()
//            // special case: trigger metadata view update for stations that have no metadata
//            if (player.isPlaying && station.name == getCurrentMetadata()) {
//                station = CollectionHelper.getStation(collection, station.uuid)
//                updateMetadata(null)
//            }
        }
    }


    /*
     * Custom MediaLibrarySession Callback that handles player commands
     */
    private inner class CustomMediaLibrarySessionCallback: MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(CollectionHelper.getRootItem(), params))
        }

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children: List<MediaItem> = CollectionHelper.getChildren(this@BasePlayerService, collection)
            return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
        }

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<List<MediaItem>> {
            val updatedMediaItems: List<MediaItem> = mediaItems.map { mediaItem ->
                CollectionHelper.getStationItem(this@BasePlayerService, collection, mediaItem.mediaId)
            }
            return Futures.immediateFuture(updatedMediaItems)
        }

        override fun onSetMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // replace the requested station with the whole collection, so that skip to next/previous can move between stations
            val requestedMediaItem: MediaItem? = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull()
            val stationPosition: Int = CollectionHelper.getStationPosition(collection, requestedMediaItem?.mediaId ?: String())
            if (stationPosition == -1) {
                // station is not part of the collection - keep the request as it is
                return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            }
            val stations: MutableList<MediaItem> = CollectionHelper.getStationsAsMediaItems(this@BasePlayerService, collection)
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(stations, stationPosition, startPositionMs))
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
            val item: MediaItem = CollectionHelper.getStationItem(this@BasePlayerService, collection, mediaId)
            return Futures.immediateFuture(LibraryResult.ofItem(item, /* params= */ null))
        }

        override fun onConnectAsync(session: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.ConnectionResult> {
            // add custom commands
            val mediaNotificationSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().also { builder ->
                builder.add(SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY))
                builder.add(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                builder.add(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_RUNNING, Bundle.EMPTY))
                builder.add(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY))
                builder.add(SessionCommand(Keys.CMD_REQUEST_METADATA_HISTORY, Bundle.EMPTY))
                builder.add(SessionCommand(Keys.CMD_REQUEST_PLATFORM_TOKEN, Bundle.EMPTY))
            }.build()
            // WebRadioDroid Libero (Android Auto free player) is signed with the same key and gets full access
            val isCompanionApp: Boolean = controller.packageName != packageName &&
                    packageManager.checkSignatures(packageName, controller.packageName) == PackageManager.SIGNATURE_MATCH
            // let untrusted controllers (third-party widgets, launchers) play, pause and switch stations
            val playerCommands: Player.Commands = if (controller.isTrusted || isCompanionApp) {
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            } else {
                MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_PLAYER_COMMANDS.buildUpon().addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SET_MEDIA_ITEM
                ).build()
            }
            // add commands
            return Futures.immediateFuture(MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).apply {
                setAvailableSessionCommands(mediaNotificationSessionCommands)
                setAvailablePlayerCommands(playerCommands)
            }.build())
        }

        override fun onSubscribe(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            val children: List<MediaItem> = CollectionHelper.getChildren(this@BasePlayerService, collection)
            session.notifyChildrenChanged(browser, parentId, children.size, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            CoroutineScope(Main).launch {
                // resume with the whole collection, so that skip to next/previous can move between stations
                val stations: MutableList<MediaItem> = CollectionHelper.getStationsAsMediaItems(this@BasePlayerService, collection)
                val stationPosition: Int = PreferencesHelper.loadLastPlayedStationPosition()
                val result: MediaSession.MediaItemsWithStartPosition = if (stationPosition in stations.indices) {
                    MediaSession.MediaItemsWithStartPosition(stations, stationPosition, C.TIME_UNSET)
                } else {
                    MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET)
                }
                future.set(result)
            }
            return future
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                Keys.CMD_START_SLEEP_TIMER -> {
                    startSleepTimer(continueWithPreviousTimerDuration = false)
                }
                Keys.CMD_CANCEL_SLEEP_TIMER -> {
                    cancelSleepTimer(delayedReset = false)
                }
                Keys.CMD_REQUEST_SLEEP_TIMER_RUNNING -> {
                    val resultBundle = Bundle()
                    resultBundle.putBoolean(Keys.EXTRA_SLEEP_TIMER_RUNNING, sleepTimerRunning)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                }
                Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING -> {
                    val resultBundle = Bundle()
                    resultBundle.putLong(Keys.EXTRA_SLEEP_TIMER_REMAINING, sleepTimerTimeRemaining)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                }
                Keys.CMD_REQUEST_METADATA_HISTORY -> {
                    val resultBundle = Bundle()
                    resultBundle.putStringArrayList(Keys.EXTRA_METADATA_HISTORY, ArrayList(metadataHistory))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                }
                Keys.CMD_REQUEST_PLATFORM_TOKEN -> {
                    // hand out the platform session token only to Transistor itself (used by the Android Auto CarAppService)
                    if (controller.packageName != packageName) {
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                    }
                    val resultBundle = Bundle()
                    resultBundle.putParcelable(Keys.EXTRA_PLATFORM_TOKEN, session.platformToken)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
            // playerCommand = one of COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_SEEK_TO_DEFAULT_POSITION, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD, COMMAND_SET_SPEED_AND_PITCH, COMMAND_SET_SHUFFLE_MODE, COMMAND_SET_REPEAT_MODE, COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_MEDIA_ITEMS_METADATA, COMMAND_SET_MEDIA_ITEMS_METADATA, COMMAND_CHANGE_MEDIA_ITEMS, COMMAND_GET_AUDIO_ATTRIBUTES, COMMAND_GET_VOLUME, COMMAND_GET_DEVICE_VOLUME, COMMAND_SET_VOLUME, COMMAND_SET_DEVICE_VOLUME, COMMAND_ADJUST_DEVICE_VOLUME, COMMAND_SET_VIDEO_SURFACE, COMMAND_GET_TEXT, COMMAND_SET_TRACK_SELECTION_PARAMETERS or COMMAND_GET_TRACK_INFOS. */
            // emulate headphone buttons
            // start/pause: adb shell input keyevent 85
            // next: adb shell input keyevent 87
            // prev: adb shell input keyevent 88
            when (playerCommand) {
                Player.COMMAND_PREPARE -> {
                    // todo check: playLastStation is never "true" ... Player.COMMAND_PREPARE -> can be probably deleted
                    val recent: MediaItem? = CollectionHelper.getRecent(this@BasePlayerService, collection)
                    if (playLastStation && recent != null) {
                        // special case: system requested media resumption (see also onGetLibraryRoot)
                        player.addMediaItem(recent)
                        player.prepare()
                        playLastStation = false
                        return SessionResult.RESULT_SUCCESS
                    } else {
                        return super.onPlayerCommandRequest(session, controller, playerCommand)
                    }
                }
                Player.COMMAND_PLAY_PAUSE -> {
                    if (player.isPlaying) {
                        return super.onPlayerCommandRequest(session, controller, playerCommand)
                    } else {
                        // seek to the start of the "live window"
                        player.seekTo(0)
                        return SessionResult.RESULT_SUCCESS
                    }
                }
                else -> {
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            }
        }

    }
    /*
     * End of inner class
     */


    /*
     * NotificationProvider to customize Notification actions
     */
    private inner class CustomNotificationProvider: DefaultMediaNotificationProvider(this@BasePlayerService) {
        override fun addNotificationActions(
            mediaSession: MediaSession,
            mediaButtons: ImmutableList<CommandButton>,
            builder: NotificationCompat.Builder,
            actionFactory: MediaNotification.ActionFactory
        ): IntArray {
            return super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
        }

        override fun getMediaButtons(session: MediaSession, playerCommands: Player.Commands, customLayout: ImmutableList<CommandButton>, showPauseButton: Boolean): ImmutableList<CommandButton> {
            val seekToPreviousCommandButton = CommandButton.Builder().apply {
                setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                setIconResId(R.drawable.ic_notification_skip_to_previous_36dp)
                setEnabled(true)
            }.build()
            val playCommandButton = CommandButton.Builder().apply {
                setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                setIconResId(if (player.isPlaying) R.drawable.ic_notification_stop_36dp else R.drawable.ic_notification_play_36dp)
                setEnabled(true)
            }.build()
            val seekToNextCommandButton = CommandButton.Builder().apply {
                setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                setIconResId(R.drawable.ic_notification_skip_to_next_36dp)
                setEnabled(true)
            }.build()
            val commandButtons: MutableList<CommandButton> = mutableListOf(
                seekToPreviousCommandButton,
                playCommandButton,
                seekToNextCommandButton
            )
            return ImmutableList.copyOf(commandButtons)
        }
    }
    /*
     * End of inner class
     */


    /*
     * Custom LoadErrorHandlingPolicy that handles network drop outs
     */
    private val loadErrorHandlingPolicy: DefaultLoadErrorHandlingPolicy = object: DefaultLoadErrorHandlingPolicy()  {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            // try to reconnect every 5 seconds - up to 20 times
            if (loadErrorInfo.errorCount <= Keys.DEFAULT_MAX_RECONNECTION_COUNT && loadErrorInfo.exception is HttpDataSource.HttpDataSourceException) {
                return Keys.RECONNECTION_WAIT_INTERVAL
//            } else {
//                CoroutineScope(Main).launch {
//                    player.stop()
//                }
            }
            //    if (loadErrorInfo.errorCount > 3) {
            //        return C.TIME_UNSET // Don't retry after 3 attempts
            //    }
            //    return super.getRetryDelayMsFor(loadErrorInfo)
            return C.TIME_UNSET
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int {
            //    return 5 // Allow more retries for manifest loads, for example
            return Int.MAX_VALUE
        }
    }
    /*
     * End of declaration
     */


    /*
     * Custom MediaSourceFactory that inserts a web browser user agent for domains in Keys.WEB_BROWSER_USER_AGENT
     */
    private val customMediaSourceFactory by lazy {
        object : MediaSource.Factory {
            val defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(Keys.USER_AGENT)
            val defaultDataSourceFactory = DefaultDataSource.Factory(this@BasePlayerService, defaultHttpDataSourceFactory)
            val defaultMediaSourceFactory = DefaultMediaSourceFactory(defaultDataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)

            val webBrowserHttpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(Keys.WEB_BROWSER_USER_AGENT)
            val webBrowserCaseDataSourceFactory = DefaultDataSource.Factory(this@BasePlayerService, webBrowserHttpDataSourceFactory)
            val webBrowserMediaSourceFactory = DefaultMediaSourceFactory(webBrowserCaseDataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)

            override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
                defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
                webBrowserMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
                return this
            }
            override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
                defaultMediaSourceFactory.setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy())
                webBrowserMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                return this
            }
            override fun getSupportedTypes(): IntArray {
                return defaultMediaSourceFactory.supportedTypes // both should support the same types
            }
            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                // return a media source that inserts a web browser user agent if necessary
                val urlString = mediaItem.requestMetadata.mediaUri?.toString() ?: mediaItem.localConfiguration?.uri.toString()
                val requiresWebBrowserUserAgent = Keys.WEB_BROWSER_USER_AGENT_REQUIRED.any { hostname ->
                    urlString.contains(hostname, ignoreCase = true)
                }
                return if (requiresWebBrowserUserAgent) {
                    Log.d(TAG, "Using web browser user agent for: $urlString")
                    webBrowserMediaSourceFactory.createMediaSource(mediaItem)
                } else {
                    Log.d(TAG, "Using default user agent for: $urlString")
                    defaultMediaSourceFactory.createMediaSource(mediaItem)
                }
            }
        }
    }
    /*
     * End of declaration
     */


    /*
     * Custom Player for local playback
     */
    val localPlayer: Player by lazy {
        // create the local player
        val exoPlayer: ExoPlayer = ExoPlayer.Builder(this).apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            setLoadControl(loadControl)
            setMediaSourceFactory(customMediaSourceFactory)
        }.build()
        exoPlayer.addAnalyticsListener(analyticsListener)
        exoPlayer.addListener(playerListener)
        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        // manually add seek to next and seek to previous since headphones issue them and they are translated to next and previous station
        val player = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS).build()
            }
            override fun isCommandAvailable(command: Int): Boolean {
                return availableCommands.contains(command)
            }
            override fun seekToNext() {
                seekToNextMediaItem()
            }
            override fun seekToPrevious() {
                seekToPreviousMediaItem()
            }
            override fun getDuration(): Long {
                return C.TIME_UNSET // this will hide progress bar for HLS stations in the notification
            }
        }
        player
    }
    /*
     * End of declaration
     */


    /*
     * Player.Listener: Called when one or more player states changed.
     */
    var playerListener: Player.Listener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            // store state of playback
            val currentMediaId: String = player.currentMediaItem?.mediaId ?: String()
            PreferencesHelper.saveIsPlaying(isPlaying)
            PreferencesHelper.saveCurrentStationPosition(player.currentMediaItemIndex)
            PreferencesHelper.saveCurrentStationId(currentMediaId)
            // reset restart counter
            playbackRestartCounter = 0
            // todo check if this can be abstracted / is still necessary - see: CMD_UPDATE_COLLECTION (https://codeberg.org/y20k/transistor/commit/8503b79777e8ca82df3c172fd6af7ec784254c0c)
            // To remove the currently playing station, it is necessary to pause the player controller,
            // which will trigger the onIsPlayingChanged method.
            // Due to the invocation of the saveCollection method, the collection of stations needs to be reloaded.
            collection = FileHelper.readCollection(this@BasePlayerService)
            // save collection and player state
            collection = CollectionHelper.savePlaybackState(this@BasePlayerService, collection, currentMediaId, isPlaying)
            //updatePlayerState(station, playbackState)

            if (isPlaying) {
                // restart the sleep time if there is still time on the clock
                if (sleepTimerTimeRemaining > 0) startSleepTimer(continueWithPreviousTimerDuration = true)
            } else {
                // cancel sleep timer
                cancelSleepTimer(delayedReset = true)
                // reset metadata
                updateMetadata()

                // playback is not active
                // Not playing because playback is paused, ended, suppressed, or the player
                // is buffering, stopped or failed. Check player.getPlayWhenReady,
                // player.getPlaybackState, player.getPlaybackSuppressionReason and
                // player.getPlaybackError for details.
                when (player.playbackState) {
                    // player is able to immediately play from its current position
                    Player.STATE_READY -> {
                        // todo
                    }
                    // buffering - data needs to be loaded
                    Player.STATE_BUFFERING -> {
                        // todo
                    }
                    // player finished playing all media
                    Player.STATE_ENDED -> {
                        // todo
                    }
                    // initial state or player is stopped or playback failed
                    Player.STATE_IDLE -> {
                        // todo
                    }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (!playWhenReady) {
                when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> {
                        // playback reached end: stop / end playback
                    }
                    else -> {
                        // playback has been paused by user or OS: update media session and save state
                        // PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY or
                        // PLAY_WHEN_READY_CHANGE_REASON_REMOTE
                        // handlePlaybackChange(PlaybackStateCompat.STATE_PAUSED)
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Log.d(TAG, "PlayerError occurred: ${error.errorCodeName}")
            // todo: test if playback needs to be restarted
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            super.onMediaMetadataChanged(mediaMetadata)
            updateMetadata(AudioHelper.getMetadataString(mediaMetadata))
        }

//        override fun onMetadata(metadata: androidx.media3.common.Metadata) {
//            super.onMetadata(metadata)
//            updateMetadata(AudioHelper.getMetadataString(metadata)) // processes ONLY icy metadata
//        }
    }
    /*
     * End of declaration
     */


    /*
     * Custom LoadControl that increases buffer size by given multiplier
     */
    val loadControl: DefaultLoadControl = DefaultLoadControl.Builder()
        .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
        .setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * bufferSizeMultiplier,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * bufferSizeMultiplier,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS * bufferSizeMultiplier,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS * bufferSizeMultiplier
        )
        .build()
    /*
     * End of declaration
     */


    /*
     * Custom receiver that handles Keys.ACTION_COLLECTION_CHANGED
     */
    private val collectionChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.hasExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE)) {
                val date: Date = Date(intent.getLongExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE, 0L))

                if (date.after(collection.modificationDate)) {
                    Log.v(TAG, "PlayerService - reload collection after broadcast received.")
                    loadCollection(context)
                }
            }
        }
    }
    /*
     * End of declaration
     */


    /*
     * Defines the listener for changes in shared preferences
     */
    private val sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            Keys.PREF_LARGE_BUFFER_SIZE -> {
                bufferSizeMultiplier = PreferencesHelper.loadBufferSizeMultiplier()
                if (!player.isPlaying && !player.isLoading) {
                    // todo re-initialize player
                }
            }
        }
    }
    /*
     * End of declaration
     */


    /*
     * Custom AnalyticsListener that enables AudioFX equalizer integration
     */
    val analyticsListener = object: AnalyticsListener {
        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            super.onAudioSessionIdChanged(eventTime, audioSessionId)
            // integrate with system equalizer (AudioFX)
            val intent: Intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            sendBroadcast(intent)
            // note: remember to broadcast AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, when not needed anymore
        }
    }
    /*
     * End of declaration
     */

}
