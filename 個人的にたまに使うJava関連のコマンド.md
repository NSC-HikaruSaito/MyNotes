## **個人的にたまに使うJava関連のコマンドとか**

**プロセスID確認**
```
jps
```
****
**JVMの全オプション確認**
```
jcmd [PID] VM.flags -all
```
****
**JVMが使ってるJavaのバージョン確認**
```
jcmd [PID] VM.version
```
****
**JVMのネイティブメモリ使用量の確認**
```
jcmd [pid] VM.native_memory summary
```
****
**JVMのネイティブメモリ使用量の増減確認**
```
jcmd <pid> VM.native_memory baseline
jcmd <pid> VM.native_memory summary.diff
```
****

**ヒープの各領域の統計情報**
```
jstat -gccapacity [PID]
```
****
**GCの統計情報（1000msごとに出力）**
```
jstat -gcutil [PID] 1000
```
****
**ヒープのヒストグラムを出力（ライブオブジェクトのみ）**
```
jmap -histo:live [PID]
```
****
