# 音質変更の調査

調査日: 2026-10-26。対象: AM Client 0.8.0、ログイン済みの Amazon Music 日本版 Web、Prime アカウント。

追記: この調査後、0.9.0 で SD の音質設定を実装した。以下の「未実装・未検証」は調査時点の状態。実装と実機確認の結果は README、analysis.md、validation.md の 0.9.0 の記録を参照。

## 結果

再生用と保存用の音質を別々に選ぶ機能は、現在の DASH / Media3 の構成で実装可能と判断する。Prime で使える標準音質の候補として、Opus の公称 48 / 192 / 320 kbps を確認した。HD / Ultra HD は契約・楽曲・端末の対応を満たす場合の別対応となる。

今回は調査のみ。アプリの音質選択機能、高いビットレートでの Android 実再生、音質を変えた保存・保存音源の再生は未実装・未検証。APK は 0.8.0 のまま。

## 現在のアプリ

- `DashPackage` は音声表現を走査し、`codecs` が Opus かつ `Format.bitrate` が最小のものを1つだけ残す。今回の取得結果では、公称 48 kbps に対応する表現が選ばれる。再生と保存が同じ処理を使う。
- `AmazonApi.capabilities()` はキューの取得時に `audioCapability: ["STD_RES"]` を指定する。DMLS のマニフェスト取得は別の経路で、`SIREN_KATANA` と `TRACK_PSSH` を要求している。
- `PlaybackService.audioSource()` は、曲 ID の保存データがあれば常にそちらを優先する。再生用音質を追加する際は、保存音源を利用する条件も決める必要がある。
- `OfflineDownloadService` は同じ曲 ID が保存済みなら取得を省略する。音質が異なる場合の再ダウンロードには、この判定の変更が必要。
- 保存メタデータには音質・選択した表現の識別情報がなく、再生時に保存済み MPD を `DashPackage` で再選択する。全候補を含む MPD が保存されていても、実際に保存した音声データは選択した1表現だけ。

参照: `DashPackage.java`、`AmazonApi.java`、`PlaybackService.java`、`OfflineDownloadService.java`、`OfflineStore.java`、`SavedTrack.java`。

## 公式 Web と配信情報の確認

Chrome DevTools MCP で公式サイトを開き、認証済みの `tier=PRIME` を確認した。音声品質の設定画面では「標準」「データセーバー」と「品質の自動調整: オン / オフ」が表示されていた。設定値は標準・自動調整オン。音質設定は変更せず、音声も再生していない。

アプリと同じ条件の `getDashManifestsV2` を3曲に対して要求し、すべて HTTP 200 / `SUCCESS`。`tryAsinSubstitution` は Prime の既存方針に従い false とした。

| 曲 ID | SD 低の bandwidth | SD 中の bandwidth | SD 高の bandwidth |
| --- | ---: | ---: | ---: |
| B092H3WNWK | 52,991 | 197,105 | 325,207 |
| B075HFFKTQ | 52,979 | 196,981 | 324,984 |
| B0GL3YDMM8 | 53,202 | 197,658 | 326,062 |

表の単位は bps。3曲とも SD の Opus / 48 kHz が3段階あり、`qualityRanking` は低=3、中=2、高=1。公式の公称ビットレートは低=48、中=192、高=320 kbps で、MPD の `bandwidth` は必要な最大帯域を表すため一致しない。ビットレートの値が公称値に完全一致することを前提に選択してはいけない。

B092H3WNWK の MPD には、SD・LD・HD の各 AdaptationSet が含まれていた。公式 Web 配布コードの `filterEntitlement` に、公式プレイヤーが保持する当アカウントの権利と各セットの属性を渡したところ、SD / LD は true、HD は false。SD の selectionPriority は1000、LD は500、HD は2000だった。

**MPD に FLAC が含まれていることは、そのアカウントで再生できる証拠にならない。** Prime に HD を提示する根拠にはしない。SD / LD などの種類・権利・優先順位を判定してから、対象セット内で表現を選ぶ必要がある。

補助的に送った `user.playableAudioQualities` の独自 GraphQL 要求は `AUTH_CLIENT_NOT_ANONYMOUS_ALLOW_LISTED` となった。独自要求からこのフィールドを取得できることは未確認。公式 Web の設定表示と公式プレイヤーの権利フィルターは別途確認できているため、独自要求が成功したとは扱わない。

