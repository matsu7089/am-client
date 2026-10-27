# Amazon Music Web 調査記録

調査日: 2026-10-26 (JST)。Chrome DevTools MCP で実サイトを開き、ユーザーによるログイン後に画面・ネットワーク・メディア要素を確認。

## 観測結果

| 対象 | 観測内容 |
| --- | --- |
| 未ログイン | ホーム、検索、人気アーティスト、プレイリスト。`/forceSignIn?useHorizonte=true` から `https://www.amazon.co.jp/ap/signin` に遷移 |
| 認証 | Amazon のサインイン画面。OpenID パラメーターと Music への return URL を使用 |
| ログイン前通信 | `POST /config.json` (`clientApplication=skyfire`)、`fe.web.skill.music.a2z.com/api/showHome`、`showHomeBrowse`、`/FE/api/cirrus/v3/` |
| ログイン後通信 | Web アプリ `web-hornet/1.186.31-7c5eafe`、`clientApplication=hornet`、`POST https://gql.music.amazon.co.jp/`。GraphQL operation `IdentityStoreSeed` を確認 |
| 検索 | 「サカナクション」を入力し Enter。`/search/<URL encoded query>` に遷移。楽曲・アーティスト・アルバム・プレイリストなどの結果とフィルター |
| ライブラリ | `/my/library`。プレイリスト・ポッドキャスト・楽曲・アルバム・アーティスト・購入済み、および再生履歴 |
| コンテンツ | `/artists/<ASIN>`、`/playlists/<ASIN>`、`/albums/<ASIN>` 等。画面内に曲・再生・フォローなどの操作 |
| 再生 | 検索結果の「怪獣を再生する」を実行。VIDEO 要素で `paused=false`、`currentTime > 9`、`duration≈252.82`、`currentSrc` は blob、`mediaKeys` が存在 |
| メディア通信 | CloudFront 上の MP4 に 206 Partial Content、`POST /FE/api/dmls/`。DRM の利用は mediaKeys から確認。キーシステム名と dmls の詳細仕様は未検証 |

再生確認後はメディアを一時停止。個人のプレイリスト名・認証 Cookie・トークン・メディアの完全な URL・レスポンス本文は記録していない。fetch の一時フックはアプリ通信を捉えなかったため、通信の判断は DevTools の Network に基づく。

## 実装判断

認証済み API は確認できたが、検索・ライブラリの GraphQL スキーマ、トークン更新、再生ライセンスの仕様は確立できていない。現段階で独立したネイティブ API プレイヤーが完成したとは扱わない。

初版は Android ネイティブの外枠と公式 Web プレイヤーを組み合わせる WebView クライアント。検索 URL とライブラリ URL は観測したものを使用し、曲一覧・再生 UI・契約による制限・DRM ライセンス処理は公式 Web アプリへ委譲する。公式 UI の DOM を書き換える CSS や JavaScript bridge は使用していない。Activity が背景へ移る際の一時停止のみメディア要素へ指示する。

保護メディア ID 権限は `https://music.amazon.co.jp` からの要求にのみ許可。これは正規 DRM 再生への許可であり、復号鍵の取得・DRM の解除は行わない。

## 検証の境界

ブラウザ上でのログイン・検索・ライブラリ・DRM 再生は確認済み。Android WebView でのログイン・音声出力・DRM 再生は実機未検証。Web アプリがモバイル WebView を拒否する場合や認証フロー変更がある場合は動作しない可能性がある。PC 表示と外部ブラウザで開く導線を用意するが、外部ブラウザとアプリのセッションは共有しない。

## 参照

- https://developer.android.com/reference/android/webkit/PermissionRequest
- https://developer.android.com/build/releases/agp-8-9-0-release-notes

# 0.2.0 ネイティブ化の追加調査

ユーザー指定により、対象は購入済み MP3 ではなく聴き放題の楽曲。

