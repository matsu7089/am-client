package jp.nao.amclient;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ArtworkTest {
    private JSONObject image(String url,int width,String type)throws Exception{return new JSONObject().put("url",url).put("width",width).put("imageType",type);}
    @Test public void prefersSmallUsableCoverToBackgroundOrOversizedImage()throws Exception {
        JSONArray images=new JSONArray().put(image("https://example.org/background.jpg",160,"BACKGROUND")).put(image("https://example.org/large.jpg",1400,"PRIMARY"))
            .put(image("https://example.org/cover.jpg",500,"PRIMARY")).put(image("https://example.org/tiny.jpg",32,"PRIMARY"));
        assertEquals("https://example.org/cover.jpg",MusicEntity.selectArtwork(images));
    }
    @Test public void usesAlbumArtworkAndRejectsLocalOrInsecureUrls()throws Exception {
        JSONArray images=new JSONArray().put(image("file:///private/image",500,"PRIMARY")).put(image("http://example.org/image",500,"PRIMARY")).put(image("https://example.org/album.jpg",500,"PRIMARY"));
        MusicEntity entity=new MusicEntity(new JSONObject().put("album",new JSONObject().put("images",images)));
        assertEquals("https://example.org/album.jpg",entity.artworkUrl);assertEquals("",MusicEntity.selectArtwork(null));
    }
    @Test public void playlistCoverWinsOverUntypedLogoInEitherResponseOrder()throws Exception {
        JSONObject logo=image("https://example.org/logo.jpg",500,"").put("height",250);
        JSONObject cover=image("https://example.org/cover.jpg",500,"PRIMARY").put("height",500);
        JSONObject large=image("https://example.org/large.jpg",2400,"PRIMARY").put("height",2400);
        for(JSONArray images:new JSONArray[]{new JSONArray().put(logo).put(large).put(cover),new JSONArray().put(cover).put(large).put(logo)}){
            MusicEntity playlist=new MusicEntity(new JSONObject().put("__typename","Playlist").put("images",images));
            assertEquals("https://example.org/cover.jpg",playlist.artworkUrl);
        }
    }
    @Test public void squareImageWinsWhenImageTypesAreMissing()throws Exception {
        JSONArray images=new JSONArray().put(image("https://example.org/banner.jpg",160,"").put("height",80))
            .put(image("https://example.org/cover.jpg",500,"").put("height",500));
        assertEquals("https://example.org/cover.jpg",MusicEntity.selectArtwork(images));
    }
    @Test public void fallsBackToAvailableArtworkWithoutAcceptingInvalidPrimary()throws Exception {
        JSONArray images=new JSONArray().put(image("http://example.org/primary.jpg",500,"PRIMARY").put("height",500))
            .put(image("https://example.org/banner.jpg",500,"").put("height",250))
            .put(image("https://example.org/background.jpg",160,"BACKGROUND").put("height",160));
        assertEquals("https://example.org/banner.jpg",MusicEntity.selectArtwork(images));
        assertEquals("https://example.org/background.jpg",MusicEntity.selectArtwork(new JSONArray().put(images.getJSONObject(2))));
    }
}
