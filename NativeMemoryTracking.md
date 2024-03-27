## NMT使ってのJVMのネイティブメモリ情報取得
NMT・・・Native Memory Tracking
下記の起動オプション追加（summary or detail）
```
-XX:NativeMemoryTracking=summary
```
コマンドプロンプトで↓実行したら情報が取れる
```
jcmd [pid] VM.native_memory summary
```
こんな感じの情報が取れる  
![image](https://github.com/NSC-HikaruSaito/MyNotes/assets/47444250/e3eea88c-134e-4e6a-aaf0-2ddc24006b42)


アプリケーション内で取りたい場合は↓のようなコード
```Java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;

public class NmtSmaple {

    public static void main(String[] args) {
        //pidを特定する
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        int index = jvmName.indexOf('@');
        String pid = jvmName.substring(0, index);

        //jcmdのコマンド：jcmd [pid] VM.native_memory summary
        //detail出したい場合はsummaryの部分を変える※JVMオプションも
        String command = new StringBuilder("jcmd ")
                .append(pid)
                .append(" VM.native_memory summary").toString();

        //コマンド実行して、1行ずつ結果を出力
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
        }
    }

}
```
