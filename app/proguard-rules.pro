# 拾词输入法 混淆规则
# 输入法服务由系统反射创建，必须保留
-keep public class com.pickword.ime.ime.PickWordImeService { public *; }
# 输入法的子类型定义来自 res/xml/method.xml，保留相关类
-keep class com.pickword.ime.** { *; }
# 数据库游标/列名不涉及反射，直接美化的通用规则
-dontobfuscate
