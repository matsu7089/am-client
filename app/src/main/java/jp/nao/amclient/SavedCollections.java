package jp.nao.amclient;

import org.json.*;
import java.util.*;

/** Collection membership is independent of a track's preferred playback origin. */
final class SavedCollections {
    private SavedCollections() {}
    static JSONObject metadata(MusicEntity owner,List<MusicEntity> tracks)throws JSONException{
        if(PlaybackOrigin.of(owner)==null)throw new IllegalArgumentException("Invalid saved collection");
        JSONObject collection=new JSONObject().put("__typename",owner.type).put("id",owner.id).put("title",owner.title).put("uri",owner.uri);
        if(!owner.artworkUrl.isEmpty())collection.put("images",new JSONArray().put(new JSONObject().put("url",owner.artworkUrl).put("width",300)));
        if(owner.playParams!=null)collection.put("playParams",owner.playParams);
        JSONArray artists=new JSONArray();for(MusicEntity.ArtistRef person:owner.artists)artists.put(new JSONObject().put("node",new JSONObject().put("id",person.id).put("name",person.name)));
        collection.put("contributingArtists",new JSONObject().put("edges",artists));
        LinkedHashSet<String> ids=new LinkedHashSet<>();for(MusicEntity track:tracks)if("Track".equals(track.type)&&!track.id.isEmpty())ids.add(track.id);
        return new JSONObject().put("collection",collection).put("trackIds",new JSONArray(ids));
    }
    static JSONObject merge(JSONObject old,JSONObject fresh)throws JSONException{
        if(old==null)return fresh;
        JSONObject previous=old.optJSONObject("collection"),current=fresh.getJSONObject("collection");
        if(previous==null||!current.optString("id").equals(previous.optString("id"))||!current.optString("__typename").equals(previous.optString("__typename")))return fresh;
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        for(JSONObject entry:Arrays.asList(fresh,old)){JSONArray tracks=entry.optJSONArray("trackIds");if(tracks!=null)for(int i=0;i<tracks.length();i++)if(!tracks.optString(i).isEmpty())ids.add(tracks.optString(i));}
        return fresh.put("trackIds",new JSONArray(ids));
    }
    static List<MusicEntity> list(String type,List<JSONObject> saved,List<JSONObject> indexes)throws JSONException{
        LinkedHashMap<String,JSONObject> collections=new LinkedHashMap<>();Map<String,Set<String>> members=new HashMap<>();Set<String> available=new HashSet<>();
        for(JSONObject index:indexes){
            JSONObject node=index.optJSONObject("collection");if(node==null)continue;
            PlaybackOrigin source=PlaybackOrigin.of(node.optString("__typename"),node.optString("id"));if(source==null)continue;
            String key=source.type+":"+source.id;collections.put(key,node);Set<String> ids=members.computeIfAbsent(key,ignored->new HashSet<>());
            JSONArray tracks=index.optJSONArray("trackIds");if(tracks!=null)for(int i=0;i<tracks.length();i++)ids.add(tracks.optString(i));
        }
        for(JSONObject item:saved){
            if(!SavedTrack.isStream(item)||item.optString("id").isEmpty())continue;
            available.add(item.optString("id"));PlaybackOrigin source=SavedTrack.origin(item);if(source==null)continue;
            String key=source.type+":"+source.id;members.computeIfAbsent(key,ignored->new HashSet<>()).add(item.optString("id"));
            if(collections.containsKey(key))continue;
            JSONObject node=new JSONObject().put("__typename",source.type).put("id",source.id).put("title",item.optString("originTitle",MusicEntity.typeLabel(source.type)));
            String artwork=item.optString("originArtwork", "Album".equals(source.type)?item.optString("artwork"):"");
            if(!artwork.isEmpty())node.put("images",new JSONArray().put(new JSONObject().put("url",artwork).put("width",300)));
            collections.put(key,node);
        }
        List<MusicEntity> result=new ArrayList<>();
        for(Map.Entry<String,JSONObject> entry:collections.entrySet())if(type.equals(entry.getValue().optString("__typename"))&&!Collections.disjoint(members.get(entry.getKey()),available))result.add(new MusicEntity(entry.getValue()));
        result.sort(Comparator.comparing(entity->entity.title));return result;
    }
}
