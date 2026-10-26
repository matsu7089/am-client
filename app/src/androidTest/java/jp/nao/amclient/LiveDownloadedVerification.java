package jp.nao.amclient;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Uses existing downloads only; never creates or removes user audio. */
final class LiveDownloadedVerification {
    static String run(Instrumentation test)throws Exception{
        Context c=test.getTargetContext();AmazonApi api=AmazonApi.get(c);OfflineStore store=new OfflineStore(c);CatalogPage page=api.collection("Playlist","B07K3YQT6J","");
        MusicEntity target=null;for(MusicEntity item:page.items)if("B07BDGWZC7".equals(item.id)&&item.hasSelection("PLAY"))target=item;
        if(target==null||!store.hasStream(target.id))throw new AssertionError("Verification download unavailable");final MusicEntity song=target;
        AudioQuality actual=SavedTrack.quality(store.get(song.id)),requested=actual==AudioQuality.HIGH?AudioQuality.MEDIUM:AudioQuality.HIGH;
        SharedPreferences prefs=c.getSharedPreferences("quality",0);String original=prefs.getString("playback",null);
        CountDownLatch connected=new CountDownLatch(1);AtomicReference<PlaybackService> bound=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){bound.set(((PlaybackService.LocalBinder)b).service());connected.countDown();}public void onServiceDisconnected(ComponentName n){}};
        test.runOnMainSync(()->{c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));c.bindService(new Intent(c,PlaybackService.class).setAction("local"),connection,Context.BIND_AUTO_CREATE);});
        if(!connected.await(10,TimeUnit.SECONDS))throw new AssertionError("Service connection timeout");PlaybackService service=bound.get();
        try{
            prefs.edit().putString("playback",requested.name()).commit();long before=OfflineStore.networkAudioBytes();
            test.runOnMainSync(()->service.playSavedFromCollection(song,page.owner));awaitPlaying(test,service);
            check(actual.kbps+" kbps",service.audioDescription());test.runOnMainSync(()->service.seek(65000));
            await(test,()->service.player.isPlaying()&&service.player.getCurrentPosition()>68000,"Saved encrypted playback failed");
            test.runOnMainSync(()->service.changeQuality(requested));
            check(actual.kbps+" kbps",service.audioDescription());
            String first=service.contentId;test.runOnMainSync(()->service.next(false));
            await(test,()->!first.equals(service.contentId)&&service.player.isPlaying()&&!service.busy&&service.requiresSavedAudio(),"Next left the saved queue");
            AudioQuality nextQuality=SavedTrack.quality(store.get(service.contentId));check(nextQuality.kbps+" kbps",service.audioDescription());
            test.runOnMainSync(()->service.playSavedFromCollection(page.owner,page.owner));awaitPlaying(test,service);
            String last=service.contentId;test.runOnMainSync(()->{service.seek(service.player.getDuration()-2000);c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));});
            await(test,()->!last.equals(service.contentId)&&service.player.isPlaying()&&!service.busy&&service.requiresSavedAudio(),"Natural advance left the saved queue");
            AudioQuality afterQuality=SavedTrack.quality(store.get(service.contentId));check(afterQuality.kbps+" kbps",service.audioDescription());
            if(OfflineStore.networkAudioBytes()!=before)throw new AssertionError("Downloaded queue fetched audio");
            return "downloaded playlist row / whole playlist / manual next / background natural next use saved qualities despite "+requested+" streaming preference; audioNetworkBytes=0";
        }finally{
            test.runOnMainSync(()->{service.player.pause();c.unbindService(connection);});SharedPreferences.Editor edit=prefs.edit();if(original==null)edit.remove("playback");else edit.putString("playback",original);edit.commit();
        }
    }
    private static void check(String expected,String actual){if(!expected.equals(actual))throw new AssertionError("Expected "+expected+", got "+actual);}
    private static void awaitPlaying(Instrumentation test,PlaybackService s)throws Exception{await(test,()->!s.busy&&s.player.isPlaying()&&s.requiresSavedAudio(),"Downloaded source did not start");}
    private static void await(Instrumentation test,BooleanSupplier condition,String message)throws Exception{
        for(int i=0;i<600;i++){AtomicBoolean result=new AtomicBoolean();test.runOnMainSync(()->result.set(condition.getAsBoolean()));if(result.get())return;Thread.sleep(50);}throw new AssertionError(message);
    }
}
