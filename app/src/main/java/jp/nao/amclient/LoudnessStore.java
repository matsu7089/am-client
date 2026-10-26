package jp.nao.amclient;

import android.content.*;
import org.json.JSONObject;
import java.util.*;

/** Bounded cache of measured levels, independent of output target and playback quality. */
final class LoudnessStore {
    private final SharedPreferences prefs;
    LoudnessStore(Context context){prefs=context.getSharedPreferences("loudness-v1",0);}
    double get(String id){try{return prefs.contains(id)?new JSONObject(prefs.getString(id,"")).getDouble("lufs"):Double.NaN;}catch(Exception ignored){return Double.NaN;}}
    void save(NormalizationAudioProcessor.Snapshot value){
        if(value==null||value.id.isEmpty()||value.seconds<30||!NormalizationEngine.validLevel(value.lufs))return;
        try{
            String old=prefs.getString(value.id,"");double seconds=old.isEmpty()?0:new JSONObject(old).optDouble("seconds",0);
            if(value.seconds<seconds+10)return;
            SharedPreferences.Editor edit=prefs.edit().putString(value.id,new JSONObject().put("lufs",value.lufs).put("seconds",value.seconds).put("updated",System.currentTimeMillis()).toString());
            Map<String,?> all=prefs.getAll();if(all.size()>=500&&!all.containsKey(value.id)){
                List<String> keys=new ArrayList<>(all.keySet());keys.sort(Comparator.comparingLong(key->{try{return new JSONObject(String.valueOf(all.get(key))).optLong("updated");}catch(Exception ignored){return 0;}}));
                for(int i=0;i<=keys.size()-500;i++)edit.remove(keys.get(i));
            }
            edit.apply();
        }catch(Exception ignored){android.util.Log.w("AMPlayback","Loudness measurement could not be saved");}
    }
}
