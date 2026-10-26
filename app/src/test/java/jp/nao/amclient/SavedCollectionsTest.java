package jp.nao.amclient;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SavedCollectionsTest {
    private MusicEntity entity(String type,String id)throws Exception{return new MusicEntity(new JSONObject().put("__typename",type).put("id",id).put("title",id));}
    private JSONObject saved(String id,MusicEntity source)throws Exception{return SavedTrack.metadata(entity("Track",id),source,10);}
    @Test public void restoresLegacyAlbumAndPlaylistEntriesWithoutDuplicatingCollections()throws Exception{
        MusicEntity album=entity("Album","same"),playlist=entity("Playlist","same");
        List<JSONObject> tracks=Arrays.asList(saved("a",album),saved("b",playlist),saved("c",playlist));
        assertEquals(1,SavedCollections.list("Album",tracks,Collections.emptyList()).size());assertEquals(1,SavedCollections.list("Playlist",tracks,Collections.emptyList()).size());
    }
    @Test public void keepsBothCollectionsWhenAllTheirTracksShareAnotherPlaybackOrigin()throws Exception{
        MusicEntity album=entity("Album","album"),playlist=entity("Playlist","playlist"),track=entity("Track","shared");
        List<JSONObject> index=Arrays.asList(SavedCollections.metadata(album,Collections.singletonList(track)),SavedCollections.metadata(playlist,Collections.singletonList(track)));
        List<JSONObject> tracks=Collections.singletonList(saved("shared",playlist));
        assertEquals("album",SavedCollections.list("Album",tracks,index).get(0).id);assertEquals("playlist",SavedCollections.list("Playlist",tracks,index).get(0).id);
    }
    @Test public void hidesCollectionsWithNoRemainingSavedTracks()throws Exception{
        MusicEntity album=entity("Album","album"),other=entity("Playlist","other");
        JSONObject index=SavedCollections.metadata(album,Collections.singletonList(entity("Track","deleted")));
        assertTrue(SavedCollections.list("Album",Collections.singletonList(saved("other-track",other)),Collections.singletonList(index)).isEmpty());
        assertTrue(SavedCollections.list("Album",Collections.emptyList(),Collections.singletonList(index)).isEmpty());
    }
    @Test public void retainsEarlierPageMembershipWhenRefreshingCollectionMetadata()throws Exception{
        MusicEntity owner=entity("Playlist","playlist");
        JSONObject older=SavedCollections.metadata(owner,Arrays.asList(entity("Track","first"),entity("Track","later")));
        JSONObject refreshed=SavedCollections.merge(older,SavedCollections.metadata(owner,Arrays.asList(entity("Track","first"),entity("Track","first"))));
        assertEquals(2,refreshed.getJSONArray("trackIds").length());
        assertEquals(1,SavedCollections.list("Playlist",Collections.singletonList(saved("later",owner)),Collections.singletonList(refreshed)).size());
    }
    @Test public void prefersIndexedTitleArtworkAndPermissionsOverLegacyFallback()throws Exception{
        MusicEntity owner=new MusicEntity(new JSONObject().put("__typename","Playlist").put("id","playlist").put("title","Updated")
            .put("images",new JSONArray().put(new JSONObject().put("url","https://example.org/cover.jpg").put("width",300)))
            .put("playParams",new JSONObject().put("selections",new JSONArray().put("PLAY"))));
        MusicEntity track=entity("Track","track");JSONObject index=SavedCollections.metadata(owner,Collections.singletonList(track));
        MusicEntity result=SavedCollections.list("Playlist",Collections.singletonList(saved("track",entity("Playlist","playlist"))),Collections.singletonList(index)).get(0);
        assertEquals("Updated",result.title);assertEquals("https://example.org/cover.jpg",result.artworkUrl);assertTrue(result.canSelectTracks());
    }
}