- `config.json?clientApplication=hornet&skipToken=false` でセッション設定、CSRF、端末識別などを取得する構成を確認。値はソースに保存しない。
- SpecSearchPage、LibraryHomePage、CreateRemoteQueue、FetchRemoteQueue、ReportDevicePlayback、ReportCurrentDeviceStreaming を観測。
- 検索結果の playParams とサーバー選曲キューを使用し、個々の曲の強制選択や契約による操作制限の回避をしない。
- DASH 取得は `getDashManifestsV2`、ライセンス要求は `getLicenseForPlaybackV2`。`Content-Encoding: amz-1.0` と要求形式を確認。独自アプリ識別子で DASH 取得成功。
- DRM チャレンジとライセンスは Media3 と Android CDM に渡す。コンテンツ鍵を取り出す処理や復号済みファイルの保存処理は実装しない。
- ダウンロードには OfflineLicenseHelper (KEY_TYPE_OFFLINE) を使う。永続ライセンスの有効期間を確認してから暗号化セグメントを保存し、オフライン再生時はネットワーク接続を許可しない。

初回調査で `currentTime > 9` と `mediaKeys` は確認したが、Amazon の公開資料では最初の 30 秒は clear lead とされる。その時点の記録だけでは暗号化区間の復号成功までは証明できないため、30 秒を超える Android ネイティブ再生は未検証とする。

Amazon の公開 API 認証資料は開発者アプリの承認を必要としており、独立した公開製品としての提供には別途その確認が必要。現在の実装は Web の非公開 API を使う実験版で、オフラインライセンスが発行されることは確認できていない。

追加参照:
- https://developer.amazon.com/docs/music/API_web_LWA.html
- https://developer.amazon.com/docs/music/playback_overview.html
- https://developer.android.com/media/media3/exoplayer/downloading-media
- https://developer.android.com/reference/androidx/media3/exoplayer/drm/OfflineLicenseHelper

# 0.2.1: 30 秒で停止する問題

Pixel 10 Pro で再現を調査。修正前は一部の楽曲でライセンス要求が HTTP 400 / `com.amazon.digitalmusicdrmlicenseservice#ForbiddenException` となり、未暗号化の clear lead が終わる約 30 秒で停止していた。一方、別の曲では数分再生できた。

原因は `getDashManifestsV2` の `tryAsinSubstitution` を Prime に対しても無条件で true にしていたこと。「いらない」(B0GL3YDMM8 / B0HDN44YZJ) のマニフェストが Unlimited 向けの別 ASIN (B0GL4ZVJRN) の音源を含み、ライセンスサーバーの権限確認で拒否された。公式 Web バンドルでは、このフラグは HAWKFIRE / KATANA の権利がある場合のみ有効だった。

`ManifestPolicy` でサーバーが返した契約 tier に応じて制御するよう変更。Prime と無料 / 不明の tier は false、Unlimited / Unlimited HD だけ true。権限判定・暗号化・端末の DRM セキュリティレベルは変更していない。

修正後、同じ端末・アカウントで上記両 ASIN のチャレンジ送信、ライセンス受信、Android CDM への登録が成功。さらに、ライセンス取得前は clear lead を再生しない設定にし、失敗を冒頭 30 秒だけの再生として見せないようにした。

根拠にした公式配布 Web バンドル:
https://d5fx445wy2wpk.cloudfront.net/release/web-hornet/1.186.31-7c5eafe/_expo/static/js/web/__common-39af82bb6c2623c20d06c44e5494d12d.js

# 0.4.0: シャッフルとリピート

公式配布の上記 Web バンドルで `ShuffleQueue` / `LoopQueue` を確認。`setQueueShuffleMode(id, shuffleMode)` に `SHUFFLE_ON` / `SHUFFLE_OFF`、`setQueueLoopMode(id, loopMode)` に `LOOP_OFF` / `LOOP_ALL` / `LOOP_ONE` を渡し、`status=SUCCESS` を確認後、現在の entityReferenceId でキューを再取得する方式。

