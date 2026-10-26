package jp.nao.amclient;

import org.json.*;
import java.util.List;

/** Server permissions and entry-based edits; song ASINs never identify a removal or move. */
final class PlaylistPolicy {
    final boolean add,remove,rename,delete,reorder;
    private PlaylistPolicy(JSONObject p,JSONObject capabilities){
        add=flag(p,"canAddTracks")&&capability(capabilities,"appendTracks");
        remove=flag(p,"canRemoveTracks")&&capability(capabilities,"removeTracks");
        rename=flag(p,"canRenamePlaylist");delete=flag(p,"canDeletePlaylist");reorder=flag(p,"canReorderTracks");
    }
    static PlaylistPolicy from(JSONObject node){JSONObject eligibility=node.optJSONObject("eligibility");return new PlaylistPolicy(eligibility==null?null:eligibility.optJSONObject("playlist"),node.optJSONObject("capabilities"));}
    private static boolean flag(JSONObject object,String key){return object!=null&&object.optBoolean(key,false);}
    private static boolean capability(JSONObject object,String key){JSONObject value=object==null?null:object.optJSONObject(key);return value!=null&&value.optBoolean("isAllowed",false);}
    boolean editable(){return add||remove||rename||delete||reorder;}
    static String title(String value){String clean=value==null?"":value.trim();if(clean.isEmpty())throw new IllegalArgumentException("プレイリスト名を入力してください");if(clean.codePointCount(0,clean.length())>200)throw new IllegalArgumentException("プレイリスト名は200文字以内で入力してください");return clean;}
    static int index(List<MusicEntity> tracks,String entry){if(entry==null||entry.isEmpty())throw new IllegalArgumentException("曲の登録情報がありません。再読込してください");for(int i=0;i<tracks.size();i++)if(entry.equals(tracks.get(i).playlistEntryId))return i;throw new IllegalArgumentException("曲一覧が変更されています。再読込してください");}
    private static JSONObject item(List<MusicEntity> tracks,int position)throws JSONException{return new JSONObject().put("item",new JSONObject().put("itemId",tracks.get(position).playlistEntryId).put("possibleIndex",position));}
    private static JSONObject input(MusicEntity owner,JSONObject update)throws JSONException{
        if(owner.uri.isEmpty())throw new IllegalArgumentException("プレイリストの情報がありません。再読込してください");
        JSONObject input=new JSONObject().put("id",owner.uri).put("updates",new JSONArray().put(update));if(!owner.version.isEmpty())input.put("version",owner.version);return input;
    }
    static JSONObject rename(MusicEntity owner,String title)throws JSONException{return input(owner,new JSONObject().put("editTitle",new JSONObject().put("title",title(title))));}
    static JSONObject remove(CatalogPage page,String entry)throws JSONException{int position=index(page.items,entry);return input(page.owner,new JSONObject().put("removeItems",new JSONObject().put("items",new JSONArray().put(item(page.items,position)))));}
    static JSONObject move(CatalogPage page,String entry,int direction)throws JSONException{
        if(direction!=-1&&direction!=1)throw new IllegalArgumentException("移動方向が不正です");int position=index(page.items,entry),other=position+direction;if(other<0||other>=page.items.size())throw new IllegalArgumentException("これ以上移動できません");
        if(page.items.get(other).playlistEntryId.isEmpty())throw new IllegalArgumentException("曲の登録情報がありません。再読込してください");
        JSONObject move=new JSONObject().put("items",new JSONArray().put(item(page.items,position))).put("location",new JSONObject().put("reference",item(page.items,other)).put("direction",direction<0?"Before":"After"));
        return input(page.owner,new JSONObject().put("moveItems",move));
    }
}
