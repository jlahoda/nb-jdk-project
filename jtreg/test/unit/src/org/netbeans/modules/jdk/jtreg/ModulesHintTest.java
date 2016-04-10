/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2016 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2016 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jdk.jtreg;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.LanguagePath;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.Sources;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.lexer.LanguageEmbedding;
import org.netbeans.spi.lexer.LanguageProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Pair;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.lookup.ServiceProvider;

public class ModulesHintTest extends NbTestCase {

    public ModulesHintTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        clearWorkDir();
    }

    public void testSimpleModules() throws Exception {
        doTest("/**@test\n" +
               " * |@modules| java.compiler/com.sun.tools.javac.file\n" +
               " *          java.compiler/com.sun.tools.javac.util\n" +
               " */\n" +
               "public class Test {\n" +
               "    com.sun.tools.javac.main.Main m;\n" +
               "}\n",
               "/**@test\n" +
               " * @modules java.compiler/com.sun.tools.javac.main\n" +
               " */\n" +
               "public class Test {\n" +
               "    com.sun.tools.javac.main.Main m;\n" +
               "}\n");
    }

    public void testMultiplePackages() throws Exception {
        doTest("/**@test\n" +
               " * |@modules| java.compiler/com.sun.tools.javac.file\n" +
               " *          java.compiler/com.sun.tools.javac.util\n" +
               " */\n" +
               "public class Test {\n" +
               "    com.sun.tools.javac.main.Main m;\n" +
               "    com.sun.tools.javac.util.List l;\n" +
               "}\n",
               "/**@test\n" +
               " * @modules java.compiler/com.sun.tools.javac.main\n" +
               " *          java.compiler/com.sun.tools.javac.util\n" +
               " */\n" +
               "public class Test {\n" +
               "    com.sun.tools.javac.main.Main m;\n" +
               "    com.sun.tools.javac.util.List l;\n" +
               "}\n");
    }

    public void testLibraries() throws Exception {
        doTest("/**@test\n" +
               " * @library /lib\n" +
               " * |@modules| java.compiler/com.sun.tools.javac.file\n" +
               " *          java.compiler/com.sun.tools.javac.util\n" +
               " * @build ToolBox Test\n" +
               " */\n" +
               "public class Test {\n" +
               "}\n",
               "/**@test\n" +
               " * @library /lib\n" +
               " * @modules java.compiler/com.sun.tools.javac.main\n" +
               " * @build ToolBox Test\n" +
               " */\n" +
               "public class Test {\n" +
               "}\n");
    }

    public void testNoModules() throws Exception {
        doTest("/**|@test|\n" +
               " */\n" +
               "public class Test {\n" +
               "    com.sun.tools.javac.main.Main m;\n" +
               "}\n",
               "/**@test\n" +
               " * @modules java.compiler/com.sun.tools.javac.main\n" +
               " */\n" +
               "public class Test {\n" +
               "    com.sun.tools.javac.main.Main m;\n" +
               "}\n");
    }

    public void testArrayLength() throws Exception {
        doTest("/**|@test|\n" +
               " */\n" +
               "public class Test {\n" +
               "    com.sun.tools.javac.main.Main m;\n" +
               "    int[] arr;\n" +
               "    {\n" +
               "        System.err.println(arr.length);\n" +
               "    }\n" +
               "}\n",
               "/**@test\n" +
               " * @modules java.compiler/com.sun.tools.javac.main\n" +
               " */\n" +
               "public class Test {\n" +
               "    com.sun.tools.javac.main.Main m;\n" +
               "    int[] arr;\n" +
               "    {\n" +
               "        System.err.println(arr.length);\n" +
               "    }\n" +
               "}\n");
    }

    public void testImplicit() throws Exception {
        doTest("/**@test\n" +
               " * @library /lib\n" +
               " * |@modules| java.compiler/com.sun.tools.javac.file\n" +
               " *          java.compiler/com.sun.tools.javac.util\n" +
               " */\n" +
               "public class Test {\n" +
               "    ToolBox t;\n" +
               "}\n",
               "/**@test\n" +
               " * @library /lib\n" +
               " * @modules java.compiler/com.sun.tools.javac.main\n" +
               " */\n" +
               "public class Test {\n" +
               "    ToolBox t;\n" +
               "}\n");
    }

    public void testInMoreThanOne() throws Exception {
        doTest("/**@test\n" +
               " * @library /lib\n" +
               " * |@modules| java.compiler/com.sun.tools.javac.file\n" +
               " * @build ToolBox Test\n" +
               " */\n" +
               "public class Test {\n" +
               "    com.sun.tools.javac.util.List l;\n" +
               "}\n",
               "/**@test\n" +
               " * @library /lib\n" +
               " * @modules java.compiler/com.sun.tools.javac.main\n" +
               " *          java.compiler/com.sun.tools.javac.util\n" +
               " * @build ToolBox Test\n" +
               " */\n" +
               "public class Test {\n" +
               "    com.sun.tools.javac.util.List l;\n" +
               "}\n");
    }

    public void testRequiresExported() throws Exception {
        doTest("/**|@test|\n" +
               " */\n" +
               "public class Test {\n" +
               "    com.sun.tools.javac.Main m;\n" +
               "}\n",
               "/**@test\n" +
               " * @modules java.compiler\n" +
               " */\n" +
               "public class Test {\n" +
               "    com.sun.tools.javac.Main m;\n" +
               "}\n");
    }

    public void testNoModulesNeeded() throws Exception {
        doTest("/**@test\n" +
               " * |@modules| java.compiler\n" +
               " */\n" +
               "public class Test {\n" +
               "}\n",
               "/**@test\n" +
               " */\n" +
               "public class Test {\n" +
               "}\n");
    }

    public void testNormalization() throws Exception {
        doTest("/**|@test|\n" +
               " */\n" +
               "public class Test {\n" +
               "    jdk.jshell.JShell s;\n" +
               "    com.sun.tools.javac.Main m;\n" +
               "}\n",
               "/**@test\n" +
               " * @modules jdk.jshell\n" +
               " */\n" +
               "public class Test {\n" +
               "    jdk.jshell.JShell s;\n" +
               "    com.sun.tools.javac.Main m;\n" +
               "}\n");
    }

    private void doTest(String originalTest, final String expected) throws Exception {
        createData("jdk/src/java.base/share/classes/module-info.java", "module java.base { exports java.lang; }");
        createData("jdk/src/java.base/share/classes/java/lang/Object.java", "package java.lang; public class Object {}");
        createData("jdk/src/java.instrument/share/classes/module-info.java", "module java.instrument { exports java.lang.instrument; }");
        createData("jdk/src/java.instrument/share/classes/java/lang/instrument/Instrumentation.java", "package java.lang.instrument; public class Instrumentation {}");
        createData("langtools/src/java.compiler/share/classes/module-info.java", "module java.compiler { exports com.sun.tools.javac; exports com.sun.tools.javac.main to jdk.compiler;}");
        createData("langtools/src/java.compiler/share/classes/com/sun/tools/javac/Main.java", "package com.sun.tools.javac; public class Main {}");
        createData("langtools/src/java.compiler/share/classes/com/sun/tools/javac/main/Main.java", "package com.sun.tools.javac.main; public class Main {}");
        createData("langtools/src/java.compiler/share/classes/com/sun/tools/javac/util/List.java", "package com.sun.tools.javac.util; public class List {}");
        createData("langtools/src/jdk.jshell/share/classes/module-info.java", "module jdk.jshell { requires java.compiler; exports jdk.jshell; }");
        createData("langtools/src/jdk.jshell/share/classes/jdk/jshell/JShell.java", "package jdk.jshell; public class JShell {}");
        createData("langtools/test/lib/ToolBox.java", "public class ToolBox { com.sun.tools.javac.main.Main m; }");

        String[] originalPart = originalTest.split("\\|");

        assertEquals(3, originalPart.length);

        final int warningStart = originalPart[0].length();
        final int warningEnd   = originalPart[0].length() + originalPart[1].length();

        FileObject testTest = createData("langtools/test/feature/Test.java", originalPart[0] + originalPart[1] + originalPart[2]);

        Project instrument = FileOwnerQuery.getOwner(FileUtil.toFileObject(new File(getWorkDir(), "jdk/src/java.instrument")));
        Project javaCompiler = FileOwnerQuery.getOwner(FileUtil.toFileObject(new File(getWorkDir(), "langtools/src/java.compiler")));
        Project jdkJShell = FileOwnerQuery.getOwner(FileUtil.toFileObject(new File(getWorkDir(), "langtools/src/jdk.jshell")));

        OpenProjects.getDefault().open(new Project[] {instrument, javaCompiler, jdkJShell}, true);

        org.netbeans.modules.parsing.impl.indexing.RepositoryUpdater.getDefault().start(true);

        SourceUtils.waitScanFinished();

        ProjectUtils.getSources(javaCompiler).getSourceGroups(Sources.TYPE_GENERIC);

        JavaSource js = JavaSource.forFileObject(testTest);
        final Fix[] fix = new Fix[1];

        Source.create(testTest).getDocument(true);

        js.runUserActionTask(new Task<CompilationController>() {
            @Override
            public void run(CompilationController cc) throws Exception {
                cc.toPhase(JavaSource.Phase.UP_TO_DATE);
                Pair<Fix, int[]> change = ModulesHint.computeChange(cc);
                if (change == null)
                    return ;
                fix[0] = change.first();
                assertEquals(warningStart, change.second()[0]);
                assertEquals(warningEnd, change.second()[1]);
            }
        }, true);

        if (fix[0] != null) {
            fix[0].implement();
        }

        js.runUserActionTask(new Task<CompilationController>() {
            @Override
            public void run(CompilationController cc) throws Exception {
                cc.toPhase(JavaSource.Phase.UP_TO_DATE);
                assertEquals(expected, cc.getText());
                assertNull(ModulesHint.computeChange(cc));
            }
        }, true);
    }

    private FileObject createData(String relPath, String content) throws IOException {
        File workDir = getWorkDir();
        FileObject file = FileUtil.createData(new File(workDir, relPath));

        try (Writer w = new OutputStreamWriter(file.getOutputStream())) {
            w.write(content);
        }

        return file;
    }

    @ServiceProvider(service=LanguageProvider.class)
    public static final class JavaLanguageProvider extends LanguageProvider {

        @Override
        public Language<?> findLanguage(String mimeType) {
            if ("text/x-java".equals(mimeType)) {
                return JavaTokenId.language();
            }

            return null;
        }

        @Override
        public LanguageEmbedding<?> findLanguageEmbedding(Token<?> token, LanguagePath languagePath, InputAttributes inputAttributes) {
            return null;
        }

    }

    static {
        System.setProperty("org.openide.util.Lookup", TestLookup.class.getName());
        System.setProperty("netbeans.dirs", System.getProperty("cluster.path.final"));
    }

    public static class TestLookup extends ProxyLookup {

        public TestLookup() {
            super(Lookups.metaInfServices(TestLookup.class.getClassLoader()));
        }

    }
}
