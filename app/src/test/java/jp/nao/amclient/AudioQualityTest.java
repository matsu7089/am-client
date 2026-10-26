package jp.nao.amclient;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AudioQualityTest {
    @Test public void selectsNominalLevelsWithPeakBandwidths(){
        List<Integer> values=Arrays.asList(325207,52991,197105);
        assertEquals(1,AudioQuality.select(values,AudioQuality.LOW));assertEquals(2,AudioQuality.select(values,AudioQuality.MEDIUM));assertEquals(0,AudioQuality.select(values,AudioQuality.HIGH));
    }
    @Test public void fallsBackWhenRequestedLevelIsMissing(){
        assertEquals(0,AudioQuality.select(Arrays.asList(53000,325000),AudioQuality.MEDIUM));
        assertEquals(0,AudioQuality.select(Collections.singletonList(197000),AudioQuality.LOW));
        assertEquals(1,AudioQuality.select(Arrays.asList(-1,53000),AudioQuality.HIGH));
    }
    @Test public void invalidPreferencesUseTheSuppliedDefault(){assertEquals(AudioQuality.MEDIUM,AudioQuality.parse("future",AudioQuality.MEDIUM));assertEquals(AudioQuality.LOW,AudioQuality.parse(null,AudioQuality.LOW));}
    @Test public void usesQualityRankingWhenPeakBitratesOverlap(){
        List<Integer> peaks=Arrays.asList(53000,600000,325000);List<AudioQuality> levels=Arrays.asList(AudioQuality.LOW,AudioQuality.MEDIUM,AudioQuality.HIGH);
        assertEquals(1,AudioQuality.select(peaks,levels,AudioQuality.MEDIUM));assertEquals(2,AudioQuality.select(peaks,levels,AudioQuality.HIGH));
    }
}
