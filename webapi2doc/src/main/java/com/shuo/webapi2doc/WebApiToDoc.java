package com.shuo.webapi2doc;

import com.alibaba.fastjson.JSON;
import javassist.*;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class WebApiToDoc {

    private static final String EXEC_PATH = "C:/Users/linshuo.UT/Desktop/webApi2doc/dist/webApi2doc/webApi2doc.exe";

    private static final String SCAN_PATH = "com.simba.controller";

    /**
     * 生成的doc文件在和.idea同目录下的1.new_docx下
     */
    public static void main(String[] args) {
        /* 配置要转换的class和转换程序路径 */
        List<Class<?>> classList = ClassUtil.getClasses(SCAN_PATH, false, false);
        /* 进行转换 */
        {
            WebApiToDoc tool = new WebApiToDoc();
            Class[] classes = new Class[classList.size()];
            String json = tool.controllerToJson(classList.toArray(classes));
            try {
                String cmd = EXEC_PATH + " \"" + json.replace("\"", "\\\"") + "\"";
                Runtime.getRuntime().exec(cmd);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * 将Controller的Api的转成beanList的json格式
     *
     * @param classes
     * @return
     */
    public String controllerToJson(Class<?>[] classes) {
        List<ControllerBean> beanList = new ArrayList<>();
        for (Class<?> clazz : classes) {
            List<ControllerBean.MethodBean> mbList = new ArrayList<>();
            RequestMapping classAnno = clazz.getAnnotation(RequestMapping.class);
            String classAnnoValue = classAnno.value()[0];
            Method[] methods = clazz.getDeclaredMethods();
            for (Method m : methods) {
                RequestMapping anno = m.getAnnotation(RequestMapping.class);
                if (anno == null) continue;
                String requestMappingValue = anno.value()[0];
                // * web uri
                String uri = classAnnoValue.replace("/", "") + "/" + requestMappingValue.replace("/", "");
                // * 参数们
                String[] params = listParameters(clazz, m.getName());
                // 因为这句导致MethodBean都必须为静态, 进而导致ControllerBean必须为静态
                ControllerBean.MethodBean bean = new ControllerBean.MethodBean(m.getName(), uri, params);
                mbList.add(bean);
            }
            ControllerBean.MethodBean[] mBeans = new ControllerBean.MethodBean[mbList.size()];
            ControllerBean controllerBean = new ControllerBean(cutLastWord(clazz.getName()), mbList.toArray(mBeans));
            beanList.add(controllerBean);
        }
        return JSON.toJSONString(beanList, false);
    }


    /**
     * 获取某个方法的局部变量 (javassist法)
     *
     * @param clazz
     * @param methodName
     * @return 参数名列表
     */
    private String[] listParameters(Class<?> clazz, String methodName) {
        ClassPool pool = ClassPool.getDefault();
        try {
            CtClass ctClass = pool.get(clazz.getName());
            CtMethod ctMethod = ctClass.getDeclaredMethod(methodName);
            // 使用javassist的反射方法的参数名
            MethodInfo methodInfo = ctMethod.getMethodInfo();
            CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
            // 获取所有局部变量(包括输入参数+方法局部变量)
            LocalVariableAttribute attr = (LocalVariableAttribute) codeAttribute.getAttribute(LocalVariableAttribute.tag);
            if (attr != null) {
                List<String> nameList = new ArrayList<>();
                int len = ctMethod.getParameterTypes().length;
                boolean isStatic = Modifier.isStatic(ctMethod.getModifiers());
                if (isStatic) {
                    for (int i = 0; i < len; i++) {
                        // 非静态的成员函数的第一个参数是this
                        nameList.add(attr.variableName(i + 1));
                    }
                } else {
                    int index = 0;
                    // this后面才是真正的输入参数, 前面可能混入局部变量
                    while (!attr.variableName(index).equals("this") && index < 20) {
                        index++;
                    }
                    for (int i = index; i < len + index; i++) {
                        nameList.add(attr.variableName(i + 1));
                    }
                }
                String[] names = new String[nameList.size()];
                return nameList.toArray(names);
            }
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取实类名
     *
     * @param packageStr
     * @return
     */
    public static String cutLastWord(String packageStr) {
        int index = packageStr.lastIndexOf('.');
        return packageStr.substring(index + 1);
    }

    /**
     * 获取某个类上的所有注解
     *
     * @param clazz
     * @return
     */
    private String[] listAnnotations(Class<?> clazz) {
        Annotation[] annotations = clazz.getAnnotations();
        List<String> annoStrList = Arrays.stream(annotations)
                .map(anno -> cutLastWord(anno.annotationType().toString()))
                .collect(Collectors.toList());
        String[] ret = new String[annoStrList.size()];
        return annoStrList.toArray(ret);
    }

    /**
     * 获取某个方法上的所有注解
     *
     * @param method
     * @return
     */
    private String[] listAnnotations(Method method) {
        Annotation[] annotations = method.getAnnotations();
        List<String> annoStrList = Arrays.stream(annotations)
                .map(anno -> cutLastWord(anno.annotationType().toString()))
                .collect(Collectors.toList());
        String[] ret = new String[annoStrList.size()];
        return annoStrList.toArray(ret);
    }

    private static class ControllerBean {
        private String className;
        private MethodBean[] methods;

        public ControllerBean(String className, MethodBean[] methods) {
            this.className = className;
            this.methods = methods;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public MethodBean[] getMethods() {
            return methods;
        }

        public void setMethods(MethodBean[] methods) {
            this.methods = methods;
        }

        private static class MethodBean {
            private String methodName;
            private String uri;
            private String[] parameters;

            public MethodBean(String methodName, String uri, String[] parameters) {
                this.methodName = methodName;
                this.uri = uri;
                this.parameters = parameters;
            }

            public String getMethodName() {
                return methodName;
            }

            public void setMethodName(String methodName) {
                this.methodName = methodName;
            }

            public String getUri() {
                return uri;
            }

            public void setUri(String uri) {
                this.uri = uri;
            }

            public String[] getParameters() {
                return parameters;
            }

            public void setParameters(String[] parameters) {
                this.parameters = parameters;
            }
        }

    }

    static class ClassUtil {

        /**
         * 取得某个接口下所有实现这个接口的类
         */
        public static List<Class> getAllClassByInterface(Class c) {
            List<Class> returnClassList = null;
            if (c.isInterface()) {
                // 获取当前的包名
                String packageName = c.getPackage().getName();
                // 获取当前包下以及子包下所以的类
                List<Class<?>> allClass = getClasses(packageName, true, true);
                if (allClass != null) {
                    returnClassList = new ArrayList<Class>();
                    for (Class classes : allClass) {
                        // 判断是否是同一个接口
                        if (c.isAssignableFrom(classes)) {
                            // 本身不加入进去
                            if (!c.equals(classes)) {
                                returnClassList.add(classes);
                            }
                        }
                    }
                }
            }
            return returnClassList;
        }


        /*
         * 取得某一类所在包的所有类名 不含迭代
         */
        public static String[] getPackageAllClassName(String classLocation, String packageName) {
            //将packageName分解
            String[] packagePathSplit = packageName.split("[.]");
            String realClassLocation = classLocation;
            int packageLength = packagePathSplit.length;
            for (int i = 0; i < packageLength; i++) {
                realClassLocation = realClassLocation + File.separator + packagePathSplit[i];
            }
            File packeageDir = new File(realClassLocation);
            if (packeageDir.isDirectory()) {
                return packeageDir.list();
            }
            return null;
        }


        /**
         * 从包package中获取所有的Class
         *
         * @param packageName
         * @return
         */
        public static List<Class<?>> getClasses(String packageName) {
            return getClasses(packageName, true, true);
        }

        /**
         * 从包package中获取所有的Class
         *
         * @param packageName 包路径
         * @param recursive   递归扫
         * @param includeJar  依赖的jar扫
         * @return
         */
        public static List<Class<?>> getClasses(String packageName, boolean recursive, boolean includeJar) {
            List<Class<?>> classes = new ArrayList<>();
            String packageDirName = packageName.replace('.', '/');
            //定义一个枚举的集合 并进行循环来处理这个目录下的things
            Enumeration<URL> dirs;
            try {
                dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
                while (dirs.hasMoreElements()) {
                    // url实例: file:/D:/workspace_idea/smartHomePlatform/trunk/cloudLock/simba-user/target/classes/com/simba/controller
                    URL url = dirs.nextElement();
                    // 文件类型: file jar 等
                    String protocol = url.getProtocol();
                    if ("file".equals(protocol)) {
                        //获取包的物理路径
                        String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                        // 将filePath下的类都加到classes中
                        findAndAddClassesInPackageByFile(packageName, filePath, recursive, classes);
                    } else if (includeJar && "jar".equals(protocol)) {
                        JarFile jar;
                        try {
                            jar = ((JarURLConnection) url.openConnection()).getJarFile();
                            Enumeration<JarEntry> entries = jar.entries();
                            while (entries.hasMoreElements()) {
                                //获取jar里的一个实体 可以是目录 和一些jar包里的其他文件 如META-INF等文件
                                JarEntry entry = entries.nextElement();
                                String name = entry.getName();
                                if (name.charAt(0) == '/') {
                                    name = name.substring(1);
                                }
                                if (name.startsWith(packageDirName)) {
                                    int idx = name.lastIndexOf('/');
                                    if (idx != -1) {
                                        packageName = name.substring(0, idx).replace('/', '.');
                                    }
                                    //如果可以迭代下去 并且是一个包
                                    if ((idx != -1) || recursive) {
                                        //如果是一个.class文件 而且不是目录
                                        if (name.endsWith(".class") && !entry.isDirectory()) {
                                            //去掉后面的".class" 获取真正的类名
                                            String className = name.substring(packageName.length() + 1, name.length() - 6);
                                            try {
                                                classes.add(Class.forName(packageName + '.' + className));
                                            } catch (ClassNotFoundException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return classes;
        }

        /**
         * 以文件的形式来获取包下的所有Class
         *
         * @param packageName
         * @param packagePath
         * @param recursive
         * @param classes
         */
        public static void findAndAddClassesInPackageByFile(String packageName, String packagePath, final boolean recursive, List<Class<?>> classes) {
            //获取此包的目录 建立一个File
            File dir = new File(packagePath);
            //如果不存在或者 也不是目录就直接返回
            if (!dir.exists() || !dir.isDirectory()) {
                return;
            }
            //如果存在 就获取包下的所有文件 包括目录
            //自定义过滤规则 如果可以循环(包含子目录) 或则是以.class结尾的文件(编译好的java类文件)
            File[] dirfiles = dir.listFiles(file -> (recursive && file.isDirectory()) || (file.getName().endsWith(".class")));
            //循环所有文件
            for (File file : dirfiles) {
                //如果是目录 则继续扫描
                if (file.isDirectory()) {
                    findAndAddClassesInPackageByFile(packageName + "." + file.getName(),
                            file.getAbsolutePath(),
                            recursive,
                            classes);
                } else {
                    //如果是java类文件 去掉后面的.class 只留下类名
                    String className = file.getName().substring(0, file.getName().length() - 6);
                    try {
                        //添加到集合中去
                        classes.add(Class.forName(packageName + '.' + className));
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


}
