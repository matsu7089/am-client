package jp.nao.amclient;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SearchCategoryTest {
    private MusicEntity entity(String id,String type)throws Exception{return new MusicEntity(new JSONObject().put("id",id).put("__typename",type).put("title",id).put("playParams",new JSONObject().put("id",id)));}
    @Test public void separatesMixedResultsAndPreservesResultOrder()throws Exception {
        List<MusicEntity> mixed=Arrays.asList(entity("album","Album"),entity("song1","Track"),entity("artist","Artist"),entity("playlist","Playlist"),entity("song2","Track"),entity("station","Station"));
        List<MusicEntity> tracks=SearchCategory.TRACKS.filter(mixed);assertEquals(2,tracks.size());assertEquals("song1",tracks.get(0).id);assertEquals("song2",tracks.get(1).id);
        assertEquals("album",SearchCategory.ALBUMS.filter(mixed).get(0).id);assertEquals("playlist",SearchCategory.PLAYLISTS.filter(mixed).get(0).id);
        assertEquals("artist",SearchCategory.ARTISTS.filter(mixed).get(0).id);assertEquals(6,mixed.size());
    }
    @Test public void showsAnEmptyTabWithoutBorrowingAnotherCategory()throws Exception {assertTrue(SearchCategory.ALBUMS.filter(Collections.singletonList(entity("song","Track"))).isEmpty());}
}
