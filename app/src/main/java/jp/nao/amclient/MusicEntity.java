package jp.nao.amclient;

import org.json.*;
import java.util.*;

public final class MusicEntity {
    public final String id, title, subtitle, type, uri, artworkUrl;
    public final JSONObject playParams;
    public final List<ArtistRef> artists;
    public final MusicEntity album;
    public final long durationSeconds;
    public final boolean ownedPlaylist;
    public final String playlistEntryId,version;
    public final PlaylistPolicy playlistPolicy;
    public static final class ArtistRef {
        public final String id,name;
        ArtistRef(String id,String name){this.id=id;this.name=name;}
    }
    MusicEntity(JSONObject node) {this(node,false);}
    MusicEntity(JSONObject node,boolean owned) {
        id = node.optString("id"); type = node.optString("__typename"); uri = node.optString("uri");
        ownedPlaylist=owned&&"Playlist".equals(type);playlistEntryId=node.optString("nativePlaylistEntryId");version=node.optString("version");playlistPolicy=PlaylistPolicy.from(node);
        title = node.optString("title", node.optString("name", node.optString("shortTitle", "名称なし")));
        playParams = node.optJSONObject("playParams");
        durationSeconds=node.optLong("duration",0);
        album="Track".equals(type)&&node.optJSONObject("album")!=null?new MusicEntity(node.optJSONObject("album")):null;
        JSONArray images=node.optJSONArray("images");
        if((images==null||images.length()==0)&&node.optJSONObject("album")!=null)images=node.optJSONObject("album").optJSONArray("images");
        artworkUrl=selectArtwork(images);
        JSONObject artists = node.optJSONObject("contributingArtists");
        JSONArray edges = artists == null ? null : artists.optJSONArray("edges");
        List<String> names = new ArrayList<>();LinkedHashMap<String,ArtistRef> people=new LinkedHashMap<>();
        if (edges != null) for (int i=0;i<edges.length();i++) {
            JSONObject edge=edges.optJSONObject(i);
            JSONObject artist = edge==null?null:edge.optJSONObject("node");
            if (artist != null){String name=artist.optString("name"),id=artist.optString("id");if(!name.isEmpty())names.add(name);if(!id.isEmpty())people.putIfAbsent(id,new ArtistRef(id,name));}
        }
        this.artists=Collections.unmodifiableList(new ArrayList<>(people.values()));
        subtitle = names.isEmpty() ? typeLabel(type) : String.join(" / ", names);
    }
    public boolean hasSelection(String selection){JSONArray choices=playParams==null?null:playParams.optJSONArray("selections");if(choices!=null)for(int i=0;i<choices.length();i++)if(selection.equals(choices.optString(i)))return true;return false;}
    public boolean canPlay(){return hasSelection("PLAY")||hasSelection("SHUFFLE")||hasSelection("STATION");}
    static MusicEntity fromEdge(JSONObject edge){return fromEdge(edge,false);}
    static MusicEntity fromEdge(JSONObject edge,boolean owned){
        JSONObject node=edge.optJSONObject("node");if(node==null)throw new IllegalArgumentException("Missing catalog node");
        try{JSONObject copy=new JSONObject(node.toString());if(edge.optJSONObject("playParams")!=null)copy.put("playParams",edge.getJSONObject("playParams"));String entry=edge.optString("itemId","");if(!entry.isEmpty())copy.put("nativePlaylistEntryId",entry);return new MusicEntity(copy,owned);}
        catch(JSONException invalid){throw new IllegalArgumentException("Invalid catalog node",invalid);}
    }
    public boolean canSelectTracks(){
        if(!"Album".equals(type)&&!"Playlist".equals(type))return false;
        JSONArray selections=playParams==null?null:playParams.optJSONArray("selections");
        if(selections!=null)for(int i=0;i<selections.length();i++)if("PLAY".equals(selections.optString(i)))return true;
        return false;
    }
    static String selectArtwork(JSONArray images) {
        String best="";int bestScore=Integer.MAX_VALUE;
        if(images!=null)for(int i=0;i<images.length();i++) {
            JSONObject image=images.optJSONObject(i);if(image==null)continue;
            String url=image.optString("url");
            try{java.net.URI parsed=java.net.URI.create(url);if(!"https".equalsIgnoreCase(parsed.getScheme())||parsed.getHost()==null||parsed.getUserInfo()!=null)continue;}catch(IllegalArgumentException invalid){continue;}
            int width=image.optInt("width",500);
            int score=("BACKGROUND".equals(image.optString("imageType"))?100000:0)+(width>=160?width:10000-width);
            if(score<bestScore){bestScore=score;best=url;}
        }
        return best;
    }
    public static List<MusicEntity> collect(Object data) {
        LinkedHashMap<String,MusicEntity> result = new LinkedHashMap<>(); collect(data, result); return new ArrayList<>(result.values());
    }
    private static void collect(Object value, Map<String,MusicEntity> result) {
        if (value instanceof JSONObject) {
            JSONObject o=(JSONObject)value; String t=o.optString("__typename");
            if ((o.has("playParams") && (t.equals("Track")||t.equals("Album")||t.equals("Playlist")||t.equals("Station")))||(t.equals("Artist")&&o.has("id")&&o.has("name"))) {
                MusicEntity e=new MusicEntity(o); result.putIfAbsent(e.id,e); return;
            }
            Iterator<String> keys=o.keys(); while(keys.hasNext()) collect(o.opt(keys.next()), result);
        } else if (value instanceof JSONArray) { JSONArray a=(JSONArray)value; for(int i=0;i<a.length();i++) collect(a.opt(i), result); }
    }
    public static String typeLabel(String type) {
        switch(type) { case "Track":return "楽曲";case "Album":return "アルバム";case "Playlist":return "プレイリスト";case "Artist":return "アーティスト";case "Station":return "ステーション";default:return type; }
    }
}
