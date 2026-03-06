# 開発環境構築ガイド

LLMtools - ForSpreadsheets の開発環境構築手順について説明します。

---

## 目次

- [必要環境](#必要環境)
- [構築手順](#構築手順)
  - [1. Gradle のプロキシを設定する（プロキシ環境の場合のみ）](#1-gradle-のプロキシを設定するプロキシ環境の場合のみ)
  - [2. IntelliJ IDEA のプロキシを設定する（プロキシ環境の場合のみ）](#2-intellij-idea-のプロキシを設定するプロキシ環境の場合のみ)
  - [3. PowerShell 実行ポリシーを設定する](#3-powershell-実行ポリシーを設定する)
  - [4. JRE を配置する（単体実行パッケージを作成する場合のみ）](#4-jre-を配置する単体実行パッケージを作成する場合のみ)

---

## 必要環境

| 項目 | バージョン・内容 |
| ---- | --------------- |
| Java | 17 以上 |
| IDE  | IntelliJ IDEA（Eclipse、Visual Studio Code 等も可） |

---

## 構築手順

### 1. Gradle のプロキシを設定する（プロキシ環境の場合のみ）

> プロキシを使用しない環境では、この手順は不要です。

ビルド時に Gradle や Spring のライブラリをインターネット経由でダウンロードします。
プロキシ環境の場合、以下のファイルにプロキシ設定を追記します。

**ファイルパス:** `C:\Users\<username>\.gradle\gradle.properties`

> 参考: [Gradle Networking ドキュメント](https://docs.gradle.org/current/userguide/networking.html)

```properties
systemProp.http.proxyHost=<プロキシの IP アドレス (HTTP)>
systemProp.http.proxyPort=<プロキシの Port (HTTP)>

systemProp.https.proxyHost=<プロキシの IP アドレス (HTTPS)>
systemProp.https.proxyPort=<プロキシの Port (HTTPS)>
```

---

### 2. IntelliJ IDEA のプロキシを設定する（プロキシ環境の場合のみ）

> プロキシを使用しない環境では、この手順は不要です。

IntelliJ IDEA のライセンス認証やプラグインダウンロードのため、プロキシを設定します。

1. IntelliJ IDEA を起動する。
2. **[ファイル] → [設定]** を選択する。
3. **[外観 & 振る舞い] → [システム設定] → [HTTP プロキシ]** で以下の設定を行い、**[OK]** を押下する。

   | 項目 | 値 |
   | ---- | -- |
   | プロキシ構成 | 手動プロキシ構成 |
   | プロトコル | HTTP |
   | ホスト名 | `<プロキシの IP アドレス>` |
   | ポート番号 | `<プロキシの PORT 番号>` |
   | プロキシ認証 | 有効化 |
   | ログイン | `<プロキシのログインアカウント>` |
   | パスワード | `<プロキシのログインパスワード>` |

---

### 3. PowerShell 実行ポリシーを設定する

本ツールは Excel との連携に PowerShell スクリプトを使用しています。
スクリプトが実行できるよう、以下の手順で実行ポリシーを変更します。

1. PowerShell を**管理者権限**で起動する。
2. 以下のコマンドを実行して実行ポリシーを変更する。

   ```powershell
   Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
   ```

3. 以下のコマンドで変更が反映されたことを確認する。

   ```powershell
   Get-ExecutionPolicy -List
   ```

   `CurrentUser` の列が `RemoteSigned` になっていれば設定完了です。

   ```
           Scope ExecutionPolicy
           ----- ---------------
   MachinePolicy       Undefined
      UserPolicy       Undefined
         Process          Bypass
     CurrentUser    RemoteSigned   ← RemoteSigned になっていれば OK
    LocalMachine       Undefined
   ```

---

### 4. JRE を配置する（単体実行パッケージを作成する場合のみ）

> IntelliJ IDEA での開発・デバッグ実行のみを行う場合、この手順は不要です。

[README.md](../README.md) の「単体実行可能パッケージの作成」でビルドする際に必要な手順です。

1. Java 17 環境を用意する。まだインストールしていない場合は、Amazon Corretto 17（Windows x64 zip 版）をダウンロードして使用できます。
   - ダウンロードページ: [Amazon Corretto 17 ダウンロード一覧](https://docs.aws.amazon.com/corretto/latest/corretto-17-ug/downloads-list.html)
   - 直接ダウンロード: [amazon-corretto-17-x64-windows-jdk.zip](https://corretto.aws/downloads/latest/amazon-corretto-17-x64-windows-jdk.zip)
2. インストール済みの Java 17 環境のフォルダをコピーする。
   - 例: `C:\Program Files\Amazon Corretto\jdk17.0.18_9`
3. コピーしたフォルダの名前を `jre` に変更し、zip 形式で圧縮する（`jre.zip`）。
4. `jre.zip` を `launcher/` フォルダ直下に配置する。
