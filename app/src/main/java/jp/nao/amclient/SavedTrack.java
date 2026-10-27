package jp.nao.amclient;

import org.json.*;

/** Saved ciphertext has no persistent license and retains its original collection context. */
final class SavedTrack {
    static final String STREAM="stream-cache";
    private SavedTrack() {}
    static boolean isStream(JSONObject item){return STREAM.equals(item.optString("storage"));}
    static JSONObject metadata(MusicEntity track,MusicEntity owner,long bytes)throws JSONException{
        JSONObject item=new JSONObject().put("id",track.id).put("title",track.title).put("artist",track.subtitle)
            .put("artwork",track.artworkUrl.isEmpty()?owner.artworkUrl:track.artworkUrl).put("storage",STREAM)
            .put("savedAt",System.currentTimeMillis()).put("bytes",bytes);
        return item.put("originType",owner.type).put("originId",owner.id).put("originTitle",owner.title).put("originArtwork",owner.artworkUrl)
            .put("originArtworkVersion",owner.artworkUrl.isEmpty()?0:ArtworkMetadata.VERSION)
            .put("originSelectable",owner.hasSelection("PLAY")&&track.hasSelection("PLAY"));
    }
    static JSONObject refreshContext(JSONObject item,MusicEntity track,MusicEntity owner)throws JSONException{
        if(item.optBoolean("originSelectable")&&!(owner.hasSelection("PLAY")&&track.hasSelection("PLAY")))return item;
        JSONObject updated=new JSONObject(item.toString()),context=metadata(track,owner,item.optLong("bytes"));
        for(String key:new String[]{"title","artist","artwork","originType","originId","originTitle","originArtwork","originArtworkVersion","originSelectable"})updated.put(key,context.get(key));
        return updated;
    }
    static AudioQuality quality(JSONObject item){return AudioQuality.parse(item.optString("audioQuality"),AudioQuality.LOW);}
    static AudioQuality requested(JSONObject item){return AudioQuality.parse(item.optString("requestedQuality"),quality(item));}
    static String variant(JSONObject item){String value=item.optString("audioVariant");if(!value.isEmpty()&&!value.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Invalid saved audio variant");return value;}
    static String manifestName(JSONObject item){String variant=variant(item);return variant.isEmpty()?"manifest.mpd":"manifest-"+variant+".mpd";}
    static PlaybackOrigin origin(JSONObject item){return PlaybackOrigin.of(item.optString("originType"),item.optString("originId"));}
    static MusicEntity selection(CatalogPage page,String id){
        // Only the server's PLAY operation permits selecting a specific track.
        if(page.owner.hasSelection("PLAY"))for(MusicEntity track:page.items)if(id.equals(track.id)&&track.hasSelection("PLAY"))return track;
        return page.owner;
    }
    static String cachePrefix(String scope,String id){
        if(!scope.matches("[A-Za-z0-9_-]{1,80}")||!id.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid saved audio id");
        return "stream/"+scope+"/"+id+"/";
    }
}