利用可否は契約名で決めない。Queue.capabilities.shuffle / loopAll と現在の edge.capabilities.loopOne の isAllowed を使用する。同じ Prime アカウントでも通常のアルバム由来のステーションは3項目とも false、Web の「曲を選んで再生」がある All-Access プレイリストは3項目とも true だった。

LOOP_ONE のキューには同じ曲の次の再生インスタンスが入る。自然終了ではそのまま次を取得して再生する。手動の次へ / 前へは一時的に LOOP_OFF にして隣の曲を取得し、その曲の再生状態を報告した後に LOOP_ONE を戻す。状態報告前にモードを書き換えようとすると `GET_CURRENT_ENTITY_REFERENCE_ERROR` になることも確認した。

ネイティブの ExoPlayer は1曲ずつ読み込むため、その本体に REPEAT_MODE_ALL を設定すると同じ1曲のループになってしまう。RemoteQueuePlayer がモードの操作と状態を公開し、実際の全曲ループとシャッフル順は Amazon のキューに委ねる。Media3 のモード・曲送りの意味も参照した: https://developer.android.com/media/media3/exoplayer/playlists

# 0.5.0: アルバム一覧とアーティストページ

Chrome DevTools MCP で Porter Robinson の公式アーティストページを確認。ランダム再生、リリース、トップアルバム、関連プレイリストのほか、トップソング、最新の楽曲、参加楽曲、関連アーティスト、類似した音楽の再生、フォロー、共有、関連ポッドキャストがあった。追加機能についてユーザーに確認し、今回は指定分のみ実装する回答を受けた。

ユーザーの追加指定により、検索にもアーティストタブを設ける。また、再生中の曲を開いた先は実際の再生キューと区別したアルバムの収録曲一覧のみとした。

- `Track.contributingArtists` の id / name を保持してアーティストページへ移動する。曲の album.id から収録曲を取得。
- `Album.tracks` はページ付きの connection ではなく Track 配列。曲ごとの playParams に containerIds が入る。
- `Playlist.tracks(limit,cursor)` は connection。node の汎用 playParams より edge の playParams を優先し、プレイリスト内での選曲権限とコンテナーを維持する。
- `Artist.albums(limit,cursor,sortBy:"release")` と `recommender(id:"uri:///artist/<id>/related-playlists",limit,cursor)` を使用。ページ送りは pageInfo.token を優先し endCursor にフォールバックする。
- アーティストの playParams は Prime で SHUFFLE / STATION を返した。公式シャッフルに合わせて SHUFFLE_ON を指定し、明示的な「類似した音楽」のステーション開始とは区別する。
- アプリで使用する NativeTrackMetadata / NativeArtistAlbums / NativeArtistPlaylists / NativeAlbum / NativePlaylist と同じクエリーを、ログイン済み Web セッションで確認。すべて GraphQL エラーなし。

参照した公式画面: https://music.amazon.co.jp/artists/B003WQES5C

Prime のアーティスト再生の追加確認: 公式のシャッフルボタンから `startPlayback` に渡る値を一時フックで確認し、アーティストの MRN と SHUFFLE / STATION の selections が使われることを確認した（フックは即時復元、Web 音声は開始していない）。同じアーティストの createQueue でオプション省略 / SHUFFLE_OFF / SHUFFLE_ON / startAsStation=false を確認したところ、いずれも関連する別アーティストの曲が含まれた。アプリの実再生でも同様だったため、これは当アカウントでの Amazon 側の選曲として扱い、特定アーティストへの強制選曲はしない。

# 0.6.0: 保存の失敗とライブラリの分類

Pixel 10 Pro で「再生中の曲をダウンロード」を実行すると、音源の保存前に、OfflineLicenseHelper の KEY_TYPE_OFFLINE 要求が getLicenseForPlaybackV2 により HTTP 400 / com.amazon.digitalmusicdrmlicenseservice#BadRequestException / EC_INVALID_PARAMETER で拒否された。再生用のライセンス要求と保存用のチャレンジは別であり、現在の Web 認証・再生 API から保存用ライセンスを取得できる経路は確認できなかった。CDM の出力や鍵を抽出する調査は行っていない。

