/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2015 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2015 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jdk.project;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.ClassPath.PathConversionMode;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;

/**
 *
 * @author lahvac
 */
public class ClassPathProviderImplTest extends NbTestCase {

    public ClassPathProviderImplTest(String name) {
        super(name);
    }

    private FileObject root;

    @Override
    protected void setUp() throws IOException {
        clearWorkDir();

        root = FileUtil.toFileObject(getWorkDir());
    }

    public void testModuleXMLTransitiveDependencies() throws IOException {
        setupModuleXMLJDK(root);
        doTestCompileCP();
    }

    public void testModuleInfoTransitiveDependencies() throws IOException {
        setupModuleInfoJDK(root);
        doTestCompileCP();
    }

    private void doTestCompileCP() {
        File fakeJdkClasses = InstalledFileLocator.getDefault().locate("modules/ext/fakeJdkClasses.zip", "org.netbeans.modules.jdk.project", false);

        checkCompileClassPath("repo/src/test1",
                              "${wd}/jdk/src/java.base/fake-target.jar" +
                              File.pathSeparatorChar +
                              "${wd}/langtools/src/java.compiler/fake-target.jar" +
                              File.pathSeparatorChar +
                              fakeJdkClasses.getAbsolutePath());
        checkCompileClassPath("repo/src/test2",
                              "${wd}/jdk/src/java.base/fake-target.jar" +
                              File.pathSeparatorChar +
                              "${wd}/langtools/src/java.compiler/fake-target.jar" +
                              File.pathSeparatorChar +
                              "${wd}/langtools/src/jdk.compiler/fake-target.jar" +
                              File.pathSeparatorChar +
                              fakeJdkClasses.getAbsolutePath());
        checkCompileClassPath("repo/src/test3",
                              "${wd}/jdk/src/java.base/fake-target.jar" +
                              File.pathSeparatorChar +
                              "${wd}/repo/src/test2/fake-target.jar" +
                              File.pathSeparatorChar +
                              fakeJdkClasses.getAbsolutePath());
    }

    private void checkCompileClassPath(String module, String expected) {
        FileObject prj = root.getFileObject(module);
        FileObject src = prj.getFileObject("share/classes");

        Project project = FileOwnerQuery.getOwner(src);

        assertNotNull(project);

        String actual = ClassPath.getClassPath(src, ClassPath.COMPILE).toString(PathConversionMode.PRINT).replace(getWorkDirPath(), "${wd}");

        assertEquals(expected, actual);
    }

    private void setupModuleXMLJDK(FileObject jdkRoot) throws IOException {
        copyString2File(FileUtil.createData(jdkRoot, "modules.xml"),
                        "<?xml version=\"1.0\" encoding=\"us-ascii\"?>\n" +
                        "<modules>\n" +
                        "  <module>\n" +
                        "    <name>java.base</name>\n" +
                        "    <export>\n" +
                        "      <name>java.lang</name>\n" +
                        "    </export>\n" +
                        "  </module>\n" +
                        "  <module>\n" +
                        "    <name>java.compiler</name>\n" +
                        "    <depend>java.base</depend>\n" +
                        "    <export>\n" +
                        "      <name>javax.lang.model</name>\n" +
                        "    </export>\n" +
                        "  </module>\n" +
                        "  <module>\n" +
                        "    <name>jdk.compiler</name>\n" +
                        "    <depend>java.base</depend>\n" +
                        "    <depend re-exports=\"true\">java.compiler</depend>\n" +
                        "    <export>\n" +
                        "      <name>com.sun.tools.javac</name>\n" +
                        "    </export>\n" +
                        "  </module>\n" +
                        "  <module>\n" +
                        "    <name>test1</name>\n" +
                        "    <depend>java.base</depend>\n" +
                        "    <depend>java.compiler</depend>\n" +
                        "    <export>\n" +
                        "      <name>test1</name>\n" +
                        "    </export>\n" +
                        "  </module>\n" +
                        "  <module>\n" +
                        "    <name>test2</name>\n" +
                        "    <depend>java.base</depend>\n" +
                        "    <depend>jdk.compiler</depend>\n" +
                        "  </module>\n" +
                        "  <module>\n" +
                        "    <name>test3</name>\n" +
                        "    <depend>java.base</depend>\n" +
                        "    <depend>test2</depend>\n" +
                        "  </module>\n" +
                        "</modules>\n");

        setupOrdinaryFiles(jdkRoot);
    }
    
