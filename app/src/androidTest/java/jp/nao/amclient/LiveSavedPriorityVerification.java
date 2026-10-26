package jp.nao.amclient;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Audits normal playback routes with real saved audio and unmodified server selections. */
final class LiveSavedPriorityVerification {
    static String run(Instrumentation test)throws Exception{
        Context c=test.getTargetContext();AmazonApi api=AmazonApi.get(c);OfflineStore store=new OfflineStore(c);
        CatalogPage playlist=api.collection("Playlist","B07K3YQT6J","");MusicEntity candidate=null;
        for(MusicEntity item:playlist.items)if("B07BDGWZC7".equals(item.id)&&item.hasSelection("PLAY"))candidate=item;
        if(candidate==null||!store.hasStream(candidate.id))throw new AssertionError("Verification download unavailable");final MusicEntity song=candidate;
        AudioQuality saved=SavedTrack.quality(store.get(song.id)),wanted=saved==AudioQuality.MEDIUM?AudioQuality.LOW:AudioQuality.MEDIUM;
        SharedPreferences prefs=c.getSharedPreferences("quality",0);String original=prefs.getString("playback",null);
        CountDownLatch connected=new CountDownLatch(1);AtomicReference<PlaybackService> bound=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){bound.set(((PlaybackService.LocalBinder)b).service());connected.countDown();}public void onServiceDisconnected(ComponentName n){}};
        test.runOnMainSync(()->{c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));c.bindService(new Intent(c,PlaybackService.class).setAction("local"),connection,Context.BIND_AUTO_CREATE);});
        if(!connected.await(10,TimeUnit.SECONDS))throw new AssertionError("Service connection timeout");PlaybackService service=bound.get();StringBuilder result=new StringBuilder();
        try{
            prefs.edit().putString("playback",wanted.name()).commit();
            probe(test,service,store,"playlist detail row",()->service.playFromCollection(song,playlist.owner),true,result);
            long before=OfflineStore.networkAudioBytes();String description=service.audioDescription();
            test.runOnMainSync(()->service.changeQuality(wanted));
            if(!description.equals(service.audioDescription())||OfflineStore.networkAudioBytes()!=before)throw new AssertionError("Quality setting replaced saved audio");
            probe(test,service,store,"search/library playlist play",()->service.play(playlist.owner),true,result);
            probe(test,service,store,"history playlist random",()->service.playHistory(playlist.owner),true,result);
            before=OfflineStore.networkAudioBytes();String first=service.contentId;
            test.runOnMainSync(()->service.next(false));awaitChanged(test,service,first);assertSaved(service,store);
            String next=service.contentId;test.runOnMainSync(()->service.next(true));awaitChanged(test,service,next);assertSaved(service,store);
            String previous=service.contentId;test.runOnMainSync(()->{service.seek(service.player.getDuration()-2000);c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));});
            awaitChanged(test,service,previous);assertSaved(service,store);
            if(OfflineStore.networkAudioBytes()!=before)throw new AssertionError("Normal queue navigation fetched saved audio");
            result.append("manual next/previous and background natural next: saved, audioNetworkBytes=0; ");
            test.runOnMainSync(()->{service.player.pause();c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));});
            MusicEntity searchSong=null;for(MusicEntity item:api.search("Aoi サカナクション"))if("Track".equals(item.type)&&item.canPlay()){searchSong=item;break;}
            if(searchSong==null)throw new AssertionError("Search track unavailable");final MusicEntity found=searchSong;
            probe(test,service,store,"search track",()->service.play(found),false,result);
            MusicEntity metadata=api.metadata(song.id);CatalogPage album=api.collection("Album",metadata.album.id,"");MusicEntity albumTrack=null;
            for(MusicEntity item:album.items)if(item.id.equals(song.id)&&item.canPlay())albumTrack=item;
            if(albumTrack==null)throw new AssertionError("Album track unavailable");final MusicEntity selectedAlbumTrack=albumTrack;
            probe(test,service,store,"album detail row",()->service.playFromCollection(selectedAlbumTrack,album.owner),false,result);
            probe(test,service,store,"history album random",()->service.playHistory(album.owner),false,result);
            MusicEntity artist=api.historyEntity(new MusicEntity(new JSONObject().put("__typename","Artist").put("id","B003WQES5C")));
            probe(test,service,store,"artist random",()->service.playRandom(artist),false,result);
            probe(test,service,store,"history artist random",()->service.playHistory(artist),false,result);
            return result.toString();
        }finally{
            test.runOnMainSync(()->{service.player.pause();c.unbindService(connection);});SharedPreferences.Editor edit=prefs.edit();if(original==null)edit.remove("playback");else edit.putString("playback",original);edit.commit();
        }
    }
    private static void probe(Instrumentation test,PlaybackService service,OfflineStore store,String name,Runnable play,boolean mustBeSaved,StringBuilder result)throws Exception{
        test.runOnMainSync(service::reset);long previous=-1;int stable=0;
        for(int i=0;i<40&&stable<3;i++){long bytes=OfflineStore.networkAudioBytes();stable=bytes==previous?stable+1:0;previous=bytes;Thread.sleep(50);}
        long before=OfflineStore.networkAudioBytes();test.runOnMainSync(play);
        await(test,()->!service.busy&&service.player.isPlaying()&&service.player.getPlayerError()==null,"Playback did not start: "+name);
        if(service.requiresSavedAudio())throw new AssertionError("Normal route became a saved-only queue: "+name);
        boolean cached=store.hasStream(service.contentId);
        if(mustBeSaved&&!cached)throw new AssertionError("Verification playlist not fully saved");
        if(cached){
            assertSaved(service,store);
            if(OfflineStore.networkAudioBytes()!=before)throw new AssertionError("Saved source fetched audio: "+name);
            // Confirm the protected portion for the guaranteed on-demand playlist track.
            if(mustBeSaved&&service.contentId.equals("B07BDGWZC7")){
                test.runOnMainSync(()->service.seek(65000));await(test,()->service.player.isPlaying()&&service.player.getCurrentPosition()>68000,"Protected saved playback failed");
                if(OfflineStore.networkAudioBytes()!=before)throw new AssertionError("Protected playback fetched audio: "+name);
            }
        }else if(!"ストリーミング".equals(service.message)||OfflineStore.networkAudioBytes()<=before)throw new AssertionError("Unsaved source did not stream: "+name);
        result.append(name).append(": ").append(cached?"saved, audioNetworkBytes=0":"unsaved, streaming").append("; ");
    }
    private static void assertSaved(PlaybackService service,OfflineStore store)throws Exception{
        if(!"保存音源".equals(service.message)||!service.audioDescription().equals(SavedTrack.quality(store.get(service.contentId)).kbps+" kbps"))throw new AssertionError("Saved audio/quality not used");
    }
    private static void awaitChanged(Instrumentation test,PlaybackService service,String old)throws Exception{
        await(test,()->!old.equals(service.contentId)&&!service.busy&&service.player.isPlaying()&&!service.requiresSavedAudio(),"Normal queue navigation did not complete");
    }
    private static void await(Instrumentation test,BooleanSupplier condition,String message)throws Exception{
        for(int i=0;i<600;i++){AtomicBoolean result=new AtomicBoolean();test.runOnMainSync(()->result.set(condition.getAsBoolean()));if(result.get())return;Thread.sleep(50);}throw new AssertionError(message);
    }
}
