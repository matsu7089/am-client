package jp.nao.amclient;

import org.json.*;
import java.util.*;

/** Owned and followed playlists have independent pagination cursors. */
final class LibraryPage {
    private LibraryPage() {}
    static CatalogPage albums(JSONObject user)throws JSONException{
        JSONObject connection=user.optJSONObject("likedAlbums");JSONArray edges=connection==null?null:connection.optJSONArray("edges"),active=new JSONArray();
        if(edges!=null)for(int i=0;i<edges.length();i++){
            JSONObject edge=edges.optJSONObject(i),node=edge==null?null:edge.optJSONObject("node"),likes=node==null?null:node.optJSONObject("likesContext");
            if(likes!=null&&"LIKE".equals(likes.optString("state")))active.put(edge);
        }
        return new CatalogPage(null,CatalogPage.connection(null,new JSONObject().put("edges",active)).items,CatalogPage.cursor(connection));
    }
    static JSONObject playlistVariables(String cursor) throws JSONException {
        JSONObject next=cursor.isEmpty()?null:new JSONObject(cursor);
        JSONObject vars=new JSONObject().put("owned",next==null||next.has("owned"))
            .put("followed",next==null||next.has("followed"));
        if(next!=null){
            if(next.has("owned"))vars.put("ownedCursor",next.getString("owned"));
            if(next.has("followed"))vars.put("followedCursor",next.getString("followed"));
        }
        return vars;
    }
    static CatalogPage playlists(JSONObject user) throws JSONException {
        CatalogPage owned=CatalogPage.connection(null,user.optJSONObject("playlists"),true);
        JSONObject following=user.optJSONObject("followedPlaylists");
        JSONArray edges=following==null?null:following.optJSONArray("edges");
        JSONArray active=new JSONArray();
        if(edges!=null)for(int i=0;i<edges.length();i++){
            JSONObject edge=edges.optJSONObject(i),node=edge==null?null:edge.optJSONObject("node");
            JSONObject follows=node==null?null:node.optJSONObject("followsContext");
            if(follows==null||follows.optBoolean("isFollowed",true))active.put(edge);
        }
        CatalogPage followed=CatalogPage.connection(null,new JSONObject().put("edges",active));
        JSONObject next=new JSONObject();
        if(!owned.cursor.isEmpty())next.put("owned",owned.cursor);
        String followCursor=CatalogPage.cursor(following);
        if(!followCursor.isEmpty())next.put("followed",followCursor);
        return new CatalogPage(null,CatalogPage.append(owned.items,followed.items),next.length()==0?"":next.toString());
    }
}