Amazon の案内でも、ブラウザでの購入済み楽曲のダウンロードと、聴き放題のアプリ内オフライン再生を区別している。拒否理由から直ちに端末の故障や再ログインの必要性とは判断しない。HTTP 400 の該当エラーだけは自動再試行せず、失敗理由を保存状態の通知と画面に反映する。保存成功・オフライン再生は未検証のまま。

- https://digprjsurvey.amazon.co.uk/csad/help/node/G6S668ZHKA8MK6T7
- https://developer.android.com/reference/androidx/media3/exoplayer/drm/OfflineLicenseHelper

ユーザーから再生音の録音による代替について質問があったため、Android の AudioPlaybackCapture と MediaProjection の仕様を確認した。実音声を取得できるかは未検証。追加範囲を確認したところ「今回は原因調査とライブラリ改善まで」と回答を受けたため、録音機能・録音権限は追加していない。

- https://developer.android.com/media/platform/av-capture

ライブラリは LibraryHomePage（おすすめや履歴を含むホーム表示）から、User.libraryTracks / libraryAlbums / playlists / followedPlaylists のカテゴリ別取得へ変更。Chrome DevTools MCP のログイン済みページで実際のクエリとページ送りを確認。各50件、tracks の edge.playParams、playlists の LAST_MODIFIED_DATE 順、followedPlaylists の followsContext.isFollowed を使用する。自作・フォロー中はカーソルを別々に保持し、完了した接続を再取得しない。0.15.0では公式Webの`/my/albums`の`LikedAlbumsPage`と照合し、アルバムを`User.likedAlbums`へ訂正した。いいね・フォローの追加解除と詳細は[library-membership.md](library-membership.md)を参照。

# 0.7.0: 暗号化された配信音源の保存

ユーザーが録音機能を依頼した後、再生音キャプチャではなくストリーミングデータの保存を希望した。暗号化したまま保存して通常のストリーミングライセンスで再生する方式と、完全オフライン再生との違いを説明し、「再生時の通信が必要でもよい」と回答を受けた。さらにアルバム・プレイリストページからの一括保存と、メイン画面の保存ボタン削除を指定された。

保存処理はDASHマニフェストと選択済みOpus表現の暗号化セグメントをMedia3 DashDownloaderで保存する。KEY_TYPE_OFFLINE を要求せず、保存音源の再生時に通常のキュー生成・権限確認・KEY_TYPE_STREAMING を使用する。現在のライセンス取得とDRMの処理はAndroid CDMのまま。マイク、MediaProjection、音声キャプチャ、復号鍵の抽出・保存・平文音声の書き出しは追加していない。

保存音源の再生ではCacheDataSourceの上流をnullにし、音声データのネットワーク取得にフォールバックしない。通常のページからの再生は、サーバーが返した曲IDに保存済み音源があれば利用し、なければ通常ストリーミング。保存済み行からの再生でサーバーが未保存の別曲を選ぶ場合は説明して止める。保存によって個別選曲の権限は追加しない。

アルバム・プレイリストは追加ページを含め全曲を取得し、重複IDを除いて保存する。一部の曲が失敗しても他の曲を続け、完了・失敗数を表示する。中止時は処理途中の曲を削除し、完了した曲は保持する。キャッシュキーにアカウントと曲IDの接頭辞を付け、別の保存曲が同じ配信URLを使っても、削除で影響しないよう分離する。

再生中とダウンロード中のメディアHTTPバイト数だけを計測する診断値を追加。認証情報、配信URL、ライセンスの本文や鍵をログに出す処理はない。この値は認証や画像などの通信量を含まない。

参照:
- https://developer.android.com/media/media3/exoplayer/downloading-media
- https://developer.android.com/media/media3/exoplayer/drm

# 0.8.0: バックグラウンドの自動曲送りと再生ウィジェット

