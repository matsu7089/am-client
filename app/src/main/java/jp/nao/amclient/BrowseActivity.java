package jp.nao.amclient;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Native artist pages and collection track lists. Playback continues in the shared service. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class BrowseActivity extends Activity {
    private static final int BG=0xff101619,ACCENT=0xff70e5d5;
    private static final String NOW="Now",ARTIST="Artist";
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThumbnailLoader thumbnails=new ThumbnailLoader();
    private PlaybackService playback;
    private String kind,id,seenTrack="",highlight="",cursor="";
    private PlaybackOrigin seenOrigin;
    private boolean playlists,loading,bound;
    private int requestGeneration;
    private MusicEntity owner;
    private List<MusicEntity> items=Collections.emptyList();
    private TextView heading,name,summary,status,empty,downloadStatus;
    private ImageView image;
    private LinearLayout people,tabs;
    private Button primary,albums,playlistTab,more,download,edit;
    private long playlistRevision=PlaylistApi.revision.get();
    private MiniPlayerView miniPlayer;
    private MusicEntity pendingDownload;
    private final BroadcastReceiver downloadReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){updateDownload();}};
    private Rows adapter;
    private ListView list;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){playback=((PlaybackService.LocalBinder)binder).service();miniPlayer.update(playback);if(NOW.equals(kind))load(false);}
        @Override public void onServiceDisconnected(ComponentName name){playback=null;miniPlayer.update(null);}
    };
    static void now(Context context){context.startActivity(BottomNavigationView.inherit(context,new Intent(context,BrowseActivity.class).putExtra("kind",NOW)));}
    static void artist(Context context,String id){open(context,ARTIST,id);}
    static void collection(Context context,String type,String id){open(context,type,id);}
    static void ownedPlaylist(Context context,String id){context.startActivity(BottomNavigationView.inherit(context,new Intent(context,BrowseActivity.class).putExtra("kind","Playlist").putExtra("id",id).putExtra("editablePlaylist",true)));}
    static void collection(Context context,String type,String id,boolean savedOnly){context.startActivity(BottomNavigationView.inherit(context,new Intent(context,BrowseActivity.class).putExtra("kind",type).putExtra("id",id).putExtra("savedOnly",savedOnly)));}
    static void savedCollection(Context context,String type,String id){context.startActivity(BottomNavigationView.inherit(context,new Intent(context,BrowseActivity.class).putExtra("kind",type).putExtra("id",id).putExtra("downloaded",true)));}
    private static void open(Context context,String type,String id){context.startActivity(BottomNavigationView.inherit(context,new Intent(context,BrowseActivity.class).putExtra("kind",type).putExtra("id",id)));}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);kind=getIntent().getStringExtra("kind");id=getIntent().getStringExtra("id");
        if(!NOW.equals(kind)&&!ARTIST.equals(kind)&&!"Album".equals(kind)&&!"Playlist".equals(kind)){finish();return;}
        if(!NOW.equals(kind)&&(id==null||id.isEmpty())){finish();return;}
        if(saved!=null)playlists=saved.getBoolean("playlists");
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=column();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        LinearLayout nav=row();nav.addView(button("‹ 戻る",this::finish),new LinearLayout.LayoutParams(dp(72),dp(52)));
        heading=text(NOW.equals(kind)?"再生元の曲一覧":ARTIST.equals(kind)?"アーティスト":"曲一覧",18,Color.WHITE);nav.addView(heading,new LinearLayout.LayoutParams(0,-2,1));nav.addView(button("設定",()->QualitySettings.show(this,playback)),new LinearLayout.LayoutParams(dp(64),dp(52)));nav.addView(button("再読込",()->load(false)),new LinearLayout.LayoutParams(dp(72),dp(52)));root.addView(nav);
        LinearLayout hero=row();hero.setPadding(dp(16),dp(12),dp(16),dp(8));image=artwork();hero.addView(image,new LinearLayout.LayoutParams(dp(72),dp(72)));
        LinearLayout info=column();info.setPadding(dp(12),0,0,0);name=text("読み込んでいます…",22,Color.WHITE);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);summary=text("",12,Color.LTGRAY);summary.setMaxLines(2);info.addView(name);info.addView(summary);hero.addView(info,new LinearLayout.LayoutParams(0,-2,1));root.addView(hero);
        HorizontalScrollView artistScroll=new HorizontalScrollView(this);artistScroll.setHorizontalScrollBarEnabled(false);people=row();people.setPadding(dp(8),0,dp(8),0);artistScroll.addView(people);root.addView(artistScroll);
        LinearLayout actions=row();primary=tab(ARTIST.equals(kind)?"ランダム再生":"全曲を再生",this::playOwner);primary.setEnabled(false);actions.addView(primary);
        download=tab("ダウンロード",this::downloadCollection);download.setVisibility(ARTIST.equals(kind)?View.GONE:View.VISIBLE);download.setEnabled(false);actions.addView(download);root.addView(actions);
        edit=tab("編集",()->PlaylistActivity.open(this,id));edit.setContentDescription("自作プレイリストを編集");edit.setVisibility(View.GONE);actions.addView(edit);
        downloadStatus=text("",12,ACCENT);downloadStatus.setPadding(dp(16),0,dp(16),dp(6));downloadStatus.setVisibility(ARTIST.equals(kind)?View.GONE:View.VISIBLE);root.addView(downloadStatus);
        tabs=row();albums=tab("アルバム",()->switchTab(false));playlistTab=tab("プレイリスト",()->switchTab(true));tabs.addView(albums);tabs.addView(playlistTab);tabs.setVisibility(ARTIST.equals(kind)?View.VISIBLE:View.GONE);root.addView(tabs);updateTabs();
        status=text("読み込んでいます…",12,Color.LTGRAY);status.setPadding(dp(16),dp(8),dp(16),dp(8));root.addView(status);
        FrameLayout body=new FrameLayout(this);list=new ListView(this);list.setDivider(new android.graphics.drawable.ColorDrawable(0xff26343b));list.setDividerHeight(dp(1));list.setCacheColorHint(BG);adapter=new Rows();list.setAdapter(adapter);body.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=text("",15,Color.LTGRAY);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(24),dp(24),dp(24));body.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        more=button("さらに表示",()->load(true));more.setVisibility(View.GONE);root.addView(more,new LinearLayout.LayoutParams(-1,dp(48)));
        miniPlayer=new MiniPlayerView(this);root.addView(miniPlayer,new LinearLayout.LayoutParams(-1,-2));root.addView(new BottomNavigationView(this,BottomNavigationView.section(this),section->BottomNavigationView.open(this,section)));setContentView(root);
        bound=bindService(new Intent(this,PlaybackService.class).setAction("local"),connection,BIND_AUTO_CREATE);
        if(!NOW.equals(kind))load(false);
    }
    private void switchTab(boolean value){if(playlists==value)return;playlists=value;updateTabs();load(false);}
    private void updateTabs(){if(albums==null)return;albums.setSelected(!playlists);playlistTab.setSelected(playlists);albums.setBackgroundColor(!playlists?0xff243c40:Color.TRANSPARENT);playlistTab.setBackgroundColor(playlists?0xff243c40:Color.TRANSPARENT);}
    private void load(boolean append){
        if(isDestroyed()||append&&loading)return;
        if(NOW.equals(kind)&&(playback==null||playback.contentId.isEmpty())){++requestGeneration;loading=false;seenTrack="";seenOrigin=null;owner=null;items=Collections.emptyList();people.removeAllViews();primary.setEnabled(false);more.setVisibility(View.GONE);adapter.notifyDataSetChanged();thumbnails.bind(image,"");heading.setText("再生元の曲一覧");name.setText("再生中の曲はありません");summary.setText("");status.setText("");empty.setText("検索やライブラリから曲を再生してください。");return;}
        final int token=++requestGeneration;final boolean selectedPlaylists=playlists;final String requestedCursor=append?cursor:"";
        final String trackId=NOW.equals(kind)?playback.contentId:"";final MusicEntity metadata=NOW.equals(kind)?playback.currentTrack:null;
        final PlaybackOrigin source=NOW.equals(kind)?playback.origin:null;
        if(NOW.equals(kind)){seenTrack=trackId;seenOrigin=source;}
        loading=true;more.setEnabled(false);status.setText("読み込んでいます…");empty.setText("");
        edit.setEnabled(false);
        if(!append){items=Collections.emptyList();cursor="";adapter.notifyDataSetChanged();more.setVisibility(View.GONE);}
        work.execute(()->{try{
            AmazonApi api=AmazonApi.get(this);MusicEntity track=metadata;CatalogPage page;
            if(ARTIST.equals(kind))page=api.artist(id,selectedPlaylists,requestedCursor);
            else if(NOW.equals(kind)){
                if(track==null||!trackId.equals(track.id))track=api.metadata(trackId);
                PlaybackOrigin target=PlaybackOrigin.target(source,track);
                if(target==null)throw new java.io.IOException("この曲の再生元が見つかりません");
                page=api.collection(target.type,target.id,requestedCursor);
            }else page=api.collection(kind,id,requestedCursor);
            if(getIntent().getBooleanExtra("downloaded",false)&&!append)new OfflineStore(this).rememberCollection(page.owner,page.items);
            MusicEntity current=track;
            main.post(()->{
                if(isDestroyed()||token!=requestGeneration)return;
                if(NOW.equals(kind)&&(playback==null||!trackId.equals(playback.contentId)||source!=playback.origin)){loading=false;load(false);return;}
                loading=false;owner=page.owner;items=append?CatalogPage.append(items,page.items):page.items;cursor=page.cursor;showOwner(current);
                status.setText((ARTIST.equals(kind)?(selectedPlaylists?"関連プレイリスト":"アルバム"):"曲一覧")+" · "+items.size()+"件");
                empty.setText("表示できる項目がありません");more.setVisibility(cursor.isEmpty()?View.GONE:View.VISIBLE);more.setEnabled(true);adapter.notifyDataSetChanged();
                if(NOW.equals(kind)&&!append)for(int i=0;i<items.size();i++)if(items.get(i).id.equals(trackId)){list.setSelection(i);break;}
            });
        }catch(Exception issue){main.post(()->{if(isDestroyed()||token!=requestGeneration)return;loading=false;status.setText(issue.getMessage()==null?"読み込めませんでした":issue.getMessage());empty.setText("再読込でやり直せます。");more.setEnabled(true);});}});
    }
    private void showOwner(MusicEntity current){
        if(NOW.equals(kind))heading.setText("Playlist".equals(owner.type)?"プレイリストの曲一覧":"アルバムの曲一覧");
        name.setText(owner.title);thumbnails.bind(image,owner.artworkUrl);people.removeAllViews();
        List<MusicEntity.ArtistRef> artists=current!=null&&!current.artists.isEmpty()?current.artists:owner.artists;
        if(!ARTIST.equals(kind))for(MusicEntity.ArtistRef person:artists){Button link=button(person.name+" ›",()->artist(this,person.id));link.setContentDescription(person.name+"のアーティストページを開く");people.addView(link,new LinearLayout.LayoutParams(-2,dp(48)));}
        summary.setText(NOW.equals(kind)?"再生中: "+(current==null?"":current.title):ARTIST.equals(kind)&&!owner.hasSelection("PLAY")?"アーティスト · Amazon の選曲でシャッフル":MusicEntity.typeLabel(owner.type));
        boolean random=ARTIST.equals(kind);primary.setText(random?"ランダム再生":owner.hasSelection("PLAY")?"全曲を再生":owner.hasSelection("SHUFFLE")?"シャッフル再生":"おまかせ再生");
        primary.setEnabled(random?(owner.hasSelection("SHUFFLE")||owner.hasSelection("PLAY")):owner.canPlay());
        boolean editable=getIntent().getBooleanExtra("editablePlaylist",false)&&"Playlist".equals(kind)&&(owner.playlistPolicy.rename||owner.playlistPolicy.delete);edit.setVisibility(editable?View.VISIBLE:View.GONE);edit.setEnabled(editable);
    }
    private boolean savedPlayback(){return getIntent().getBooleanExtra("downloaded",false)||getIntent().getBooleanExtra("savedOnly",false)||NOW.equals(kind)&&playback!=null&&playback.requiresSavedAudio();}
    private void playOwner(){if(playback==null||owner==null)return;if(ARTIST.equals(kind))playback.playRandom(owner);else if(savedPlayback())playback.playSavedFromCollection(owner,owner);else playback.play(owner);}
    private void downloadCollection(){
        if(owner==null||ARTIST.equals(kind))return;
        if(OfflineDownloadService.active){startService(new Intent(this,OfflineDownloadService.class).setAction("cancel"));return;}
        pendingDownload=owner;
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},13);return;}
        startDownload();
    }
    private void startDownload(){
        MusicEntity collection=pendingDownload;pendingDownload=null;if(collection==null)return;
        startForegroundService(new Intent(this,OfflineDownloadService.class).putExtra("type",collection.type).putExtra("id",collection.id).putExtra("title",collection.title));
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==13)startDownload();}
    private void updateDownload(){
        if(download==null)return;boolean collection=owner!=null&&("Album".equals(owner.type)||"Playlist".equals(owner.type));
        download.setVisibility(collection?View.VISIBLE:View.GONE);
        if(!collection){downloadStatus.setVisibility(View.GONE);return;}android.content.SharedPreferences prefs=getSharedPreferences("download",0);
        boolean thisCollection=owner.id.equals(prefs.getString("collectionId",""))&&owner.type.equals(prefs.getString("collectionType",""));
        download.setEnabled(!loading&&(!OfflineDownloadService.active||thisCollection));
        setText(download,OfflineDownloadService.active?(thisCollection?"ダウンロードを中止":"別の曲を保存中"):"ダウンロード");
        String message=thisCollection?OfflineDownloadService.displayStatus(prefs.getString("lastStatus","")):"";
        setText(downloadStatus,message);downloadStatus.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);
    }
    private void openItem(MusicEntity entity){
        if("Album".equals(entity.type)||"Playlist".equals(entity.type))collection(this,entity.type,entity.id);
        else if(entity.canPlay()&&playback!=null&&owner!=null){if(savedPlayback())playback.playSavedFromCollection(entity,owner);else playback.playFromCollection(entity,owner);}
    }
    private final class Rows extends BaseAdapter {
        @Override public int getCount(){return items.size();}
        @Override public Object getItem(int position){return items.get(position);}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            Holder h;if(recycled==null){h=new Holder();LinearLayout cell=row();cell.setPadding(dp(16),dp(12),dp(8),dp(12));h.image=artwork();cell.addView(h.image,new LinearLayout.LayoutParams(dp(56),dp(56)));
                LinearLayout detail=column();detail.setPadding(dp(12),0,dp(4),0);h.title=text("",16,Color.WHITE);h.title.setMaxLines(2);h.title.setEllipsize(TextUtils.TruncateAt.END);h.subtitle=text("",12,Color.LTGRAY);h.subtitle.setMaxLines(2);h.subtitle.setEllipsize(TextUtils.TruncateAt.END);detail.addView(h.title);detail.addView(h.subtitle);cell.addView(detail,new LinearLayout.LayoutParams(0,-2,1));h.action=button("",()->{});cell.addView(h.action,new LinearLayout.LayoutParams(dp(80),dp(48)));cell.setTag(h);recycled=cell;}else h=(Holder)recycled.getTag();
            MusicEntity entity=items.get(position);boolean track="Track".equals(entity.type),active=track&&entity.id.equals(highlight);
            h.title.setText((active?"▶ ":track?(position+1)+"　":"")+entity.title);h.title.setTextColor(active?ACCENT:Color.WHITE);
            h.subtitle.setText(entity.subtitle+(track&&entity.durationSeconds>0?" · "+duration(entity.durationSeconds):entity.canSelectTracks()?"　曲を選んで再生":""));
            String artwork=entity.artworkUrl;if(artwork.isEmpty()&&owner!=null&&track)artwork=owner.artworkUrl;thumbnails.bind(h.image,artwork);
            h.action.setText(!track?"開く":entity.hasSelection("PLAY")?"再生":entity.hasSelection("SHUFFLE")?"シャッフル":entity.hasSelection("STATION")?"おまかせ":"再生不可");h.action.setEnabled(!track||entity.canPlay());h.action.setAlpha(h.action.isEnabled()?1f:.4f);h.action.setContentDescription(entity.title+(!track?"の曲一覧を開く":"を再生"));
            h.action.setOnClickListener(v->openItem(entity));recycled.setOnClickListener(v->openItem(entity));recycled.setBackgroundColor(active?0xff243c40:Color.TRANSPARENT);return recycled;
        }
    }
    private static final class Holder{ImageView image;TextView title,subtitle;Button action;}
    private final Runnable tick=new Runnable(){@Override public void run(){
        updateDownload();
        if(playback!=null){
            if(!highlight.equals(playback.contentId)){highlight=playback.contentId;adapter.notifyDataSetChanged();}
            if(NOW.equals(kind)&&!loading&&!playback.busy&&(!playback.contentId.equals(seenTrack)||playback.origin!=seenOrigin))load(false);
        }
        miniPlayer.update(playback);main.postDelayed(this,500);
    }};
    private void setText(TextView v,String text){if(!TextUtils.equals(v.getText(),text))v.setText(text);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(ACCENT);b.setBackgroundColor(Color.TRANSPARENT);b.setPadding(dp(4),0,dp(4),0);b.setOnClickListener(v->action.run());return b;}
    private Button tab(String label,Runnable action){Button b=button(label,action);b.setLayoutParams(new LinearLayout.LayoutParams(0,dp(48),1));return b;}
    private ImageView artwork(){ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.CENTER_CROP);v.setImageResource(R.drawable.ic_music);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);GradientDrawable bg=new GradientDrawable();bg.setColor(0xff28383e);bg.setCornerRadius(dp(8));v.setBackground(bg);v.setClipToOutline(true);return v;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String duration(long seconds){return String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    @Override protected void onStart(){super.onStart();androidx.core.content.ContextCompat.registerReceiver(this,downloadReceiver,new IntentFilter(OfflineDownloadService.STATUS),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);OfflineDownloadService.reconcileStatus(this);long current=PlaylistApi.revision.get();if(playlistRevision!=current){playlistRevision=current;if("Playlist".equals(kind))load(false);}main.post(tick);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==27&&result==RESULT_OK)finish();}
    @Override protected void onStop(){main.removeCallbacks(tick);unregisterReceiver(downloadReceiver);super.onStop();}
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putBoolean("playlists",playlists);}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);work.shutdownNow();thumbnails.close();if(miniPlayer!=null)miniPlayer.close();if(bound)unbindService(connection);super.onDestroy();}
}
