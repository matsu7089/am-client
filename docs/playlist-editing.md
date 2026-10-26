# 自作プレイリストの編集（0.13.0）

Chrome DevTools MCPで公式Webのライブラリ、自作プレイリスト、作成ダイアログを確認。公式配布バンドルのCreatePlaylist、updatePlaylistDetails、deletePlaylist、addTrackToPlaylist、reorderPlaylistTracks、removePlaylistTracksを参照した。

公式バンドル:
https://d5fx445wy2wpk.cloudfront.net/release/web-hornet/1.186.31-7c5eafe/_expo/static/js/web/__common-39af82bb6c2623c20d06c44e5494d12d.js

## 通信と権限

- 作成はcreatePlaylist(title, visibility: PRIVATE)。名前変更・曲削除・並べ替えはbatchUpdatePlaylist、曲追加はappendTracks、全体削除はdeletePlaylistを使う。
- PlaylistApiは更新直前にuser.playlistsを最後のページまで確認し、自作一覧への所属を検証する。followedPlaylistsへの所属だけでは更新しない。
- 現在のeligibility.playlistのcanRenamePlaylist / canDeletePlaylist / canAddTracks / canRemoveTracks / canReorderTracksを確認する。曲の追加・削除にはcapabilities.appendTracks / removeTracksのisAllowedも必要。欠ける権限は許可と解釈しない。Myいいねなど、名前変更・全体削除ができない特殊なプレイリストは対象から除く。
- 編集画面は全ページを取得し、ページ間のversionの一致とカーソルの循環を検証する。曲単位の操作は選択されたitemIdを最新の曲一覧で再検索する。曲のASINで削除しない。
- removeItemsとmoveItemsにはitemIdとpossibleIndex、更新にはplaylist.uriとversionを渡す。移動先は隣のitemを参照し、PlaylistItemDirectionはBefore / After。
- appendTracksはユーザーが再度追加した同じ曲も受け付ける。二重押しによる重複要求は画面の処理中制御で防ぐ。PlaylistTrackEdge.itemIdを保持し、同じ曲が複数あってもページ結合時に統合しない。
- 自動の書き込み再試行は行わない。サーバーが返す結果のID（全体削除はisDeletedも）を確認する。曲一覧やライブラリは再取得する。

## 画面

ライブラリの自作一覧由来の行だけが編集画面への導線を持つ。検索・履歴・ダウンロード・フォロー中の一覧には編集操作を追加しない。編集は独立したネイティブActivityで行い、共通ミニプレイヤーと下部ナビゲーションを表示する。名前変更、楽曲検索と追加、登録ごとの上下移動・削除、全体削除に対応。曲とプレイリストの削除には対象名を示す確認画面を出す。

変更時はプロセス内のrevisionを更新し、戻り先のライブラリや曲一覧のキャッシュを破棄して再取得する。プレイリストを削除した場合は削除済みの曲一覧も閉じる。ダウンロード済み音源の削除や、進行中の再生キューの作り直しは行わない。

## 検証

PlaylistPolicyTestで、同一楽曲の複数登録、登録IDを使う削除と移動、古い登録の拒否、端の曲の移動制限、権限欠落時の拒否、名前の検証を実施する。LibraryPageTestで自作とフォロー中の区別を確認する。

`adb.exe shell am instrument -w -e livePlaylist true jp.nao.amclient.test/jp.nao.amclient.PlaybackVerification`はAmazon上に確認用プレイリストを新規作成し、そのプレイリストだけで追加・同一曲の複数登録・上下移動・個別削除・名前変更・全体削除を検証する。finallyでも確認用プレイリストを削除し、元の自作一覧のID集合を比較する。公式プレイリストは所有確認で拒否することを読み取りだけで確認する。既存のユーザープレイリストは変更しない。

認証情報、セッション設定、個人のプレイリスト名や登録IDはソース・ログ・文書に保存しない。