ユーザーから、自然終了で次の曲になってもウィジェットが前の曲のままになることと、特にバックグラウンドで発生する可能性について報告を受けた。0.7.1の実機で、ホーム表示中に Divinity → Still Here の自然な遷移を観測。PlaybackServiceとframework MediaSessionのメタデータは新しい曲だった一方、SystemUIのAM ClientカードにはDivinityが残っていた。PlaybackServiceはisForegroundが外れ、infoAllowStartForegroundにBFGS denied=true / code:DENIEDが記録されていた。

Media3 1.5.1のMediaNotificationManagerでは、playWhenReady=trueかつREADYまたはBUFFERINGであることが前景サービス維持の条件。実装は保護された音源を1曲ずつ置き換えるため、自然終了時のENDEDを公開した直後に前景状態が解除されていた。次の曲の通知更新時にstartForegroundServiceを再度呼ぶと、ホーム表示中では拒否される。手動の曲送りはユーザー操作による許可があり、従来の手動操作だけの検証では検出できていなかった。

RemoteQueuePlayerが自動遷移中をBUFFERINGとして公開し、次の音源が準備できるまで再生要求を保持する。エラー・リセット・実際のキュー末尾ではその状態を解除する。準備中の一時停止は次の音源にも反映する。音源・復号・選曲の権限処理は変更しない。

WidgetControlsは、状態を切り替えるTOGGLE_SHUFFLEと利用可能なリピートを循環するCYCLE_REPEATの2つのSessionCommandを公開する。通常のPlayer.COMMAND_SET_SHUFFLE_MODE / SET_REPEAT_MODEは残したまま、ボタン用の引数不要操作を追加した。MediaButtonPreferencesのアイコンと表示名、コントローラーのコマンド可否をサーバー権限・処理中状態に合わせて更新する。標準の5スロットのうち前後移動以外のスロットを使う。

参照:
- https://github.com/androidx/media/blob/1.5.1/libraries/session/src/main/java/androidx/media3/session/MediaNotificationManager.java
- https://github.com/androidx/media/blob/1.5.1/libraries/session/src/main/java/androidx/media3/session/PlayerWrapper.java
- https://developer.android.com/media/implement/surfaces/mobile
- https://developer.android.com/media/media3/session/control-playback


# 0.9.0: 再生音質と保存音質

追加調査は audio-quality.md に記録。SD の Opus に限定して48 / 192 / 320 kbpsを選択する。HDやLDとSDの表現を混ぜず、現在のPrimeで利用するSDセットから選ぶ。MPDのbandwidthにはピーク帯域が入るため、48,000 / 192,000 / 320,000との完全一致は使わない。SDのqualityRanking（1=高、2=標準、3=節約）を優先し、ピーク帯域の増大で公称品質を誤判定しない。ランキングが欠ける場合のみ観測した帯域の区分を使う。実際に取得した表現から選択し、希望設定と実際の品質を別々に保持する。配信候補が不足する場合は下位候補（下位もなければ最小候補）にフォールバックする。

QualitySettingsの再生・保存設定は別々に永続化し、初期値はMEDIUM。ダウンロード開始時の設定をバッチ全体に固定する。再生音質の切り替えでは同じ曲ID・キュー・再生元・シャッフル・リピートを維持する。新しい音源の準備中も旧音源を継続し、準備後の位置とplayWhenReadyを引き継ぐ。取得失敗では旧音源と旧設定を維持する。許可されないシークを伴う変更は次曲へ延期する。曲末と変更が重なった場合は通常の自動曲送りへ接続する。

保存ごとに独立したキャッシュ名前空間とMPDを作り、音質・選択StreamKey・実際の帯域とサンプルレートをcomplete.jsonへ記録する。旧データのMPDを先に上書きせず、完了後のメタデータを原子的に差し替える。中止・失敗の削除対象は新しい未完了の名前空間だけ。保存済みの判定には希望音質を含め、旧版の品質情報がない項目はLOWとして扱う。refreshContextでも新しい音質情報を保持する。

