package jp.nao.amclient;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.exoplayer.dash.offline.DashDownloader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Explicit opt-in integration check using the account's ordinary API and Android CDM. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class LiveQualityVerification {
    static String run(Instrumentation test)throws Exception{
        Context c=test.getTargetContext();AmazonApi api=AmazonApi.get(c);OfflineStore store=new OfflineStore(c);
        CatalogPage page=api.collection("Playlist","B07K3YQT6J","");MusicEntity track=null;
        for(MusicEntity item:page.items)if("B07BDGWZC7".equals(item.id)&&item.hasSelection("PLAY"))track=item;
        if(track==null||!page.owner.hasSelection("PLAY"))throw new AssertionError("On-demand verification track unavailable");
        CountDownLatch connected=new CountDownLatch(1);AtomicReference<PlaybackService> bound=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){bound.set(((PlaybackService.LocalBinder)binder).service());connected.countDown();}public void onServiceDisconnected(ComponentName name){}};
        test.runOnMainSync(()->{c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));c.bindService(new Intent(c,PlaybackService.class).setAction("local"),connection,Context.BIND_AUTO_CREATE);});
        if(!connected.await(10,TimeUnit.SECONDS))throw new AssertionError("Service connection timeout");PlaybackService service=bound.get();
        StringBuilder evidence=new StringBuilder();
        try{
            QualitySettings.set(c,"playback",AudioQuality.LOW);
            for(AudioQuality quality:new AudioQuality[]{AudioQuality.HIGH,AudioQuality.MEDIUM}){
                String variant=store.newVariant();DashPackage dash=new DashPackage(api.manifest(track.id),quality);
                boolean committed=false;
                try{
                    java.io.File file=store.stage(track.id,variant,dash.xml);
                    MediaItem media=new MediaItem.Builder().setUri(Uri.fromFile(file)).setMimeType(MimeTypes.APPLICATION_MPD).setStreamKeys(Collections.singletonList(dash.streamKey)).build();
                    DashDownloader downloader=new DashDownloader(media,store.streamSource(c,track.id,variant,true));long[] bytes={0};downloader.download((length,downloaded,percent)->bytes[0]=downloaded);
                    store.commit(track,page.owner,bytes[0],dash,variant);committed=true;store.cleanup(c,track.id);
                    // A replacement must not interrupt a reader of the previous saved variant.
                    if(quality==AudioQuality.MEDIUM)await(test,()->service.player.isPlaying()&&service.audioDescription().equals("320 kbps"),"Replacing saved audio interrupted current playback");
                    JSONObject saved=store.get(track.id);long before=OfflineStore.networkAudioBytes();test.runOnMainSync(()->service.playOffline(saved));
                    final String description=quality.kbps+" kbps";
                    await(test,()->service.player.getPlaybackState()==Player.STATE_READY&&service.player.isPlaying()&&service.audioDescription().equals(description)&&!service.busy,"Saved quality did not start");
                    test.runOnMainSync(()->service.seek(65000));
                    await(test,()->service.player.getCurrentPosition()>68000&&service.player.isPlaying()&&service.player.getPlayerError()==null,"Encrypted saved playback failed");
                    if(OfflineStore.networkAudioBytes()!=before)throw new AssertionError("Saved audio fetched network bytes");
                    evidence.append(quality).append(" ").append(dash.format.bitrate).append(" bps, ").append(bytes[0]).append(" saved bytes, position>68s, audioNetworkBytes=0; ");
                }catch(Throwable failure){if(!committed)store.discardVariant(c,track.id,variant);throw failure;}
            }
            test.runOnMainSync(()->service.player.pause());c.getSharedPreferences("quality",0).edit().putString("playback","MEDIUM").putString("download","MEDIUM").commit();
            store.cleanup(c,track.id);return evidence.toString();
        }finally{test.runOnMainSync(()->c.unbindService(connection));}
    }
    private static void await(Instrumentation test,BooleanSupplier condition,String message)throws Exception{
        for(int i=0;i<400;i++){AtomicBoolean result=new AtomicBoolean();test.runOnMainSync(()->result.set(condition.getAsBoolean()));if(result.get())return;Thread.sleep(50);}throw new AssertionError(message);
    }
}
