package jp.nao.amclient;

/** Small native queries using the fields observed in the authenticated web client. */
final class CatalogQueries {
    private static final String IMAGES="images { url width height imageType } ";
    private static final String ARTISTS="contributingArtists { edges { node { __typename id uri name } } } ";
    private static final String PLAY="playParams { id containerIds selections } ";
    private static final String ALBUM="__typename id uri title trackCount "+IMAGES+ARTISTS+PLAY;
    private static final String PLAYLIST="__typename id uri title trackCount "+IMAGES+PLAY;
    private static final String EDIT="version eligibility { playlist { canAddTracks canDeletePlaylist canRemoveTracks canRenamePlaylist canReorderTracks } } capabilities { appendTracks { isAllowed } removeTracks { isAllowed } } ";
    private static final String TRACK="__typename id uri title duration "+IMAGES+ARTISTS+PLAY;
    private static final String ARTIST="__typename id uri name "+IMAGES+PLAY;
    private static final String PAGE="pageInfo { hasNextPage token endCursor } ";
    static String query(String operation){
        switch(operation){
            case "NativeLibraryTracks":return "query NativeLibraryTracks($cursor:String) { user { libraryTracks(limit:50,cursor:$cursor) { edges { "+PLAY+" node { "+TRACK+" } } "+PAGE+" } } }";
            case "NativeLibraryAlbums":return "query NativeLibraryAlbums($cursor:String) { user { libraryAlbums(limit:50,cursor:$cursor) { edges { node { "+ALBUM+" } } "+PAGE+" } } }";
            case "NativeLibraryPlaylists":return "query NativeLibraryPlaylists($ownedCursor:String,$followedCursor:String,$owned:Boolean!,$followed:Boolean!) { user { playlists(limit:50,cursor:$ownedCursor,sortBy:\"LAST_MODIFIED_DATE\") @include(if:$owned) { edges { node { "+PLAYLIST+" } } "+PAGE+" } followedPlaylists(limit:50,cursor:$followedCursor) @include(if:$followed) { edges { node { "+PLAYLIST+" followsContext { isFollowed } } } "+PAGE+" } } }";
            case "NativeTrackMetadata":return "query NativeTrackMetadata($id:String!) { track(id:$id) { "+TRACK+" album { "+ALBUM+" } } }";
            case "NativeHistoryAlbum":return "query NativeHistoryAlbum($id:String!) { album(id:$id) { "+ALBUM+" } }";
            case "NativeHistoryPlaylist":return "query NativeHistoryPlaylist($id:String!) { playlist(id:$id) { "+PLAYLIST+" } }";
            case "NativeHistoryArtist":return "query NativeHistoryArtist($id:String!) { artist(id:$id) { "+ARTIST+" } }";
            case "NativeArtistAlbums":return "query NativeArtistAlbums($id:String!,$cursor:String) { artist(id:$id) { "+ARTIST+" albums(limit:20,cursor:$cursor,sortBy:\"release\") { edges { node { "+ALBUM+" } } "+PAGE+" } } }";
            case "NativeArtistPlaylists":return "query NativeArtistPlaylists($id:String!,$recommender:String!,$cursor:String) { artist(id:$id) { "+ARTIST+" } recommender(id:$recommender,limit:20,cursor:$cursor) { edges { node { recommendedContent { edges { node { __typename ... on Playlist { "+PLAYLIST+" } } } "+PAGE+" } } } } }";
            case "NativeAlbum":return "query NativeAlbum($id:String!) { album(id:$id) { "+ALBUM+" tracks { "+TRACK+" } } }";
            case "NativePlaylist":return "query NativePlaylist($id:String!,$cursor:String) { playlist(id:$id) { "+PLAYLIST+EDIT+" tracks(limit:100,cursor:$cursor) { edges { itemId playParams { id containerIds selections } node { "+TRACK+" } } "+PAGE+" } } }";
            case "NativeOwnedPlaylists":return "query NativeOwnedPlaylists($cursor:String) { user { playlists(limit:50,cursor:$cursor,sortBy:\"LAST_MODIFIED_DATE\") { edges { node { "+PLAYLIST+EDIT+" } } "+PAGE+" } } }";
            case "NativeCreatePlaylist":return "mutation NativeCreatePlaylist($title:String!) { createPlaylist(title:$title,visibility:\"PRIVATE\") { id } }";
            case "NativeUpdatePlaylist":return "mutation NativeUpdatePlaylist($input:BatchUpdatePlaylistInput!) { batchUpdatePlaylist(input:$input) { playlist { id title } } }";
            case "NativeAppendPlaylist":return "mutation NativeAppendPlaylist($id:String!,$track:String!) { appendTracks(playlistId:$id,trackIds:[$track],rejectDuplicateTracks:false) { id } }";
            case "NativeDeletePlaylist":return "mutation NativeDeletePlaylist($id:String!) { deletePlaylist(id:$id) { id isDeleted } }";
            default:return null;
        }
    }
}