保存音源の取得から再生終了までキャッシュの参照を保持し、保存し直した際の掃除から保護する。再生中の旧表現を削除しない。参照のなくなった旧表現は以降の掃除で除去する。保存再生は記録した表現と名前空間を読み、現時点の再生設定で別表現を選び直さない。通常再生では希望音質が一致する保存データを優先し、ダウンロード一覧では保存した音質を優先する。

参考: https://developer.android.com/media/media3/exoplayer/downloading-media
再生位置の指定: https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer


# 0.10.0: アプリの再生履歴とダウンロード一覧からの再生

ライブラリのカテゴリをLibraryCategory（HISTORY / ALBUMS / PLAYLISTS）として検索・ダウンロードのSearchCategoryから分離した。旧画面状態のTRACKS / ARTISTSはHISTORYへ移行する。履歴はAmazonの履歴APIではなく、アプリが実際に再生を開始した元の項目を記録する。

PlaybackServiceで新しいキューの元となるMusicEntityを保持し、実プレイヤーのisPlaying=trueで1回記録する。Album / Playlist / Artistを記録対象とし、曲一覧からの曲選択はその一覧、保存曲は保存元、単独楽曲は実際のアルバムへ解決する。選曲された関連楽曲のアルバムでアーティスト履歴を上書きしない。表示だけの操作、失敗したキュー準備、曲送りや音質変更による同じキューの再読込は新規履歴を増やさない。

HistoryRecordsが種類とIDで重複をまとめ、再生開始時刻の降順で最大50件に制限する。遅れた古い書き込みで新しい情報を上書きしない。同時刻では新しい操作を優先する。HistoryStoreは顧客IDのハッシュによるアカウント別SharedPreferencesへ同期commitし、アプリ内通知で表示を更新する。保存するのは種類・ID・タイトル・画像・アーティストと時刻で、再生コマンドや認証情報を記録しない。履歴からの再生は小さなNativeHistoryAlbum / Playlist / Artistクエリーで現在のplayParamsを取り直す。

続くユーザー報告の再現では、保存済みプレイリストの全曲再生でもBrowseActivityが通常のplay / playFromCollectionを呼んでいた。夜の踊り子（B07BDHGNWH）の保存音質はHIGH / 323,352 bps、通常の再生設定はMEDIUM。audioSourceは品質が一致する保存曲だけを再利用するため、195,352 bpsのストリーミングになり、savedAudio=false / 音声通信1,454,487 bytesを観測した。

ダウンロード一覧の曲・全曲再生をplaySavedFromCollectionへ接続した。prepareQueueのrequireSavedをキュー単位のsavedQueueとして維持し、手動と自動の曲送りにも渡す。再生中の曲から開くNOW画面もこの区別を引き継ぐ。通常再生の品質選択と区別し、保存音質を無条件に選び、CacheDataSourceの音声上流はnullのままとする。未保存や不足した音源は明示的なエラーになり、ストリーミングにフォールバックしない。保存キュー中の再生音質変更は次の通常再生向けの設定だけを更新する。


# 0.10.1: 全再生導線での保存音源の優先

0.10.0ではダウンロード一覧をrequireSavedのキューとして保存音源へ固定したが、通常の再生では音質が一致した保存データだけを利用する条件が残っていた。調査時点のGoodbye To A World（B075HFQCJW）は保存HIGH / 325,236 bpsに対して、履歴由来の再生はMEDIUM / 197,133 bps、savedAudio=falseだった。

音源選択を行うPlaybackService.audioSourceの保存判定をOfflineStore.openIfSavedへ統一した。完成した保存データがあれば、現在の再生音質を比較せず、記録した表現とキャッシュ名前空間のスナップショットを取得する。保存データがない場合、通常のキューは指定音質の配信を使い、requireSavedのキューは未保存の理由を表示する。ダウンロード済み判定の品質比較matchesは、保存し直すかどうかを決める処理にだけ使う。

