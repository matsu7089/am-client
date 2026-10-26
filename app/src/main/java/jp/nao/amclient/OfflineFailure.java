package jp.nao.amclient;

import java.io.*;
import java.net.*;

final class OfflineFailure {
    private OfflineFailure() {}
    static boolean licenseRejected(Throwable error){
        for(Throwable e=error;e!=null;e=e.getCause())if(e instanceof AmazonApi.HttpFailure){
            AmazonApi.HttpFailure failure=(AmazonApi.HttpFailure)e;
            return failure.status==400&&failure.category.contains("BadRequestException")&&failure.category.contains("EC_INVALID_PARAMETER");
        }
        return false;
    }
    static String message(Throwable error,boolean licensePhase){
        if(licensePhase&&licenseRejected(error))return "Amazon が保存用ライセンスの発行を拒否しました。現在の Web 接続では、この曲をダウンロードできません。";
        for(Throwable e=error;e!=null;e=e.getCause()){
            if(e instanceof SocketTimeoutException||e instanceof UnknownHostException||e instanceof ConnectException)
                return "通信できず、保存に失敗しました。接続を確認して再試行してください。";
        }
        if(licensePhase)return "保存用ライセンスを取得できませんでした。現在の接続・契約・端末では保存できない可能性があります。";
        return "音声データを保存できませんでした。通信状態と空き容量を確認して再試行してください。";
    }
}
