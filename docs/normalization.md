# 音量ノーマライズ（0.11.0）

## 再生経路

PlaybackServiceのExoPlayerにNormalizationAudioSinkとNormalizationAudioProcessorを接続した。保存DASHも配信DASHも同じAudioSinkを通る。Android CDMによる通常の再生を継続し、PCMのファイル出力・外部送信・録音は行わない。追加の権限もない。

Media3 1.5.1のAudioProcessorはPCM経路で動作するため、浮動小数点の直接出力、圧縮音声のパススルーとオフロードを無効にし、モノラル／ステレオの16-bit PCMとして処理する。無効設定のときもプロセッサを接続しておき、曲の読み直しなしでオン／オフできる。端末側のメディア音量とオーディオフォーカスは従来のまま。

参考: [Media3 1.5.1 DefaultAudioSink](https://github.com/androidx/media/blob/1.5.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java)、[BaseAudioProcessor](https://github.com/androidx/media/blob/1.5.1/libraries/common/src/main/java/androidx/media3/common/audio/BaseAudioProcessor.java)。

## 測定と補正

LoudnessMeterはK-weighting後の左右のエネルギーを合算し、400 ms窓・100 ms刻みで集計する。絶対ゲート−70 LUFSと相対ゲート−10 LUを使う。相対ゲート用のヒストグラムは0.1 LU刻みで、長時間再生でもメモリ量は一定。信号自体にはフィルターをかけず、測定用の信号にだけK-weightingを適用する。

初回は元の音量を−10 LUFSと仮定した小さめの出力から開始する。約3秒の有効な測定後に再生範囲の推定音量を使う。補正係数は下げるとき約150 ms、上げるとき約2秒の時定数で補間する。左右に共通の係数を適用し、増幅上限12 dBと−1 dBFSのサンプルピークリミッター（即時抑制・約100 ms回復）を設ける。True Peakを測る認証済みR128メーターではなく、初回再生時には曲全体の積分値も未確定。局所的な音量差を完全に一定にするコンプレッサーとも異なる。

参考: [EBU Tech 3341](https://tech.ebu.ch/docs/tech/tech3341.pdf)。

## 永続化と曲送り

LoudnessStoreは有効な測定30秒以上を対象に、曲ID・音量・測定時間・更新日時だけをSharedPreferencesに記録する。より長い測定を優先し、更新間隔は測定時間10秒以上。最大500曲とし、追加時に古い記録を除く。出力の基準音量や保存・配信のビットレートには依存しない。次回は記録した曲の音量から冒頭の補正を決め、その再生中はその値を固定して使う。

新しい曲IDでは測定と補正を切り替える。シークや同じ曲の音質変更では既存の音量統計を保ち、フィルターの過渡状態だけ初期化する。測定のキャッシュは5秒周期、曲切り替え時とサービス終了時に保存する。音量設定は端末内に永続化し、サービスが変更を監視して次の処理バッファから反映する。基準音量の変更だけでは、再生位置・配信キュー・音源選択・音質設定を変更しない。
