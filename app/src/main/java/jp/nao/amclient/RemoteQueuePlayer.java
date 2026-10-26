package jp.nao.amclient;

import androidx.media3.common.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Exposes the server's queue actions even though ExoPlayer loads one protected source at a time. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class RemoteQueuePlayer extends ForwardingSimpleBasePlayer {
    public interface Navigation {void advance(boolean previous);}
    public interface Modes {
        ListenableFuture<?> shuffle(boolean enabled);
        ListenableFuture<?> repeat(int mode);
    }
    private final Navigation navigation;
    private final Modes modes;
    private boolean canSeek,canNext,canPrevious,busy;
    private QueueModes permissions=QueueModes.NONE;
    private boolean shuffle,modeUpdating;
    private boolean automaticTransition,continuePlaying;
    private int repeat=Player.REPEAT_MODE_OFF;
    public RemoteQueuePlayer(Player player,Navigation navigation){this(player,navigation,null);}
    public RemoteQueuePlayer(Player player,Navigation navigation,Modes modes){super(player);this.navigation=navigation;this.modes=modes;}
    public void updateControls(boolean seek,boolean next,boolean previous,boolean busy){
        if(canSeek==seek&&canNext==next&&canPrevious==previous&&this.busy==busy)return;
        canSeek=seek;canNext=next;canPrevious=previous;this.busy=busy;invalidateState();
    }
    public void updateModes(QueueModes permissions,boolean shuffle,int repeat,boolean updating){
        this.permissions=permissions;this.shuffle=shuffle;this.repeat=repeat;modeUpdating=updating;invalidateState();
    }
    public void beginAutomaticTransition(){beginAutomaticTransition(true);}
    public void beginAutomaticTransition(boolean play){if(automaticTransition)return;automaticTransition=true;continuePlaying=play;invalidateState();}
    public boolean isAutomaticTransition(){return automaticTransition;}
    public boolean shouldContinuePlaying(){return !automaticTransition||continuePlaying;}
    public void endAutomaticTransition(){if(!automaticTransition)return;automaticTransition=false;invalidateState();}
    public ListenableFuture<?> toggleShuffle(){return isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)?modes.shuffle(!shuffle):Futures.immediateFailedFuture(new IllegalStateException("Shuffle unavailable"));}
    public ListenableFuture<?> cycleRepeat(){return isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)?modes.repeat(permissions.next(repeat)):Futures.immediateFailedFuture(new IllegalStateException("Repeat unavailable"));}
    @Override protected State getState(){
        State state=super.getState();Player.Commands.Builder commands=state.availableCommands.buildUpon();
        commands.removeAll(Player.COMMAND_SET_SHUFFLE_MODE,Player.COMMAND_SET_REPEAT_MODE);
        if(modes!=null&&!busy&&!modeUpdating){
            if(permissions.shuffle)commands.add(Player.COMMAND_SET_SHUFFLE_MODE);
            if(permissions.all||permissions.one||repeat!=Player.REPEAT_MODE_OFF)commands.add(Player.COMMAND_SET_REPEAT_MODE);
        }
        commands.removeAll(Player.COMMAND_SEEK_TO_NEXT,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
        if(canNext&&!busy)commands.addAll(Player.COMMAND_SEEK_TO_NEXT,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
        if(canPrevious&&!busy)commands.addAll(Player.COMMAND_SEEK_TO_PREVIOUS,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
        if(!canSeek||busy)commands.removeAll(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,Player.COMMAND_SEEK_TO_DEFAULT_POSITION,Player.COMMAND_SEEK_TO_MEDIA_ITEM);
        State.Builder result=state.buildUpon().setAvailableCommands(commands.build()).setRepeatMode(repeat).setShuffleModeEnabled(shuffle);
        // A remote queue is still playing while its next protected source is fetched.
        // Publishing ENDED here drops the foreground service before a background restart.
        if(automaticTransition&&!state.timeline.isEmpty())result.setPlaybackState(Player.STATE_BUFFERING).setPlayWhenReady(continuePlaying,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).setIsLoading(true);
        return result.build();
    }
    @Override protected State getPlaceholderState(State suggested){return suggested.buildUpon().setRepeatMode(repeat).setShuffleModeEnabled(shuffle).build();}
    @Override protected ListenableFuture<?> handleSetShuffleModeEnabled(boolean enabled){return modes.shuffle(enabled);}
    @Override protected ListenableFuture<?> handleSetRepeatMode(int mode){return modes.repeat(mode);}
    @Override protected ListenableFuture<?> handleSetPlayWhenReady(boolean enabled){
        if(automaticTransition){continuePlaying=enabled;invalidateState();return Futures.immediateVoidFuture();}
        return super.handleSetPlayWhenReady(enabled);
    }
    @Override protected ListenableFuture<?> handleSeek(int index,long positionMs,int command){
        if(command==Player.COMMAND_SEEK_TO_NEXT||command==Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM){navigation.advance(false);return Futures.immediateVoidFuture();}
        if(command==Player.COMMAND_SEEK_TO_PREVIOUS||command==Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM){navigation.advance(true);return Futures.immediateVoidFuture();}
        return super.handleSeek(index,positionMs,command);
    }
}
