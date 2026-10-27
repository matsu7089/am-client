package jp.nao.amclient;

import android.content.*;
import org.json.*;
import java.io.IOException;
import java.util.*;

final class HistoryStore {
    static final String CHANGED="jp.nao.amclient.HISTORY_CHANGED";
    private final Context context;
    private final SharedPreferences preferences;
    HistoryStore(Context context){this(context,context.getSharedPreferences("account",0).getString("scope","signed-out"));}
    HistoryStore(Context context,String scope){
        if(scope==null||!scope.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid history account");
        this.context=context.getApplicationContext();preferences=context.getSharedPreferences("playback-history-"+scope,0);
    }
    private JSONArray read(){try{return new JSONArray(preferences.getString("items","[]"));}catch(JSONException invalid){return new JSONArray();}}
    List<MusicEntity> list(){synchronized(HistoryStore.class){return HistoryRecords.list(read());}}
    void record(MusicEntity entity,long time)throws Exception{
        if(!HistoryRecords.supported(entity))return;
        synchronized(HistoryStore.class){if(!preferences.edit().putString("items",HistoryRecords.record(read(),entity,time).toString()).commit())throw new IOException("履歴を保存できませんでした");}
        context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));
    }
    void remove(MusicEntity entity)throws IOException{
        if(!HistoryRecords.supported(entity))return;
        synchronized(HistoryStore.class){if(!preferences.edit().putString("items",HistoryRecords.remove(read(),entity).toString()).commit())throw new IOException("履歴を削除できませんでした");}
        context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));
    }
    boolean refreshArtwork(MusicEntity fresh)throws Exception{
        synchronized(HistoryStore.class){JSONArray entries=read();boolean changed=false;
            for(int i=0;i<entries.length();i++){JSONObject node=entries.optJSONObject(i);if(node!=null)changed|=ArtworkMetadata.update(node,fresh);}
            if(changed&&!preferences.edit().putString("items",entries.toString()).commit())throw new IOException("表紙を保存できませんでした");
            return changed;
        }
    }
}
