package jp.nao.amclient;

import android.app.Activity;
import android.graphics.Color;
import android.text.TextUtils;
import android.widget.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@android.annotation.SuppressLint("ViewConstructor") // Built by its owning activity, never inflated from XML.
final class MiniPlayerView extends LinearLayout implements AutoCloseable {
    private final ThumbnailLoader thumbnails=new ThumbnailLoader();
    private final ImageView cover;
    private final TextView title,artist;
    private final ImageButton play,next;
    private PlaybackService service;
    MiniPlayerView(Activity activity){super(activity);setGravity(android.view.Gravity.CENTER_VERTICAL);setPadding(PlayerViews.dp(activity,12),PlayerViews.dp(activity,8),PlayerViews.dp(activity,8),PlayerViews.dp(activity,8));setBackground(PlayerViews.ripple(activity,PlayerViews.SURFACE,0));setMinimumHeight(PlayerViews.dp(activity,64));
        cover=PlayerViews.artwork(activity,8);addView(cover,new LinearLayout.LayoutParams(PlayerViews.dp(activity,44),PlayerViews.dp(activity,44)));
        LinearLayout info=PlayerViews.column(activity);info.setPadding(PlayerViews.dp(activity,12),0,PlayerViews.dp(activity,8),0);
        title=PlayerViews.text(activity,"",15,Color.WHITE);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);info.addView(title);
        artist=PlayerViews.text(activity,"",12,PlayerViews.MUTED);artist.setSingleLine(true);artist.setEllipsize(TextUtils.TruncateAt.END);info.addView(artist);addView(info,new LinearLayout.LayoutParams(0,-2,1));
        play=PlayerViews.iconButton(activity,PlayerViews.Icon.PLAY,"再生",()->{if(service!=null)service.togglePlayback();});next=PlayerViews.iconButton(activity,PlayerViews.Icon.NEXT,"次の曲",()->{if(service!=null)service.next(false);});
        addView(play,new LinearLayout.LayoutParams(PlayerViews.dp(activity,48),PlayerViews.dp(activity,48)));addView(next,new LinearLayout.LayoutParams(PlayerViews.dp(activity,48),PlayerViews.dp(activity,48)));
        setContentDescription("再生詳細を開く");setFocusable(true);setOnClickListener(v->{if(service!=null&&!service.contentId.isEmpty())PlayerActivity.open(activity);});setVisibility(GONE);
    }
    void update(PlaybackService service){this.service=service;boolean hasTrack=service!=null&&!service.contentId.isEmpty();setVisibility(hasTrack?VISIBLE:GONE);if(!hasTrack)return;
        PlayerViews.text(title,service.title);PlayerViews.text(artist,service.artist);thumbnails.bind(cover,service.artworkUrl);
        boolean playing=service.playbackRequested();PlayerViews.icon(play,playing?PlayerViews.Icon.PAUSE:PlayerViews.Icon.PLAY,PlayerViews.ACCENT);play.setContentDescription(playing?"一時停止":"再生");PlayerViews.enabled(play,service.canTogglePlayback());PlayerViews.enabled(next,!service.busy&&!service.modeUpdating&&service.canNext);
    }
    @Override public void close(){thumbnails.close();service=null;}
}
