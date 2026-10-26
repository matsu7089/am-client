package jp.nao.amclient;

import android.os.Bundle;
import androidx.media3.common.Player;
import androidx.media3.session.*;
import com.google.common.util.concurrent.*;
import java.util.*;

/** Parameter-free toggle/cycle buttons alongside the standard absolute player commands. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class WidgetControls implements MediaSession.Callback {
    static final SessionCommand SHUFFLE=new SessionCommand("jp.nao.amclient.TOGGLE_SHUFFLE",Bundle.EMPTY);
    static final SessionCommand REPEAT=new SessionCommand("jp.nao.amclient.CYCLE_REPEAT",Bundle.EMPTY);
    private final RemoteQueuePlayer player;
    private SessionCommands lastCommands;
    private List<CommandButton> lastButtons;
    WidgetControls(RemoteQueuePlayer player){this.player=player;}
    private SessionCommands commands(){
        SessionCommands.Builder commands=MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon();
        if(player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE))commands.add(SHUFFLE);
        if(player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE))commands.add(REPEAT);
        return commands.build();
    }
    private List<CommandButton> buttons(){
        List<CommandButton> buttons=new ArrayList<>();
        if(player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE))buttons.add(new CommandButton.Builder(player.getShuffleModeEnabled()?CommandButton.ICON_SHUFFLE_ON:CommandButton.ICON_SHUFFLE_OFF)
            .setSessionCommand(SHUFFLE).setDisplayName("シャッフル: "+(player.getShuffleModeEnabled()?"ON":"OFF")).setSlots(CommandButton.SLOT_BACK_SECONDARY).build());
        if(player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)){
            int repeat=player.getRepeatMode();buttons.add(new CommandButton.Builder(repeat==Player.REPEAT_MODE_ONE?CommandButton.ICON_REPEAT_ONE:repeat==Player.REPEAT_MODE_ALL?CommandButton.ICON_REPEAT_ALL:CommandButton.ICON_REPEAT_OFF)
                .setSessionCommand(REPEAT).setDisplayName("リピート: "+(repeat==Player.REPEAT_MODE_ONE?"1曲":repeat==Player.REPEAT_MODE_ALL?"全曲":"OFF")).setSlots(CommandButton.SLOT_FORWARD_SECONDARY).build());
        }
        return buttons;
    }
    @Override public MediaSession.ConnectionResult onConnect(MediaSession session,MediaSession.ControllerInfo controller){
        return new MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands()).build();
    }
    void refresh(MediaSession session){
        SessionCommands commands=commands();List<CommandButton> buttons=buttons();
        if(!commands.equals(lastCommands)){
            lastCommands=commands;
            for(MediaSession.ControllerInfo controller:session.getConnectedControllers())session.setAvailableCommands(controller,commands,MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS);
        }
        if(!buttons.equals(lastButtons)){lastButtons=buttons;session.setMediaButtonPreferences(buttons);}
    }
    @Override public ListenableFuture<SessionResult> onCustomCommand(MediaSession session,MediaSession.ControllerInfo controller,SessionCommand command,Bundle args){
        ListenableFuture<?> pending;
        if(command.equals(SHUFFLE)){
            if(!player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE))return Futures.immediateFuture(new SessionResult(SessionError.ERROR_PERMISSION_DENIED));
            pending=player.toggleShuffle();
        }else if(command.equals(REPEAT)){
            if(!player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE))return Futures.immediateFuture(new SessionResult(SessionError.ERROR_PERMISSION_DENIED));
            pending=player.cycleRepeat();
        }else return Futures.immediateFuture(new SessionResult(SessionError.ERROR_NOT_SUPPORTED));
        return Futures.catching(Futures.transform(pending,ignored->new SessionResult(SessionResult.RESULT_SUCCESS),MoreExecutors.directExecutor()),Exception.class,
            ignored->new SessionResult(SessionError.ERROR_IO),MoreExecutors.directExecutor());
    }
}
