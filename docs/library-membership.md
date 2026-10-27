# ライブラリへの追加・解除

## 公式Webの仕様確認（2026-10-28）

Chrome DevTools MCPでログイン済みの公式Webと配信中の公開JavaScriptを確認した。

- [アルバム](https://music.amazon.co.jp/albums/B00BNJ596W)のハートは「いいね」。`likesContext.state == LIKE`が登録済み。`setAlbumLikeState(albumId, likeState)`に追加は`POSITIVE`、解除は`NEUTRAL`を指定し、応答の`edges.node.likesContext.state`を確認する。
- [公式Webのアルバム一覧](https://music.amazon.co.jp/my/albums)は`LikedAlbumsPage`から`user.likedAlbums(limit, cursor)`を取得する。従来のアプリの`user.libraryAlbums`とは異なる。解除済みの`NEUTRAL`項目が接続に残る場合があるため、`LIKE`の項目だけを表示し、ページ送りは元の接続のカーソルを保つ。
- [他者のプレイリスト](https://music.amazon.co.jp/playlists/B082F7WLV1)のハートは「フォロー」。`followsContext.isFollowed`で状態を取得し、`followPlaylist(id)` / `unfollowPlaylist(id)`の`playlist`のIDと状態を確認する。ライブラリは既存の自作一覧と`user.followedPlaylists`を合わせる。
- 公式Webのフォローボタンは、`eligibility.playlist.canRenamePlaylist`が真の自作、32桁の小文字16進IDのMyいいね、`PP`に16進文字が続く個人向けミックスでは表示しない。通常の公式プレイリストでは`eligibility.playlist`がnullになる。
- 実機では他者の共有プレイリストでも`canDeletePlaylist`が真になる場合があった。自作の判定には使わず、公式Webと同じ`canRenamePlaylist`とライブラリの自作所属を使う。
- アルバムの追加・解除、プレイリストの追加・解除を試し、応答と一覧への反映を確認。調査対象のアルバムとプレイリストは元の未登録状態に戻した。楽曲個別のいいねは変更していない。

## アプリの動作

- 検索・アーティスト・履歴・ダウンロード・再生元から開く共通の曲一覧に「ライブラリに追加／ライブラリ追加済み」を表示する。
- 登録済みを押すと確認画面を出して解除する。プレイリスト自体の削除とは別の操作であり、端末のダウンロードも消さない。
- 読み込み中・送信中は操作できない。現在の権限と状態を再取得し、表示時点の状態を反転するのではなく、指定した登録状態に変更する。自作と特殊プレイリストのフォロー操作はAPI側でも拒否する。
- 成功は対象IDと応答の登録状態を確認してから表示する。通信エラー時は書き込みを自動再試行せず、状態を再取得する。変更を試みた場合は一覧キャッシュを無効にし、戻ったライブラリに反映する。

公開Webバンドル: `web-hornet/1.186.31-7c5eafe`の`__common-39af82bb6c2623c20d06c44e5494d12d.js`（`toggleAlbumLike`、`togglePlaylistFollow`、`PlaylistFollowIconButton`）。認証情報は保存・記録しない。
