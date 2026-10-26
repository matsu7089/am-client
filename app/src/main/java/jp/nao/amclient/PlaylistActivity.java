package jp.nao.amclient;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Editing is entered only from the owned playlists in the library. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class PlaylistActivity extends Activity {
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThumbnailLoader thumbnails=new ThumbnailLoader();
    private String id="";
    private boolean busy,adding,bound;
    private int generation;
    private CatalogPage page;
    private List<MusicEntity> found=Collections.emptyList();
    private TextView title,status,empty;
    private EditText query,newName;
    private LinearLayout create,search,tabs;
    private Button tracksTab,addTab,searchButton,createButton,menu,reload;
    private Rows rows;
    private MiniPlayerView miniPlayer;
    private PlaybackService playback;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){playback=((PlaybackService.LocalBinder)binder).service();miniPlayer.update(playback);}
        @Override public void onServiceDisconnected(ComponentName name){playback=null;miniPlayer.update(null);}
    };
    static void open(Context context,String id){Intent intent=BottomNavigationView.inherit(context,new Intent(context,PlaylistActivity.class).putExtra("id",id));if(context instanceof BrowseActivity)((Activity)context).startActivityForResult(intent,27);else context.startActivity(intent);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);id=saved==null?getIntent().getStringExtra("id"):saved.getString("id","");if(id==null)id="";
        getWindow().setStatusBarColor(PlayerViews.BG);getWindow().setNavigationBarColor(PlayerViews.BG);
        LinearLayout root=PlayerViews.column(this);PlayerViews.insets(root);
        LinearLayout header=PlayerViews.row(this);header.addView(button("‹ 戻る",this::finish),size(72,52));TextView heading=text("プレイリスト編集",18);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));menu=button("メニュー",this::menu);header.addView(menu,size(80,52));root.addView(header);
        title=text("新しいプレイリスト",22);title.setPadding(dp(16),dp(12),dp(16),dp(12));title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);root.addView(title);
        create=PlayerViews.column(this);create.setPadding(dp(16),0,dp(16),dp(8));newName=input("プレイリスト名");create.addView(newName,size(-1,52));TextView privacy=text("非公開のプレイリストを作成します",12);privacy.setTextColor(PlayerViews.MUTED);create.addView(privacy);createButton=button("作成",this::create);create.addView(createButton,size(-1,48));root.addView(create);
        tabs=PlayerViews.row(this);tracksTab=button("曲一覧",()->switchMode(false));addTab=button("曲を追加",()->switchMode(true));reload=button("再読込",this::load);tabs.addView(tracksTab,new LinearLayout.LayoutParams(0,dp(48),1));tabs.addView(addTab,new LinearLayout.LayoutParams(0,dp(48),1));tabs.addView(reload,size(72,48));root.addView(tabs);
        search=PlayerViews.row(this);search.setPadding(dp(12),0,dp(8),0);query=input("追加する曲を検索");query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.addView(query,new LinearLayout.LayoutParams(0,dp(52),1));searchButton=button("検索",this::search);search.addView(searchButton,size(64,52));query.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEARCH){search();return true;}return false;});root.addView(search);
        status=text("",13);status.setTextColor(PlayerViews.MUTED);status.setPadding(dp(16),dp(8),dp(16),dp(8));root.addView(status);
        FrameLayout body=new FrameLayout(this);ListView list=new ListView(this);list.setDivider(new android.graphics.drawable.ColorDrawable(0xff26343b));list.setDividerHeight(dp(1));rows=new Rows();list.setAdapter(rows);body.addView(list,new FrameLayout.LayoutParams(-1,-1));empty=text("",15);empty.setTextColor(PlayerViews.MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(24),dp(24),dp(24));body.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        miniPlayer=new MiniPlayerView(this);root.addView(miniPlayer,new LinearLayout.LayoutParams(-1,-2));root.addView(new BottomNavigationView(this,BottomNavigationView.Section.LIBRARY,section->BottomNavigationView.open(this,section)));setContentView(root);
        bound=bindService(new Intent(this,PlaybackService.class).setAction("local"),connection,BIND_AUTO_CREATE);
        if(saved!=null){newName.setText(saved.getString("newName",""));query.setText(saved.getString("query",""));adding=saved.getBoolean("adding");}
        render();if(!id.isEmpty())load();
    }
    private PlaylistApi api(){return new PlaylistApi(AmazonApi.get(this));}
    private interface Job{void run()throws Exception;}
    private void run(String label,Job job,Runnable done){
        if(busy)return;busy=true;int token=++generation;status.setText(label);render();
        work.execute(()->{try{job.run();main.post(()->{if(isDestroyed()||token!=generation)return;busy=false;render();done.run();});}
            catch(Exception failure){main.post(()->{if(isDestroyed()||token!=generation)return;busy=false;render();status.setText(failure.getMessage()==null?"操作を完了できませんでした。再読込してください":failure.getMessage());});}});
    }
    private void load(){if(id.isEmpty())return;final CatalogPage[] result=new CatalogPage[1];run("曲一覧を読み込んでいます…",()->result[0]=api().open(id),()->{page=result[0];render();status.setText(getString(R.string.playlist_track_count,page.items.size()));});}
    private void create(){final String value;try{value=PlaylistPolicy.title(newName.getText().toString());}catch(IllegalArgumentException invalid){newName.setError(invalid.getMessage());return;}hideKeyboard(newName);final String[] created=new String[1];run("作成しています…",()->created[0]=api().create(value),()->{id=created[0];getIntent().putExtra("id",id);load();});}
    private void switchMode(boolean value){if(busy||page==null||value&&!page.owner.playlistPolicy.add)return;adding=value;render();status.setText(adding?"追加する曲を検索してください":getString(R.string.playlist_track_count,page.items.size()));}
    private void search(){String value=query.getText().toString().trim();if(value.isEmpty()){query.setError("曲名やアーティスト名を入力してください");return;}hideKeyboard(query);final List<MusicEntity> result=new ArrayList<>();run("検索しています…",()->result.addAll(SearchCategory.TRACKS.filter(AmazonApi.get(this).search(value))),()->{found=result;render();status.setText(getString(R.string.playlist_search_count,found.size()));});}
    private void change(String message,Job mutation){
        final CatalogPage[] updated=new CatalogPage[1];run("変更を保存しています…",()->{mutation.run();updated[0]=api().open(id);},()->{page=updated[0];render();status.setText(getString(R.string.playlist_change_count,message,page.items.size()));});
    }
    private void menu(){
        if(busy||page==null)return;List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        if(page.owner.playlistPolicy.rename){labels.add("名前を変更");actions.add(this::rename);}
        if(page.owner.playlistPolicy.delete){labels.add("プレイリストを削除");actions.add(this::delete);}
        new AlertDialog.Builder(this).setTitle(page.owner.title).setItems(labels.toArray(new String[0]),(d,w)->actions.get(w).run()).setNegativeButton("閉じる",null).show();
    }
    private void rename(){
        EditText field=input("プレイリスト名");field.setText(page.owner.title);field.setSelectAllOnFocus(true);LinearLayout box=PlayerViews.column(this);box.setPadding(dp(20),0,dp(20),0);box.addView(field,size(-1,56));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("名前を変更").setView(box).setNegativeButton("キャンセル",null).setPositiveButton("保存",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{String value;try{value=PlaylistPolicy.title(field.getText().toString());}catch(IllegalArgumentException invalid){field.setError(invalid.getMessage());return;}dialog.dismiss();change("名前を変更しました",()->api().rename(id,value));}));dialog.show();
    }
    private void delete(){
        new AlertDialog.Builder(this).setTitle("プレイリストを削除").setMessage("「"+page.owner.title+"」をAmazon Musicから削除します。この操作は取り消せません。").setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->run("削除しています…",()->api().delete(id),()->{Toast.makeText(this,"プレイリストを削除しました",Toast.LENGTH_SHORT).show();setResult(RESULT_OK);finish();})).show();
    }
    private void editTrack(MusicEntity track){
        if(busy||page==null)return;int index;try{index=PlaylistPolicy.index(page.items,track.playlistEntryId);}catch(IllegalArgumentException invalid){status.setText(invalid.getMessage());return;}
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();PlaylistPolicy policy=page.owner.playlistPolicy;
        if(policy.reorder&&index>0){labels.add("上へ移動");actions.add(()->change("曲順を変更しました",()->api().move(id,track.playlistEntryId,-1)));}
        if(policy.reorder&&index<page.items.size()-1){labels.add("下へ移動");actions.add(()->change("曲順を変更しました",()->api().move(id,track.playlistEntryId,1)));}
        if(policy.remove){labels.add("プレイリストから削除");actions.add(()->new AlertDialog.Builder(this).setTitle("曲を削除").setMessage("「"+track.title+"」をこのプレイリストから削除します。").setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->change("曲を削除しました",()->api().remove(id,track.playlistEntryId))).show());}
        if(!labels.isEmpty())new AlertDialog.Builder(this).setTitle((index+1)+". "+track.title).setItems(labels.toArray(new String[0]),(d,w)->actions.get(w).run()).setNegativeButton("閉じる",null).show();
    }
    private void render(){
        boolean existing=!id.isEmpty();create.setVisibility(existing?View.GONE:View.VISIBLE);tabs.setVisibility(existing?View.VISIBLE:View.GONE);search.setVisibility(existing&&adding?View.VISIBLE:View.GONE);
        if(page!=null)title.setText(page.owner.title);else if(existing)title.setText("プレイリスト");
        menu.setVisibility(existing?View.VISIBLE:View.GONE);PlayerViews.enabled(menu,!busy&&page!=null&&(page.owner.playlistPolicy.rename||page.owner.playlistPolicy.delete));PlayerViews.enabled(createButton,!busy);newName.setEnabled(!busy);
        PlayerViews.enabled(tracksTab,!busy&&page!=null);PlayerViews.enabled(addTab,!busy&&page!=null&&page.owner.playlistPolicy.add);PlayerViews.enabled(reload,!busy);query.setEnabled(!busy);PlayerViews.enabled(searchButton,!busy);
        tracksTab.setSelected(!adding);addTab.setSelected(adding);tracksTab.setBackgroundColor(!adding?0xff243c40:Color.TRANSPARENT);addTab.setBackgroundColor(adding?0xff243c40:Color.TRANSPARENT);
        empty.setText(!existing?"":page==null?"再読込でやり直せます。":adding?"曲名やアーティスト名を入力して検索してください。":"曲はまだありません。「曲を追加」から追加できます。");rows.notifyDataSetChanged();
    }
    private final class Rows extends BaseAdapter{
        private List<MusicEntity> items(){return adding?found:page==null?Collections.emptyList():page.items;}
        @Override public int getCount(){return items().size();}
        @Override public Object getItem(int position){return items().get(position);}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            Holder h;if(recycled==null){h=new Holder();LinearLayout row=PlayerViews.row(PlaylistActivity.this);row.setPadding(dp(16),dp(10),dp(8),dp(10));h.image=PlayerViews.artwork(PlaylistActivity.this,8);row.addView(h.image,size(48,48));LinearLayout info=PlayerViews.column(PlaylistActivity.this);info.setPadding(dp(12),0,dp(4),0);h.title=text("",16);h.title.setMaxLines(2);h.title.setEllipsize(TextUtils.TruncateAt.END);h.subtitle=text("",12);h.subtitle.setTextColor(PlayerViews.MUTED);h.subtitle.setMaxLines(2);h.subtitle.setEllipsize(TextUtils.TruncateAt.END);info.addView(h.title);info.addView(h.subtitle);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));h.action=button("",()->{});row.addView(h.action,size(64,48));row.setTag(h);recycled=row;}else h=(Holder)recycled.getTag();
            MusicEntity track=items().get(position);h.title.setText(adding?track.title:getString(R.string.playlist_numbered_track,position+1,track.title));h.subtitle.setText(track.subtitle);thumbnails.bind(h.image,track.artworkUrl);h.action.setText(adding?"追加":"編集");
            boolean permitted=page!=null&&(adding?page.owner.playlistPolicy.add:!track.playlistEntryId.isEmpty()&&(page.owner.playlistPolicy.remove||page.owner.playlistPolicy.reorder));PlayerViews.enabled(h.action,!busy&&permitted);h.action.setContentDescription(track.title+(adding?"をプレイリストに追加":"の登録を編集"));h.action.setOnClickListener(v->{if(adding)change("「"+track.title+"」を追加しました",()->api().add(id,track.id));else editTrack(track);});recycled.setOnClickListener(v->{if(!adding)editTrack(track);});return recycled;
        }
    }
    private static final class Holder{ImageView image;TextView title,subtitle;Button action;}
    private int dp(int value){return PlayerViews.dp(this,value);}
    private LinearLayout.LayoutParams size(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private TextView text(String value,int size){return PlayerViews.text(this,value,size,Color.WHITE);}
    private Button button(String label,Runnable click){return PlayerViews.button(this,label,click);}
    private EditText input(String hint){EditText field=new EditText(this);field.setSingleLine(true);field.setTextColor(Color.WHITE);field.setHintTextColor(PlayerViews.MUTED);field.setHint(hint);field.setTextSize(16);return field;}
    private void hideKeyboard(EditText field){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(),0);field.clearFocus();}
    private final Runnable tick=new Runnable(){@Override public void run(){miniPlayer.update(playback);main.postDelayed(this,500);}};
    @Override protected void onStart(){super.onStart();main.post(tick);}
    @Override protected void onStop(){main.removeCallbacks(tick);super.onStop();}
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putString("id",id);saved.putString("newName",newName.getText().toString());saved.putString("query",query.getText().toString());saved.putBoolean("adding",adding);}
    @Override protected void onDestroy(){++generation;main.removeCallbacksAndMessages(null);work.shutdown();thumbnails.close();miniPlayer.close();if(bound)unbindService(connection);super.onDestroy();}
}
