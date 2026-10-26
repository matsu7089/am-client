package jp.nao.amclient;

import android.content.Context;
import org.json.JSONObject;

final class HistoryVerification {
    static void run(Context context)throws Exception{
        String a="history-verification-a",b="history-verification-b";
        try{
            clear(context,a);clear(context,b);HistoryStore first=new HistoryStore(context,a),second=new HistoryStore(context,b);
            MusicEntity album=new MusicEntity(new JSONObject().put("__typename","Album").put("id","album").put("title","Album"));
            MusicEntity artist=new MusicEntity(new JSONObject().put("__typename","Artist").put("id","artist").put("name","Artist"));
            first.record(album,10);first.record(artist,20);first.record(album,30);
            HistoryStore reopened=new HistoryStore(context,a);
            if(reopened.list().size()!=2||!"album".equals(reopened.list().get(0).id)||!second.list().isEmpty())throw new AssertionError("History persistence, order, or account isolation failed");
        }finally{clear(context,a);clear(context,b);}
    }
    private static void clear(Context context,String scope){context.getSharedPreferences("playback-history-"+scope,0).edit().clear().commit();}
}
