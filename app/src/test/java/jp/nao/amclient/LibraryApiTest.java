package jp.nao.amclient;

import java.io.IOException;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LibraryApiTest {
    private static final class Server implements LibraryApi.Requests {
        final String type;String id="fixture";boolean registered,owned,missingState,wrongResponse,wrongId,failAfterWrite;int reads,writes;String lastOperation,lastState;
        Server(String type){this.type=type;}
        JSONObject node(boolean permissions)throws Exception{
            JSONObject n=new JSONObject().put("__typename",type).put("id",id);
            if(!missingState)n.put("Album".equals(type)?"likesContext":"followsContext",new JSONObject().put("Album".equals(type)?"state":"isFollowed","Album".equals(type)?registered?"LIKE":"NEUTRAL":registered));
            if(permissions&&"Playlist".equals(type))n.put("eligibility",new JSONObject().put("playlist",owned?new JSONObject().put("canRenamePlaylist",true):JSONObject.NULL));
            return n;
        }
        MusicEntity item()throws Exception{return new MusicEntity(node(true));}
        @Override public JSONObject call(String operation,JSONObject variables)throws Exception{
            lastOperation=operation;
            if(operation.startsWith("NativeMembership")){reads++;return new JSONObject().put("Album".equals(type)?"album":"playlist",node(true));}
            writes++;lastState=variables.optString("state");registered="Album".equals(type)?"POSITIVE".equals(lastState):"NativeFollowPlaylist".equals(operation);
            if(failAfterWrite)throw new IOException("connection lost after write");
            JSONObject n=node(false);if(wrongId)n.put("id","other");
            if(wrongResponse)n.put("Album".equals(type)?"likesContext":"followsContext",new JSONObject().put("Album".equals(type)?"state":"isFollowed","Album".equals(type)?registered?"NEUTRAL":"LIKE":!registered));
            return new JSONObject().put("Album".equals(type)?"setAlbumLikeState":registered?"followPlaylist":"unfollowPlaylist","Album".equals(type)?new JSONObject().put("edges",new JSONArray().put(new JSONObject().put("node",n))):new JSONObject().put("playlist",n));
        }
    }
    @Test public void albumsUsePositiveToAddAndNeutralToRemoveAndConfirmState()throws Exception{
        Server s=new Server("Album");LibraryApi api=new LibraryApi(s);MusicEntity stale=s.item();
        assertTrue(api.set(stale,true).registered);assertEquals("POSITIVE",s.lastState);
        assertFalse(api.set(stale,false).registered);assertEquals("NEUTRAL",s.lastState);assertEquals(2,s.reads);assertEquals(2,s.writes);
    }
    @Test public void playlistsUseSeparateFollowAndUnfollowAndRetainCheckedPermission()throws Exception{
        Server s=new Server("Playlist");LibraryApi api=new LibraryApi(s);MusicEntity item=s.item();LibraryMembership added=api.set(item,true);
        assertTrue(added.registered&&added.canChange);assertEquals("NativeFollowPlaylist",s.lastOperation);
        LibraryMembership removed=api.set(item,false);assertFalse(removed.registered);assertTrue(removed.canChange);assertEquals("NativeUnfollowPlaylist",s.lastOperation);
    }
    @Test public void desiredStateUsesFreshReadInsteadOfTogglingStaleEntity()throws Exception{
        Server s=new Server("Album");MusicEntity old=s.item();s.registered=true;long revision=LibraryApi.revision.get();
        assertTrue(new LibraryApi(s).set(old,true).registered);assertEquals(1,s.reads);assertEquals(0,s.writes);assertTrue(LibraryApi.revision.get()>revision);
    }
    @Test public void freshOwnedPermissionBlocksFollowEvenWhenOldEntityWasNotOwned()throws Exception{
        Server s=new Server("Playlist");MusicEntity old=s.item();s.owned=true;
        assertThrows(IOException.class,()->new LibraryApi(s).set(old,true));assertEquals(0,s.writes);
    }
    @Test public void unknownStateAndDifferentReadTargetNeverWrite()throws Exception{
        Server s=new Server("Album");MusicEntity old=s.item();s.missingState=true;
        assertThrows(IOException.class,()->new LibraryApi(s).set(old,true));s.missingState=false;s.id="different";
        assertThrows(IOException.class,()->new LibraryApi(s).set(old,true));assertEquals(0,s.writes);
    }
    @Test public void missingOrMismatchedConfirmationIsNotSuccess()throws Exception{
        for(boolean wrongId:new boolean[]{false,true}){Server s=new Server("Playlist");s.wrongId=wrongId;s.wrongResponse=!wrongId;assertThrows(IOException.class,()->new LibraryApi(s).set(s.item(),true));assertEquals(1,s.writes);}
        Server s=new Server("Album");s.wrongId=true;assertThrows(IOException.class,()->new LibraryApi(s).set(s.item(),true));
    }
    @Test public void uncertainWriteInvalidatesLibraryWithoutAutomaticRetry()throws Exception{
        Server s=new Server("Album");s.failAfterWrite=true;long revision=LibraryApi.revision.get();LibraryApi api=new LibraryApi(s);
        assertThrows(IOException.class,()->api.set(s.item(),true));assertEquals(1,s.writes);assertTrue(s.registered);assertTrue(LibraryApi.revision.get()>revision);assertTrue(api.read(s.item()).registered);
    }
    @Test public void specialPlaylistsAndOtherEntityTypesCannotBeFollowed()throws Exception{
        for(String id:new String[]{"aefe265332c8475992f9302196517baf","PPABC123"}){Server s=new Server("Playlist");s.id=id;assertThrows(IOException.class,()->new LibraryApi(s).set(s.item(),true));assertEquals(0,s.writes);}
        Server s=new Server("Track");assertThrows(IOException.class,()->new LibraryApi(s).set(s.item(),true));assertEquals(0,s.reads);
    }
    @Test public void policyHandlesNullEligibilityAndRejectsMissingPermissionsOrNonBooleanState()throws Exception{
        Server s=new Server("Playlist");JSONObject n=s.node(true);assertTrue(LibraryMembership.from(n,false).canChange);
        n.remove("eligibility");assertFalse(LibraryMembership.from(n,false).canChange);
        n=s.node(true);n.getJSONObject("followsContext").put("isFollowed","false");assertFalse(LibraryMembership.from(n,false).known);
        assertFalse(LibraryMembership.from(s.node(true),true).canChange);
    }
    @Test public void removableSharedPlaylistIsNotMistakenForAnOwnedPlaylist()throws Exception{
        Server s=new Server("Playlist");JSONObject node=s.node(true).put("eligibility",new JSONObject().put("playlist",new JSONObject().put("canRenamePlaylist",false).put("canDeletePlaylist",true)));
        assertTrue(LibraryMembership.from(node,false).canChange);
    }
}
