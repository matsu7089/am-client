package jp.nao.amclient;

import androidx.media3.common.Player;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class QueueModesTest {
    private JSONObject capabilities(boolean shuffle,boolean all,boolean one)throws Exception{
        return new JSONObject().put("capabilities",new JSONObject()
            .put("shuffle",new JSONObject().put("isAllowed",shuffle))
            .put("loopAll",new JSONObject().put("isAllowed",all))
            .put("loopOne",new JSONObject().put("isAllowed",one)));
    }
    @Test public void allAccessCyclesOffAlbumOneOff()throws Exception{
        QueueModes modes=QueueModes.from(capabilities(true,true,false),capabilities(false,false,true));
        assertTrue(modes.shuffle);assertTrue(modes.all);assertTrue(modes.one);
        assertEquals(Player.REPEAT_MODE_ALL,modes.next(Player.REPEAT_MODE_OFF));
        assertEquals(Player.REPEAT_MODE_ONE,modes.next(Player.REPEAT_MODE_ALL));
        assertEquals(Player.REPEAT_MODE_OFF,modes.next(Player.REPEAT_MODE_ONE));
    }
    @Test public void stationCannotGainModesFromSelectableSearchResult()throws Exception{
        QueueModes modes=QueueModes.from(capabilities(false,false,false),capabilities(false,false,false));
        assertFalse(modes.shuffle);assertFalse(modes.allows(Player.REPEAT_MODE_ALL));assertFalse(modes.allows(Player.REPEAT_MODE_ONE));
        assertEquals(Player.REPEAT_MODE_OFF,modes.next(Player.REPEAT_MODE_OFF));assertTrue(modes.allows(Player.REPEAT_MODE_OFF));
    }
    @Test public void currentTrackAndQueuePermissionsAreIndependent()throws Exception{
        QueueModes one=QueueModes.from(capabilities(false,false,true),capabilities(false,true,true));
        assertFalse(one.all);assertTrue(one.one);assertEquals(Player.REPEAT_MODE_ONE,one.next(Player.REPEAT_MODE_OFF));
        QueueModes all=QueueModes.from(capabilities(true,true,true),capabilities(false,true,false));
        assertTrue(all.all);assertFalse(all.one);assertEquals(Player.REPEAT_MODE_OFF,all.next(Player.REPEAT_MODE_ALL));
    }
    @Test public void missingPermissionsStayDisabled(){
        QueueModes modes=QueueModes.from(null,null);assertFalse(modes.shuffle);assertFalse(modes.all);assertFalse(modes.one);
        assertFalse(modes.allows(99));assertEquals(Player.REPEAT_MODE_OFF,modes.next(Player.REPEAT_MODE_OFF));
    }
    @Test public void modesMapToServerValues(){
        assertEquals("LOOP_OFF",QueueModes.remoteLoop(Player.REPEAT_MODE_OFF));
        assertEquals("LOOP_ALL",QueueModes.remoteLoop(Player.REPEAT_MODE_ALL));
        assertEquals("LOOP_ONE",QueueModes.remoteLoop(Player.REPEAT_MODE_ONE));
    }
    @Test(expected=IllegalArgumentException.class)public void rejectsUnknownMode(){QueueModes.remoteLoop(99);}
}
