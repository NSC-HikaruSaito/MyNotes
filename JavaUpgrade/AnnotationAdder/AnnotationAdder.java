import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AnnotationAdder {

    public static void main(String[] args) {
        //対象プロジェクトのパス
        Path rootPath = Paths.get("C:/hoge/hogePJ");
        try {
            // Javaファイルを解析
            Files.walk(rootPath)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(AnnotationAdder::parseAndModifyJavaFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void parseAndModifyJavaFile(Path javaFilePath) {
        try {
            //JavaParserを使用してCompilationUnitを取得する
            CompilationUnit cu = new JavaParser().parse(javaFilePath).getResult().get();

            //パッケージ名を取得
            String packageName = cu.getPackageDeclaration().map(pd -> pd.getName().asString()).orElseThrow(() -> new RuntimeException("Not found package"));

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                //パッケージ名＋クラス名
                StringBuilder clsName = new StringBuilder(packageName)
                        .append(".")
                        .append(cls.getNameAsString());

                //対象かどうかのチェック
                if (!ReplaceTarget.TARGETS.contains(clsName.toString())) {
                    return;
                }

                System.out.println("File: " + javaFilePath.getFileName() + " - package: " + packageName + " - Class: " + clsName);

                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(ClassOrInterfaceDeclaration declaration, Void arg) {
                        super.visit(declaration, arg);

                        //@NoArgsConstructorを追加
                        if (!declaration.getAnnotationByName("NoArgsConstructor").isPresent()) {
                            declaration.addAnnotation("NoArgsConstructor");
                            cu.addImport("lombok.NoArgsConstructor");
                        }

                        //@Getterを追加
                        if (!declaration.getAnnotationByName("Getter").isPresent()) {
                            declaration.addAnnotation("Getter");
                            cu.addImport("lombok.Getter");
                        }

                        //@Value か　@Builder　か　@RequiredArgsConstructor が付いていた場合は、@AllArgsConstructorを付ける
                        if (
                                ((declaration.getAnnotationByName("Value").isPresent()
                                        || declaration.getAnnotationByName("Builder").isPresent()
                                        || declaration.getAnnotationByName("RequiredArgsConstructor").isPresent())
                                ) && !declaration.getAnnotationByName("AllArgsConstructor").isPresent()) {

                            declaration.addAnnotation("AllArgsConstructor");
                            cu.addImport("lombok.AllArgsConstructor");

                        }

                        //@Valueがあれば削除
                        declaration.getAnnotations().removeIf(anno -> anno.getName().asString().equals("Value"));
                        cu.getImports().removeIf(i -> i.getName().asString().equals("lombok.Value"));

                        //@RequiredArgsConstructorがあれば削除
                        declaration.getAnnotations().removeIf(anno -> anno.getName().asString().equals("RequiredArgsConstructor"));
                        cu.getImports().removeIf(i -> i.getName().asString().equals("lombok.RequiredArgsConstructor"));

                        //フィールドのfinal修飾子を削除
                        declaration.getFields().forEach(field -> {
                            field.getModifiers().removeIf(modifier -> modifier.getKeyword() == Modifier.Keyword.FINAL);
                        });
                    }
                }, null);

                // 変更をファイルに書き戻す
                try {
                    Files.write(javaFilePath, cu.toString().getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
