package jp.nao.amclient;

import org.json.JSONObject;

/** Album likes and playlist follows are different server-side library memberships. */
final class LibraryMembership {
    final boolean known,registered,canChange;
    private LibraryMembership(boolean known,boolean registered,boolean canChange){this.known=known;this.registered=registered;this.canChange=canChange;}
    // Playlist mutation responses omit eligibility: keep the freshly checked permission.
    static LibraryMembership confirmed(LibraryMembership checked,LibraryMembership response){return new LibraryMembership(response.known,response.registered,checked.canChange);}
    static LibraryMembership from(JSONObject node,boolean owned){
        String type=node.optString("__typename"),id=node.optString("id");
        if("Album".equals(type)){
            JSONObject likes=node.optJSONObject("likesContext");String state=likes==null?"":likes.optString("state");
            boolean known="LIKE".equals(state)||"NEUTRAL".equals(state)||"DISLIKE".equals(state);
            return new LibraryMembership(known,"LIKE".equals(state),known&&!id.isEmpty());
        }
        if("Playlist".equals(type)){
            JSONObject follows=node.optJSONObject("followsContext"),eligibility=node.optJSONObject("eligibility");
            JSONObject playlist=eligibility==null?null:eligibility.optJSONObject("playlist");
            boolean known=follows!=null&&follows.opt("isFollowed") instanceof Boolean;
            boolean editable=owned||playlist!=null&&playlist.optBoolean("canRenamePlaylist");
            boolean special=id.matches("[0-9a-f]{32}")||id.matches("PP[A-F0-9]+");
            boolean permitted=eligibility!=null&&eligibility.has("playlist")&&!editable&&!special&&!id.isEmpty();
            return new LibraryMembership(known,known&&follows.optBoolean("isFollowed"),known&&permitted);
        }
        return new LibraryMembership(false,false,false);
    }
}
