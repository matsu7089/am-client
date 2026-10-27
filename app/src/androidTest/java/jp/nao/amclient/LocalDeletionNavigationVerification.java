package jp.nao.amclient;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Actual long presses and dialogs against isolated local fixtures, with account scope restored. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class LocalDeletionNavigationVerification {
    private final Instrumentation test;private MainActivity activity;
    private LocalDeletionNavigationVerification(Instrumentation test){this.test=test;}
    static String run(Instrumentation test)throws Exception{return new LocalDeletionNavigationVerification(test).run();}
    private String run()throws Exception{
        Context c=test.getTargetContext();String scope="offline-deletion-ui";SharedPreferences account=c.getSharedPreferences("account",0);boolean existed=account.contains("scope");String original=account.getString("scope","");
        try{
            OfflineDeletionVerification.clear(c,scope);OfflineDeletionVerification.Fixture f=new OfflineDeletionVerification.Fixture(c,scope);HistoryStore history=new HistoryStore(c,scope);c.getSharedPreferences("playback-history-"+scope,0).edit().clear().commit();
            history.record(f.album,1);history.record(f.playlist,2);history.record(OfflineDeletionVerification.entity("Artist","delete-artist","削除テストアーティスト"),3);account.edit().putString("scope",scope).commit();
            activity=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->view(f.album.title)!=null,"History fixture not visible");longPress(f.album.title+"をランダム再生");dialog("キャンセル");check(history.list().size()==3,"History changed on cancel");
            longPress(f.album.title+"をランダム再生");dialog("削除");await(()->history.list().size()==2&&view(f.album.title)==null,"History deletion did not refresh");
            click("ダウンロードタブ");await(()->view(f.album.title)!=null,"Album download fixture not shown");check(onMain(()->view("楽曲")==null&&view("アーティスト")==null&&view("アルバム").isSelected()),"Downloads contain unsupported tabs");
            longPress(f.album.title+"の曲一覧を開く");dialog("キャンセル");check(f.store.hasStream(OfflineDeletionVerification.ALBUM_ONLY),"Download changed on cancel");
            longPress(f.album.title+"の曲一覧を開く");dialog("削除");await(()->view(f.album.title)==null&&!f.store.hasStream(OfflineDeletionVerification.ALBUM_ONLY),"Downloaded album removal did not refresh");check(f.store.hasStream(OfflineDeletionVerification.SHARED),"Shared audio removed");
            click("プレイリスト");await(()->view(f.playlist.title)!=null,"Shared playlist disappeared");longPress(f.playlist.title+"の曲一覧を開く");dialog("削除");await(()->view(f.playlist.title)==null&&f.store.list().isEmpty(),"Playlist removal did not reach empty state");
            click("検索タブ");check(onMain(()->view("楽曲").isShown()&&view("アーティスト").isShown()),"Search lost its song or artist tabs");
            return "history long press/cancel/delete, download album and playlist long press/cancel/delete, two download tabs, shared audio retained, final empty states and search tabs preserved; isolated fixtures only";
        }finally{
            if(activity!=null)test.runOnMainSync(activity::finish);SharedPreferences.Editor editor=account.edit();if(existed)editor.putString("scope",original);else editor.remove("scope");editor.commit();
            c.getSharedPreferences("playback-history-"+scope,0).edit().clear().commit();OfflineDeletionVerification.clear(c,scope);
        }
    }
    private boolean onMain(BooleanSupplier condition){AtomicBoolean result=new AtomicBoolean();AtomicReference<Throwable> failure=new AtomicReference<>();test.runOnMainSync(()->{try{result.set(condition.getAsBoolean());}catch(Throwable t){failure.set(t);}});if(failure.get()!=null)throw new AssertionError(failure.get());return result.get();}
    private void click(String key){check(onMain(()->{View v=view(key);return v!=null&&v.isEnabled()&&v.performClick();}),"Control unavailable: "+key);}
    private void longPress(String key){check(onMain(()->{View v=view(key);return v!=null&&v.isEnabled()&&v.performLongClick();}),"Long press unavailable: "+key);}
    private View view(String key){return find(activity.getWindow().getDecorView(),v->v.isShown()&&(key.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())||v instanceof TextView&&key.contentEquals(((TextView)v).getText())));}
    private View find(View root,Predicate<View> predicate){if(predicate.test(root))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),predicate);if(found!=null)return found;}}return null;}
    private void dialog(String label)throws Exception{for(int i=0;i<100;i++){AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))if(label.contentEquals(node.getText()==null?"":node.getText())&&node.isEnabled()&&node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;SystemClock.sleep(50);}throw new AssertionError("Dialog button missing: "+label);}
    private void await(BooleanSupplier predicate,String message)throws Exception{for(int i=0;i<200;i++){if(onMain(predicate))return;SystemClock.sleep(50);}throw new AssertionError(message);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
