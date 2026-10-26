package jp.nao.amclient;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class MainActivity extends Activity {
    private static final int BG=0xff101619,ACCENT=0xff70e5d5;
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThumbnailLoader thumbnails=new ThumbnailLoader();
    private final Map<SearchCategory,Button> categoryButtons=new EnumMap<>(SearchCategory.class);
    private final Map<LibraryCategory,CatalogPage> libraryPages=new EnumMap<>(LibraryCategory.class);
    private final Map<LibraryCategory,Button> libraryButtons=new EnumMap<>(LibraryCategory.class);
    private PlaybackService playback;
    private TextView state,empty,downloadStatus,heading;
    private EditText input;
    private LinearLayout categories,libraryCategories,searchBar;
    private CatalogAdapter adapter;
    private Button more,createPlaylist;
    private long playlistRevision=PlaylistApi.revision.get();
    private MiniPlayerView miniPlayer;
    private BottomNavigationView navigation;
    private boolean downloads,bound,searchMode,loading,showDownloadStatus,searchLoaded;
    private SearchCategory category=SearchCategory.TRACKS;
    private LibraryCategory libraryCategory=LibraryCategory.HISTORY;
    private SearchCategory searchCategory=SearchCategory.TRACKS,downloadCategory=SearchCategory.TRACKS;
    private List<MusicEntity> searchResults=Collections.emptyList();
    private String query="";
    private int requestGeneration;
    private final BroadcastReceiver downloadReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){showDownloadStatus=true;if(downloads&&!intent.getBooleanExtra("active",false))renderDownloads();refreshDownloadStatus();}
    };
    private final BroadcastReceiver historyReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){if(showingHistory())renderHistory();}};
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){playback=((PlaybackService.LocalBinder)binder).service();miniPlayer.update(playback);}
        public void onServiceDisconnected(ComponentName name){playback=null;miniPlayer.update(null);}
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=column();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        LinearLayout top=row();heading=text("ライブラリ",24,Color.WHITE);heading.setPadding(dp(16),dp(12),0,dp(12));top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));top.addView(PlayerViews.iconButton(this,PlayerViews.Icon.SETTINGS,"再生設定",()->QualitySettings.show(this,playback)),new LinearLayout.LayoutParams(dp(48),dp(48)));top.addView(PlayerViews.iconButton(this,PlayerViews.Icon.ACCOUNT,"アカウント",this::account),new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(top);
        searchBar=row();searchBar.setPadding(dp(12),0,dp(8),dp(4));input=new EditText(this);input.setSingleLine(true);input.setTextColor(Color.WHITE);input.setHintTextColor(PlayerViews.MUTED);input.setTextSize(16);input.setHint("曲・アルバム・アーティストを検索");input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBar.addView(input,new LinearLayout.LayoutParams(0,dp(52),1));searchBar.addView(PlayerViews.iconButton(this,PlayerViews.Icon.SEARCH,"検索する",()->search(input.getText().toString())),new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(searchBar);
        input.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEARCH){search(input.getText().toString());return true;}return false;});
        categories=row();for(SearchCategory value:SearchCategory.values()){
            Button button=tab(value.label,()->{category=value;showDownloadStatus=false;refreshDownloadStatus();if(downloads){downloadCategory=value;renderDownloads();}else if(searchMode){searchCategory=value;updateTabs();if(!loading)renderSearch();}});button.setPadding(dp(4),0,dp(4),0);categoryButtons.put(value,button);categories.addView(button);
        }root.addView(categories);categories.setVisibility(View.GONE);
        libraryCategories=row();for(LibraryCategory value:LibraryCategory.values()){Button button=tab(value.label,()->{libraryCategory=value;showDownloadStatus=false;loadLibrary(false);});libraryButtons.put(value,button);libraryCategories.addView(button);}root.addView(libraryCategories);
        createPlaylist=button("＋ プレイリストを作成",()->PlaylistActivity.open(this,""));createPlaylist.setContentDescription("プレイリストを作成");root.addView(createPlaylist,new LinearLayout.LayoutParams(-1,dp(48)));createPlaylist.setVisibility(View.GONE);
        state=text("読み込んでいます…",13,Color.LTGRAY);state.setPadding(dp(16),dp(8),dp(16),dp(8));root.addView(state);
        downloadStatus=text("",13,ACCENT);downloadStatus.setPadding(dp(16),0,dp(16),dp(8));downloadStatus.setVisibility(View.GONE);root.addView(downloadStatus);
        FrameLayout content=new FrameLayout(this);ListView results=new ListView(this);results.setDivider(new android.graphics.drawable.ColorDrawable(0xff26343b));results.setDividerHeight(dp(1));results.setCacheColorHint(BG);
        adapter=new CatalogAdapter();results.setAdapter(adapter);content.addView(results,new FrameLayout.LayoutParams(-1,-1));
        empty=text("",16,Color.LTGRAY);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(20),dp(24),dp(20));content.addView(empty,new FrameLayout.LayoutParams(-1,-1));results.setEmptyView(empty);
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        more=button("さらに表示",()->loadLibrary(true));root.addView(more);more.setVisibility(View.GONE);
        miniPlayer=new MiniPlayerView(this);root.addView(miniPlayer,new LinearLayout.LayoutParams(-1,-2));
        navigation=new BottomNavigationView(this,BottomNavigationView.Section.LIBRARY,this::selectSection);root.addView(navigation);setContentView(root);
        bound=bindService(new Intent(this,PlaybackService.class).setAction("local"),connection,BIND_AUTO_CREATE);
        if(saved!=null){input.setText(saved.getString("input",""));libraryCategory=LibraryCategory.restore(saved.getString("libraryCategory","HISTORY"));try{searchCategory=SearchCategory.valueOf(saved.getString("searchCategory","TRACKS"));downloadCategory=SearchCategory.valueOf(saved.getString("downloadCategory","TRACKS"));if(downloadCategory==SearchCategory.ARTISTS)downloadCategory=SearchCategory.TRACKS;}catch(IllegalArgumentException ignored){} }
        if(getIntent().getBooleanExtra("showDownloads",false))downloadList();else if(getIntent().hasExtra("destination"))selectSection(BottomNavigationView.Section.parse(getIntent().getStringExtra("destination")));else if(saved!=null&&saved.getBoolean("search")){query=saved.getString("query","");openSearch();}else if(saved!=null&&saved.getBoolean("downloads"))downloadList();else library();
    }
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextColor(ACCENT);b.setTextSize(13);b.setBackgroundColor(Color.TRANSPARENT);b.setOnClickListener(v->action.run());return b;}
    private Button tab(String title,Runnable action){Button b=button(title,action);b.setLayoutParams(new LinearLayout.LayoutParams(0,dp(48),1));return b;}
    private ImageView artwork(int size){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageResource(R.drawable.ic_music);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable background=new GradientDrawable();background.setColor(0xff28383e);background.setCornerRadius(dp(8));image.setBackground(background);image.setClipToOutline(true);image.setMinimumWidth(dp(size));return image;}
    private boolean showingHistory(){return !downloads&&!searchMode&&libraryCategory==LibraryCategory.HISTORY;}
    private void updateTabs(){boolean library=!downloads&&!searchMode;categories.setVisibility(library?View.GONE:View.VISIBLE);libraryCategories.setVisibility(library?View.VISIBLE:View.GONE);searchBar.setVisibility(searchMode?View.VISIBLE:View.GONE);
        createPlaylist.setVisibility(library&&libraryCategory==LibraryCategory.PLAYLISTS?View.VISIBLE:View.GONE);
        BottomNavigationView.Section section=downloads?BottomNavigationView.Section.DOWNLOADS:searchMode?BottomNavigationView.Section.SEARCH:BottomNavigationView.Section.LIBRARY;getIntent().putExtra("section",section.name());getIntent().removeExtra("destination");navigation.select(section);heading.setText(section.label);
        for(Map.Entry<LibraryCategory,Button> entry:libraryButtons.entrySet()){boolean selected=entry.getKey()==libraryCategory;entry.getValue().setSelected(selected);entry.getValue().setTextColor(selected?ACCENT:Color.LTGRAY);entry.getValue().setBackgroundColor(selected?0xff243c40:Color.TRANSPARENT);}
        for(Map.Entry<SearchCategory,Button> entry:categoryButtons.entrySet()){
        entry.getValue().setVisibility(searchMode||entry.getKey()!=SearchCategory.ARTISTS?View.VISIBLE:View.GONE);
        boolean selected=entry.getKey()==category;entry.getValue().setSelected(selected);entry.getValue().setTextColor(selected?ACCENT:Color.LTGRAY);entry.getValue().setBackgroundColor(selected?0xff243c40:Color.TRANSPARENT);
    }}
    private interface Loader{List<MusicEntity> load()throws Exception;}
    private void load(String label,boolean isSearch,Loader loader){downloads=false;searchMode=isSearch;refreshDownloadStatus();loading=true;int token=++requestGeneration;categories.setVisibility(View.VISIBLE);more.setVisibility(View.GONE);updateTabs();state.setText(label+"を読み込んでいます…");empty.setText("");adapter.setEntities(Collections.emptyList());
        work.execute(()->{try{List<MusicEntity> items=loader.load();main.post(()->{if(isDestroyed()||token!=requestGeneration)return;loading=false;if(isSearch){searchResults=items;searchLoaded=true;renderSearch();}else{state.setText(label+" · "+items.size()+"件");empty.setText("表示できる項目がありません");adapter.setEntities(items);}});}
        catch(Exception issue){main.post(()->{if(token!=requestGeneration||isDestroyed())return;loading=false;state.setText(issue.getMessage());empty.setText("アカウントからログインして、再試行してください。");});}});
    }
    private void renderSearch(){List<MusicEntity> items=category.filter(searchResults);updateTabs();state.setText(query.isEmpty()?"曲・アルバム・プレイリスト・アーティスト": "検索結果 · "+category.label+" · "+items.size()+"件");empty.setText(query.isEmpty()?"曲名・アルバム名・アーティスト名を入力して検索できます。":"一致する"+category.label+"はありません");adapter.setEntities(items);}
    private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();}
    private void selectSection(BottomNavigationView.Section section){switch(section){case SEARCH:openSearch();break;case LIBRARY:library();break;case DOWNLOADS:downloadList();break;}}
    private void openSearch(){if(searchMode)return;downloads=false;searchMode=true;loading=false;++requestGeneration;category=searchCategory;more.setVisibility(View.GONE);showDownloadStatus=false;refreshDownloadStatus();if(!query.isEmpty()&&!searchLoaded)search(query);else renderSearch();}
    private void search(String value){if(value.trim().isEmpty()){query="";searchLoaded=false;searchResults=Collections.emptyList();downloads=false;searchMode=true;loading=false;++requestGeneration;category=searchCategory;more.setVisibility(View.GONE);hideKeyboard();renderSearch();return;}showDownloadStatus=false;refreshDownloadStatus();category=searchCategory;query=value.trim();String requestedQuery=query;searchLoaded=false;searchResults=Collections.emptyList();hideKeyboard();load("検索結果",true,()->AmazonApi.get(this).search(requestedQuery));}
    private void library(){hideKeyboard();showDownloadStatus=false;refreshDownloadStatus();loadLibrary(false);}
    private void loadLibrary(boolean append){
        downloads=false;searchMode=false;refreshDownloadStatus();updateTabs();final LibraryCategory requested=libraryCategory;
        final CatalogPage old=libraryPages.get(requested);final int token=++requestGeneration;
        if(requested==LibraryCategory.HISTORY){loading=false;renderHistory();return;}
        if(!append&&old!=null){loading=false;renderLibrary(old);return;}
        if(append&&(old==null||old.cursor.isEmpty()))return;
        loading=true;more.setVisibility(append?View.VISIBLE:View.GONE);more.setEnabled(false);
        state.setText("ライブラリ · "+requested.label+"を読み込んでいます…");if(!append){empty.setText("");adapter.setEntities(Collections.emptyList());}
        work.execute(()->{try{
            AmazonApi api=AmazonApi.get(this);JSONObject c=api.config();getSharedPreferences("account",0).edit().putString("scope",OfflineStore.scope(c.getString("customerId"))).apply();
            CatalogPage page=api.library(requested.source,append?old.cursor:"");
            CatalogPage result=append?new CatalogPage(null,CatalogPage.append(old.items,page.items),page.cursor):page;
            main.post(()->{if(isDestroyed()||token!=requestGeneration)return;loading=false;libraryPages.put(requested,result);renderLibrary(result);});
        }catch(Exception issue){main.post(()->{if(isDestroyed()||token!=requestGeneration)return;loading=false;state.setText(issue.getMessage());empty.setText("ライブラリを読み込めませんでした。ライブラリタブから再試行してください。");more.setEnabled(true);});}});
    }
    private void renderLibrary(CatalogPage page){state.setText("ライブラリ · "+libraryCategory.label+" · "+page.items.size()+"件");empty.setText("登録済みの"+libraryCategory.label+"はありません");adapter.setEntities(page.items);more.setEnabled(true);more.setVisibility(page.cursor.isEmpty()?View.GONE:View.VISIBLE);}
    private void renderHistory(){List<MusicEntity> items=new HistoryStore(this).list();updateTabs();more.setVisibility(View.GONE);state.setText("ライブラリ · 履歴 · "+items.size()+"件");empty.setText("再生履歴はまだありません。\nアルバム・プレイリスト・アーティストを再生すると、ここに表示されます。");adapter.setHistory(items);}
    private void downloadList(){hideKeyboard();getIntent().removeExtra("showDownloads");downloads=true;searchMode=false;category=downloadCategory;loading=false;++requestGeneration;categories.setVisibility(View.VISIBLE);more.setVisibility(View.GONE);renderDownloads();refreshDownloadStatus();}
    private void renderDownloads(){
        OfflineStore store=new OfflineStore(this);updateTabs();int count;
        if(category==SearchCategory.TRACKS){List<JSONObject> items=store.list();count=items.size();adapter.setSaved(items);}
        else{List<MusicEntity> items=store.collections(category.type);count=items.size();adapter.setEntities(items);}
        state.setText("ダウンロード · "+category.label+" · "+count+"件");empty.setText("保存済みの"+category.label+"はありません。\nアルバム・プレイリストの曲一覧からダウンロードできます。");
    }
    private void refreshDownloadStatus(){String last=OfflineDownloadService.displayStatus(getSharedPreferences("download",0).getString("lastStatus",""));downloadStatus.setVisibility(!last.isEmpty()&&(downloads||showDownloadStatus||OfflineDownloadService.active)?View.VISIBLE:View.GONE);setTextIfChanged(downloadStatus,last);}
    private class CatalogAdapter extends BaseAdapter {
        private List<MusicEntity> entries=Collections.emptyList();private List<JSONObject> saved=Collections.emptyList();private boolean offlineRows,historyRows;
        void setEntities(List<MusicEntity> items){offlineRows=false;historyRows=false;entries=items;saved=Collections.emptyList();notifyDataSetChanged();}
        void setHistory(List<MusicEntity> items){offlineRows=false;historyRows=true;entries=items;saved=Collections.emptyList();notifyDataSetChanged();}
        void setSaved(List<JSONObject> items){offlineRows=true;historyRows=false;saved=items;entries=Collections.emptyList();notifyDataSetChanged();}
        @Override public int getCount(){return offlineRows?saved.size():entries.size();}
        @Override public Object getItem(int position){return offlineRows?saved.get(position):entries.get(position);}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View recycled,android.view.ViewGroup parent){
            RowHolder holder;if(recycled==null){holder=new RowHolder();LinearLayout item=row();item.setPadding(dp(16),dp(12),dp(8),dp(12));holder.image=artwork(64);item.addView(holder.image,new LinearLayout.LayoutParams(dp(64),dp(64)));
                LinearLayout details=column();details.setPadding(dp(12),0,dp(4),0);holder.title=text("",17,Color.WHITE);holder.title.setMaxLines(2);holder.title.setEllipsize(TextUtils.TruncateAt.END);holder.subtitle=text("",12,Color.LTGRAY);holder.subtitle.setMaxLines(2);holder.subtitle.setEllipsize(TextUtils.TruncateAt.END);details.addView(holder.title);details.addView(holder.subtitle);item.addView(details,new LinearLayout.LayoutParams(0,-2,1));
                holder.play=button("再生",()->{});item.addView(holder.play,new LinearLayout.LayoutParams(dp(64),dp(56)));item.setTag(holder);recycled=item;
            }else holder=(RowHolder)recycled.getTag();
            final Runnable action;Runnable replay=null;String title,subtitle,url;boolean selectable=false;
            if(offlineRows){JSONObject item=saved.get(position);title=item.optString("title");subtitle=item.optString("artist")+(SavedTrack.isStream(item)?" · 保存音源 · "+SavedTrack.quality(item).kbps+" kbps":"");url=item.optString("artwork");action=()->{if(playback!=null)playback.playOffline(item);};}
            else{MusicEntity entity=entries.get(position);title=entity.title;subtitle=entity.subtitle;url=entity.artworkUrl;selectable=entity.canSelectTracks();boolean openCollection="Album".equals(entity.type)||"Playlist".equals(entity.type);action=()->{if("Artist".equals(entity.type))BrowseActivity.artist(MainActivity.this,entity.id);else if(openCollection){if(downloads)BrowseActivity.savedCollection(MainActivity.this,entity.type,entity.id);else if(!searchMode&&!historyRows&&entity.ownedPlaylist)BrowseActivity.ownedPlaylist(MainActivity.this,entity.id);else BrowseActivity.collection(MainActivity.this,entity.type,entity.id);}else if(playback!=null)playback.play(entity);};
                if(historyRows){String type=MusicEntity.typeLabel(entity.type);subtitle=type+(type.equals(subtitle)?"":" · "+subtitle);replay=()->{if(playback!=null)playback.playHistory(entity);};}}
            holder.title.setText(title);
            if(selectable){SpannableString description=new SpannableString(subtitle+"　曲を選んで再生");description.setSpan(new ForegroundColorSpan(ACCENT),subtitle.length()+1,description.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);holder.subtitle.setText(description);}
            else holder.subtitle.setText(subtitle);
            boolean artistRow=!offlineRows&&"Artist".equals(entries.get(position).type),collectionRow=!offlineRows&&("Album".equals(entries.get(position).type)||"Playlist".equals(entries.get(position).type));holder.play.setText(historyRows?"ランダム再生":artistRow||collectionRow?"開く":"再生");holder.play.setLayoutParams(new LinearLayout.LayoutParams(dp(historyRows?112:64),dp(56)));holder.play.setContentDescription(title+(historyRows?"をランダム再生":artistRow?"のアーティストページを開く":collectionRow?"の曲一覧を開く":"を再生"));final Runnable buttonAction=replay==null?action:replay;holder.play.setOnClickListener(v->buttonAction.run());recycled.setOnClickListener(v->action.run());final JSONObject savedItem=offlineRows?saved.get(position):null;recycled.setOnLongClickListener(savedItem!=null&&SavedTrack.isStream(savedItem)?v->{deleteSaved(savedItem);return true;}:null);thumbnails.bind(holder.image,url);return recycled;
        }
    }
    private static final class RowHolder {ImageView image;TextView title,subtitle;Button play;}
    private void deleteSaved(JSONObject item){
        new AlertDialog.Builder(this).setTitle("保存した音声データを削除").setMessage(item.optString("title"))
            .setPositiveButton("削除",(dialog,which)->{if(OfflineDownloadService.active){Toast.makeText(this,"保存の完了後に削除してください",Toast.LENGTH_SHORT).show();return;}
                if(playback!=null&&item.optString("id").equals(playback.contentId))playback.reset();
                work.execute(()->{try{new OfflineStore(this).removeStream(this,item.getString("id"));main.post(()->{if(!isDestroyed()&&downloads)renderDownloads();});}
                    catch(Exception issue){main.post(()->{if(!isDestroyed())Toast.makeText(this,"削除できませんでした",Toast.LENGTH_LONG).show();});}});
            }).setNegativeButton("戻る",null).show();
    }
    private void account(){new AlertDialog.Builder(this).setTitle("Amazon Music")
        .setItems(new String[]{"ログイン / アカウントを変更","アプリについて"},(d,w)->{if(w==0){if(playback!=null)playback.reset();stopService(new Intent(this,OfflineDownloadService.class));startActivityForResult(new Intent(this,LoginActivity.class),10);}else new AlertDialog.Builder(this).setTitle("AM Client "+BuildConfig.VERSION_NAME)
            .setMessage("検索・一覧・音声再生はネイティブ実装です。ログイン時のみ Amazon の認証画面を使います。\n\nアルバム・プレイリストから暗号化された音声を保存できます。保存音源の再生時も認証・ライセンス取得・再生状態の送信に通信を使います。完全オフラインでは再生できません。")
            .setPositiveButton("閉じる",null).show();}).setNegativeButton("閉じる",null).show();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);String section=getIntent().getStringExtra("section");setIntent(intent);getIntent().putExtra("section",section);if(intent.getBooleanExtra("showDownloads",false))downloadList();else if(intent.hasExtra("destination"))selectSection(BottomNavigationView.Section.parse(intent.getStringExtra("destination")));}
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);if(code==10&&result==RESULT_OK){libraryPages.clear();searchResults=Collections.emptyList();searchLoaded=false;query="";input.setText("");library();}}
    private void setTextIfChanged(TextView view,String value){if(!TextUtils.equals(view.getText(),value))view.setText(value);}
    private void refreshPlaylists(){long current=PlaylistApi.revision.get();if(playlistRevision==current)return;playlistRevision=current;libraryPages.remove(LibraryCategory.PLAYLISTS);if(!downloads&&!searchMode&&libraryCategory==LibraryCategory.PLAYLISTS)loadLibrary(false);}
    private final Runnable tick=new Runnable(){public void run(){refreshPlaylists();miniPlayer.update(playback);main.postDelayed(this,500);}};
    @Override protected void onStart(){super.onStart();androidx.core.content.ContextCompat.registerReceiver(this,downloadReceiver,new IntentFilter(OfflineDownloadService.STATUS),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);androidx.core.content.ContextCompat.registerReceiver(this,historyReceiver,new IntentFilter(HistoryStore.CHANGED),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);OfflineDownloadService.reconcileStatus(this);refreshPlaylists();refreshDownloadStatus();if(downloads)renderDownloads();else if(showingHistory())renderHistory();main.post(tick);}
    @Override protected void onStop(){main.removeCallbacks(tick);unregisterReceiver(downloadReceiver);unregisterReceiver(historyReceiver);super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("input",input.getText().toString());out.putBoolean("search",searchMode);out.putBoolean("downloads",downloads);out.putString("query",query);out.putString("searchCategory",searchCategory.name());out.putString("libraryCategory",libraryCategory.name());out.putString("downloadCategory",downloadCategory.name());}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);thumbnails.close();miniPlayer.close();if(bound)unbindService(connection);work.shutdownNow();super.onDestroy();}
}
