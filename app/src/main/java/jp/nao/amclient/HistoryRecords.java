package jp.nao.amclient;

import org.json.*;
import java.util.*;

/** Local playback sources, with no cached authorization or play commands. */
final class HistoryRecords {
    static final int LIMIT=50;
    private HistoryRecords(){}
    static boolean supported(MusicEntity entity){return entity!=null&&!entity.id.isEmpty()&&("Album".equals(entity.type)||"Playlist".equals(entity.type)||"Artist".equals(entity.type));}
    static MusicEntity source(MusicEntity selected,MusicEntity track){return supported(selected)?selected:track!=null&&supported(track.album)?track.album:null;}
    static MusicEntity savedSource(JSONObject item)throws JSONException{
        PlaybackOrigin origin=SavedTrack.origin(item);if(origin==null)return null;
        JSONObject node=new JSONObject().put("__typename",origin.type).put("id",origin.id).put("title",item.optString("originTitle",MusicEntity.typeLabel(origin.type)));
        String artwork=item.optString("originArtwork");if(!artwork.isEmpty())node.put("images",new JSONArray().put(new JSONObject().put("url",artwork).put("width",300)));
        node.put("artworkVersion",item.optInt("originArtworkVersion",0));
        return new MusicEntity(node);
    }
    private static JSONObject metadata(MusicEntity entity,long time)throws JSONException{
        JSONObject node=new JSONObject().put("__typename",entity.type).put("id",entity.id).put("title",entity.title).put("playedAt",time);
        if(!entity.artworkUrl.isEmpty())node.put("images",new JSONArray().put(new JSONObject().put("url",entity.artworkUrl).put("width",300)));
        if(!entity.artworkUrl.isEmpty())node.put("artworkVersion",ArtworkMetadata.VERSION);
        JSONArray artists=new JSONArray();for(MusicEntity.ArtistRef artist:entity.artists)artists.put(new JSONObject().put("node",new JSONObject().put("id",artist.id).put("name",artist.name)));
        node.put("contributingArtists",new JSONObject().put("edges",artists));return node;
    }
    static JSONArray record(JSONArray previous,MusicEntity entity,long time)throws JSONException{
        JSONArray combined=new JSONArray();if(supported(entity))combined.put(metadata(entity,time));
        if(previous!=null)for(int i=0;i<previous.length();i++)combined.put(previous.opt(i));return normalize(combined);
    }
    static JSONArray remove(JSONArray previous,MusicEntity entity){
        JSONArray remaining=new JSONArray();
        if(previous!=null)for(int i=0;i<previous.length();i++){JSONObject item=previous.optJSONObject(i);if(item==null)continue;
            if(supported(entity)&&entity.type.equals(item.optString("__typename"))&&entity.id.equals(item.optString("id")))continue;remaining.put(item);
        }
        return normalize(remaining);
    }
    static JSONArray normalize(JSONArray entries){
        Map<String,JSONObject> unique=new LinkedHashMap<>();
        if(entries!=null)for(int i=0;i<entries.length();i++){
            JSONObject entry=entries.optJSONObject(i);if(entry==null||entry.optLong("playedAt",-1)<0)continue;
            MusicEntity entity=new MusicEntity(entry);if(!supported(entity))continue;
            String key=entity.type+":"+entity.id;JSONObject old=unique.get(key);
            if(old==null||entry.optLong("playedAt")>old.optLong("playedAt"))unique.put(key,entry);
        }
        List<JSONObject> ordered=new ArrayList<>(unique.values());ordered.sort(Comparator.comparingLong((JSONObject item)->item.optLong("playedAt")).reversed());
        JSONArray result=new JSONArray();for(int i=0;i<Math.min(LIMIT,ordered.size());i++)result.put(ordered.get(i));return result;
    }
    static List<MusicEntity> list(JSONArray entries){JSONArray normalized=normalize(entries);List<MusicEntity> result=new ArrayList<>();for(int i=0;i<normalized.length();i++)result.add(new MusicEntity(normalized.optJSONObject(i)));return result;}
}
