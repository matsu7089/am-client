package jp.nao.amclient;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.media3.common.Player;
import java.util.List;

/** Full playback controls; opening and closing the screen never replaces the playing source. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class PlayerActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThumbnailLoader thumbnails=new ThumbnailLoader();
    private PlaybackService service;
    private boolean bound,seeking;
    private ImageView cover;
    private TextView title,artist,elapsed,duration,status,permission;
    private SeekBar seek;
    private ImageButton play,previous,next,shuffle,repeat;
    private Button source,album;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){service=((PlaybackService.LocalBinder)binder).service();render();}
        @Override public void onServiceDisconnected(ComponentName name){service=null;render();}
    };
    static void open(Context context){context.startActivity(BottomNavigationView.inherit(context,new Intent(context,PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().setStatusBarColor(PlayerViews.BG);getWindow().setNavigationBarColor(PlayerViews.BG);
        LinearLayout root=PlayerViews.column(this);PlayerViews.insets(root);
        LinearLayout header=PlayerViews.row(this);header.setPadding(PlayerViews.dp(this,8),0,PlayerViews.dp(this,8),0);
        header.addView(PlayerViews.iconButton(this,PlayerViews.Icon.DOWN,"再生詳細を閉じる",this::finish),new LinearLayout.LayoutParams(PlayerViews.dp(this,48),PlayerViews.dp(this,56)));
        TextView heading=PlayerViews.text(this,"再生中",16,PlayerViews.MUTED);heading.setGravity(Gravity.CENTER);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(PlayerViews.iconButton(this,PlayerViews.Icon.SETTINGS,"再生設定",()->QualitySettings.show(this,service)),new LinearLayout.LayoutParams(PlayerViews.dp(this,48),PlayerViews.dp(this,56)));root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout body=PlayerViews.column(this);body.setGravity(Gravity.CENTER_VERTICAL);int p=PlayerViews.dp(this,24);body.setPadding(p,PlayerViews.dp(this,12),p,p);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        cover=PlayerViews.artwork(this,16);int size=Math.max(PlayerViews.dp(this,112),Math.min(PlayerViews.dp(this,320),Math.min(getResources().getDisplayMetrics().widthPixels-p*2,(int)(getResources().getDisplayMetrics().heightPixels*.36f))));LinearLayout.LayoutParams art=new LinearLayout.LayoutParams(size,size);art.gravity=Gravity.CENTER_HORIZONTAL;art.bottomMargin=PlayerViews.dp(this,24);body.addView(cover,art);
        title=PlayerViews.text(this,"曲を選んでください",24,Color.WHITE);title.setMaxLines(4);title.setEllipsize(TextUtils.TruncateAt.END);body.addView(title);
        artist=PlayerViews.text(this,"",15,PlayerViews.ACCENT);artist.setMaxLines(3);artist.setEllipsize(TextUtils.TruncateAt.END);artist.setPadding(0,PlayerViews.dp(this,6),0,PlayerViews.dp(this,8));artist.setMinHeight(PlayerViews.dp(this,48));artist.setGravity(Gravity.CENTER_VERTICAL);artist.setContentDescription("再生中のアーティストを開く");artist.setBackground(PlayerViews.ripple(this,Color.TRANSPARENT,8));artist.setOnClickListener(v->openArtist());body.addView(artist);
        status=PlayerViews.text(this,"",12,PlayerViews.MUTED);body.addView(status);
        seek=new SeekBar(this);seek.setContentDescription("再生位置");body.addView(seek,new LinearLayout.LayoutParams(-1,PlayerViews.dp(this,48)));seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int progress,boolean user){if(user)PlayerViews.text(elapsed,PlayerViews.time(progress));}
            public void onStartTrackingTouch(SeekBar b){seeking=true;}
            public void onStopTrackingTouch(SeekBar b){if(service!=null)service.seek(b.getProgress());seeking=false;render();}
        });
        LinearLayout times=PlayerViews.row(this);elapsed=PlayerViews.text(this,"0:00",12,PlayerViews.MUTED);duration=PlayerViews.text(this,"0:00",12,PlayerViews.MUTED);duration.setGravity(Gravity.END);times.addView(elapsed,new LinearLayout.LayoutParams(0,-2,1));times.addView(duration,new LinearLayout.LayoutParams(0,-2,1));body.addView(times);
        LinearLayout transport=PlayerViews.row(this);transport.setGravity(Gravity.CENTER);transport.setPadding(0,PlayerViews.dp(this,12),0,PlayerViews.dp(this,12));previous=PlayerViews.iconButton(this,PlayerViews.Icon.PREVIOUS,"前の曲",()->{if(service!=null)service.next(true);});play=PlayerViews.iconButton(this,PlayerViews.Icon.PLAY,"再生",()->{if(service!=null)service.togglePlayback();});next=PlayerViews.iconButton(this,PlayerViews.Icon.NEXT,"次の曲",()->{if(service!=null)service.next(false);});
        transport.addView(previous,new LinearLayout.LayoutParams(0,PlayerViews.dp(this,64),1));play.setBackground(PlayerViews.ripple(this,PlayerViews.ACCENT,40));PlayerViews.icon(play,PlayerViews.Icon.PLAY,PlayerViews.BG);transport.addView(play,new LinearLayout.LayoutParams(PlayerViews.dp(this,72),PlayerViews.dp(this,72)));transport.addView(next,new LinearLayout.LayoutParams(0,PlayerViews.dp(this,64),1));body.addView(transport);
        LinearLayout modes=PlayerViews.row(this);shuffle=PlayerViews.iconButton(this,PlayerViews.Icon.SHUFFLE,"シャッフル: OFF",()->{if(service!=null)service.setShuffle(!service.shuffleEnabled);});repeat=PlayerViews.iconButton(this,PlayerViews.Icon.REPEAT,"リピート: OFF",()->{if(service!=null)service.setRepeat(service.modes.next(service.repeatMode));});
        for(ImageButton control:new ImageButton[]{shuffle,repeat}){FrameLayout cell=new FrameLayout(this);cell.addView(control,new FrameLayout.LayoutParams(PlayerViews.dp(this,56),PlayerViews.dp(this,56),Gravity.CENTER));modes.addView(cell,new LinearLayout.LayoutParams(0,PlayerViews.dp(this,56),1));}body.addView(modes);
        permission=PlayerViews.text(this,"",12,PlayerViews.MUTED);permission.setPadding(0,PlayerViews.dp(this,6),0,PlayerViews.dp(this,12));permission.setGravity(Gravity.CENTER);body.addView(permission);
        LinearLayout links=PlayerViews.row(this);source=PlayerViews.button(this,"曲一覧",()->{if(service!=null)BrowseActivity.now(this);});album=PlayerViews.button(this,"アルバムの曲一覧",this::openAlbum);links.addView(source,new LinearLayout.LayoutParams(0,PlayerViews.dp(this,52),1));links.addView(album,new LinearLayout.LayoutParams(0,PlayerViews.dp(this,52),1));body.addView(links);
        setContentView(root);render();bound=bindService(new Intent(this,PlaybackService.class).setAction("local"),connection,BIND_AUTO_CREATE);
    }
    private void openArtist(){if(service==null||service.currentTrack==null)return;List<MusicEntity.ArtistRef> artists=service.currentTrack.artists;if(artists.size()==1){BrowseActivity.artist(this,artists.get(0).id);return;}if(artists.isEmpty())return;String[] names=new String[artists.size()];for(int i=0;i<names.length;i++)names[i]=artists.get(i).name;new AlertDialog.Builder(this).setTitle("アーティスト").setItems(names,(d,which)->BrowseActivity.artist(this,artists.get(which).id)).setNegativeButton("閉じる",null).show();}
    private void openAlbum(){if(service==null||service.currentTrack==null||service.currentTrack.album==null||service.currentTrack.album.id.isEmpty())return;BrowseActivity.collection(this,"Album",service.currentTrack.album.id,service.requiresSavedAudio());}
    private void render(){boolean hasTrack=service!=null&&!service.contentId.isEmpty(),available=hasTrack&&!service.busy&&!service.modeUpdating;
        PlaybackOrigin origin=hasTrack?PlaybackOrigin.target(service.origin,service.currentTrack):null;boolean playlist=origin!=null&&"Playlist".equals(origin.type);album.setVisibility(playlist?View.VISIBLE:View.GONE);PlayerViews.enabled(album,playlist&&service.currentTrack!=null&&service.currentTrack.album!=null&&!service.currentTrack.album.id.isEmpty());
        PlayerViews.enabled(previous,available&&service.canPrevious);PlayerViews.enabled(next,available&&service.canNext);PlayerViews.enabled(play,hasTrack&&service.canTogglePlayback());seek.setEnabled(available&&service.canSeek);PlayerViews.enabled(source,hasTrack);boolean hasArtists=hasTrack&&service.currentTrack!=null&&!service.currentTrack.artists.isEmpty();artist.setEnabled(hasArtists);
        if(!hasTrack){PlayerViews.text(title,"曲を選んでください");PlayerViews.text(artist,"");PlayerViews.text(status,"");thumbnails.bind(cover,"");seek.setProgress(0);PlayerViews.text(elapsed,"0:00");PlayerViews.text(duration,"0:00");PlayerViews.text(permission,"");PlayerViews.enabled(shuffle,false);PlayerViews.enabled(repeat,false);return;}
        PlayerViews.text(title,service.title);PlayerViews.text(artist,service.artist);thumbnails.bind(cover,service.artworkUrl);boolean playing=service.playbackRequested();PlayerViews.icon(play,playing?PlayerViews.Icon.PAUSE:PlayerViews.Icon.PLAY,PlayerViews.BG);play.setContentDescription(playing?"一時停止":"再生");
        long total=Math.max(0,service.player.getDuration()),current=Math.max(0,service.player.getCurrentPosition());if(!seeking){seek.setMax((int)Math.min(Integer.MAX_VALUE,total));seek.setProgress((int)Math.min(Integer.MAX_VALUE,current));PlayerViews.text(elapsed,PlayerViews.time(current));}PlayerViews.text(duration,PlayerViews.time(total));
        PlayerViews.text(status,service.message+(service.audioDescription().isEmpty()?"":" · "+service.audioDescription()));
        String shuffleLabel="シャッフル: "+(service.shuffleEnabled?"ON":"OFF");shuffle.setContentDescription(shuffleLabel);shuffle.setTooltipText(shuffleLabel);PlayerViews.icon(shuffle,PlayerViews.Icon.SHUFFLE,service.shuffleEnabled?PlayerViews.ACCENT:PlayerViews.MUTED);
        String loop=service.repeatMode==Player.REPEAT_MODE_ONE?"1曲":service.repeatMode==Player.REPEAT_MODE_ALL?("Album".equals(service.containerType)?"アルバム":"全曲"):"OFF",repeatLabel="リピート: "+loop;repeat.setContentDescription(repeatLabel);repeat.setTooltipText(repeatLabel);PlayerViews.icon(repeat,service.repeatMode==Player.REPEAT_MODE_ONE?PlayerViews.Icon.REPEAT_ONE:PlayerViews.Icon.REPEAT,service.repeatMode==Player.REPEAT_MODE_OFF?PlayerViews.MUTED:PlayerViews.ACCENT);
        PlayerViews.enabled(shuffle,available&&service.modes.shuffle);PlayerViews.enabled(repeat,available&&(service.modes.all||service.modes.one||service.repeatMode!=Player.REPEAT_MODE_OFF));modeSelection(shuffle,service.shuffleEnabled);modeSelection(repeat,service.repeatMode!=Player.REPEAT_MODE_OFF);
        boolean restricted=!service.modes.shuffle&&!service.modes.all&&!service.modes.one;PlayerViews.text(permission,restricted?"この再生ではシャッフル・リピートを利用できません":"");permission.setVisibility(restricted?View.VISIBLE:View.GONE);
        PlayerViews.text(source,playlist?"プレイリストの曲一覧":"アルバムの曲一覧");
    }
    private void modeSelection(ImageButton button,boolean selected){if(button.isSelected()==selected)return;button.setSelected(selected);button.setBackground(PlayerViews.ripple(this,selected?0xff243c40:Color.TRANSPARENT,28));}
    private final Runnable tick=new Runnable(){public void run(){render();main.postDelayed(this,500);}};
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);render();}
    @Override protected void onStart(){super.onStart();main.post(tick);}
    @Override protected void onStop(){main.removeCallbacks(tick);super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);thumbnails.close();if(bound)unbindService(connection);super.onDestroy();}
}
