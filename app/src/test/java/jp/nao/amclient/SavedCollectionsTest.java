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
    @Test public void removesExclusiveTracksAndSkipsSharedTracks()throws Exception{
        MusicEntity album=entity("Album","album"),playlist=entity("Playlist","playlist");List<JSONObject> saved=Arrays.asList(saved("only-album",album),saved("shared",album),saved("only-playlist",playlist));
        List<JSONObject> indexes=Arrays.asList(SavedCollections.metadata(album,Arrays.asList(entity("Track","only-album"),entity("Track","shared"))),SavedCollections.metadata(playlist,Arrays.asList(entity("Track","shared"),entity("Track","only-playlist"))));
        SavedCollections.Removal plan=SavedCollections.removal(album,saved,indexes);assertEquals(Collections.singleton("only-album"),plan.delete);assertEquals(Collections.singleton("shared"),plan.shared);assertEquals("playlist",plan.reassign.get("shared").id);
    }
    @Test public void allSharedDeletionDoesNotRestoreTheDeletedCollection()throws Exception{
        MusicEntity album=entity("Album","same"),playlist=entity("Playlist","same");JSONObject item=saved("shared",album),remaining=SavedCollections.metadata(playlist,Collections.singletonList(entity("Track","shared")));
        SavedCollections.Removal plan=SavedCollections.removal(album,Collections.singletonList(item),Collections.singletonList(remaining));assertTrue(plan.delete.isEmpty());assertEquals(1,plan.shared.size());
        List<JSONObject> after=Collections.singletonList(SavedCollections.reassign(item,plan.reassign.get("shared")));assertTrue(SavedCollections.list("Album",after,Collections.singletonList(remaining)).isEmpty());assertEquals(1,SavedCollections.list("Playlist",after,Collections.singletonList(remaining)).size());
    }
    @Test public void keepsASecondLegacyOriginWithoutRewritingIt()throws Exception{
        MusicEntity target=entity("Playlist","target"),other=entity("Album","other");JSONObject saved=saved("shared",other),index=SavedCollections.metadata(target,Collections.singletonList(entity("Track","shared")));
        SavedCollections.Removal plan=SavedCollections.removal(target,Collections.singletonList(saved),Collections.singletonList(index));assertTrue(plan.delete.isEmpty());assertEquals(Collections.singleton("shared"),plan.shared);assertTrue(plan.reassign.isEmpty());
    }
    @Test public void unsharedLegacyDownloadsCanBeDeletedWithoutAnIndex()throws Exception{
        MusicEntity owner=entity("Playlist","legacy");SavedCollections.Removal plan=SavedCollections.removal(owner,Collections.singletonList(saved("old",owner)),Collections.emptyList());assertEquals(Collections.singleton("old"),plan.delete);assertTrue(plan.shared.isEmpty());
    }
    @Test public void excludesMissingAndUnrelatedTracksFromDeletion()throws Exception{
        MusicEntity target=entity("Album","target"),other=entity("Playlist","other");SavedCollections.Removal plan=SavedCollections.removal(target,Collections.singletonList(saved("other",other)),Collections.singletonList(SavedCollections.metadata(target,Collections.singletonList(entity("Track","missing")))));assertTrue(plan.delete.isEmpty());assertTrue(plan.shared.isEmpty());
    }
    @Test public void reassignKeepsAudioVariantQualityAndSavedTimestamp()throws Exception{
        MusicEntity owner=entity("Album","old"),other=entity("Playlist","new");JSONObject item=saved("shared",owner).put("audioVariant","0123456789abcdef0123456789abcdef").put("audioQuality","HIGH").put("savedAt",123).put("audioGroup",2).put("audioStream",1);
        JSONObject changed=SavedCollections.reassign(item,other);for(String key:new String[]{"id","storage","audioVariant","audioQuality","savedAt","audioGroup","audioStream","bytes","artwork"})assertEquals(item.get(key).toString(),changed.get(key).toString());assertEquals("new",changed.getString("originId"));assertEquals("old",item.getString("originId"));assertFalse(changed.getBoolean("originSelectable"));
    }
}
