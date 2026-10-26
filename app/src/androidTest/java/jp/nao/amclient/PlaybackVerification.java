package jp.nao.amclient;

import android.app.*;
import android.os.*;
import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.json.JSONObject;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import com.google.common.util.concurrent.Futures;

/** Exercises a real MediaController with a single-source player; no Amazon playback is needed. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class PlaybackVerification extends Instrumentation {
    private boolean liveQuality;
    private boolean liveHistory;
    private boolean liveDownloaded;
    private boolean liveSavedPriority;
    private boolean liveNormalization;
    private ExoPlayer engine;
    private RemoteQueuePlayer transport;
    private MediaSession session;
    private MediaController controller;
    private android.media.session.MediaController platformController;
    private WidgetControls widget;
    private FakePlayer automaticEngine;
    private RemoteQueuePlayer automaticTransport;
    private MediaSession automaticSession;
    private MediaController automaticController;
    private android.media.session.MediaController automaticPlatform;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);liveQuality=arguments!=null&&"true".equals(arguments.getString("liveQuality"));liveHistory=arguments!=null&&"true".equals(arguments.getString("liveHistory"));liveDownloaded=arguments!=null&&"true".equals(arguments.getString("liveDownloaded"));liveSavedPriority=arguments!=null&&"true".equals(arguments.getString("liveSavedPriority"));liveNormalization=arguments!=null&&"true".equals(arguments.getString("liveNormalization"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int outcome=Activity.RESULT_CANCELED;
        if(liveNormalization){try{result.putString("stream","PASS: "+LiveNormalizationVerification.run(this)+"\n");outcome=Activity.RESULT_OK;}catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n");}finish(outcome,result);return;}
        if(liveSavedPriority){try{result.putString("stream","PASS: "+LiveSavedPriorityVerification.run(this)+"\n");outcome=Activity.RESULT_OK;}catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n");}finish(outcome,result);return;}
        if(liveDownloaded){try{result.putString("stream","PASS: "+LiveDownloadedVerification.run(this)+"\n");outcome=Activity.RESULT_OK;}catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n");}finish(outcome,result);return;}
        if(liveHistory){try{result.putString("stream","PASS: "+LiveHistoryVerification.run(this)+"\n");outcome=Activity.RESULT_OK;}catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n");}finish(outcome,result);return;}
        if(liveQuality){try{result.putString("stream","PASS: "+LiveQualityVerification.run(this)+"\n");outcome=Activity.RESULT_OK;}catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n");}finish(outcome,result);return;}
        try {
            NormalizationVerification.run(getTargetContext());
            QualityVerification.run(this);
            HistoryVerification.run(getTargetContext());
            CountDownLatch changed=new CountDownLatch(1),platformChanged=new CountDownLatch(1),modeRequested=new CountDownLatch(1),shuffleRequested=new CountDownLatch(1);
            AtomicReference<ListenableFuture<MediaController>> connection=new AtomicReference<>();AtomicInteger nextCalls=new AtomicInteger();
            AtomicReference<SettableFuture<Void>> modeFuture=new AtomicReference<>();
            AtomicReference<CountDownLatch> repeatSignal=new AtomicReference<>(modeRequested),shuffleSignal=new AtomicReference<>(shuffleRequested);
            AtomicInteger requestedRepeat=new AtomicInteger(-1),shuffleCalls=new AtomicInteger();AtomicBoolean requestedShuffle=new AtomicBoolean();
            runOnMainSync(()->{
                engine=new ExoPlayer.Builder(getTargetContext()).build();
                engine.setMediaItem(item("first","最初の曲","first.jpg"));
                transport=new RemoteQueuePlayer(engine,previous->{
                    if(previous)throw new AssertionError("Next dispatched as previous");
                    nextCalls.incrementAndGet();engine.setMediaItem(item("second","次の曲","second.jpg"));
                },new RemoteQueuePlayer.Modes(){
                    public ListenableFuture<?> shuffle(boolean enabled){SettableFuture<Void> pending=SettableFuture.create();modeFuture.set(pending);requestedShuffle.set(enabled);shuffleCalls.incrementAndGet();shuffleSignal.get().countDown();return pending;}
                    public ListenableFuture<?> repeat(int mode){SettableFuture<Void> pending=SettableFuture.create();modeFuture.set(pending);requestedRepeat.set(mode);repeatSignal.get().countDown();return pending;}
                });
                transport.updateControls(false,true,false,false);
                if(!transport.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)||!transport.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))throw new AssertionError("Missing transport commands");
                if(transport.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)||transport.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))throw new AssertionError("Disallowed commands advertised");
                if(transport.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)||transport.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE))throw new AssertionError("Station exposes modes without permission");
                widget=new WidgetControls(transport);session=new MediaSession.Builder(getTargetContext(),transport).setCallback(widget).build();widget.refresh(session);
                platformController=new android.media.session.MediaController(getTargetContext(),session.getPlatformToken());
                platformController.registerCallback(new android.media.session.MediaController.Callback(){@Override public void onMetadataChanged(android.media.MediaMetadata metadata){if(metadata!=null&&"次の曲".equals(metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)))platformChanged.countDown();}},new Handler(Looper.getMainLooper()));
                Bundle notificationHints=new Bundle();notificationHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG,true);
                connection.set(new MediaController.Builder(getTargetContext(),session.getToken()).setConnectionHints(notificationHints).buildAsync());
            });
            controller=connection.get().get(10,TimeUnit.SECONDS);
            runOnMainSync(()->{
                if(!"最初の曲".contentEquals(controller.getMediaMetadata().title))throw new AssertionError("Initial metadata missing");
                if(!controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))throw new AssertionError("System controller cannot skip");
                long actions=platformController.getPlaybackState().getActions();
                if((actions&android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT)==0||(actions&android.media.session.PlaybackState.ACTION_PLAY)==0)throw new AssertionError("Platform widget lacks play/next actions");
                controller.addListener(new Player.Listener(){@Override public void onMediaMetadataChanged(MediaMetadata metadata){if("次の曲".contentEquals(metadata.title))changed.countDown();}});
                platformController.getTransportControls().skipToNext();
            });
            if(!changed.await(10,TimeUnit.SECONDS))throw new AssertionError("Metadata did not update after next");
            if(!platformChanged.await(5,TimeUnit.SECONDS))throw new AssertionError("Android widget retained old title");
            runOnMainSync(()->{
                if(nextCalls.get()!=1)throw new AssertionError("Next must be dispatched once");
                if(!"second".equals(controller.getCurrentMediaItem().mediaId))throw new AssertionError("Controller retained old media id");
                if(!Uri.parse("content://jp.nao.amclient.test/artwork/second.jpg").equals(controller.getMediaMetadata().artworkUri))throw new AssertionError("Artwork retained old track");
                transport.updateControls(true,false,true,false);
            });
            CountDownLatch commandsChanged=new CountDownLatch(1);
            runOnMainSync(()->{if(!controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)&&controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))commandsChanged.countDown();else controller.addListener(new Player.Listener(){@Override public void onAvailableCommandsChanged(Player.Commands commands){if(!commands.contains(Player.COMMAND_SEEK_TO_NEXT)&&commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))commandsChanged.countDown();}});});
            if(!commandsChanged.await(5,TimeUnit.SECONDS))throw new AssertionError("Queue capabilities not propagated");
            QueueModes allowed=QueueModes.from(new JSONObject("{capabilities:{shuffle:{isAllowed:true},loopAll:{isAllowed:true}}}"),new JSONObject("{capabilities:{loopOne:{isAllowed:true}}}"));
            CountDownLatch modeAvailable=new CountDownLatch(1),repeatChanged=new CountDownLatch(1);
            runOnMainSync(()->{
                controller.addListener(new Player.Listener(){
                    @Override public void onAvailableCommandsChanged(Player.Commands commands){if(commands.contains(Player.COMMAND_SET_REPEAT_MODE)&&commands.contains(Player.COMMAND_SET_SHUFFLE_MODE))modeAvailable.countDown();}
                    @Override public void onRepeatModeChanged(int mode){if(mode==Player.REPEAT_MODE_ALL)repeatChanged.countDown();}
                });
                transport.updateModes(allowed,false,Player.REPEAT_MODE_OFF,false);widget.refresh(session);
            });
            if(!modeAvailable.await(5,TimeUnit.SECONDS))throw new AssertionError("All-Access modes missing in MediaController");
            runOnMainSync(()->controller.setRepeatMode(Player.REPEAT_MODE_ALL));
            if(!modeRequested.await(5,TimeUnit.SECONDS))throw new AssertionError("Repeat not dispatched to server adapter");
            runOnMainSync(()->{
                if(transport.getRepeatMode()!=Player.REPEAT_MODE_OFF)throw new AssertionError("Unconfirmed server mode advertised");
                transport.updateModes(allowed,false,Player.REPEAT_MODE_ALL,false);widget.refresh(session);modeFuture.get().set(null);
            });
            if(!repeatChanged.await(5,TimeUnit.SECONDS))throw new AssertionError("Confirmed repeat mode not published");
            runOnMainSync(()->{
                if(engine.getRepeatMode()!=Player.REPEAT_MODE_OFF)throw new AssertionError("Album loop incorrectly loops the single loaded source");
                controller.setShuffleModeEnabled(true);
            });
            if(!shuffleRequested.await(5,TimeUnit.SECONDS))throw new AssertionError("Shuffle not dispatched to server adapter");
            runOnMainSync(()->{
                if(transport.getShuffleModeEnabled())throw new AssertionError("Unconfirmed shuffle advertised");
                transport.updateModes(allowed,true,Player.REPEAT_MODE_ALL,false);widget.refresh(session);modeFuture.get().set(null);
            });
            waitForIdleSync();
            runOnMainSync(()->{
                if(!transport.getShuffleModeEnabled()||engine.getShuffleModeEnabled())throw new AssertionError("Shuffle must be controlled by the remote queue");
                transport.updateModes(QueueModes.NONE,false,Player.REPEAT_MODE_OFF,false);widget.refresh(session);
                if(transport.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)||transport.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE))throw new AssertionError("Old modes retained after station switch");
            });
            // Both framework custom actions and Media3 commands must reach the same async backend.
            runOnMainSync(()->{transport.updateModes(allowed,false,Player.REPEAT_MODE_OFF,false);widget.refresh(session);});
            await("Widget buttons missing",()->hasAction("シャッフル: OFF")&&hasAction("リピート: OFF"));
            CountDownLatch widgetShuffle=new CountDownLatch(1);shuffleSignal.set(widgetShuffle);
            platformController.getTransportControls().sendCustomAction(WidgetControls.SHUFFLE.customAction,Bundle.EMPTY);
            if(!widgetShuffle.await(5,TimeUnit.SECONDS))throw new AssertionError("Widget shuffle never reached backend");
            if(!requestedShuffle.get())throw new AssertionError("Shuffle button did not toggle ON");
            runOnMainSync(()->{
                if(transport.getShuffleModeEnabled())throw new AssertionError("Widget announced an unconfirmed mode");
                transport.updateModes(allowed,true,Player.REPEAT_MODE_OFF,false);widget.refresh(session);modeFuture.get().set(null);
            });
            await("Widget shuffle state stale",()->hasAction("シャッフル: ON"));
            for(int expected:new int[]{Player.REPEAT_MODE_ALL,Player.REPEAT_MODE_ONE,Player.REPEAT_MODE_OFF}){
                CountDownLatch signal=new CountDownLatch(1);repeatSignal.set(signal);
                AtomicReference<ListenableFuture<SessionResult>> command=new AtomicReference<>();
                runOnMainSync(()->command.set(controller.sendCustomCommand(WidgetControls.REPEAT,Bundle.EMPTY)));
                if(!signal.await(5,TimeUnit.SECONDS))throw new AssertionError("Widget repeat never reached backend");
                if(requestedRepeat.get()!=expected)throw new AssertionError("Repeat cycle skipped a permitted mode");
                if(command.get().isDone())throw new AssertionError("Mode command completed before server confirmation");
                runOnMainSync(()->{transport.updateModes(allowed,true,expected,false);widget.refresh(session);modeFuture.get().set(null);});
                if(command.get().get(5,TimeUnit.SECONDS).resultCode!=SessionResult.RESULT_SUCCESS)throw new AssertionError("Confirmed mode failed");
                String label=expected==Player.REPEAT_MODE_ALL?"全曲":expected==Player.REPEAT_MODE_ONE?"1曲":"OFF";
                await("Widget repeat label stale",()->hasAction("リピート: "+label));
            }
            CountDownLatch failedSignal=new CountDownLatch(1);repeatSignal.set(failedSignal);
            AtomicReference<ListenableFuture<SessionResult>> failedCommand=new AtomicReference<>();
            runOnMainSync(()->failedCommand.set(controller.sendCustomCommand(WidgetControls.REPEAT,Bundle.EMPTY)));
            if(!failedSignal.await(5,TimeUnit.SECONDS))throw new AssertionError("Missing failed request");
            modeFuture.get().setException(new java.io.IOException("Server rejected test mode"));
            if(failedCommand.get().get(5,TimeUnit.SECONDS).resultCode!=SessionError.ERROR_IO)throw new AssertionError("Failure was reported as success");
            await("Failed mode changed label",()->hasAction("リピート: OFF"));
            int calls=shuffleCalls.get();
            runOnMainSync(()->{transport.updateModes(QueueModes.NONE,false,Player.REPEAT_MODE_OFF,false);widget.refresh(session);});
            await("Station still exposes widget modes",()->platformController.getPlaybackState().getCustomActions().isEmpty());
            AtomicReference<ListenableFuture<SessionResult>> denied=new AtomicReference<>();
            runOnMainSync(()->denied.set(widget.onCustomCommand(session,null,WidgetControls.SHUFFLE,Bundle.EMPTY)));
            if(denied.get().get().resultCode!=SessionError.ERROR_PERMISSION_DENIED||shuffleCalls.get()!=calls)throw new AssertionError("Stale widget action bypassed permissions");
            verifyAutomaticTransition();
            result.putString("stream","PASS: normalization PCM target, stereo, bypass, lifecycle and learned levels; history persistence/order/account isolation; SD quality selection, saved representation, atomic replacement/cancellation and legacy compatibility; transport and metadata, widget shuffle/repeat confirmation/permissions, automatic transition without stopped state, pause during transition, and true queue end\n");
            outcome=Activity.RESULT_OK;
        }catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n");}
        finally{runOnMainSync(()->{if(automaticController!=null)automaticController.release();if(automaticSession!=null)automaticSession.release();if(automaticTransport!=null)automaticTransport.release();if(controller!=null)controller.release();if(session!=null)session.release();if(transport!=null)transport.release();else if(engine!=null)engine.release();});}
        finish(outcome,result);
    }
    private boolean hasAction(String name){
        android.media.session.PlaybackState state=platformController.getPlaybackState();
        if(state==null)return false;
        for(android.media.session.PlaybackState.CustomAction action:state.getCustomActions())if(name.contentEquals(action.getName())&&action.getIcon()!=0)return true;
        return false;
    }
    private void await(String message,BooleanSupplier condition)throws Exception{
        for(int i=0;i<250;i++){AtomicBoolean ready=new AtomicBoolean();runOnMainSync(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(20);}
        throw new AssertionError(message);
    }
    private void verifyAutomaticTransition()throws Exception{
        AtomicReference<ListenableFuture<MediaController>> connection=new AtomicReference<>();
        AtomicBoolean nextExists=new AtomicBoolean(true);AtomicInteger stopped=new AtomicInteger();
        runOnMainSync(()->{
            automaticEngine=new FakePlayer(item("automatic-first","自動送り前","auto-first.jpg"));
            automaticTransport=new RemoteQueuePlayer(automaticEngine,previous->{});automaticTransport.updateControls(true,true,true,false);
            automaticEngine.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&nextExists.get()){automaticTransport.beginAutomaticTransition();automaticEngine.pause();}}});
            automaticSession=new MediaSession.Builder(getTargetContext(),automaticTransport).setId("automatic-transition-test").build();
            automaticPlatform=new android.media.session.MediaController(getTargetContext(),automaticSession.getPlatformToken());
            automaticPlatform.registerCallback(new android.media.session.MediaController.Callback(){@Override public void onPlaybackStateChanged(android.media.session.PlaybackState state){if(state!=null&&(state.getState()==android.media.session.PlaybackState.STATE_STOPPED||state.getState()==android.media.session.PlaybackState.STATE_PAUSED))stopped.incrementAndGet();}},new Handler(Looper.getMainLooper()));
            Bundle notificationHints=new Bundle();notificationHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG,true);
            connection.set(new MediaController.Builder(getTargetContext(),automaticSession.getToken()).setConnectionHints(notificationHints).buildAsync());
        });
        automaticController=connection.get().get(5,TimeUnit.SECONDS);
        runOnMainSync(()->automaticEngine.publish(item("automatic-first","自動送り前","auto-first.jpg"),Player.STATE_ENDED,true));
        await("Natural end became stopped",()->automaticPlatform.getPlaybackState().getState()==android.media.session.PlaybackState.STATE_BUFFERING&&automaticTransport.getPlayWhenReady());
        runOnMainSync(()->{
            automaticEngine.publish(item("automatic-second","自動送り後","auto-second.jpg"),Player.STATE_BUFFERING,false);
            automaticEngine.setPlayWhenReady(automaticTransport.shouldContinuePlaying());automaticTransport.endAutomaticTransition();
            automaticEngine.publish(item("automatic-second","自動送り後","auto-second.jpg"),Player.STATE_READY,true);
        });
        await("Automatic track metadata stale",()->"自動送り後".equals(automaticPlatform.getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_TITLE))&&automaticPlatform.getPlaybackState().getState()==android.media.session.PlaybackState.STATE_PLAYING);
        if(stopped.get()!=0)throw new AssertionError("Natural transition exposed a stopped/paused state");
        runOnMainSync(()->automaticEngine.publish(item("automatic-second","自動送り後","auto-second.jpg"),Player.STATE_ENDED,true));
        runOnMainSync(()->automaticController.pause());
        await("Pause during transition ignored",()->!automaticTransport.shouldContinuePlaying());
        runOnMainSync(()->{
            automaticEngine.publish(item("automatic-third","停止を維持","auto-third.jpg"),Player.STATE_BUFFERING,false);
            automaticEngine.setPlayWhenReady(automaticTransport.shouldContinuePlaying());automaticTransport.endAutomaticTransition();
            automaticEngine.publish(item("automatic-third","停止を維持","auto-third.jpg"),Player.STATE_READY,false);
        });
        await("Transition restarted after explicit pause",()->automaticPlatform.getPlaybackState().getState()==android.media.session.PlaybackState.STATE_PAUSED);
        nextExists.set(false);
        runOnMainSync(()->automaticEngine.publish(item("automatic-third","停止を維持","auto-third.jpg"),Player.STATE_ENDED,false));
        await("True queue end masked",()->automaticPlatform.getPlaybackState().getState()==android.media.session.PlaybackState.STATE_STOPPED);
    }
    private static final class FakePlayer extends SimpleBasePlayer {
        private State value;
        FakePlayer(MediaItem first){super(Looper.getMainLooper());publish(first,Player.STATE_READY,true);}
        void publish(MediaItem item,int state,boolean play){value=new State.Builder().setAvailableCommands(new Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(Collections.singletonList(new MediaItemData.Builder(item.mediaId).setMediaItem(item).setMediaMetadata(item.mediaMetadata).setDurationUs(1_000_000).build()))
            .setPlaybackState(state).setPlayWhenReady(play,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build();invalidateState();}
        @Override protected State getState(){return value;}
        @Override protected ListenableFuture<?> handleSetPlayWhenReady(boolean ready){value=value.buildUpon().setPlayWhenReady(ready,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build();invalidateState();return Futures.immediateVoidFuture();}
        @Override protected ListenableFuture<?> handleRelease(){return Futures.immediateVoidFuture();}
    }
    private MediaItem item(String id,String title,String artwork){return new MediaItem.Builder().setMediaId(id).setUri("https://example.org/"+id+".mpd")
        .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).setArtist("テストアーティスト").setArtworkUri(Uri.parse("content://jp.nao.amclient.test/artwork/"+artwork)).build()).build();}
}
