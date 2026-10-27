package jp.nao.amclient;

import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.json.JSONObject;

/** Real add/cancel/remove UI; only initially unregistered test targets are changed and restored. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class LiveLibraryVerification {
    private final Instrumentation test;private final AmazonApi api;private final LibraryApi library;
    private MainActivity main;private BrowseActivity browse;
    private final List<MusicEntity> fixtures=new ArrayList<>();
    private LiveLibraryVerification(Instrumentation test)throws Exception{this.test=test;api=AmazonApi.get(test.getTargetContext());library=new LibraryApi(api);}
    static String run(Instrumentation test)throws Exception{return new LiveLibraryVerification(test).run();}
    private String run()throws Exception{
        Set<String> beforeAlbums=ids(SearchCategory.ALBUMS),beforePlaylists=ids(SearchCategory.PLAYLISTS);
        try{
            JSONObject shared=api.gql("NativeMembershipPlaylist",new JSONObject().put("id","5e8f823755db427c8dc0e6ecbe301bb3jajp")).getJSONObject("playlist");
            if(!LibraryMembership.from(shared,false).canChange)throw new AssertionError("Other user's shared playlist cannot change membership");
            fixtures.add(unregistered("Album",new String[]{"B00BNJ596W","B0HJGY47K1"}));
            fixtures.add(unregistered("Playlist",new String[]{"B082F7WLV1","B07TSN4K7X"}));
            Context context=test.getTargetContext();main=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            for(MusicEntity target:fixtures){
                String category="Album".equals(target.type)?"アルバム":"プレイリスト";
                click(main,category);await(()->loaded(category),"Library did not load before addition");
                browse=(BrowseActivity)test.startActivitySync(new Intent(context,BrowseActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("kind",target.type).putExtra("id",target.id).putExtra("section","SEARCH"));
                await(()->enabled(browse,"ライブラリに追加"),"Add action missing");click(browse,"ライブラリに追加");
                await(()->enabled(browse,"ライブラリから削除"),"Added state not confirmed");
                if(!library.read(target).registered)throw new AssertionError("Addition not persisted by server");
                click(browse,"ライブラリタブ");browse=null;await(()->loaded(category),"Library not refreshed after addition");
                for(int page=0;!onMain(()->contains(target.id))&&page<10;page++){
                    if(!onMain(()->enabled(main,"さらに表示")))break;click(main,"さらに表示");await(()->loaded(category),"Library pagination failed");
                }
                if(!onMain(()->contains(target.id)))throw new AssertionError("Added item absent from library UI");
                openLibraryItem(target.id);await(()->enabled(browse,"ライブラリから削除"),"Registered action missing after reopening");
                click(browse,"ライブラリから削除");dialog("キャンセル");if(!library.read(target).registered)throw new AssertionError("Cancel removed membership");
                click(browse,"ライブラリから削除");dialog("削除");await(()->enabled(browse,"ライブラリに追加"),"Removal not confirmed");
                if(library.read(target).registered)throw new AssertionError("Removal not persisted");
                click(browse,"ライブラリタブ");browse=null;await(()->loaded(category),"Library not refreshed after removal");
                if(onMain(()->contains(target.id)))throw new AssertionError("Removed item remained in cached library");
            }
            CatalogPage playlists=api.library(SearchCategory.PLAYLISTS,"");
            MusicEntity owned=null;for(MusicEntity item:playlists.items)if(item.ownedPlaylist&&item.id.contains("-")){owned=item;break;}
            if(owned==null)throw new AssertionError("Missing owned playlist for read-only protection check");
            MusicEntity protectedItem=owned;if(library.read(protectedItem).canChange)throw new AssertionError("Owned playlist exposes follow");
            try{library.set(protectedItem,false);throw new AssertionError("Owned playlist accepted as unfollowable");}catch(java.io.IOException expected){if(!expected.getMessage().contains("変更できません"))throw expected;}
            browse=(BrowseActivity)test.startActivitySync(new Intent(context,BrowseActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("kind","Playlist").putExtra("id",protectedItem.id).putExtra("editablePlaylist",true));
            await(()->enabled(browse,"自作プレイリストを編集"),"Owned editing route lost");
            if(onMain(()->view(browse,"ライブラリに追加")!=null||view(browse,"ライブラリから削除")!=null))throw new AssertionError("Owned playlist displays follow button");
            test.runOnMainSync(browse::finish);
            browse=(BrowseActivity)test.startActivitySync(new Intent(context,BrowseActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("kind","Playlist").putExtra("id",shared.getString("id")));
            boolean sharedRegistered=LibraryMembership.from(shared,false).registered;
            await(()->enabled(browse,sharedRegistered?"ライブラリから削除":"ライブラリに追加"),"Shared-user follow control missing");
            if(sharedRegistered){click(browse,"ライブラリから削除");dialog("キャンセル");if(!library.read(new MusicEntity(shared)).registered)throw new AssertionError("Shared playlist changed on cancel");}
            if(!beforeAlbums.equals(ids(SearchCategory.ALBUMS))||!beforePlaylists.equals(ids(SearchCategory.PLAYLISTS)))throw new AssertionError("Original library membership not restored");
            return "album likes and playlist follows via actual add/cancel/remove UI; search/detail to library navigation, pagination and cache refresh; owned follow blocked with editing retained, shared-user follow control and cancel; original memberships restored";
        }finally{
            for(MusicEntity fixture:fixtures)library.set(fixture,false);
            test.runOnMainSync(()->{if(browse!=null&&!browse.isFinishing())browse.finish();if(main!=null&&!main.isFinishing())main.finish();});
        }
    }
    private MusicEntity unregistered(String type,String[] candidates)throws Exception{
        for(String id:candidates){MusicEntity item=new MusicEntity(new JSONObject().put("__typename",type).put("id",id));LibraryMembership state=library.read(item);if(state.canChange&&!state.registered)return item;}
        throw new AssertionError("No initially unregistered test target; no existing membership changed");
    }
    private Set<String> ids(SearchCategory category)throws Exception{
        Set<String> result=new HashSet<>(),visited=new HashSet<>();String cursor="";
        do{CatalogPage p=api.library(category,cursor);for(MusicEntity item:p.items)result.add(item.id);cursor=p.cursor;if(!cursor.isEmpty()&&!visited.add(cursor))throw new AssertionError("Repeated library cursor");}while(!cursor.isEmpty());return result;
    }
    private boolean loaded(String category){return find(main.getWindow().getDecorView(),v->v.isShown()&&v instanceof TextView&&((TextView)v).getText().toString().startsWith("ライブラリ · "+category+" · "))!=null;}
    private ListView list(){return (ListView)find(main.getWindow().getDecorView(),v->v instanceof ListView&&v.isShown());}
    private int position(String id){ListView list=list();if(list!=null)for(int i=0;i<list.getAdapter().getCount();i++){Object item=list.getAdapter().getItem(i);if(item instanceof MusicEntity&&id.equals(((MusicEntity)item).id))return i;}return -1;}
    private boolean contains(String id){return position(id)>=0;}
    private void openLibraryItem(String id)throws Exception{
        AtomicInteger index=new AtomicInteger();onMain(()->{index.set(position(id));list().setSelection(index.get());return index.get()>=0;});
        await(()->{ListView l=list();return index.get()>=l.getFirstVisiblePosition()&&index.get()<=l.getLastVisiblePosition();},"Target row not visible");
        Instrumentation.ActivityMonitor monitor=test.addMonitor(BrowseActivity.class.getName(),null,false);
        try{onMain(()->list().getChildAt(index.get()-list().getFirstVisiblePosition()).performClick());browse=(BrowseActivity)monitor.waitForActivityWithTimeout(5000);if(browse==null)throw new AssertionError("Library row did not open detail");}finally{test.removeMonitor(monitor);}
    }
    private boolean onMain(BooleanSupplier condition){AtomicBoolean result=new AtomicBoolean();AtomicReference<Throwable> failure=new AtomicReference<>();test.runOnMainSync(()->{try{result.set(condition.getAsBoolean());}catch(Throwable t){failure.set(t);}});if(failure.get()!=null)throw new AssertionError(failure.get());return result.get();}
    private boolean enabled(Activity activity,String key){View v=view(activity,key);return v!=null&&v.isEnabled();}
    private void click(Activity activity,String key){if(!onMain(()->{View v=view(activity,key);return v!=null&&v.isEnabled()&&v.performClick();}))throw new AssertionError("Control unavailable: "+key);}
    private View view(Activity activity,String key){return find(activity.getWindow().getDecorView(),v->v.isShown()&&(key.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())||v instanceof TextView&&key.contentEquals(((TextView)v).getText())));}
    private View find(View root,Predicate<View> predicate){if(predicate.test(root))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),predicate);if(found!=null)return found;}}return null;}
    private void await(BooleanSupplier condition,String message)throws Exception{for(int n=0;n<800;n++){if(onMain(condition))return;SystemClock.sleep(50);}throw new AssertionError(message);}
    private void dialog(String label)throws Exception{for(int i=0;i<100;i++){AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))if(label.contentEquals(node.getText()==null?"":node.getText())&&node.isEnabled()&&node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;SystemClock.sleep(50);}throw new AssertionError("Dialog button missing: "+label);}
}
