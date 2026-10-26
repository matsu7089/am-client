package jp.nao.amclient;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class MusicEntityTest {
    @Test public void collectsPlayableResultsWithoutDuplicatesOrNestedAlbum() throws Exception {
        JSONObject track=new JSONObject("{\"__typename\":\"Track\",\"id\":\"T1\",\"title\":\"テスト曲\",\"uri\":\"track:T1\",\"playParams\":{\"id\":\"track:T1\",\"selections\":[\"STATION\"]},\"album\":{\"__typename\":\"Album\",\"id\":\"A1\"},\"contributingArtists\":{\"edges\":[{\"node\":{\"name\":\"アーティスト\"}}]}}");
        JSONObject response=new JSONObject().put("data",new JSONArray().put(track).put(track).put(new JSONObject().put("__typename","Artist").put("id","X1")));
        List<MusicEntity> result=MusicEntity.collect(response);
        assertEquals(1,result.size());assertEquals("テスト曲",result.get(0).title);assertEquals("アーティスト",result.get(0).subtitle);
        assertEquals("STATION",result.get(0).playParams.getJSONArray("selections").getString(0));
    }
    @Test public void toleratesEmptyResponses() {assertTrue(MusicEntity.collect(new JSONObject()).isEmpty());assertTrue(MusicEntity.collect(null).isEmpty());}
    @Test public void retainsArtistLinksAndAlbumWithoutCollectingContributorsAsSearchResults()throws Exception{
        JSONObject artist=new JSONObject().put("__typename","Artist").put("id","artist").put("name","共同アーティスト");
        JSONObject track=new JSONObject().put("__typename","Track").put("id","track").put("title","曲").put("duration",95).put("playParams",new JSONObject())
            .put("contributingArtists",new JSONObject().put("edges",new JSONArray().put(new JSONObject().put("node",artist))))
            .put("album",new JSONObject().put("__typename","Album").put("id","album").put("title","アルバム"));
        List<MusicEntity> result=MusicEntity.collect(new JSONArray().put(track).put(artist));
        assertEquals(2,result.size());assertEquals("artist",result.get(0).artists.get(0).id);assertEquals("album",result.get(0).album.id);assertEquals(95,result.get(0).durationSeconds);
        assertEquals("共同アーティスト",result.get(1).title);assertEquals("アーティスト",result.get(1).subtitle);
    }
}
