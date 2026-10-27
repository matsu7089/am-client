package jp.nao.amclient;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class HistoryRecordsTest {
    private MusicEntity entity(String type,String id,String title)throws Exception{return new MusicEntity(new JSONObject().put("__typename",type).put("id",id).put("title",title).put("playParams",new JSONObject().put("id","private-command").put("selections",new JSONArray().put("PLAY"))));}
    @Test public void retainsFiftyUniqueSourcesAndEvictsTheOldest()throws Exception{
        JSONArray history=new JSONArray();for(int i=0;i<60;i++)history=HistoryRecords.record(history,entity("Album","album"+i,"Album "+i),i);
        assertEquals(50,history.length());assertEquals("album59",history.getJSONObject(0).getString("id"));assertEquals("album10",history.getJSONObject(49).getString("id"));
        history=HistoryRecords.record(history,entity("Album","album10","Updated title"),100);
        assertEquals(50,history.length());assertEquals("album10",history.getJSONObject(0).getString("id"));assertEquals("Updated title",history.getJSONObject(0).getString("title"));
        history=HistoryRecords.record(history,entity("Playlist","new","New"),101);assertEquals(50,history.length());assertEquals("album12",history.getJSONObject(49).getString("id"));
    }
    @Test public void distinguishesTypesAndPreservesNewerMetadataFromDelayedWrites()throws Exception{
        JSONArray history=HistoryRecords.record(new JSONArray(),entity("Album","same","Fresh"),20);
        history=HistoryRecords.record(history,entity("Artist","same","Artist"),30);
        history=HistoryRecords.record(history,entity("Album","same","Stale"),10);
        history=HistoryRecords.record(history,entity("Playlist","same","Playlist"),25);
        assertEquals(3,history.length());assertEquals("Artist",history.getJSONObject(0).getString("__typename"));assertEquals("Playlist",history.getJSONObject(1).getString("__typename"));assertEquals("Fresh",history.getJSONObject(2).getString("title"));
    }
    @Test public void usesTheLatestInsertionForEqualTimestampsAndSkipsUnsupportedItems()throws Exception{
        JSONArray history=HistoryRecords.record(new JSONArray(),entity("Album","first","First"),1);
        history=HistoryRecords.record(history,entity("Playlist","second","Second"),1);
        history=HistoryRecords.record(history,entity("Track","track","Track"),2);
        history=HistoryRecords.record(history,entity("Station","station","Station"),3);
        history=HistoryRecords.record(history,entity("Artist","","Invalid"),4);
        assertEquals(2,history.length());assertEquals("second",history.getJSONObject(0).getString("id"));
    }
    @Test public void cleansMalformedAndUnsortedInput()throws Exception{
        JSONArray old=new JSONArray().put("broken").put(new JSONObject()).put(new JSONObject().put("__typename","Track").put("id","track").put("playedAt",100));
        old.put(HistoryRecords.record(new JSONArray(),entity("Album","old","Old"),10).get(0));old.put(HistoryRecords.record(new JSONArray(),entity("Album","new","New"),20).get(0));
        List<MusicEntity> list=HistoryRecords.list(old);assertEquals(2,list.size());assertEquals("new",list.get(0).id);
    }
    @Test public void storesDisplayMetadataWithoutAPlayCommand()throws Exception{
        JSONObject item=HistoryRecords.record(new JSONArray(),entity("Artist","artist","Artist"),1).getJSONObject(0);
        assertFalse(item.has("playParams"));assertEquals("Artist",new MusicEntity(item).title);
    }
    @Test public void keepsTheSelectedSourceEvenIfTheServerChoosesRelatedMusic()throws Exception{
        MusicEntity track=new MusicEntity(new JSONObject().put("__typename","Track").put("id","track").put("album",new JSONObject().put("__typename","Album").put("id","actual-album")));
        for(String type:new String[]{"Album","Playlist","Artist"}){MusicEntity chosen=entity(type,"selected","Selected");assertSame(chosen,HistoryRecords.source(chosen,track));}
        assertEquals("actual-album",HistoryRecords.source(track,track).id);assertNull(HistoryRecords.source(null,null));
    }
    @Test public void savedAudioKeepsItsOriginalCollection()throws Exception{
        MusicEntity source=HistoryRecords.savedSource(new JSONObject().put("originType","Playlist").put("originId","playlist").put("originTitle","Saved playlist"));
        assertEquals("playlist",source.id);assertEquals("Saved playlist",source.title);assertNull(HistoryRecords.savedSource(new JSONObject()));
    }
    @Test public void migratesTheOldLibraryTrackTabToHistory(){
        assertEquals(LibraryCategory.HISTORY,LibraryCategory.restore("TRACKS"));assertEquals(LibraryCategory.HISTORY,LibraryCategory.restore("ARTISTS"));assertEquals(LibraryCategory.ALBUMS,LibraryCategory.restore("ALBUMS"));assertEquals(LibraryCategory.PLAYLISTS,LibraryCategory.restore("PLAYLISTS"));
    }
    @Test public void removesOnlyTheSelectedTypeAndIdWithoutChangingOtherOrder()throws Exception{
        JSONArray history=new JSONArray();for(String type:new String[]{"Album","Playlist","Artist"})history=HistoryRecords.record(history,entity(type,"same",type),history.length()+1);
        JSONArray removed=HistoryRecords.remove(history,entity("Playlist","same","Ignored"));assertEquals(2,removed.length());assertEquals("Artist",removed.getJSONObject(0).getString("__typename"));assertEquals("Album",removed.getJSONObject(1).getString("__typename"));assertEquals(3,removed.getJSONObject(0).getLong("playedAt"));
    }
    @Test public void removesEveryDuplicateAndAllowsAFuturePlayToReturn()throws Exception{
        MusicEntity album=entity("Album","album","Album");JSONArray history=HistoryRecords.record(new JSONArray(),album,1);history.put(history.getJSONObject(0));assertEquals(0,HistoryRecords.remove(history,album).length());
        JSONArray replayed=HistoryRecords.record(HistoryRecords.remove(history,album),album,2);assertEquals(1,replayed.length());assertEquals(2,replayed.getJSONObject(0).getLong("playedAt"));
    }
    @Test public void deletingUnknownHistoryKeepsRemainingMetadata()throws Exception{
        JSONArray history=HistoryRecords.record(new JSONArray(),entity("Artist","person","Person"),8);assertEquals(history.toString(),HistoryRecords.remove(history,entity("Album","absent","Absent")).toString());
    }
    @Test public void migratesOldDownloadSelectionsToAlbums(){
        for(String value:new String[]{"TRACKS","ARTISTS","invalid",null})assertEquals(DownloadCategory.ALBUMS,DownloadCategory.restore(value));assertEquals(DownloadCategory.PLAYLISTS,DownloadCategory.restore("PLAYLISTS"));assertEquals(2,DownloadCategory.values().length);
    }
}