    private void setupModuleInfoJDK(FileObject jdkRoot) throws IOException {
        copyString2File(FileUtil.createData(jdkRoot, "jdk/src/java.base/share/classes/module-info.java"),
                        "module java.base {\n" +
                        "    exports java.lang;\n" +
                        "}\n");
        copyString2File(FileUtil.createData(jdkRoot, "langtools/src/java.compiler/share/classes/module-info.java"),
                        "module java.compiler {\n" +
                        "    requires java.base;\n" +
                        "    exports javax.lang.model;\n" +
                        "}\n");
        copyString2File(FileUtil.createData(jdkRoot, "langtools/src/jdk.compiler/share/classes/module-info.java"),
                        "module jdk.compiler {\n" +
                        "    requires java.base;\n" +
                        "    requires public java.compiler;\n" +
                        "    exports com.sun.tools.javac;\n" +
                        "}\n");
        copyString2File(FileUtil.createData(jdkRoot, "repo/src/test1/share/classes/module-info.java"),
                        "module test1 {\n" +
                        "    requires java.compiler;\n" +
                        "    exports test1;\n" +
                        "}\n");
        copyString2File(FileUtil.createData(jdkRoot, "repo/src/test2/share/classes/module-info.java"),
                        "module test2 {\n" +
                        "    requires java.base;\n" +
                        "    requires jdk.compiler;\n" +
                        "}\n");
        copyString2File(FileUtil.createData(jdkRoot, "repo/src/test3/share/classes/module-info.java"),
                        "module test3 {\n" +
                        "    requires test2;\n" +
                        "}\n");

        setupOrdinaryFiles(jdkRoot);
    }

    private void setupOrdinaryFiles(FileObject jdkRoot) throws IOException {
        copyString2File(FileUtil.createData(jdkRoot, "jdk/src/java.base/share/classes/java/lang/Object.java"),
                        "package java.lang;\n" +
                        "public class Object {}\n");
        copyString2File(FileUtil.createData(jdkRoot, "langtools/src/java.compiler/share/classes/javax/lang/model/SourceVersion.java"),
                        "package javax.lang.model;\n" +
                        "public enum SourceVersion {\n" +
                        "    RELEASE_9;\n" +
                        "}\n");
        copyString2File(FileUtil.createData(jdkRoot, "langtools/src/jdk.compiler/share/classes/com/sun/tools/javac/Main.java"),
                        "package com.sun.tools.javac;\n" +
                        "public class Main {}\n");
        copyString2File(FileUtil.createData(jdkRoot, "repo/src/test1/share/classes/test1/Test.java"),
                        "package test1;\n" +
                        "public class Test {}\n");
        copyString2File(FileUtil.createData(jdkRoot, "repo/src/test2/share/classes/test2/Test.java"),
                        "package test2;\n" +
                        "public class Test {}\n");
        copyString2File(FileUtil.createData(jdkRoot, "repo/src/test3/share/classes/test3/Test.java"),
                        "package test3;\n" +
                        "public class Test {}\n");
    }

    private void copyString2File(FileObject file, String content) throws IOException {
        try (OutputStream out = file.getOutputStream()) {
            out.write(content.getBytes("UTF-8"));
        }
    }

    static {
        System.setProperty("netbeans.dirs", System.getProperty("cluster.path.final", ""));
    }
}
