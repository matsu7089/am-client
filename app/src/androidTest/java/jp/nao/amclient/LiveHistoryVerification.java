package jp.nao.amclient;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

final class LiveHistoryVerification {
    static String run(Instrumentation test)throws Exception{
        Context c=test.getTargetContext();AmazonApi api=AmazonApi.get(c);String scope=OfflineStore.scope(api.config().getString("customerId"));HistoryStore store=new HistoryStore(c,scope);
        MusicEntity album=api.metadata("B07BDGWZC7").album;
        MusicEntity artist=api.historyEntity(new MusicEntity(new JSONObject().put("__typename","Artist").put("id","B003WQES5C")));
        CatalogPage page=api.collection("Playlist","B07K3YQT6J","");MusicEntity song=null;
        for(MusicEntity item:page.items)if("B07BDGWZC7".equals(item.id)&&item.hasSelection("PLAY"))song=item;
        if(song==null)throw new AssertionError("Verification playlist track unavailable");final MusicEntity chosenSong=song;
        CountDownLatch connected=new CountDownLatch(1);AtomicReference<PlaybackService> bound=new AtomicReference<>();
        ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){bound.set(((PlaybackService.LocalBinder)b).service());connected.countDown();}public void onServiceDisconnected(ComponentName n){}};
        test.runOnMainSync(()->{c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));c.bindService(new Intent(c,PlaybackService.class).setAction("local"),connection,Context.BIND_AUTO_CREATE);});
        if(!connected.await(10,TimeUnit.SECONDS))throw new AssertionError("Service connection timeout");PlaybackService service=bound.get();
        try{
            test.runOnMainSync(()->service.play(album));awaitSource(test,service,store,album,false);
            test.runOnMainSync(()->service.playRandom(artist));awaitSource(test,service,store,artist,true);
            test.runOnMainSync(()->service.playFromCollection(chosenSong,page.owner));awaitSource(test,service,store,page.owner,false);
            for(MusicEntity item:Arrays.asList(album,page.owner,artist)){
                test.runOnMainSync(()->service.playHistory(item));awaitSource(test,service,store,item,true);
            }
            List<MusicEntity> entries=new HistoryStore(c,scope).list();Set<String> unique=new HashSet<>();
            for(MusicEntity item:entries)if(!unique.add(item.type+":"+item.id))throw new AssertionError("Duplicate history source");
            if(entries.size()>50||!unique.contains(album.type+":"+album.id)||!unique.contains("Artist:"+artist.id)||!unique.contains("Playlist:"+page.owner.id))throw new AssertionError("Missing history source");
            test.runOnMainSync(()->service.player.pause());return "album / playlist / artist recorded after playback, all three replayed with shuffle, deduplicated and moved to the top; count="+entries.size();
        }finally{test.runOnMainSync(()->c.unbindService(connection));}
    }
    private static void awaitSource(Instrumentation test,PlaybackService service,HistoryStore store,MusicEntity source,boolean random)throws Exception{
        await(test,()->{
            List<MusicEntity> entries=store.list();return service.player.isPlaying()&&!service.busy&&service.player.getPlayerError()==null&&(!random||service.shuffleEnabled)&&!entries.isEmpty()&&source.id.equals(entries.get(0).id)&&source.type.equals(entries.get(0).type);
        },"History did not record/replay "+source.type+":"+source.id);
    }
    private static void await(Instrumentation test,BooleanSupplier condition,String message)throws Exception{
        for(int i=0;i<600;i++){AtomicBoolean result=new AtomicBoolean();test.runOnMainSync(()->result.set(condition.getAsBoolean()));if(result.get())return;Thread.sleep(50);}throw new AssertionError(message);
    }
}
