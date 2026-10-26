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
}
