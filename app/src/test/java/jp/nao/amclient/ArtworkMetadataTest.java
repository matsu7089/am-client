package jp.nao.amclient;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkMetadataTest {
    private MusicEntity fresh(String id,String type,String url)throws Exception{
        return new MusicEntity(new JSONObject().put("__typename",type).put("id",id).put("images",new JSONArray().put(new JSONObject().put("url",url).put("width",500).put("height",500).put("imageType","PRIMARY"))));
    }
    @Test public void repairsOnlyArtworkAndPreservesHistoryOrderAndCollectionMembership()throws Exception{
        JSONObject node=new JSONObject().put("__typename","Playlist").put("id","playlist").put("title","Original").put("playedAt",1234).put("images",new JSONArray().put(new JSONObject().put("url","https://example.org/logo.jpg")));
        JSONObject index=new JSONObject().put("collection",node).put("trackIds",new JSONArray().put("one").put("two"));
        MusicEntity cover=fresh("playlist","Playlist","https://example.org/cover.jpg");
        assertTrue(ArtworkMetadata.update(node,cover));
        assertEquals("Original",node.getString("title"));assertEquals(1234,node.getLong("playedAt"));assertEquals("[\"one\",\"two\"]",index.getJSONArray("trackIds").toString());
        assertEquals(cover.artworkUrl,new MusicEntity(node).artworkUrl);assertEquals(ArtworkMetadata.VERSION,new MusicEntity(node).artworkVersion);
        assertFalse(ArtworkMetadata.update(node,fresh("playlist","Playlist","https://example.org/later.jpg")));
        assertEquals(cover.artworkUrl,new MusicEntity(node).artworkUrl);
    }
    @Test public void leavesOtherTargetsOrUnavailableArtworkUnchanged()throws Exception{
        JSONObject node=new JSONObject().put("__typename","Playlist").put("id","playlist").put("playedAt",1234);
        String original=node.toString();
        assertFalse(ArtworkMetadata.update(node,fresh("other","Playlist","https://example.org/cover.jpg")));
        assertFalse(ArtworkMetadata.update(node,fresh("playlist","Album","https://example.org/cover.jpg")));
        assertFalse(ArtworkMetadata.update(node,fresh("playlist","Playlist","http://example.org/cover.jpg")));
        assertEquals(original,node.toString());
    }
}
