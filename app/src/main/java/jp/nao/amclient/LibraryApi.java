package jp.nao.amclient;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.json.*;

/** Re-read permissions, set an explicit desired state, and confirm the server response. */
final class LibraryApi {
    interface Requests {JSONObject call(String operation,JSONObject variables)throws Exception;}
    static final AtomicLong revision=new AtomicLong();
    private static final Object MUTATIONS=new Object();
    private final Requests requests;
    LibraryApi(AmazonApi api){this(api::gql);}
    LibraryApi(Requests requests){this.requests=requests;}
    LibraryMembership read(MusicEntity item)throws Exception{
        supported(item);
        String field="Album".equals(item.type)?"album":"playlist";
        JSONObject node=requests.call("NativeMembership"+item.type,new JSONObject().put("id",item.id)).optJSONObject(field);
        if(node==null||!item.id.equals(node.optString("id"))||!item.type.equals(node.optString("__typename")))throw new IOException("ライブラリの登録状態を確認できませんでした");
        LibraryMembership state=LibraryMembership.from(node,item.ownedPlaylist);
        if(!state.known)throw new IOException("ライブラリの登録状態を確認できませんでした");
        return state;
    }
    LibraryMembership set(MusicEntity item,boolean registered)throws Exception{synchronized(MUTATIONS){
        LibraryMembership current=read(item);
        if(!current.canChange)throw new IOException("この項目はライブラリへの追加・解除を変更できません");
        if(current.registered==registered){revision.incrementAndGet();return current;}
        boolean album="Album".equals(item.type);
        String operation=album?"NativeSetAlbumLike":registered?"NativeFollowPlaylist":"NativeUnfollowPlaylist";
        JSONObject variables=new JSONObject().put("id",item.id);
        if(album)variables.put("state",registered?"POSITIVE":"NEUTRAL");
        JSONObject data;
        try{data=requests.call(operation,variables);}finally{revision.incrementAndGet();}
        JSONObject node=null;
        if(album){
            JSONObject result=data.optJSONObject("setAlbumLikeState");JSONArray edges=result==null?null:result.optJSONArray("edges");
            if(edges!=null)for(int i=0;i<edges.length();i++){JSONObject edge=edges.optJSONObject(i),candidate=edge==null?null:edge.optJSONObject("node");if(candidate!=null&&item.id.equals(candidate.optString("id"))){node=candidate;break;}}
        }else{JSONObject result=data.optJSONObject(registered?"followPlaylist":"unfollowPlaylist");if(result!=null)node=result.optJSONObject("playlist");}
        LibraryMembership confirmed=node==null?null:LibraryMembership.from(node,false);
        if(node==null||!item.id.equals(node.optString("id"))||!item.type.equals(node.optString("__typename"))||!confirmed.known||confirmed.registered!=registered)throw new IOException("変更結果を確認できません。登録状態を再取得してください");
        return LibraryMembership.confirmed(current,confirmed);
    }}
    private static void supported(MusicEntity item)throws IOException{if(item==null||item.id.isEmpty()||!"Album".equals(item.type)&&!"Playlist".equals(item.type))throw new IOException("アルバムとプレイリストだけを登録できます");}
}
