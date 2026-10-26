package jp.nao.amclient;

enum LibraryCategory {
    HISTORY("履歴",null), ALBUMS("アルバム",SearchCategory.ALBUMS), PLAYLISTS("プレイリスト",SearchCategory.PLAYLISTS);
    final String label;
    final SearchCategory source;
    LibraryCategory(String label,SearchCategory source){this.label=label;this.source=source;}
    static LibraryCategory restore(String value){try{return valueOf(value);}catch(Exception ignored){return HISTORY;}}
}
