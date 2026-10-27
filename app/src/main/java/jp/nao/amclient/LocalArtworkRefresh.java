package jp.nao.amclient;

import android.content.Context;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Repairs legacy local playlist covers without downloading or changing any audio. */
final class LocalArtworkRefresh implements AutoCloseable {
    private final Context context;
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<String> attempted=new HashSet<>();
    private volatile boolean closed;
    LocalArtworkRefresh(Context context){this.context=context.getApplicationContext();}
    private String scope(){return context.getSharedPreferences("account",0).getString("scope","signed-out");}
    void refresh(List<MusicEntity> items,Runnable changed){
        if(closed)return;String account=scope();OfflineStore saved=new OfflineStore(context);HistoryStore history=new HistoryStore(context);
        for(MusicEntity item:items)if("Playlist".equals(item.type)&&item.artworkVersion<ArtworkMetadata.VERSION&&attempted.add(account+":"+item.id))work.execute(()->{
            if(closed||!account.equals(scope()))return;
            try{
                JSONObject node=AmazonApi.get(context).gql("NativeHistoryPlaylist",new JSONObject().put("id",item.id)).optJSONObject("playlist");
                if(node==null||!item.id.equals(node.optString("id"))||!"Playlist".equals(node.optString("__typename")))return;
                MusicEntity fresh=new MusicEntity(node);if(fresh.artworkUrl.isEmpty()||!account.equals(scope()))return;
                boolean updated=saved.refreshArtwork(fresh);updated|=history.refreshArtwork(fresh);
                if(updated)main.post(()->{if(!closed&&account.equals(scope()))changed.run();});
            }catch(Exception unavailable){/* Keep existing artwork; retry on a later activity instance. */}
        });
    }
    @Override public void close(){closed=true;work.shutdownNow();main.removeCallbacksAndMessages(null);}
}
