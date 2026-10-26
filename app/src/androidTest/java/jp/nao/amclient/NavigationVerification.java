package jp.nao.amclient;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import org.json.JSONObject;
import java.io.File;
import java.nio.*;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Real native activities with a local silent WAV; no catalog/queue requests or preference edits. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class NavigationVerification {
    private final Instrumentation instrumentation;
    private MainActivity main;
    private PlayerActivity detail;
    private PlaybackService service;
    private File fixture;
    private boolean bound;
    private final CountDownLatch connected=new CountDownLatch(1);
    private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){service=((PlaybackService.LocalBinder)binder).service();connected.countDown();}public void onServiceDisconnected(ComponentName name){service=null;}};
    private NavigationVerification(Instrumentation instrumentation){this.instrumentation=instrumentation;}
    static String run(Instrumentation instrumentation)throws Exception{return new NavigationVerification(instrumentation).verify();}
    private String verify()throws Exception{
        Context context=instrumentation.getTargetContext();
        try{
            main=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            instrumentation.runOnMainSync(()->bound=context.bindService(new Intent(context,PlaybackService.class).setAction("local"),connection,Context.BIND_AUTO_CREATE));
            if(!connected.await(5,TimeUnit.SECONDS))throw new AssertionError("Playback service not connected");
            check(()->view(main,"ライブラリタブ").isSelected(),"Initial library destination missing");
            check(()->view(main,"アカウント") instanceof ImageButton,"Account action is not an icon");
            check(()->find(main.getWindow().getDecorView(),v->v instanceof EditText&&v.isShown())==null,"Search field leaked into library");
            check(()->find(main.getWindow().getDecorView(),v->v instanceof MiniPlayerView&&v.isShown())==null,"Empty mini player consumes space");
            click(main,"検索タブ");click(main,"アーティスト");onMain(()->((EditText)find(main.getWindow().getDecorView(),v->v instanceof EditText)).setText("入力途中"));
            click(main,"ダウンロードタブ");click(main,"アルバム");check(()->view(main,"ダウンロードタブ").isSelected()&&view(main,"アルバム").isSelected(),"Download categories lost");
            main=recreate(main,MainActivity.class);check(()->view(main,"ダウンロードタブ").isSelected()&&view(main,"アルバム").isSelected(),"Download destination not restored");
            click(main,"検索タブ");check(()->view(main,"アーティスト").isSelected()&&"入力途中".contentEquals(((EditText)find(main.getWindow().getDecorView(),v->v instanceof EditText)).getText()),"Search draft/category lost across navigation/recreation");
            fixture=new File(context.getCacheDir(),"ui-navigation-verification.wav");silentWav(fixture);
            MusicEntity first=track("ui-fixture-1","検証用の曲","検証アーティスト","ui-artist-1");
            onMain(()->{
                service.contentId=first.id;service.title=first.title;service.artist=first.subtitle;service.artworkUrl="";service.currentTrack=first;service.origin=PlaybackOrigin.of("Playlist","ui-playlist");service.containerType="Playlist";service.canSeek=true;service.canPrevious=false;service.canNext=false;service.modes=QueueModes.NONE;service.message="UI検証";
                service.player.setMediaItem(new MediaItem.Builder().setUri(android.net.Uri.fromFile(fixture)).setMediaId(first.id).setMediaMetadata(new MediaMetadata.Builder().setTitle(first.title).setArtist(first.subtitle).build()).build());service.player.prepare();
            });
            await(()->service.player.getPlaybackState()==Player.STATE_READY,"Local fixture failed to prepare");await(()->find(main.getWindow().getDecorView(),v->v instanceof MiniPlayerView&&v.isShown())!=null,"Mini player did not appear");
            check(()->{MiniPlayerView mini=(MiniPlayerView)view(main,"再生詳細を開く");View nav=view(main,"検索タブ");int[] m=new int[2],n=new int[2];mini.getLocationOnScreen(m);nav.getLocationOnScreen(n);return mini.getHeight()<=PlayerViews.dp(main,80)&&m[1]+mini.getHeight()<=n[1];},"Mini player is oversized or below navigation");
            click(main,"再生");await(()->service.player.isPlaying()&&view(main,"一時停止").isShown(),"Mini player play not dispatched");click(main,"一時停止");await(()->!service.player.getPlayWhenReady(),"Mini player pause not dispatched");onMain(()->service.player.seekTo(23000));
            Instrumentation.ActivityMonitor opened=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);click(main,"再生詳細を開く");detail=(PlayerActivity)opened.waitForActivityWithTimeout(5000);instrumentation.removeMonitor(opened);if(detail==null)throw new AssertionError("Full player not opened");
            await(()->view(detail,first.title).isShown()&&view(detail,"再生位置").isEnabled(),"Player metadata/seek not ready");
            check(()->!view(detail,"シャッフル: OFF").isEnabled()&&!view(detail,"リピート: OFF").isEnabled()&&!view(detail,"次の曲").isEnabled(),"Disallowed commands enabled");
            check(()->view(detail,"シャッフル: OFF") instanceof ImageButton&&view(detail,"リピート: OFF") instanceof ImageButton&&find(detail.getWindow().getDecorView(),v->v instanceof Button&&"アーティスト".contentEquals(((Button)v).getText()))==null,"Mode icons or duplicate artist action incorrect");
            check(()->first.id.equals(service.player.getCurrentMediaItem().mediaId)&&service.player.getCurrentPosition()>=23000&&!service.player.getPlayWhenReady(),"Opening detail replaced source, position or pause state");
            AtomicReference<Intent> intercepted=new AtomicReference<>();Instrumentation.ActivityMonitor links=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(intent.getComponent()!=null&&BrowseActivity.class.getName().equals(intent.getComponent().getClassName())){intercepted.set(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}return null;}};
            instrumentation.addMonitor(links);
            try{
                click(detail,"プレイリストの曲一覧");check(()->intercepted.get()!=null&&"Now".equals(intercepted.get().getStringExtra("kind"))&&"SEARCH".equals(intercepted.get().getStringExtra("section")),"Playlist source route/context lost");
                click(detail,"アルバムの曲一覧");check(()->"Album".equals(intercepted.get().getStringExtra("kind"))&&first.album.id.equals(intercepted.get().getStringExtra("id"))&&"SEARCH".equals(intercepted.get().getStringExtra("section")),"Current track album route/context lost");
                onMain(()->BrowseActivity.collection(detail,"Album",first.album.id,true));check(()->intercepted.get().getBooleanExtra("savedOnly",false)&&!intercepted.get().getBooleanExtra("downloaded",false),"Saved-only album navigation changed playback policy or download index");
                click(detail,"再生中のアーティストを開く");check(()->"Artist".equals(intercepted.get().getStringExtra("kind"))&&"ui-artist-1".equals(intercepted.get().getStringExtra("id")),"Artist route lost");
            }finally{instrumentation.removeMonitor(links);}
            QueueModes allowed=QueueModes.from(new JSONObject("{\"capabilities\":{\"shuffle\":{\"isAllowed\":true},\"loopAll\":{\"isAllowed\":true}}}"),new JSONObject("{\"capabilities\":{\"loopOne\":{\"isAllowed\":true}}}"));
            onMain(()->{service.modes=allowed;service.shuffleEnabled=true;service.repeatMode=Player.REPEAT_MODE_ALL;});await(()->view(detail,"シャッフル: ON").isEnabled()&&view(detail,"リピート: 全曲").isSelected(),"Confirmed modes not displayed");
            onMain(()->service.modeUpdating=true);await(()->!view(detail,"シャッフル: ON").isEnabled(),"Updating controls remain active");check(()->find(detail.getWindow().getDecorView(),v->v.isShown()&&v instanceof TextView&&((TextView)v).getText().toString().contains("再生設定を変更しています"))==null,"Unwanted changing-settings message remains");onMain(()->service.modeUpdating=false);
            onMain(()->{service.busy=true;service.canSeek=false;});await(()->!view(detail,"再生位置").isEnabled()&&!view(detail,"シャッフル: ON").isEnabled(),"Busy commands remain active");onMain(()->{service.busy=false;service.canSeek=true;});
            detail=recreate(detail,PlayerActivity.class);await(()->view(detail,first.title).isShown()&&view(detail,"リピート: 全曲").isSelected(),"Player recreation lost state");
            MusicEntity second=track("ui-fixture-2","次の検証曲","別のアーティスト","ui-artist-2");onMain(()->{service.contentId=second.id;service.title=second.title;service.artist=second.subtitle;service.currentTrack=second;service.player.setMediaItem(new MediaItem.Builder().setUri(android.net.Uri.fromFile(fixture)).setMediaId(second.id).setMediaMetadata(new MediaMetadata.Builder().setTitle(second.title).setArtist(second.subtitle).build()).build());service.player.prepare();});
            await(()->view(detail,second.title).isShown()&&view(detail,"プレイリストの曲一覧").isShown()&&view(detail,"アルバムの曲一覧").isEnabled(),"Next-track metadata/source stale");
            instrumentation.addMonitor(links);try{click(detail,"アルバムの曲一覧");check(()->second.album.id.equals(intercepted.get().getStringExtra("id")),"Album link still points to previous track");}finally{instrumentation.removeMonitor(links);}
            onMain(()->{service.origin=PlaybackOrigin.of(second.album);service.containerType="Album";});await(()->view(detail,"アルバムの曲一覧").isShown()&&view(detail,"リピート: アルバム").isShown()&&find(detail.getWindow().getDecorView(),v->v.isShown()&&v instanceof Button&&"プレイリストの曲一覧".contentEquals(((Button)v).getText()))==null,"Album playback retains playlist links");
            check(()->count(detail.getWindow().getDecorView(),v->v.isShown()&&v instanceof Button&&"アルバムの曲一覧".contentEquals(((Button)v).getText()))==1,"Duplicate album links in album playback");
            click(detail,"再生詳細を閉じる");await(()->view(main,second.title).isShown(),"Mini player metadata stale after detail closed");click(main,"ダウンロードタブ");check(()->view(main,second.title).isShown(),"Mini player disappeared on destination change");
            return "native bottom navigation, search draft/category and download restoration; compact shared player, play/pause; detail preserves source/seek/pause, permissions and confirmed modes; playlist and current-album links, saved-only navigation, next-track album updates, no duplicate album links; artist links and recreation";
        }finally{
            onMain(()->{if(detail!=null&&!detail.isFinishing())detail.finish();if(main!=null&&!main.isFinishing())main.finish();if(service!=null)service.reset();if(bound)context.unbindService(connection);});if(fixture!=null)Files.deleteIfExists(fixture.toPath());
        }
    }
    private MusicEntity track(String id,String title,String artist,String artistId)throws Exception{return new MusicEntity(new JSONObject().put("id",id).put("__typename","Track").put("title",title).put("album",new JSONObject().put("id","album-"+id).put("__typename","Album").put("title","検証アルバム")).put("contributingArtists",new JSONObject("{\"edges\":[{\"node\":{\"id\":\""+artistId+"\",\"name\":\""+artist+"\"}}]}")));}
    private static void silentWav(File file)throws Exception{int length=8000*90*2;ByteBuffer wav=ByteBuffer.allocate(44+length).order(ByteOrder.LITTLE_ENDIAN);wav.put(new byte[]{'R','I','F','F'}).putInt(length+36).put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(length);Files.write(file.toPath(),wav.array());}
    private <T extends Activity>T recreate(T activity,Class<T> type)throws Exception{Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(type.getName(),null,false);onMain(activity::recreate);Activity replacement=monitor.waitForActivityWithTimeout(5000);instrumentation.removeMonitor(monitor);if(replacement==null)throw new AssertionError("Activity not recreated: "+type.getSimpleName());return type.cast(replacement);}
    private void click(Activity activity,String key){onMain(()->{View v=view(activity,key);if(!v.isEnabled()||!v.performClick())throw new AssertionError("Control unavailable: "+key);});}
    private void onMain(Runnable action){AtomicReference<Throwable> failure=new AtomicReference<>();instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable t){failure.set(t);}});if(failure.get()!=null)throw new AssertionError(failure.get());}
    private void check(BooleanSupplier condition,String message){onMain(()->{if(!condition.getAsBoolean())throw new AssertionError(message);});}
    private void await(BooleanSupplier condition,String message)throws Exception{for(int n=0;n<100;n++){AtomicBoolean ready=new AtomicBoolean();onMain(()->{try{ready.set(condition.getAsBoolean());}catch(AssertionError ignored){}});if(ready.get())return;Thread.sleep(50);}throw new AssertionError(message);}
    private View view(Activity activity,String key){View result=find(activity.getWindow().getDecorView(),v->v.isShown()&&(key.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())||v instanceof TextView&&key.contentEquals(((TextView)v).getText())));if(result==null)throw new AssertionError("Missing visible control: "+key);return result;}
    private View find(View root,Predicate<View> matches){if(matches.test(root))return root;if(root instanceof ViewGroup){ViewGroup parent=(ViewGroup)root;for(int n=0;n<parent.getChildCount();n++){View result=find(parent.getChildAt(n),matches);if(result!=null)return result;}}return null;}
    private int count(View root,Predicate<View> matches){int result=matches.test(root)?1:0;if(root instanceof ViewGroup){ViewGroup parent=(ViewGroup)root;for(int n=0;n<parent.getChildCount();n++)result+=count(parent.getChildAt(n),matches);}return result;}
}
