package jp.nao.amclient;

import android.app.Instrumentation;
import android.content.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Explicit real-account check: standard CDM playback, no recording or new audio downloads. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class LiveNormalizationVerification {
    static String run(Instrumentation test)throws Exception{
        Context c=test.getTargetContext();AmazonApi api=AmazonApi.get(c);OfflineStore store=new OfflineStore(c);
        CatalogPage page=api.collection("Playlist","B07K3YQT6J","");MusicEntity candidate=null;
        for(MusicEntity item:page.items)if(item.id.equals("B07BDGWZC7")&&item.hasSelection("PLAY"))candidate=item;
        if(candidate==null||!store.hasStream(candidate.id))throw new AssertionError("Saved fixture unavailable");final MusicEntity song=candidate;
        SharedPreferences prefs=c.getSharedPreferences("normalization",0);Map<String,?> original=prefs.getAll();
        CountDownLatch connected=new CountDownLatch(1);AtomicReference<PlaybackService> bound=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,android.os.IBinder b){bound.set(((PlaybackService.LocalBinder)b).service());connected.countDown();}public void onServiceDisconnected(ComponentName n){}};
        test.runOnMainSync(()->{c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));c.bindService(new Intent(c,PlaybackService.class).setAction("local"),connection,Context.BIND_AUTO_CREATE);});
        if(!connected.await(10,TimeUnit.SECONDS))throw new AssertionError("Service timeout");PlaybackService service=bound.get();StringBuilder evidence=new StringBuilder();
        try{
            settings(test,prefs,service,true,-20);long before=OfflineStore.networkAudioBytes();test.runOnMainSync(()->service.playFromCollection(song,page.owner));
            await(test,()->ready(service)&&service.normalizationSnapshot().id.equals(song.id)&&service.normalizationSnapshot().seconds>3,"Saved normalization inactive");
            test.runOnMainSync(()->{service.seek(65000);c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));});
            await(test,()->ready(service)&&service.player.getCurrentPosition()>68000&&service.normalizationSnapshot().seconds>=32,"Background/protected PCM measurement failed");
            if(!"保存音源".equals(service.message)||OfflineStore.networkAudioBytes()!=before)throw new AssertionError("Saved preference regressed");
            NormalizationAudioProcessor.Snapshot level=service.normalizationSnapshot();
            evidence.append("saved/background/protected: frames=").append(level.frames).append(", LUFS=").append(level.lufs).append(", gain=").append(level.gainDb).append(", audioNetworkBytes=0; ");
            settings(test,prefs,service,true,-26);
            await(test,()->ready(service)&&service.normalizationSnapshot().target==-26&&service.normalizationSnapshot().gainDb<level.gainDb-4,"Lower target did not attenuate");
            evidence.append("live target -26 applied; ");
            String first=service.contentId;test.runOnMainSync(()->service.next(false));
            await(test,()->ready(service)&&!service.contentId.equals(first)&&service.normalizationSnapshot().id.equals(service.contentId)&&service.normalizationSnapshot().frames>48000,"Next track normalization did not reset");
            if(OfflineStore.networkAudioBytes()!=before)throw new AssertionError("Saved next fetched audio");
            if(!NormalizationEngine.validLevel(new LoudnessStore(c).get(song.id)))throw new AssertionError("Played track level not learned");
            evidence.append("next track reset and level cache persisted; ");
            MusicEntity search=null;for(MusicEntity item:api.search("光の中へ 結束バンド"))if("Track".equals(item.type)&&item.canPlay()&&!store.hasStream(item.id)){search=item;break;}
            if(search==null)throw new AssertionError("Unsaved verification track unavailable");final MusicEntity found=search;
            test.runOnMainSync(()->{service.player.pause();c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));service.play(found);});
            await(test,()->ready(service)&&!service.contentId.equals(first)&&service.normalizationSnapshot().id.equals(service.contentId)&&service.normalizationSnapshot().frames>48000&&"ストリーミング".equals(service.message),"Streaming normalization inactive");
            if(OfflineStore.networkAudioBytes()<=before)throw new AssertionError("Streaming verification used no stream");
            evidence.append("streaming PCM normalized; ");
            test.runOnMainSync(()->service.player.pause());settings(test,prefs,service,false,-26);
            evidence.append("pause/settings preserve source and playback quality; ");return evidence.toString();
        }finally{
            test.runOnMainSync(()->{service.player.pause();SharedPreferences.Editor edit=prefs.edit().clear();if(original.containsKey("enabled"))edit.putBoolean("enabled",(Boolean)original.get("enabled"));if(original.containsKey("target"))edit.putInt("target",(Integer)original.get("target"));edit.commit();service.refreshNormalization();c.unbindService(connection);});
        }
    }
    private static boolean ready(PlaybackService s){return !s.busy&&s.player.isPlaying()&&s.player.getPlayerError()==null&&s.normalizationSnapshot()!=null;}
    private static void settings(Instrumentation test,SharedPreferences prefs,PlaybackService service,boolean enabled,int target){test.runOnMainSync(()->{prefs.edit().putBoolean("enabled",enabled).putInt("target",target).commit();service.refreshNormalization();});}
    private static void await(Instrumentation test,BooleanSupplier condition,String error)throws Exception{for(int i=0;i<1100;i++){AtomicBoolean pass=new AtomicBoolean();test.runOnMainSync(()->pass.set(condition.getAsBoolean()));if(pass.get())return;Thread.sleep(50);}throw new AssertionError(error);}
}
