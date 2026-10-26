package jp.nao.amclient;

import androidx.media3.common.Player;
import org.json.JSONObject;

/** Queue and current-track permissions come from Amazon, including Prime All-Access. */
public final class QueueModes {
    public static final QueueModes NONE=new QueueModes(false,false,false);
    public final boolean shuffle,all,one;
    private QueueModes(boolean shuffle,boolean all,boolean one){this.shuffle=shuffle;this.all=all;this.one=one;}
    public static QueueModes from(JSONObject queue,JSONObject edge){
        JSONObject q=queue==null?null:queue.optJSONObject("capabilities"),e=edge==null?null:edge.optJSONObject("capabilities");
        return new QueueModes(allowed(q,"shuffle"),allowed(q,"loopAll"),allowed(e,"loopOne"));
    }
    private static boolean allowed(JSONObject c,String name){JSONObject action=c==null?null:c.optJSONObject(name);return action!=null&&action.optBoolean("isAllowed",false);}
    public boolean allows(int mode){return mode==Player.REPEAT_MODE_OFF||mode==Player.REPEAT_MODE_ALL&&all||mode==Player.REPEAT_MODE_ONE&&one;}
    public int next(int mode){
        if(mode==Player.REPEAT_MODE_OFF)return all?Player.REPEAT_MODE_ALL:one?Player.REPEAT_MODE_ONE:Player.REPEAT_MODE_OFF;
        if(mode==Player.REPEAT_MODE_ALL&&one)return Player.REPEAT_MODE_ONE;
        return Player.REPEAT_MODE_OFF;
    }
    public static String remoteLoop(int mode){
        switch(mode){case Player.REPEAT_MODE_OFF:return "LOOP_OFF";case Player.REPEAT_MODE_ALL:return "LOOP_ALL";case Player.REPEAT_MODE_ONE:return "LOOP_ONE";default:throw new IllegalArgumentException("Unknown repeat mode");}
    }
}
