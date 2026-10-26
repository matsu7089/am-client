package jp.nao.amclient;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.json.*;

/** Owned-library membership and current permissions are checked again before every edit. */
final class PlaylistApi {
    private static final Object MUTATIONS=new Object();
    static final AtomicLong revision=new AtomicLong();
    private final AmazonApi api;
    PlaylistApi(AmazonApi api){this.api=api;}
    CatalogPage open(String id)throws Exception{
        String cursor="";Set<String> visited=new HashSet<>();boolean owned=false;
        do{
            JSONObject user=api.gql("NativeOwnedPlaylists",new JSONObject().put("cursor",cursor.isEmpty()?JSONObject.NULL:cursor)).getJSONObject("user");
            CatalogPage page=CatalogPage.connection(null,user.optJSONObject("playlists"),true);
            for(MusicEntity item:page.items)if(id.equals(item.id)){owned=true;break;}
            if(owned)break;cursor=page.cursor;
            if(!cursor.isEmpty()&&!visited.add(cursor))throw new IOException("自作プレイリストの一覧を取得できませんでした");
        }while(!cursor.isEmpty());
        if(!owned)throw new IOException("編集できるのはライブラリの自作プレイリストだけです");
        CatalogPage result=tracks(id);if(!result.owner.playlistPolicy.rename&&!result.owner.playlistPolicy.delete)throw new IOException("このプレイリストは自作プレイリストとして編集できません");return result;
    }
    private CatalogPage tracks(String id)throws Exception{
        String cursor="";Set<String> visited=new HashSet<>();List<MusicEntity> items=Collections.emptyList();MusicEntity owner=null;
        do{
            CatalogPage page=api.collection("Playlist",id,cursor);if(owner==null)owner=page.owner;else if(!owner.version.equals(page.owner.version))throw new IOException("読み込み中に曲一覧が変更されました。再読込してください");
            items=CatalogPage.append(items,page.items);cursor=page.cursor;
            if(!cursor.isEmpty()&&!visited.add(cursor))throw new IOException("曲一覧の続きが取得できませんでした");
        }while(!cursor.isEmpty());
        return new CatalogPage(owner,items,"");
    }
    String create(String title)throws Exception{synchronized(MUTATIONS){JSONObject created=api.gql("NativeCreatePlaylist",new JSONObject().put("title",PlaylistPolicy.title(title))).optJSONObject("createPlaylist");if(created==null||created.optString("id").isEmpty())throw new IOException("作成結果を確認できません。ライブラリを再読込してください");revision.incrementAndGet();return created.getString("id");}}
    void rename(String id,String title)throws Exception{synchronized(MUTATIONS){CatalogPage page=open(id);allowed(page.owner.playlistPolicy.rename);update(id,PlaylistPolicy.rename(page.owner,title));}}
    void add(String id,String track)throws Exception{synchronized(MUTATIONS){CatalogPage page=open(id);allowed(page.owner.playlistPolicy.add);if(track==null||track.isEmpty())throw new IOException("追加する曲がありません");JSONObject result=api.gql("NativeAppendPlaylist",new JSONObject().put("id",id).put("track",track)).optJSONObject("appendTracks");confirmed(result,id);}}
    void remove(String id,String entry)throws Exception{synchronized(MUTATIONS){CatalogPage page=open(id);allowed(page.owner.playlistPolicy.remove);update(id,PlaylistPolicy.remove(page,entry));}}
    void move(String id,String entry,int direction)throws Exception{synchronized(MUTATIONS){CatalogPage page=open(id);allowed(page.owner.playlistPolicy.reorder);update(id,PlaylistPolicy.move(page,entry,direction));}}
    void delete(String id)throws Exception{synchronized(MUTATIONS){CatalogPage page=open(id);allowed(page.owner.playlistPolicy.delete);JSONObject result=api.gql("NativeDeletePlaylist",new JSONObject().put("id",id)).optJSONObject("deletePlaylist");if(result==null||!result.optBoolean("isDeleted",false))throw new IOException("プレイリストを削除できませんでした");confirmed(result,id);}}
    private void update(String id,JSONObject input)throws Exception{JSONObject result=api.gql("NativeUpdatePlaylist",new JSONObject().put("input",input)).optJSONObject("batchUpdatePlaylist");confirmed(result==null?null:result.optJSONObject("playlist"),id);}
    private void confirmed(JSONObject result,String id)throws IOException{if(result==null||!id.equals(result.optString("id")))throw new IOException("変更結果を確認できません。再読込してください");revision.incrementAndGet();}
    private static void allowed(boolean value)throws IOException{if(!value)throw new IOException("この操作は現在許可されていません。曲一覧を再読込してください");}
}
