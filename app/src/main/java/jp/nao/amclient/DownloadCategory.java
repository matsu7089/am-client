package jp.nao.amclient;

/** Old track/artist selections migrate to the first supported download tab. */
enum DownloadCategory {
    ALBUMS(SearchCategory.ALBUMS),PLAYLISTS(SearchCategory.PLAYLISTS);
    final SearchCategory source;
    DownloadCategory(SearchCategory source){this.source=source;}
    static DownloadCategory restore(String name){try{return valueOf(name);}catch(Exception ignored){return ALBUMS;}}
    static DownloadCategory from(SearchCategory source){return restore(source.name());}
}
