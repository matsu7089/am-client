package jp.nao.amclient;

import org.json.*;

/** Marks local thumbnails selected with the PRIMARY/square preference. */
final class ArtworkMetadata {
    static final int VERSION=1;
    private ArtworkMetadata(){}
    static boolean update(JSONObject node,MusicEntity fresh)throws JSONException{
        if(!"Playlist".equals(fresh.type)||fresh.artworkUrl.isEmpty()||!fresh.type.equals(node.optString("__typename"))||!fresh.id.equals(node.optString("id"))||node.optInt("artworkVersion",0)>=VERSION)return false;
        node.put("images",new JSONArray().put(new JSONObject().put("url",fresh.artworkUrl).put("width",300).put("height",300).put("imageType","PRIMARY")));
        node.put("artworkVersion",VERSION);return true;
    }
}
