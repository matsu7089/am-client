package jp.nao.amclient;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CatalogPageTest {
    @Test public void usesPlaylistEdgePermissionsAndContainerWithoutMutatingCatalogTrack()throws Exception{
        JSONObject generic=new JSONObject().put("id","track").put("__typename","Track").put("title","曲")
            .put("playParams",new JSONObject().put("id","global").put("selections",new JSONArray().put("STATION")));
        JSONObject contextual=new JSONObject().put("id","contextual").put("containerIds",new JSONArray().put("playlist"))
            .put("selections",new JSONArray().put("PLAY"));
        CatalogPage page=CatalogPage.connection(null,new JSONObject().put("edges",new JSONArray().put(new JSONObject().put("node",generic).put("playParams",contextual))));
        assertTrue(page.items.get(0).hasSelection("PLAY"));assertEquals("playlist",page.items.get(0).playParams.getJSONArray("containerIds").getString(0));
        assertEquals("STATION",generic.getJSONObject("playParams").getJSONArray("selections").getString(0));
    }
    @Test public void handlesBothPaginationCursorShapesAndJsonNull()throws Exception{
        JSONObject info=new JSONObject().put("hasNextPage",true).put("token","token").put("endCursor",JSONObject.NULL),connection=new JSONObject().put("pageInfo",info);
        assertEquals("token",CatalogPage.cursor(connection));info.put("token",JSONObject.NULL).put("endCursor","end");assertEquals("end",CatalogPage.cursor(connection));
        info.put("endCursor",JSONObject.NULL);assertEquals("",CatalogPage.cursor(connection));info.put("token","unused").put("hasNextPage",false);assertEquals("",CatalogPage.cursor(connection));
    }
    @Test public void mergesPagesWithoutDuplicatingCatalogItemsOrChangingOrder()throws Exception{
        MusicEntity a=new MusicEntity(new JSONObject().put("id","a").put("title","first")),duplicate=new MusicEntity(new JSONObject().put("id","a").put("title","duplicate")),b=new MusicEntity(new JSONObject().put("id","b"));
        List<MusicEntity> result=CatalogPage.append(Collections.singletonList(a),Arrays.asList(duplicate,b));
        assertEquals(2,result.size());assertEquals("first",result.get(0).title);assertEquals("b",result.get(1).id);
    }
    @Test public void ignoresNullEdgesAndUnionMembersWithoutAnEntityId()throws Exception{
        JSONObject connection=new JSONObject().put("edges",new JSONArray().put(JSONObject.NULL).put(new JSONObject().put("node",new JSONObject().put("__typename","Other"))));
        assertTrue(CatalogPage.connection(null,connection).items.isEmpty());assertTrue(CatalogPage.connection(null,null).items.isEmpty());
    }
}
