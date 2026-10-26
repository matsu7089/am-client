package jp.nao.amclient;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PlaylistPolicyTest {
    private MusicEntity song(String id,String entry)throws Exception{return MusicEntity.fromEdge(new JSONObject().put("itemId",entry).put("node",new JSONObject().put("__typename","Track").put("id",id)));}
    private CatalogPage playlist()throws Exception{return new CatalogPage(new MusicEntity(new JSONObject().put("__typename","Playlist").put("id","list").put("uri","playlist-uri").put("version","v2")),Arrays.asList(song("same","one"),song("other","two"),song("same","three")),"");}
    @Test public void unknownPermissionsDenyEveryEdit(){PlaylistPolicy p=PlaylistPolicy.from(new JSONObject());assertFalse(p.editable());assertFalse(p.add);assertFalse(p.remove);}
    @Test public void trackChangesRequireEligibilityAndCurrentCapability()throws Exception{
        JSONObject node=new JSONObject("{eligibility:{playlist:{canAddTracks:true,canRemoveTracks:true,canRenamePlaylist:true,canDeletePlaylist:true,canReorderTracks:true}},capabilities:{appendTracks:{isAllowed:false},removeTracks:{isAllowed:true}}}");PlaylistPolicy p=PlaylistPolicy.from(node);assertFalse(p.add);assertTrue(p.remove);assertTrue(p.rename);assertTrue(p.delete);assertTrue(p.reorder);node.remove("capabilities");p=PlaylistPolicy.from(node);assertFalse(p.add);assertFalse(p.remove);
    }
    @Test public void duplicateSongsKeepSeparateEntriesAcrossPages()throws Exception{CatalogPage p=playlist();List<MusicEntity> result=CatalogPage.append(p.items.subList(0,2),p.items.subList(1,3));assertEquals(3,result.size());assertEquals("one",result.get(0).playlistEntryId);assertEquals("three",result.get(2).playlistEntryId);}
    @Test public void removeTargetsSelectedDuplicateWithVersion()throws Exception{JSONObject input=PlaylistPolicy.remove(playlist(),"three");assertEquals("playlist-uri",input.getString("id"));assertEquals("v2",input.getString("version"));JSONObject item=input.getJSONArray("updates").getJSONObject(0).getJSONObject("removeItems").getJSONArray("items").getJSONObject(0).getJSONObject("item");assertEquals("three",item.getString("itemId"));assertEquals(2,item.getInt("possibleIndex"));assertFalse(input.toString().contains("same"));}
    @Test public void movesUseAdjacentEntryAndCorrectDirection()throws Exception{JSONObject move=PlaylistPolicy.move(playlist(),"three",-1).getJSONArray("updates").getJSONObject(0).getJSONObject("moveItems");assertEquals("Before",move.getJSONObject("location").getString("direction"));assertEquals("two",move.getJSONObject("location").getJSONObject("reference").getJSONObject("item").getString("itemId"));move=PlaylistPolicy.move(playlist(),"one",1).getJSONArray("updates").getJSONObject(0).getJSONObject("moveItems");assertEquals("After",move.getJSONObject("location").getString("direction"));}
    @Test public void nameIsTrimmedAndUnicodePreserved()throws Exception{JSONObject input=PlaylistPolicy.rename(playlist().owner,"  新しい名前 🎵  ");assertEquals("新しい名前 🎵",input.getJSONArray("updates").getJSONObject(0).getJSONObject("editTitle").getString("title"));}
    @Test(expected=IllegalArgumentException.class) public void rejectsBlankName(){PlaylistPolicy.title("  \n ");}
    @Test(expected=IllegalArgumentException.class) public void rejectsOverlongName(){PlaylistPolicy.title(String.join("",Collections.nCopies(201,"曲")));}
    @Test(expected=IllegalArgumentException.class) public void rejectsStaleEntry()throws Exception{PlaylistPolicy.remove(playlist(),"missing");}
    @Test(expected=IllegalArgumentException.class) public void rejectsSongIdAsRemovalTarget()throws Exception{PlaylistPolicy.remove(playlist(),"same");}
    @Test(expected=IllegalArgumentException.class) public void refusesMoveBeforeFirst()throws Exception{PlaylistPolicy.move(playlist(),"one",-1);}
    @Test(expected=IllegalArgumentException.class) public void refusesMoveAfterLast()throws Exception{PlaylistPolicy.move(playlist(),"three",1);}
}
