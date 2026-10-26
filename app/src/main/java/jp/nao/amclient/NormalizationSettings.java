package jp.nao.amclient;

import android.app.*;
import android.content.*;
import android.widget.*;
import java.util.Locale;

final class NormalizationSettings {
    static boolean enabled(Context c){return c.getSharedPreferences("normalization",0).getBoolean("enabled",true);}
    static int target(Context c){return NormalizationEngine.clampTarget(c.getSharedPreferences("normalization",0).getInt("target",NormalizationEngine.DEFAULT_TARGET));}
    static String description(Context c){return enabled(c)?"ON · "+target(c)+" LUFS":"OFF";}
    static void show(Activity activity,PlaybackService service){
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);int p=Math.round(24*activity.getResources().getDisplayMetrics().density);body.setPadding(p,p/2,p,p/2);
        Switch toggle=new Switch(activity);toggle.setText("音量ノーマライズ");toggle.setChecked(enabled(activity));body.addView(toggle);
        TextView label=new TextView(activity);label.setTextSize(18);label.setPadding(0,p,0,p/2);body.addView(label);
        SeekBar level=new SeekBar(activity);level.setMax(NormalizationEngine.MAX_TARGET-NormalizationEngine.MIN_TARGET);level.setProgress(target(activity)-NormalizationEngine.MIN_TARGET);level.setContentDescription("ノーマライズの基準音量");body.addView(level);
        TextView help=new TextView(activity);help.setPadding(0,p/2,0,p/2);help.setText("数値を小さくすると音量が小さくなります。\n初めての曲は再生しながら音量を測り、徐々に調整します。端末の音量も併用できます。");body.addView(help);
        Runnable render=()->{label.setText(String.format(Locale.ROOT,"基準音量: %d LUFS",level.getProgress()+NormalizationEngine.MIN_TARGET));level.setEnabled(toggle.isChecked());label.setEnabled(toggle.isChecked());};
        Runnable save=()->{activity.getSharedPreferences("normalization",0).edit().putBoolean("enabled",toggle.isChecked()).putInt("target",level.getProgress()+NormalizationEngine.MIN_TARGET).apply();if(service!=null)service.refreshNormalization();render.run();};
        toggle.setOnCheckedChangeListener((view,checked)->save.run());
        level.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int progress,boolean user){render.run();if(user)save.run();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        render.run();new AlertDialog.Builder(activity).setTitle("音量ノーマライズ").setView(body).setPositiveButton("閉じる",null).show();
    }
}
