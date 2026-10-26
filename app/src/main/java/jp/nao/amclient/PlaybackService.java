package jp.nao.amclient;

import android.app.PendingIntent;
import android.content.*;
import android.os.*;
import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.drm.*;
import androidx.media3.session.*;
import org.json.*;
import java.util.concurrent.*;
import com.google.common.util.concurrent.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlaybackService extends MediaSessionService {
    public class LocalBinder extends Binder {public PlaybackService service(){return PlaybackService.this;}}
    private final LocalBinder binder=new LocalBinder();
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    public ExoPlayer player;
    private MediaSession session;
    private RemoteQueuePlayer transport;
    private WidgetControls widget;
    private AmazonApi api;
    private JSONObject queue,edge;
    private boolean offline,changing,pendingEnd,restoreOne,savedAudio;
    public volatile String title="曲を選んでください",artist="",artworkUrl="",contentId="",message="";
    public volatile boolean canSeek=false,canNext=false,canPrevious=false,busy=false;
    public QueueModes modes=QueueModes.NONE;
    public boolean shuffleEnabled,modeUpdating;
    public int repeatMode=Player.REPEAT_MODE_OFF;
    public String containerType="";
    public MusicEntity currentTrack;
    public PlaybackOrigin origin;
    private int generation;
    private boolean qualityUpdating;
    private OfflineStore.SavedAudio cachedLease;
    private DashPackage currentDash;
    private MusicEntity historySource;
    private String historyScope="signed-out";
    private boolean historyPending;
    private boolean savedQueue;
    private NormalizationAudioProcessor normalizer;
    private LoudnessStore loudnessStore;
    private final SharedPreferences.OnSharedPreferenceChangeListener normalizationChanged=(prefs,key)->refreshNormalization();
    public boolean requiresSavedAudio(){return savedQueue;}
    public String audioDescription(){return currentDash==null?"":currentDash.quality.kbps+" kbps";}
    @Override public void onCreate() {
        super.onCreate();api=AmazonApi.get(this);
        normalizer=new NormalizationAudioProcessor();loudnessStore=new LoudnessStore(this);
        DefaultRenderersFactory renderers=new DefaultRenderersFactory(this){
            @Override protected androidx.media3.exoplayer.audio.AudioSink buildAudioSink(Context context,boolean floatOutput,boolean playbackParams){
                return new NormalizationAudioSink(context,normalizer);
            }
        };
        player=new ExoPlayer.Builder(this,renderers).build();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setAudioOffloadPreferences(
            new TrackSelectionParameters.AudioOffloadPreferences.Builder().setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED).build()).build());
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        getSharedPreferences("normalization",0).registerOnSharedPreferenceChangeListener(normalizationChanged);
        PendingIntent activity=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        transport=new RemoteQueuePlayer(player,this::next,new RemoteQueuePlayer.Modes(){
            public ListenableFuture<?> shuffle(boolean enabled){return setShuffle(enabled);}
            public ListenableFuture<?> repeat(int mode){return setRepeat(mode);}
        });
        widget=new WidgetControls(transport);
        session=new MediaSession.Builder(this,transport).setSessionActivity(activity).setCallback(widget).build();
        widget.refresh(session);
        addSession(session);
        player.addListener(new Player.Listener(){
            @Override public void onIsPlayingChanged(boolean playing) {if(playing&&!changing)recordHistory();if(!offline&&!changing&&edge!=null&&player.getPlaybackState()!=Player.STATE_ENDED)report(playing?"PLAYING":"PAUSED");}
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_READY&&restoreOne&&!changing)report(player.isPlaying()?"PLAYING":"PAUSED");
                if(state==Player.STATE_ENDED&&!offline&&!changing)ended();
            }
            @Override public void onMediaMetadataChanged(MediaMetadata metadata){
                MediaItem item=player.getCurrentMediaItem();if(item==null||!contentId.equals(item.mediaId))return;
                if(metadata.title!=null)title=metadata.title.toString();if(metadata.artist!=null)artist=metadata.artist.toString();
                artworkUrl=metadata.artworkUri==null?"":metadata.artworkUri.toString();
            }
            @Override public void onPlayerError(PlaybackException error){transport.endAutomaticTransition();android.util.Log.e("AMPlayback","Playback failed at "+player.getCurrentPosition()+"ms: "+error.getErrorCodeName(),error);message=savedAudio?(error.getErrorCodeName().startsWith("ERROR_CODE_DRM")?"保存音源の再生ライセンスを取得できませんでした。通信と利用権限を確認してください。":"保存した音源を再生できませんでした（"+error.getErrorCodeName()+"）。通信状態と保存データを確認してください。"):"再生できませんでした（"+error.getErrorCodeName()+"）";busy=false;if(restoreOne){restoreOne=false;modeUpdating=false;repeatMode=Player.REPEAT_MODE_OFF;}refreshControls();}
        });
        main.postDelayed(tick,15000);
        main.postDelayed(saveLoudness,5000);
    }
    public void refreshNormalization(){normalizer.settings(NormalizationSettings.enabled(this),NormalizationSettings.target(this));}
    NormalizationAudioProcessor.Snapshot normalizationSnapshot(){return normalizer.snapshot();}
    private final Runnable saveLoudness=new Runnable(){public void run(){loudnessStore.save(normalizer.snapshot());main.postDelayed(this,5000);}};
    private final Runnable tick=new Runnable(){public void run(){if(player!=null&&player.isPlaying()&&!offline)report("PLAYING");main.postDelayed(this,15000);}};
    @Override public IBinder onBind(Intent intent){return "local".equals(intent.getAction())?binder:super.onBind(intent);}
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controller){return session;}
    public void play(MusicEntity entity) {play(entity,false,entity.type,PlaybackOrigin.of(entity),entity,false);}
    public void playRandom(MusicEntity entity){play(entity,true,entity.type,PlaybackOrigin.of(entity),entity,false);}
    public void playFromCollection(MusicEntity entity,MusicEntity collection){play(entity,false,collection.type,PlaybackOrigin.of(collection),collection,false);}
    public void playSavedFromCollection(MusicEntity entity,MusicEntity collection){play(entity,false,collection.type,PlaybackOrigin.of(collection),collection,true);}
    public void playHistory(MusicEntity item){
        if(busy||modeUpdating)return;switching();final int token=++generation;
        work.execute(()->{try{MusicEntity fresh=api.historyEntity(item);prepareQueue(fresh,true,fresh.type,PlaybackOrigin.of(fresh),fresh,token,false);}catch(Exception issue){fail(issue,token);}});
    }
    private void play(MusicEntity entity,boolean random,String type,PlaybackOrigin source,MusicEntity selected,boolean saved) {
        if(busy||modeUpdating)return;switching();final int token=++generation;
        work.execute(()->{try{prepareQueue(entity,random,type,source,selected,token,saved);}catch(Exception e){fail(e,token);}});
    }
    private void prepareQueue(MusicEntity entity,boolean random,String type,PlaybackOrigin source,MusicEntity selected,int token,boolean requireSaved)throws Exception{
        JSONObject q=api.createQueue(entity,random),e=q.getJSONObject("current").getJSONArray("edges").getJSONObject(0);
        String scope=OfflineStore.scope(api.config().getString("customerId"));
        String id=e.getJSONObject("node").getString("id");MusicEntity metadata=api.metadata(id);AudioSource audio=audioSource(id,requireSaved);
        main.post(()->{if(token!=generation){audio.close();return;}queue=q;edge=e;offline=false;savedQueue=requireSaved;origin=source;containerType=type;shuffleEnabled=random||!entity.hasSelection("PLAY")&&entity.hasSelection("SHUFFLE");repeatMode=Player.REPEAT_MODE_OFF;contentId=id;
            getSharedPreferences("account",0).edit().putString("scope",scope).apply();historySource=HistoryRecords.source(selected,metadata);historyScope=scope;historyPending=historySource!=null;
            currentTrack=metadata;title=metadata.title;artist=metadata.subtitle;artworkUrl=metadata.artworkUrl;capabilities();start(audio,0);});
    }
    private void recordHistory(){
        if(!historyPending||historySource==null)return;historyPending=false;MusicEntity source=historySource;String scope=historyScope;long time=System.currentTimeMillis();
        work.execute(()->{try{new HistoryStore(this,scope).record(source,time);}catch(Exception issue){android.util.Log.w("AMPlayback","History could not be saved");}});
    }
    private static final class AudioSource implements AutoCloseable{final DashPackage dash;final OfflineStore.SavedAudio saved;AudioSource(DashPackage dash,OfflineStore.SavedAudio saved){this.dash=dash;this.saved=saved;}public void close(){if(saved!=null)saved.close();}}
    private AudioSource audioSource(String id,boolean requireSaved)throws Exception{
        return audioSource(id,requireSaved,QualitySettings.playback(this));
    }
    private AudioSource audioSource(String id,boolean requireSaved,AudioQuality quality)throws Exception{
        OfflineStore store=new OfflineStore(this);
        OfflineStore.SavedAudio saved=store.openIfSaved(id);
        if(saved!=null)return new AudioSource(saved.dash,saved);
        if(requireSaved)throw new java.io.IOException("この曲の音源は保存されていません。アルバム・プレイリストのページからダウンロードしてください。");
        return new AudioSource(new DashPackage(api.manifest(id),quality),null);
    }
    public boolean changeQuality(AudioQuality quality){
        if(busy||modeUpdating){android.widget.Toast.makeText(this,"処理の完了後に音質を変更してください",android.widget.Toast.LENGTH_SHORT).show();return false;}
        if(savedAudio||savedQueue){QualitySettings.set(this,"playback",quality);if(currentDash!=null&&currentDash.quality!=quality)android.widget.Toast.makeText(this,"保存音源の音質は、再ダウンロードで変更できます",android.widget.Toast.LENGTH_SHORT).show();return true;}
        if(contentId.isEmpty()||currentDash==null||offline||!canSeek){QualitySettings.set(this,"playback",quality);if(!contentId.isEmpty())message="再生音質は次の曲から適用します";return true;}
        if(currentDash.quality==quality){QualitySettings.set(this,"playback",quality);return true;}
        qualityUpdating=true;busy=true;refreshControls();message="音質を変更しています…";final int token=generation;final String id=contentId;
        work.execute(()->{try{
            AudioSource audio=audioSource(id,false,quality);
            main.post(()->{if(token!=generation){audio.close();return;}qualityUpdating=false;busy=false;QualitySettings.set(this,"playback",quality);
                if(pendingEnd&&player.getPlaybackState()==Player.STATE_ENDED){audio.close();pendingEnd=false;ended();return;}
                long position=Math.max(0,player.getCurrentPosition());transport.beginAutomaticTransition(player.getPlayWhenReady());changing=true;start(audio,position);
            });
        }catch(Exception issue){main.post(()->{if(token!=generation)return;qualityUpdating=false;busy=false;refreshControls();message="音質を変更できませんでした。再試行してください。";if(pendingEnd&&player.getPlaybackState()==Player.STATE_ENDED){pendingEnd=false;ended();}});}});return true;
    }
    public void next(boolean previous) {advance(previous,false);}
    private void ended(){
        if(queue==null||edge==null)return;
        transport.beginAutomaticTransition();
        if(modeUpdating||qualityUpdating){pendingEnd=true;return;}
        pendingEnd=false;report("ENDED");advance(false,true);
    }
    private void advance(boolean previous,boolean automatic) {
        if(busy||modeUpdating||offline||queue==null||edge==null)return;
        if(!automatic && (previous?!canPrevious:!canNext)){message="この操作は現在の契約・再生モードでは利用できません";return;}
        JSONObject oldQueue=queue,oldEdge=edge;boolean skipOne=!automatic&&repeatMode==Player.REPEAT_MODE_ONE,savedOnly=savedQueue;switching();final int token=++generation;
        work.execute(()->{boolean suspended=false;try{
            // LOOP_ONE inserts another instance of this track into the remote queue.
            // Explicit next/previous must first request the ordinary adjacent track.
            if(skipOne){api.loop(oldQueue.getString("id"),Player.REPEAT_MODE_OFF);suspended=true;}
            JSONObject q=api.queue(oldQueue.getString("id"),oldEdge.getString("entityReferenceId"));
            JSONArray items=q.getJSONObject(previous?"previous":"next").getJSONArray("edges");
            if(items.length()==0){if(suspended)api.loop(oldQueue.getString("id"),Player.REPEAT_MODE_ONE);main.post(()->{if(token!=generation)return;queue=q;busy=false;changing=false;if(!previous)canNext=false;transport.endAutomaticTransition();refreshControls();message=automatic?"全曲の再生が終了しました":"移動先の楽曲がありません";});return;}
            JSONObject e=items.getJSONObject(previous?items.length()-1:0);
            MusicEntity metadata=api.metadata(e.getJSONObject("node").getString("id"));
            AudioSource audio=audioSource(e.getJSONObject("node").getString("id"),savedOnly);
            main.post(()->{if(token!=generation){audio.close();return;}queue=q;edge=e;contentId=e.optJSONObject("node").optString("id");currentTrack=metadata;title=metadata.title;artist=metadata.subtitle;artworkUrl=metadata.artworkUrl;capabilities();restoreOne=skipOne;modeUpdating=skipOne;start(audio,0);});
        }catch(Exception e){boolean restored=true;if(suspended)try{api.loop(oldQueue.getString("id"),Player.REPEAT_MODE_ONE);}catch(Exception ignored){restored=false;}
            if(!restored)main.post(()->{if(token==generation){repeatMode=Player.REPEAT_MODE_OFF;refreshControls();}});fail(e,token);}});
    }
    public void playOffline(JSONObject item) {
        if(busy||modeUpdating)return;
        switching();final int token=++generation;
        if(SavedTrack.isStream(item)){
            work.execute(()->{try{MusicEntity entity=api.savedSelection(item);PlaybackOrigin source=SavedTrack.origin(item);prepareQueue(entity,false,source==null?entity.type:source.type,source,HistoryRecords.savedSource(item),token,true);}catch(Exception issue){fail(issue,token);}});return;
        }
        work.execute(()->{try{String id=item.getString("id");DashPackage dash=new OfflineStore(this).dash(id);byte[] keys=OfflineStore.keySet(item);
            main.post(()->{if(token!=generation)return;offline=true;savedQueue=true;queue=null;edge=null;currentTrack=null;origin=null;containerType="";modes=QueueModes.NONE;shuffleEnabled=false;repeatMode=Player.REPEAT_MODE_OFF;contentId=id;title=item.optString("title");artist=item.optString("artist");artworkUrl=item.optString("artwork");canSeek=true;canNext=false;canPrevious=false;start(dash,keys);});
        }catch(Exception e){fail(e,token);}});
    }
    private void switching(){historyPending=false;if(!offline&&edge!=null&&player.getPlaybackState()!=Player.STATE_ENDED)report("PAUSED");changing=true;if(player!=null)player.pause();message="準備しています…";busy=true;refreshControls();}
    private void capabilities() {
        JSONObject c=edge.optJSONObject("capabilities");canSeek=allowed(c,"scrubForward")&&allowed(c,"scrubBackward");
        canNext=allowed(c,"next");canPrevious=allowed(c,"previous");
        modes=QueueModes.from(queue,edge);
    }
    private void refreshControls(){if(transport!=null){transport.updateControls(canSeek,canNext,canPrevious,busy||modeUpdating);transport.updateModes(modes,shuffleEnabled,repeatMode,modeUpdating);if(session!=null)widget.refresh(session);}}
    public ListenableFuture<?> setShuffle(boolean enabled){return changeMode(true,enabled,repeatMode);}
    public ListenableFuture<?> setRepeat(int mode){return changeMode(false,shuffleEnabled,mode);}
    private ListenableFuture<?> changeMode(boolean shuffle,boolean enabled,int mode){
        if(busy||modeUpdating||offline||queue==null||edge==null||!(shuffle?modes.shuffle:modes.allows(mode)))
            return Futures.immediateFailedFuture(new IllegalStateException("この再生キューでは操作できません"));
        if(shuffle?enabled==shuffleEnabled:mode==repeatMode)return Futures.immediateVoidFuture();
        final int token=generation;JSONObject oldQueue=queue,oldEdge=edge;SettableFuture<Void> done=SettableFuture.create();
        modeUpdating=true;refreshControls();
        work.execute(()->{
            boolean accepted=false;JSONObject refreshed=null;Exception failure=null;
            try{
                if(shuffle)api.shuffle(oldQueue.getString("id"),enabled);else api.loop(oldQueue.getString("id"),mode);
                accepted=true;refreshed=api.queue(oldQueue.getString("id"),oldEdge.getString("entityReferenceId"));
            }catch(Exception issue){failure=issue;}
            boolean committed=accepted;JSONObject result=refreshed;Exception issue=failure;
            main.post(()->{
                if(token!=generation){done.cancel(false);return;}
                if(committed){
                    if(shuffle)shuffleEnabled=enabled;else repeatMode=mode;
                    if(result!=null){
                        queue=result;JSONArray current=result.optJSONObject("current")==null?null:result.optJSONObject("current").optJSONArray("edges");
                        if(current!=null)for(int i=0;i<current.length();i++){JSONObject candidate=current.optJSONObject(i);
                            if(candidate!=null&&candidate.optJSONObject("node")!=null&&contentId.equals(candidate.optJSONObject("node").optString("id"))){edge=candidate;break;}}
                        capabilities();
                    }
                    message=issue==null?playbackLabel():"設定を変更しました。次の曲でキューを再取得します。";
                }else message=(shuffle?"シャッフル":"リピート")+"を変更できませんでした。再試行してください。";
                modeUpdating=false;refreshControls();
                if(committed)done.set(null);else done.setException(issue==null?new java.io.IOException("Queue update failed"):issue);
                if(pendingEnd&&player.getPlaybackState()==Player.STATE_ENDED)ended();else pendingEnd=false;
            });
        });
        return done;
    }
    private boolean allowed(JSONObject c,String action){return c!=null&&c.optJSONObject(action)!=null&&c.optJSONObject(action).optBoolean("isAllowed");}
    private void start(DashPackage dash,byte[] keys) {
        start(dash,keys,null,0);
    }
    private void start(AudioSource source,long position){start(source.dash,null,source.saved,position);}
    private void start(DashPackage dash,byte[] keys,OfflineStore.SavedAudio saved,long position) {
        loudnessStore.save(normalizer.snapshot());
        normalizer.track(contentId,NormalizationSettings.enabled(this),NormalizationSettings.target(this),loudnessStore.get(contentId));
        boolean cached=saved!=null;
        savedAudio=cached;
        currentDash=dash;
        DefaultDrmSessionManager drm=AmazonDrmCallback.manager(api,keys==null);
        if(keys!=null)drm.setMode(DefaultDrmSessionManager.MODE_PLAYBACK,keys);
        MediaMetadata.Builder metadata=new MediaMetadata.Builder().setTitle(title).setArtist(artist);
        if(!artworkUrl.isEmpty())metadata.setArtworkUri(Uri.parse(artworkUrl));
        MediaItem item=new MediaItem.Builder().setMediaId(contentId).setUri(Uri.parse("https://music.amazon.co.jp/native/manifest.mpd"))
            .setMediaMetadata(metadata.build()).build();
        DataSource.Factory data=cached?new OfflineStore(this).streamSource(this,contentId,saved.variant,false):keys==null?OfflineStore.networkSource(this):OfflineStore.dataSource(this,false);
        DashMediaSource source=new DashMediaSource.Factory(data).setDrmSessionManagerProvider(unused->drm)
            .createMediaSource(dash.manifest,item);
        source.addDrmEventListener(main,new DrmSessionEventListener(){
            @Override public void onDrmKeysLoaded(int windowIndex,androidx.media3.exoplayer.source.MediaSource.MediaPeriodId period){android.util.Log.i("AMPlayback","DRM keys loaded");}
            @Override public void onDrmSessionManagerError(int windowIndex,androidx.media3.exoplayer.source.MediaSource.MediaPeriodId period,Exception error){android.util.Log.e("AMPlayback","DRM session failed",error);}
        });
        boolean play=transport.shouldContinuePlaying();player.setMediaSource(source,position);player.prepare();if(cachedLease!=null)cachedLease.close();cachedLease=saved;changing=false;player.setPlayWhenReady(play);transport.endAutomaticTransition();busy=false;refreshControls();message=playbackLabel();
    }
    private String playbackLabel(){return savedAudio?"保存音源":offline?"オフライン再生":"ストリーミング";}
    private void report(String state) {
        if(queue==null||edge==null)return;JSONObject q=queue,e=edge;String id=contentId;long position=player.getCurrentPosition();int token=generation;
        boolean restore=restoreOne;restoreOne=false;
        work.execute(()->{try{api.report(q.getString("id"),e.getString("entityReferenceId"),id,state,position);
                if(restore){api.loop(q.getString("id"),Player.REPEAT_MODE_ONE);main.post(()->{if(token!=generation)return;modeUpdating=false;refreshControls();if(pendingEnd&&player.getPlaybackState()==Player.STATE_ENDED)ended();});}
            }catch(Exception issue){main.post(()->{if(token!=generation)return;if(restore){modeUpdating=false;repeatMode=Player.REPEAT_MODE_OFF;refreshControls();}message="再生状態を Amazon に送信できません。再生を停止しました。";changing=true;player.pause();changing=false;});}});
    }
    private void fail(Exception e,int token){main.post(()->{if(token!=generation)return;busy=false;changing=false;transport.endAutomaticTransition();refreshControls();message=e.getMessage()==null?"処理に失敗しました":e.getMessage();});}
    public void seek(long position){if(canSeek){if(player.getPlaybackState()==Player.STATE_ENDED)message=playbackLabel();player.seekTo(position);}}
    public void togglePlayback(){if(transport.isAutomaticTransition()){transport.setPlayWhenReady(!transport.getPlayWhenReady());return;}if(player.isPlaying())player.pause();else{if(player.getPlaybackState()==Player.STATE_ENDED&&canSeek)seek(0);player.play();}}
    public void reset() {
        loudnessStore.save(normalizer.snapshot());normalizer.track("",NormalizationSettings.enabled(this),NormalizationSettings.target(this),Double.NaN);
        transport.endAutomaticTransition();qualityUpdating=false;currentDash=null;historySource=null;historyPending=false;savedQueue=false;if(cachedLease!=null){cachedLease.close();cachedLease=null;}
        if(!offline&&edge!=null)report("PAUSED");++generation;changing=true;player.stop();player.clearMediaItems();queue=null;edge=null;currentTrack=null;origin=null;savedAudio=false;contentId="";title="曲を選んでください";artist="";artworkUrl="";message="";busy=false;canSeek=false;canNext=false;canPrevious=false;containerType="";modes=QueueModes.NONE;shuffleEnabled=false;repeatMode=Player.REPEAT_MODE_OFF;modeUpdating=false;pendingEnd=false;restoreOne=false;changing=false;refreshControls();
    }
    @Override protected void dump(java.io.FileDescriptor fd,java.io.PrintWriter writer,String[] args){
        writer.println("state="+player.getPlaybackState()+" playing="+player.isPlaying()+" positionMs="+player.getCurrentPosition()+" bufferedMs="+player.getBufferedPosition()+" durationMs="+player.getDuration());
        writer.println("busy="+busy+" offline="+offline+" savedAudio="+savedAudio+" requireSavedAudio="+savedQueue+" mediaNetworkBytes="+OfflineStore.networkAudioBytes()+" error="+(player.getPlayerError()==null?"none":player.getPlayerError().getErrorCodeName()));
        writer.println("message="+message);
        writer.println("title="+title+" artist="+artist+" mediaId="+contentId);
        writer.println("next="+canNext+" previous="+canPrevious+" commands="+transport.getAvailableCommands());
        writer.println("shuffle="+shuffleEnabled+" repeat="+QueueModes.remoteLoop(repeatMode)+" updating="+modeUpdating+" allowedShuffle="+modes.shuffle+" allowedAll="+modes.all+" allowedOne="+modes.one+" container="+containerType);
        writer.println("origin="+(origin==null?"none":origin.type+":"+origin.id));
        writer.println("transitioning="+transport.isAutomaticTransition()+" sessionState="+transport.getPlaybackState()+" sessionPlayWhenReady="+transport.getPlayWhenReady());
        writer.println("quality="+(currentDash==null?"none":currentDash.quality)+" bitrate="+(currentDash==null?0:currentDash.format.bitrate)+" sampleRate="+(currentDash==null?0:currentDash.format.sampleRate)+" playbackQuality="+QualitySettings.playback(this)+" downloadQuality="+QualitySettings.download(this)+" qualityUpdating="+qualityUpdating);
        NormalizationAudioProcessor.Snapshot level=normalizer.snapshot();
        writer.println("normalization="+NormalizationSettings.enabled(this)+" targetLufs="+NormalizationSettings.target(this)+" appliedNormalization="+(level==null?"none":level.enabled)+" appliedTargetLufs="+(level==null?"none":level.target)+" measuredLufs="+(level==null?"none":level.lufs)+" gainDb="+(level==null?0:level.gainDb)+" measuredSeconds="+(level==null?0:level.seconds)+" processedFrames="+(level==null?0:level.frames)+" normalizationTrack="+(level==null?"none":level.id));
        if(BuildConfig.DEBUG && args.length==2 && "drm-check".equals(args[0]) && args[1].matches("[A-Z0-9]{10}")) {
            String asin=args[1];work.execute(()->checkDrm(asin));writer.println("DRM diagnostic scheduled");
        }
    }
    private void checkDrm(String asin){
        FrameworkMediaDrm platform=null;byte[] sessionId=null;
        try {
            DashPackage dash=new DashPackage(api.manifest(asin));platform=FrameworkMediaDrm.newInstance(C.WIDEVINE_UUID);sessionId=platform.openSession();
            java.util.List<DrmInitData.SchemeData> data=new java.util.ArrayList<>();
            for(int i=0;i<dash.format.drmInitData.schemeDataCount;i++){DrmInitData.SchemeData item=dash.format.drmInitData.get(i);if(item.matches(C.WIDEVINE_UUID))data.add(item);}
            ExoMediaDrm.KeyRequest request=platform.getKeyRequest(sessionId,data,android.media.MediaDrm.KEY_TYPE_STREAMING,new java.util.HashMap<>());
            byte[] response=new AmazonDrmCallback(api).executeKeyRequest(C.WIDEVINE_UUID,request);platform.provideKeyResponse(sessionId,response);
            android.util.Log.i("AMPlayback","DRM diagnostic accepted for "+asin);
        }catch(Exception error){android.util.Log.e("AMPlayback","DRM diagnostic rejected for "+asin,error);}
        finally{if(platform!=null){if(sessionId!=null)platform.closeSession(sessionId);platform.release();}}
    }
    @Override public void onDestroy(){loudnessStore.save(normalizer.snapshot());getSharedPreferences("normalization",0).unregisterOnSharedPreferenceChangeListener(normalizationChanged);main.removeCallbacksAndMessages(null);work.shutdownNow();if(session!=null)session.release();if(transport!=null)transport.release();if(cachedLease!=null)cachedLease.close();super.onDestroy();}
}
