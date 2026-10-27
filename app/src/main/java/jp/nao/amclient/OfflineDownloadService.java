package jp.nao.amclient;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.dash.offline.DashDownloader;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Saves ciphertext; playback still needs the normal online queue and license. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class OfflineDownloadService extends Service {
    public static final String STATUS="jp.nao.amclient.DOWNLOAD_STATUS";
    public static volatile boolean active;
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile DashDownloader downloader;
    private volatile boolean cancelled;
    private boolean busy;
    private int latestStartId;
    private String collectionName="";
    @Override public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("download","楽曲の保存",NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        latestStartId=startId;
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("cancel".equals(intent.getAction())){cancelled=true;if(downloader!=null)downloader.cancel();if(!busy)stopSelf();return START_NOT_STICKY;}
        if(busy)return START_NOT_STICKY;
        synchronized(OfflineStore.class){busy=true;active=true;cancelled=false;}
        collectionName=intent.getStringExtra("title");if(collectionName==null)collectionName="曲一覧";
        getSharedPreferences("download",0).edit().putString("collectionId",intent.getStringExtra("id")).putString("collectionType",intent.getStringExtra("type")).apply();
        startForeground(200,notification("曲一覧を確認しています",0,0));
        status(collectionName+" · 曲一覧を確認しています",true);
        String type=intent.getStringExtra("type"),id=intent.getStringExtra("id");
        AudioQuality quality=QualitySettings.download(this);
        work.execute(()->download(type,id,quality));return START_NOT_STICKY;
    }
    private void checkCancelled()throws InterruptedIOException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");}
    private void download(String type,String id,AudioQuality quality){
        int saved=0,existing=0,failed=0,unavailable=0,total=0;
        try{
            if(!("Album".equals(type)||"Playlist".equals(type))||id==null||id.isEmpty())throw new IOException("アルバムかプレイリストを開いてやり直してください。");
            AmazonApi api=AmazonApi.get(this);String scope=OfflineStore.scope(api.config().getString("customerId"));
            getSharedPreferences("account",0).edit().putString("scope",scope).apply();OfflineStore store=new OfflineStore(this);
            LinkedHashMap<String,MusicEntity> tracks=new LinkedHashMap<>();Set<String> visited=new HashSet<>();String cursor="";MusicEntity owner=null;
            do{
                checkCancelled();CatalogPage page=api.collection(type,id,cursor);if(owner==null)owner=page.owner;
                for(MusicEntity track:page.items)if("Track".equals(track.type)&&!track.id.isEmpty())tracks.putIfAbsent(track.id,track);
                cursor=page.cursor;if(!cursor.isEmpty()&&!visited.add(cursor))throw new IOException("曲一覧の追加ページを読み込めませんでした。");
            }while(!cursor.isEmpty());
            total=tracks.size();if(total==0)throw new IOException("保存できる曲がありません。");
            store.rememberCollection(owner,new ArrayList<>(tracks.values()));
            for(MusicEntity track:tracks.values()){
                checkCancelled();
                if(store.matches(track.id,quality)){store.refreshStreamContext(track,owner);existing++;continue;}
                if(!track.canPlay()){unavailable++;continue;}
                int done=saved+existing+failed+unavailable;boolean staged=false;String variant=store.newVariant();
                try{
                    status(collectionName+" · "+quality.description()+" · "+(done+1)+" / "+total+"曲を保存中: "+track.title,true);
                    store.cleanup(this,track.id);
                    DashPackage dash=new DashPackage(api.manifest(track.id),quality);checkCancelled();
                    staged=true;java.io.File manifest=store.stage(track.id,variant,dash.xml);
                    MediaItem media=new MediaItem.Builder().setUri(android.net.Uri.fromFile(manifest))
                        .setMimeType(MimeTypes.APPLICATION_MPD).setStreamKeys(Collections.singletonList(dash.streamKey)).build();
                    downloader=new DashDownloader(media,store.streamSource(this,track.id,variant,true));
                    final int completed=done,count=total;final long[] bytes={0};final int[] shown={-1};
                    downloader.download((length,downloaded,percent)->{
                        bytes[0]=downloaded;if(cancelled){downloader.cancel();return;}
                        int progress=Math.max(0,(int)percent);if(progress!=shown[0]){shown[0]=progress;getSystemService(NotificationManager.class).notify(200,notification(track.title,completed*100+progress,count*100));}
                    });
                    checkCancelled();store.commit(track,owner,bytes[0],dash,variant);staged=false;saved++;
                    try{store.cleanup(this,track.id);}catch(Exception ignored){}
                }catch(Exception failure){
                    if(staged)try{store.discardVariant(this,track.id,variant);}catch(Exception ignored){}
                    checkCancelled();failed++;
                    if(BuildConfig.DEBUG)android.util.Log.i("AMDownload","Track save failed: "+track.id+" "+failure.getClass().getSimpleName());
                }finally{downloader=null;}
            }
            status(collectionName+" · "+quality.description()+" · 保存済み "+(saved+existing)+" / "+total+"曲"+(unavailable>0?"（再生不可 "+unavailable+"曲）":"")+(failed>0?"（失敗 "+failed+"曲）":""),false);
        }catch(Exception issue){
            status(cancelled||issue instanceof InterruptedIOException?"保存を中止しました · 保存済み "+(saved+existing)+" / "+total+"曲":issue.getMessage()==null?"保存できませんでした。接続と空き容量を確認してください。":issue.getMessage(),false);
        }finally{main.post(()->{busy=false;active=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelfResult(latestStartId);});}
    }
    private Notification notification(String text,int progress,int max){
        PendingIntent cancel=PendingIntent.getService(this,1,new Intent(this,OfflineDownloadService.class).setAction("cancel"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class).putExtra("showDownloads",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"download").setSmallIcon(R.drawable.ic_music).setContentTitle("AM Client · "+collectionName).setContentText(text)
            .setContentIntent(open).setOngoing(true).setProgress(max,progress,max==0).addAction(new Notification.Action.Builder(null,"中止",cancel).build()).build();
    }
    private void status(String text,boolean running){getSharedPreferences("download",0).edit().putString("lastStatus",text).putBoolean("active",running).apply();sendBroadcast(new Intent(STATUS).setPackage(getPackageName()).putExtra("active",running));}
    static String displayStatus(String text){return text.replace("再生には通信が必要です。","");}
    static void reconcileStatus(Context context){SharedPreferences prefs=context.getSharedPreferences("download",0);if(!active&&prefs.getBoolean("active",false))prefs.edit().putBoolean("active",false).putString("lastStatus","保存が中断されました。元の一覧から再試行してください。保存済みの曲は引き継ぎます。").apply();}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onTimeout(int startId,int type){cancelled=true;if(downloader!=null)downloader.cancel();stopSelf();}
    @Override public void onDestroy(){cancelled=true;active=false;if(downloader!=null)downloader.cancel();work.shutdownNow();if(getSharedPreferences("download",0).getBoolean("active",false))status("保存が中断されました。元の一覧から再試行できます。",false);super.onDestroy();}
}
