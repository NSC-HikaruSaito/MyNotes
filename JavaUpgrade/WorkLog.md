# Work Log

### Gradleでビルドしてるので、まずはbuild.gradleからどうにかしてく

1.下記のリンクをもとに記述を修正。（compile → implementation とか）  
[Upgrading your build from Gradle 6.x to 7.0](https://docs.gradle.org/current/userguide/upgrading_version_6.html#sec:configuration_removal)  

  
2.taskで「<<」が使えなくなってるので、「doLast {}」を使う
```java
//    　task cleanandbuildallproject (dependsOn: ['clean', 'build'])<< {
//    　}
    
	task cleanandbuildallproject (dependsOn: ['clean', 'build']) {doLast {
	}}
```


3.jarタスクで重複してモジュールが入るとエラーになるので、
`
duplicatesStrategy = DuplicatesStrategy.EXCLUDE
`
を追記

4.JavaEEからJakartaEEへ変更  
`compileOnly 'jakarta.platform:jakarta.jakartaee-api:10.0.0'`  
※名前空間が「javax」→「jakarta」と変わるので、コード上のimportも直す必要あり
  
5.annotationProcessorでLombokを追記
```
compileOnly group: 'org.projectlombok', name: 'lombok', version: '1.18.30'
annotationProcessor group: 'org.projectlombok', name: 'lombok', version: '1.18.30'
testCompileOnly group: 'org.projectlombok', name: 'lombok', version: '1.18.30'
testAnnotationProcessor  group: 'org.projectlombok', name: 'lombok', version: '1.18.30'
```

6.その他ライブラリをついでに上げておく
あまりメンテされてなかったようなので、ついでに上げれるものは上げとく
