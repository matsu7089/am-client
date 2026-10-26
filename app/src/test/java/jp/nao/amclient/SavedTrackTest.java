package jp.nao.amclient;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SavedTrackTest {
    private MusicEntity entity(String type,String id,String selection)throws Exception{return new MusicEntity(new JSONObject().put("__typename",type).put("id",id).put("title",id).put("playParams",new JSONObject().put("id",id).put("selections",new JSONArray().put(selection))));}
    @Test public void storesCollectionContextWithoutAnOfflineLicense()throws Exception{
        MusicEntity track=entity("Track","track","PLAY"),owner=entity("Playlist","playlist","PLAY");
        JSONObject item=SavedTrack.metadata(track,owner,1234);assertTrue(SavedTrack.isStream(item));assertFalse(item.has("keySet"));assertEquals(1234,item.getLong("bytes"));
        assertEquals("Playlist",SavedTrack.origin(item).type);assertEquals("playlist",SavedTrack.origin(item).id);assertEquals("track",item.getString("id"));
        assertFalse(SavedTrack.isStream(new JSONObject().put("keySet","legacy")));assertNull(SavedTrack.origin(new JSONObject().put("originType","Artist").put("originId","artist")));
    }
    @Test public void usesFreshServerTrackSelectionForOnDemandCollections()throws Exception{
        MusicEntity owner=entity("Playlist","playlist","PLAY"),track=entity("Track","track","PLAY");
        assertSame(track,SavedTrack.selection(new CatalogPage(owner,Collections.singletonList(track),""),"track"));
        assertSame(owner,SavedTrack.selection(new CatalogPage(owner,Collections.singletonList(track),""),"removed-track"));
    }
    @Test public void respectsShuffleOnlyCollectionsAndContextSpecificTrackRestrictions()throws Exception{
        MusicEntity owner=entity("Album","album","SHUFFLE"),track=entity("Track","track","PLAY");
        assertSame(owner,SavedTrack.selection(new CatalogPage(owner,Collections.singletonList(track),""),"track"));
        MusicEntity onDemand=entity("Playlist","playlist","PLAY"),restricted=entity("Track","track","PREVIEW");
        assertSame(onDemand,SavedTrack.selection(new CatalogPage(onDemand,Collections.singletonList(restricted),""),"track"));
    }
    @Test public void isolatesCacheEntriesByAccountAndTrack(){
        String first=SavedTrack.cachePrefix("account-a","track-a");assertFalse(SavedTrack.cachePrefix("account-b","track-a").startsWith(first));assertFalse(SavedTrack.cachePrefix("account-a","track-b").startsWith(first));
        for(String id:new String[]{"../track","track/file","","track?token=secret"})try{SavedTrack.cachePrefix("account",id);fail("Unsafe id");}catch(IllegalArgumentException expected){}
    }
    @Test public void reusesAudioWhilePreferringASelectableCollectionContext()throws Exception{
        MusicEntity track=entity("Track","track","PLAY"),album=entity("Album","album","SHUFFLE"),playlist=entity("Playlist","playlist","PLAY");
        JSONObject original=SavedTrack.metadata(track,album,4567);long savedAt=original.getLong("savedAt");
        JSONObject upgraded=SavedTrack.refreshContext(original,track,playlist);assertEquals("playlist",SavedTrack.origin(upgraded).id);assertEquals(4567,upgraded.getLong("bytes"));assertEquals(savedAt,upgraded.getLong("savedAt"));
        assertSame(upgraded,SavedTrack.refreshContext(upgraded,track,album));
    }
    @Test public void keepsQualityAndImmutableSourceWhenRefreshingContext()throws Exception{
        MusicEntity track=entity("Track","track","PLAY"),owner=entity("Playlist","playlist","PLAY");
        JSONObject item=SavedTrack.metadata(track,owner,555).put("audioVariant","0123456789abcdef0123456789abcdef").put("audioQuality","HIGH").put("requestedQuality","HIGH").put("audioGroup",0).put("audioStream",2);
        JSONObject refreshed=SavedTrack.refreshContext(item,track,owner);
        assertEquals(AudioQuality.HIGH,SavedTrack.quality(refreshed));assertEquals(item.getString("audioVariant"),refreshed.getString("audioVariant"));assertEquals(2,refreshed.getInt("audioStream"));
        assertEquals("manifest-0123456789abcdef0123456789abcdef.mpd",SavedTrack.manifestName(refreshed));
        assertEquals(AudioQuality.LOW,SavedTrack.requested(new JSONObject()));assertEquals("manifest.mpd",SavedTrack.manifestName(new JSONObject()));
        try{SavedTrack.manifestName(new JSONObject().put("audioVariant","../file"));fail("Unsafe saved path");}catch(IllegalArgumentException expected){}
    }
}
