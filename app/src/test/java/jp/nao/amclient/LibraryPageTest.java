package jp.nao.amclient;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LibraryPageTest {
    private JSONObject edge(String id,boolean followed)throws Exception{return new JSONObject().put("node",new JSONObject().put("__typename","Playlist").put("id",id).put("title",id).put("followsContext",new JSONObject().put("isFollowed",followed)));}
    private JSONObject connection(JSONArray edges,String cursor)throws Exception{return new JSONObject().put("edges",edges).put("pageInfo",new JSONObject().put("hasNextPage",!cursor.isEmpty()).put("token",cursor));}
    @Test public void likedAlbumsExcludeNeutralOrMissingNodesAndKeepServerCursor()throws Exception{
        JSONObject liked=new JSONObject().put("__typename","Album").put("id","liked").put("likesContext",new JSONObject().put("state","LIKE"));
        JSONObject removed=new JSONObject(liked.toString()).put("id","removed").put("likesContext",new JSONObject().put("state","NEUTRAL"));
        CatalogPage page=LibraryPage.albums(new JSONObject().put("likedAlbums",connection(new JSONArray().put(new JSONObject().put("node",liked)).put(new JSONObject().put("node",removed)).put(new JSONObject().put("node",JSONObject.NULL)),"next")));
        assertEquals(1,page.items.size());assertEquals("liked",page.items.get(0).id);assertEquals("next",page.cursor);
    }
    @Test public void emptyLikedPageStillAllowsLoadingNextPage()throws Exception{
        CatalogPage page=LibraryPage.albums(new JSONObject().put("likedAlbums",connection(new JSONArray(),"next")));
        assertTrue(page.items.isEmpty());assertEquals("next",page.cursor);assertTrue(LibraryPage.albums(new JSONObject()).items.isEmpty());
    }
    @Test public void combinesOwnedAndFollowedWithoutDuplicatesOrUnfollowedItems()throws Exception{
        JSONObject user=new JSONObject().put("playlists",connection(new JSONArray().put(edge("owned",true)),""))
            .put("followedPlaylists",connection(new JSONArray().put(edge("owned",true)).put(edge("followed",true)).put(edge("removed",false)),""));
        CatalogPage page=LibraryPage.playlists(user);assertEquals(2,page.items.size());assertEquals("owned",page.items.get(0).id);assertEquals("followed",page.items.get(1).id);assertEquals("",page.cursor);assertTrue(page.items.get(0).ownedPlaylist);assertFalse(page.items.get(1).ownedPlaylist);
    }
    @Test public void onlyRequestsConnectionsWithRemainingPages()throws Exception{
        CatalogPage page=LibraryPage.playlists(new JSONObject().put("playlists",connection(new JSONArray(),""))
            .put("followedPlaylists",connection(new JSONArray(),"next-followed")));
        JSONObject variables=LibraryPage.playlistVariables(page.cursor);assertFalse(variables.getBoolean("owned"));assertTrue(variables.getBoolean("followed"));assertEquals("next-followed",variables.getString("followedCursor"));assertFalse(variables.has("ownedCursor"));
        assertEquals("",LibraryPage.playlists(new JSONObject().put("followedPlaylists",connection(new JSONArray(),""))).cursor);
    }
    @Test public void startsBothConnectionsAndPreservesIndependentCursors()throws Exception{
        JSONObject initial=LibraryPage.playlistVariables("");assertTrue(initial.getBoolean("owned"));assertTrue(initial.getBoolean("followed"));
        CatalogPage page=LibraryPage.playlists(new JSONObject().put("playlists",connection(new JSONArray(),"next-owned"))
            .put("followedPlaylists",connection(new JSONArray(),"next-followed")));
        JSONObject next=LibraryPage.playlistVariables(page.cursor);assertEquals("next-owned",next.getString("ownedCursor"));assertEquals("next-followed",next.getString("followedCursor"));
    }
}
