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
        if(!owner.artworkUrl.isEmpty())collection.put("artworkVersion",ArtworkMetadata.VERSION);
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
    private static final class State{
        final LinkedHashMap<String,JSONObject> collections=new LinkedHashMap<>();final Map<String,Set<String>> members=new HashMap<>();final Set<String> available=new HashSet<>();
    }
    private static State state(List<JSONObject> saved,List<JSONObject> indexes)throws JSONException{
        State state=new State();Map<String,JSONObject> collections=state.collections;Map<String,Set<String>> members=state.members;Set<String> available=state.available;
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
            node.put("artworkVersion",item.optInt("originArtworkVersion",0));
            collections.put(key,node);
        }
        return state;
    }
    static List<MusicEntity> list(String type,List<JSONObject> saved,List<JSONObject> indexes)throws JSONException{
        State state=state(saved,indexes);
        List<MusicEntity> result=new ArrayList<>();
        for(Map.Entry<String,JSONObject> entry:state.collections.entrySet())if(type.equals(entry.getValue().optString("__typename"))&&!Collections.disjoint(state.members.get(entry.getKey()),state.available))result.add(new MusicEntity(entry.getValue()));
        result.sort(Comparator.comparing(entity->entity.title));return result;
    }
    static final class Removal{
        final Set<String> delete=new LinkedHashSet<>(),shared=new LinkedHashSet<>();
        final Map<String,MusicEntity> reassign=new LinkedHashMap<>();
    }
    static Removal removal(MusicEntity target,List<JSONObject> saved,List<JSONObject> indexes)throws JSONException{
        PlaybackOrigin source=PlaybackOrigin.of(target);if(source==null)throw new IllegalArgumentException("Invalid saved collection");String key=source.type+":"+source.id;
        State state=state(saved,indexes);Removal result=new Removal();Set<String> candidates=state.members.getOrDefault(key,Collections.emptySet());
        for(String id:candidates){
            if(!state.available.contains(id))continue;MusicEntity other=null;
            for(Map.Entry<String,JSONObject> entry:state.collections.entrySet())if(!key.equals(entry.getKey())&&state.members.get(entry.getKey()).contains(id)){
                MusicEntity choice=new MusicEntity(entry.getValue());if(other==null||choice.hasSelection("PLAY")&&!other.hasSelection("PLAY"))other=choice;
            }
            if(other==null){result.delete.add(id);continue;}result.shared.add(id);
            for(JSONObject item:saved)if(id.equals(item.optString("id"))){PlaybackOrigin origin=SavedTrack.origin(item);if(origin!=null&&source.type.equals(origin.type)&&source.id.equals(origin.id))result.reassign.put(id,other);break;}
        }
        return result;
    }
    static JSONObject reassign(JSONObject item,MusicEntity owner)throws JSONException{
        JSONObject updated=new JSONObject(item.toString());return updated.put("originType",owner.type).put("originId",owner.id).put("originTitle",owner.title).put("originArtwork",owner.artworkUrl).put("originArtworkVersion",owner.artworkVersion).put("originSelectable",false);
    }
}
