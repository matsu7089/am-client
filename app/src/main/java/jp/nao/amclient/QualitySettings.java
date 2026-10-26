package jp.nao.amclient;

import android.app.*;
import android.content.Context;

final class QualitySettings {
    static AudioQuality playback(Context c){return get(c,"playback");}
    static AudioQuality download(Context c){return get(c,"download");}
    private static AudioQuality get(Context c,String key){return AudioQuality.parse(c.getSharedPreferences("quality",0).getString(key,""),AudioQuality.MEDIUM);}
    static void set(Context c,String key,AudioQuality value){c.getSharedPreferences("quality",0).edit().putString(key,value.name()).apply();}
    static void show(Activity activity,PlaybackService service){
        new AlertDialog.Builder(activity).setTitle("再生設定").setItems(new String[]{"再生音質: "+playback(activity).description(),"保存音質: "+download(activity).description(),"音量ノーマライズ: "+NormalizationSettings.description(activity)},(d,which)->{
            if(which==2){NormalizationSettings.show(activity,service);return;}
            String key=which==0?"playback":"download";AudioQuality selected=which==0?playback(activity):download(activity);
            String[] labels=new String[AudioQuality.values().length];for(AudioQuality q:AudioQuality.values())labels[q.ordinal()]=q.description();
            AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(which==0?"再生音質":"保存音質").setSingleChoiceItems(labels,selected.ordinal(),(choice,index)->{
                AudioQuality quality=AudioQuality.values()[index];
                if(which==0&&service!=null){if(!service.changeQuality(quality))return;}else set(activity,key,quality);
                choice.dismiss();
            }).setNegativeButton("閉じる",null).create();
            if(which==1){android.widget.TextView title=new android.widget.TextView(activity);title.setText("保存音質\n次のダウンロードから適用します。保存済みの曲は、元の曲一覧から再ダウンロードで変更できます。");title.setTextSize(16);int padding=Math.round(24*activity.getResources().getDisplayMetrics().density);title.setPadding(padding,padding,padding,padding/2);dialog.setCustomTitle(title);}
            dialog.show();
        }).setNegativeButton("閉じる",null).show();
    }
}