| 再生導線 | 共通処理への入口 |
| --- | --- |
| 履歴のランダム再生 | playHistory → prepareQueue → audioSource |
| 検索・ライブラリの楽曲、曲一覧の全曲再生 | play → prepareQueue → audioSource |
| 履歴・検索・ライブラリ・アーティストから開いた曲一覧の楽曲 | playFromCollection → prepareQueue → audioSource |
| アーティストのランダム再生 | playRandom → prepareQueue → audioSource |
| ダウンロード内の曲と曲一覧 | playOffline / playSavedFromCollection → prepareQueue → audioSource |
| 前後移動、通知操作、自動曲送り、リピートの次音源 | advance → audioSource |

保存音源を選んだ後は、音声の上流を持たないCacheDataSourceで読み込む。通常の再生キュー自体を保存曲限定に変更しないため、保存曲と未保存曲が混在する場合も、Amazonが選択した各曲について同じ優先規則を適用する。曲の選択権限、再生元、認証・ライセンス取得は従来と同じ。

再生中の音質変更も、savedQueueだけでなく実際のsavedAudioを判定するよう変更した。通常の導線から保存音源を再生している場合も、設定の変更だけで保存音源を読み直したり配信へ切り替えたりせず、未保存曲向けの設定を更新する。
# 音量ノーマライズ（0.11.0）

曲ごとの音量差に対応するため、共通の再生AudioSinkで音量を測定・補正する処理を追加した。基準は初期値−20 LUFS、−30〜−14 LUFSを選択可能。初回は再生範囲から推定し、測定できた曲の音量統計を次回に利用する。保存・配信の音源選択やビットレートと独立した設定で、バックグラウンドの曲送りにも適用する。方式と制限は[音量ノーマライズの実装](normalization.md)、検証結果はvalidation.mdの0.11.0を参照。

# プレイリストの表紙と再生方式の表示（0.15.1）

Chrome DevTools MCPで[Best of サカナクション](https://music.amazon.co.jp/playlists/B07K3YQT6J)と公式Webのフォロー済み一覧を確認。`images`には表紙のPRIMARY画像（500×500 / 2400×2400）と、imageTypeがnullの横長画像（500×250 / 2560×1280）が混在していた。500×250の実体はAmazon Musicのロゴで、画像取得はいずれもHTTP 200だった。

従来の選択処理はBACKGROUNDだけを後回しにし、幅が同じ候補を返却順で選んでいた。フォロー済み一覧ではロゴが表紙より先に来るため、500pxのロゴが選ばれた。公式WebのSquareImage処理もPRIMARYと正方形を優先している。共通のMusicEntity.selectArtworkをPRIMARY → その他 → BACKGROUND、同じ種類では正方形、表示に十分な小ささの順に変更した。検索・ライブラリ・アーティスト・曲一覧で共通の規則を使う。

実機のダウンロード一覧にも旧版が選んだロゴURLが保存されていた。ローカルのプレイリスト表紙にartworkVersionを付け、旧データを表示した際に別のバックグラウンド処理で最新の表紙を問い合わせる。既存のコレクション情報・曲の保存元・履歴の画像だけを更新し、曲ID、保存音源、マニフェスト、音質、履歴の時刻や順序を保持する。保存先はアカウントごとに固定し、削除と同じロック内で読み直して、通信中に削除された項目を復活させない。通信失敗時は既存画像を保持し、後のActivity生成時に再試行する。

「おまかせ再生」はSHUFFLEの別名ではなく、SHUFFLEもPLAYもないときにSTATIONとして開始する操作だった。SHUFFLEでは`shuffle=SHUFFLE_ON`、STATIONだけの場合は`startAsStation=true`となり、関連曲も選ばれ得る。実際の再生処理は維持し、SHUFFLEを「シャッフル再生」、旧「おまかせ」を「ステーション再生」と表示する。曲ごとのボタンも同じ呼称にそろえた。
