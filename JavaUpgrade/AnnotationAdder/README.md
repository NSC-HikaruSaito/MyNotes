# AnnotationAdder
JAX-RSのAPIの引数としているクラスにデフォルトコンストラクタが付いてないものが多数あり、動作しなくなった  
（何で今まで動いていたかは不明・・・。）  

対象のAPIが700件以上あり、1つ1つ修正すると途方もなく時間がかかるため、一気にデフォルトコンストラクタを付与していくコードを作成。


## 1.APIの引数チェック
まずは対象の洗い出し
### やりたい事
- WebAPIの引数でデフォルトコンストラクタが付いてないものを出力
- 引数のクラスが持つフィールドのクラスも再帰的にチェックして一緒に出力

Reflection使うのが手っ取り早いのでサービス内に埋め込む（コミットとかはしない）
めんどくさいので、結果はSystem.out.printlnする感じで  
[JaxRsApiChecker](https://github.com/NSC-HikaruSaito/MyNotes/blob/main/JavaUpgrade/AnnotationAdder/JaxRsApiChecker.java)


## 2.一括でアノテーション付与
1で洗い出した対象を処理していく
### やりたい事
- @NoArgsConstructorを付ける
- @Getterを付ける
- @Value、@Builder、@RequiredArgsConstructorのいずれかが付いていた場合は、@AllArgsConstructorを付ける
- @Valueがあれば削除
- @RequiredArgsConstructorがあれば削除
- フィールドにfinal修飾子があれば削除
不要なアノテーションが多数付いていたので、不要なものは削除する感じで
