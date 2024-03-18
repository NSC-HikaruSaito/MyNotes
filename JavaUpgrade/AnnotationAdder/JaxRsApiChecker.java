package hoge.hoge;

import org.reflections.Reflections;
import javax.ws.rs.Path;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JaxRsApiChecker {

    private static final int HIERARCHY＿LIMIT = 10;

    public void check() {
        //スキャンするパッケージ名
        String packageToScan = "hoge";

        //@Pathがついているクラスをスキャン
        Reflections reflections = new Reflections(packageToScan);
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(Path.class);

        annotatedClasses.forEach(clazz -> {
            //Wsクラスに付けられたPathを取得
            Path wsPath = clazz.getAnnotation(Path.class);
            if (wsPath == null) {
                wsPath = clazz.getSuperclass().getAnnotation(Path.class);
            }

            //Wsクラス内のメソッド毎にチェック
            for (Method method : clazz.getDeclaredMethods()) {

                //@Pathがついているかチェック
                if (!method.isAnnotationPresent(Path.class)) {
                    continue;
                }

                StringBuilder apiDetail = new StringBuilder();
                apiDetail.append("API_LIST,")
                        //APIのPathの構築
                        .append(wsPath.value()).append("/").append(method.getAnnotation(Path.class).value()).append(",")
                        //Wsクラス名
                        .append(clazz.getName()).append(",")
                        //メソッド名
                        .append(method.getName()).append(",");

                //デフォルトコンストラクタが付いていないパラメーターを出力する
                this.getParamsLackingDefCons(method.getParameters()).forEach(param -> {
                    System.out.println(apiDetail.toString() + param);
                });

            }
        });
    }

    //デフォルトコンストラクタが付いていないクラスの名前のリストを返す
    private List<String> getParamsLackingDefCons(Parameter[] parameters) {
        return Arrays.stream(parameters).flatMap(param -> {
            Class<?> paramType = param.getType();

            //デフォルトコンストラクタが付いているかチェック
            if (this.hasDefaultConstructor(paramType)) {
                return Stream.empty();
            }

            //フィールドも同様にチェックして、デフォルトコンストラクタが付いていなければそれも加える
            return Stream.concat(Stream.of(paramType.getName()), this.getFieldsLackingDefCons(paramType.getDeclaredFields(), 0).stream());
        }).collect(Collectors.toList());
    }


    //フィールドの中でデフォルトコンストラクタがないものを取得
    private List<String> getFieldsLackingDefCons(Field[] fields, int parentHierarchy) {

        //同じクラスで階層化されると無限ループするので、制限する
        int hierarchy = parentHierarchy + 1;
        if(hierarchy>=HIERARCHY＿LIMIT){
            return Collections.emptyList();
        }

        return Arrays.stream(fields).flatMap(field -> {
            Class<?> paramType = field.getType();

            //フィールドがコレクションだった場合に要素のクラスに置き換える
            Type genericFieldType = field.getGenericType();
            if (genericFieldType instanceof ParameterizedType) {
                ParameterizedType type = (ParameterizedType) genericFieldType;
                Type[] fieldArgTypes = type.getActualTypeArguments();
                if (fieldArgTypes.length != 0) {
                    Type fieldArgType = fieldArgTypes[0];
                    try {
                        paramType = Class.forName(fieldArgType.getTypeName());
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            //staticフィールドなのか、デフォルトコンストラクタが付いているのか　をチェック
            if (Modifier.isStatic(field.getModifiers()) || this.hasDefaultConstructor(paramType)) {
                return Stream.empty();
            }

            //さらにフィールドのクラス内に持つフィールドに対して再帰的に処理していく
            return Stream.concat(Stream.of(paramType.getName()), this.getFieldsLackingDefCons(paramType.getDeclaredFields(),hierarchy).stream());
        }).collect(Collectors.toList());
    }

    //デフォルトコンストラクタがあるかどうか
    private boolean hasDefaultConstructor(Class<?> paramType) {
        boolean hasDefaultConstructor = true;
        //プリミティブ型や対象パッケージのクラス以外は対象外
        if (!paramType.isPrimitive() && paramType.getPackage() != null && paramType.getPackage().getName().startsWith("hoge")) {
            hasDefaultConstructor = Arrays.stream(paramType.getConstructors()).anyMatch(cons -> cons.getParameterCount() == 0);
        }
        return hasDefaultConstructor;
    }
}