## 公式資料との対応

| 種別 | 形式 | 音質の目安 | 契約条件 |
| --- | --- | --- | --- |
| 標準音質 SD | Opus / DASH | 公称 48 / 192 / 320 kbps、48 kHz | Free / Prime / Unlimited。実際の候補と権利を確認 |
| HD | FLAC / DASH | 16 bit、44.1 kHz 以上 | 対応する Unlimited の権利が必要 |
| Ultra HD | FLAC / DASH | 24 bit、最大192 kHz | 対応する Unlimited の権利・楽曲・端末が必要 |

公式 API の capability は `STD_RES`、`HI_FI`、`HI_RES`（新しい別名は SD / HD / UHD）。公式 Web バンドルではキューに `['HI_RES','HI_FI','STD_RES']` を渡す一方、プレイヤー側でも権利・音声設定によるフィルターを行う。capability に追加するだけで高音質対応が完了するわけではない。

Media3 は DASH と Widevine を扱え、音声のデコード能力は端末に依存する。現在の Opus 経路の音質選択は実装可能。FLAC の DRM 再生と実際の出力品質は、権利のある契約と端末・接続先を使った検証が必要。

## 実装する場合の構成

1. 「再生音質」と「保存音質」を別々の設定として保持する。SD の候補は「節約 48 kbps」「標準 192 kbps」「高音質 320 kbps」。再生側に「自動」を追加することも可能だが、現在の1表現固定を変更して適応選択に対応する必要がある。
2. `DashPackage` に音質選択方針を渡す。契約上許可されたセットから選び、`qualityRanking` と `bandwidth` を使って候補を決める。楽曲ごとに候補が不足する場合も考慮し、希望設定と実際の音質を区別する。
3. 再生中に音質を切り替える場合は、同じ曲・同じ再生キューを維持し、再生位置と再生 / 一時停止状態を引き継いで音源を切り替える。読み込みによる短い待ち時間は生じ得る。0.8.0 のバックグラウンド遷移・ウィジェット更新の修正を維持する。
4. 保存時に、実際に選択した表現の識別情報・品質・形式・帯域を記録する。保存音源の再生ではその記録を使い、現在の設定で未保存の別表現を選ばないようにする。既存の保存曲は旧実装の低音質として互換性を維持する。
5. 保存音質の変更は次の保存から適用する。既存曲の変更は別音源の再ダウンロードになる。新しい音源の保存が完了するまで既存データを残し、中止・失敗で元の保存曲を失わないようにする。曲 ID だけでなく音質も保存済み判定に含める。
6. ストリーミングの希望音質と保存音源が異なる場合の扱いを実装する。「ダウンロード」からは保存した音質で再生し、通常再生では希望音質に合う保存データを再利用する構成が可能。

実装後は各音質の30秒超の DRM 再生、途中切り替え時の位置と再生状態、各音質の保存・キャッシュからの再生、音質の異なる保存曲の扱い、保存変更の中止・失敗、バックグラウンドの自動曲送りと通知を確認する。

## 出典と調査の範囲

- [Amazon Music: Request Headers / Audio Quality](https://developer.amazon.com/docs/music/API_web_request_headers.html)
- [Amazon Music: Audio Formats](https://developer.amazon.com/docs/music/audio-formats.html)
- [Amazon Music: DRM Overview / Manifest](https://developer.amazon.com/docs/music/playback_overview.html)
- [Media3: Supported formats](https://developer.android.com/media/media3/exoplayer/supported-formats)
- [Media3: Digital rights management](https://developer.android.com/media/media3/exoplayer/drm)
- [公式 Web 配布バンドル](https://d5fx445wy2wpk.cloudfront.net/release/web-hornet/1.186.31-7c5eafe/_expo/static/js/web/__common-39af82bb6c2623c20d06c44e5494d12d.js): `setStreamingQuality`、`filterEntitlement`、`filterHd`、`AudioQualityModal`、DMLS の要求処理。

確認したのは公式設定画面、配信候補、公式コードの権利判定、既存アプリの選択・保存処理。高音質の Android 実再生やダウンロード成功を今回の成果としては扱わない。認証情報、署名付きメディア URL、DRM データはこの記録に含めていない。
