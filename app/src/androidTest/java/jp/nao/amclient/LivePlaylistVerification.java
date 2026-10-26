package jp.nao.amclient;

import android.app.Instrumentation;
import org.json.*;
import java.util.*;
import java.util.function.Predicate;

/** Changes only a newly created disposable playlist; existing playlists are read only. */
final class LivePlaylistVerification {
    static String run(Instrumentation test)throws Exception{
        AmazonApi api=AmazonApi.get(test.getTargetContext());PlaylistApi playlists=new PlaylistApi(api);Set<String> before=owned(api);String id=null;
        try{
            try{playlists.open("B07K3YQT6J");throw new AssertionError("Official playlist accepted as owned");}catch(java.io.IOException expected){if(!expected.getMessage().contains("自作"))throw expected;}
            String name="AM Client 検証 "+UUID.randomUUID();id=playlists.create(name);String created=id;
            CatalogPage page=await(playlists,id,p->p.items.isEmpty()&&name.equals(p.owner.title));if(!page.owner.playlistPolicy.add||!page.owner.playlistPolicy.remove||!page.owner.playlistPolicy.reorder||!page.owner.playlistPolicy.delete)throw new AssertionError("Missing owned playlist permissions");
            playlists.add(id,"B07BDGWZC7");await(playlists,id,p->p.items.size()==1);playlists.add(id,"B07BDD7XV9");await(playlists,id,p->p.items.size()==2);playlists.add(id,"B07BDGWZC7");page=await(playlists,id,p->p.items.size()==3);
            String first=page.items.get(0).playlistEntryId,second=page.items.get(1).playlistEntryId,third=page.items.get(2).playlistEntryId;
            if(first.isEmpty()||third.isEmpty()||first.equals(third)||!page.items.get(0).id.equals(page.items.get(2).id))throw new AssertionError("Duplicate entries collapsed");
            playlists.move(id,third,-1);await(playlists,id,p->p.items.size()==3&&third.equals(p.items.get(1).playlistEntryId));
            playlists.move(id,first,1);await(playlists,id,p->p.items.size()==3&&third.equals(p.items.get(0).playlistEntryId)&&first.equals(p.items.get(1).playlistEntryId));
            playlists.remove(id,first);await(playlists,id,p->p.items.size()==2&&third.equals(p.items.get(0).playlistEntryId)&&second.equals(p.items.get(1).playlistEntryId));
            playlists.rename(id,name+" 変更済み");await(playlists,id,p->(name+" 変更済み").equals(p.owner.title));playlists.delete(id);id=null;
            for(int n=0;n<10&&owned(api).contains(created);n++)Thread.sleep(500);
            if(!before.equals(owned(api)))throw new AssertionError("Owned library membership changed after cleanup");
            return "owned-only protection, private create, add songs, distinct duplicate entries, move up/down, selected-entry removal, rename and delete; fixture removed, original library membership preserved";
        }finally{if(id!=null)playlists.delete(id);}
    }
    private static CatalogPage await(PlaylistApi api,String id,Predicate<CatalogPage> matches)throws Exception{for(int i=0;i<20;i++){CatalogPage p=api.open(id);if(matches.test(p))return p;Thread.sleep(500);}throw new AssertionError("Playlist edit not reflected by server");}
    private static Set<String> owned(AmazonApi api)throws Exception{Set<String> result=new HashSet<>(),visited=new HashSet<>();String cursor="";do{CatalogPage p=CatalogPage.connection(null,api.gql("NativeOwnedPlaylists",new JSONObject().put("cursor",cursor.isEmpty()?JSONObject.NULL:cursor)).getJSONObject("user").optJSONObject("playlists"));for(MusicEntity item:p.items)result.add(item.id);cursor=p.cursor;if(!cursor.isEmpty()&&!visited.add(cursor))throw new AssertionError("Repeated library cursor");}while(!cursor.isEmpty());return result;}
}
