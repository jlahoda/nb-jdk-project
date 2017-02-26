/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2014 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2014 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jdk.jtreg;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.swing.Action;
import javax.tools.Diagnostic;

import com.sun.tdk.jcov.instrument.DataRoot;
import com.sun.tdk.jcov.report.ClassCoverage;
import com.sun.tdk.jcov.report.CoverageData;
import com.sun.tdk.jcov.report.DataType;
import com.sun.tdk.jcov.report.ItemCoverage;
import com.sun.tdk.jcov.report.MethodCoverage;
import com.sun.tdk.jcov.report.PackageCoverage;
import com.sun.tdk.jcov.report.ProductCoverage;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.java.hints.test.Utilities.TestLookup;
import org.netbeans.modules.jdk.jtreg.ActionProviderImpl.StackTraceLine;
import static org.netbeans.modules.jdk.jtreg.IntegrationTest.finished;
import static org.netbeans.modules.jdk.jtreg.IntegrationTest.outcome;
import org.netbeans.modules.jdk.project.common.api.BuildUtils;
import org.netbeans.spi.project.ActionProgress;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.Pair;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class ActionProviderImplTest extends NbTestCase {

    public ActionProviderImplTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        clearWorkDir();

        ((TestLookup) Lookup.getDefault()).setLookupsImpl(Lookups.metaInfServices(ClassPathProviderImplTest.class.getClassLoader()));
    }

    public void testModulatizedFullJDKTraditionalLangtools() throws Exception {
        doTestModulatizedFullJDK("langtools/build/classes");
    }

    public void testModulatizedFullJDKModularizedLangtools() throws Exception {
        doTestModulatizedFullJDK("langtools/build/jdk.compiler/classes");
    }

    private void doTestModulatizedFullJDK(String langtoolsClassesDir) throws Exception {
        createFile("modules.xml");
        FileObject javaBaseSource = createDir("jdk/src/java.base/share/classes");
        FileObject javaBaseTestFile = createFile("jdk/test/Test.java");
        createDir("jdk/src/java.base/linux/classes");
        createDir("jdk/src/java.desktop/share/classes");
        createDir("build/conf/jdk/modules/java.base");
        createDir("build/conf/jdk/modules/java.desktop");
        FileObject javaCompilerSource = createDir("langtools/src/java.compiler/share/classes");
        createDir(langtoolsClassesDir);
        FileObject langtoolsTestFile = createFile("langtools/test/Test.java");

        createDir("").setAttribute(BuildUtils.NB_JDK_PROJECT_BUILD, FileUtil.toFile(createDir("build/conf")));
        
        FileOwnerQuery.markExternalOwner(javaBaseTestFile.getParent(), FileOwnerQuery.getOwner(javaBaseSource), FileOwnerQuery.EXTERNAL_ALGORITHM_TRANSIENT);
        FileOwnerQuery.markExternalOwner(langtoolsTestFile.getParent(), FileOwnerQuery.getOwner(javaCompilerSource), FileOwnerQuery.EXTERNAL_ALGORITHM_TRANSIENT);

        Set<String> expectedAllSources = new HashSet<>(Arrays.asList("langtools/src/java.compiler/share/classes",
                                                                     "jdk/src/java.base/linux/classes",
                                                                     "jdk/src/java.base/share/classes",
                                                                     "jdk/src/java.desktop/share/classes"));
        assertEquals(expectedAllSources,
                     relative(FileUtil.toFileObject(getWorkDir()),
                              Arrays.asList(ActionProviderImpl.allSources(javaBaseTestFile).getRoots())));
        assertEquals(expectedAllSources,
                     relative(FileUtil.toFileObject(getWorkDir()),
                              Arrays.asList(ActionProviderImpl.allSources(langtoolsTestFile).getRoots())));
        
        String builtClassesJDKDirs = ActionProviderImpl.builtClassesDirsForBootClassPath(javaBaseTestFile);
        builtClassesJDKDirs = builtClassesJDKDirs.replace(getWorkDir().getAbsolutePath(), "");
        assertEquals(new HashSet<>(Arrays.asList("/build/conf/jdk/modules/java.desktop",
                                                 "/build/conf/jdk/modules/java.base")),
                     new HashSet<>(Arrays.asList(builtClassesJDKDirs.split(Pattern.quote(File.pathSeparator)))));

        String builtClassesLangtoolsDirs = ActionProviderImpl.builtClassesDirsForBootClassPath(langtoolsTestFile);
        builtClassesLangtoolsDirs = builtClassesLangtoolsDirs.replace(getWorkDir().getAbsolutePath(), "");
        assertEquals("/" + langtoolsClassesDir, builtClassesLangtoolsDirs);

        String jtregOutputDirs = Utilities.jtregOutputDir(langtoolsTestFile).getAbsolutePath();
        jtregOutputDirs = jtregOutputDirs.replace(getWorkDir().getAbsolutePath(), "");
        assertEquals("/langtools/build/nb-jtreg", jtregOutputDirs);
    }

    public void testStandaloneLangtoolsTraditionalLangtools() throws Exception {
        doTestStandaloneLangtools("langtools/build/classes");
    }

    public void testStandaloneLangtoolsModularizedLangtools() throws Exception {
        doTestStandaloneLangtools("langtools/build/jdk.compiler/classes");
    }

    private void doTestStandaloneLangtools(String langtoolsClassesDir) throws Exception {
        createDir("langtools/src/java.compiler/share/classes");
        createDir(langtoolsClassesDir);
        FileObject langtoolsProject = createDir("langtools/make/netbeans/langtools");
        FileObject testFile = createFile("langtools/test/Test.java");

        FileOwnerQuery.markExternalOwner(testFile.getParent(), FileOwnerQuery.getOwner(langtoolsProject), FileOwnerQuery.EXTERNAL_ALGORITHM_TRANSIENT);

        ClassPath allSources = ActionProviderImpl.allSources(testFile);

        assertEquals(new HashSet<>(Arrays.asList("langtools/src/java.compiler/share/classes")),
                     relative(FileUtil.toFileObject(getWorkDir()), Arrays.asList(allSources.getRoots())));

        String builtClassesDirs = ActionProviderImpl.builtClassesDirsForBootClassPath(testFile);
        builtClassesDirs = builtClassesDirs.replace(getWorkDir().getAbsolutePath(), "");
        assertEquals("/" + langtoolsClassesDir, builtClassesDirs);
        
        String jtregOutputDirs = Utilities.jtregOutputDir(testFile).getAbsolutePath();
        jtregOutputDirs = jtregOutputDirs.replace(getWorkDir().getAbsolutePath(), "");
        assertEquals("/langtools/build/nb-jtreg", jtregOutputDirs);
    }

    public void testImages1() throws Exception {
        createFile("modules.xml");
        createDir("jdk/src/java.base/share/classes");
        createDir("build/conf/images/j2sdk-image");
        createDir("langtools/src/java.compiler/share/classes");
        FileObject testFile = createFile("langtools/test/Test.java");

        createDir("").setAttribute(BuildUtils.NB_JDK_PROJECT_BUILD, FileUtil.toFile(createDir("build/conf")));

        FileOwnerQuery.markExternalOwner(testFile.getParent(), FileOwnerQuery.getOwner(createDir("langtools/src/java.compiler")), FileOwnerQuery.EXTERNAL_ALGORITHM_TRANSIENT);

        File target = BuildUtils.findTargetJavaHome(testFile);

        assertEquals("/build/conf/images/j2sdk-image", target.getAbsolutePath().substring(getWorkDir().getAbsolutePath().length()));
    }

    public void testImages2() throws Exception {
        createFile("modules.xml");
        createDir("jdk/src/java.base/share/classes");
        createDir("build/conf/jdk");
        createDir("build/conf/images/jdk");
        createDir("langtools/src/java.compiler/share/classes");
        FileObject testFile = createFile("langtools/test/Test.java");

        createDir("").setAttribute(BuildUtils.NB_JDK_PROJECT_BUILD, FileUtil.toFile(createDir("build/conf")));

        FileOwnerQuery.markExternalOwner(testFile.getParent(), FileOwnerQuery.getOwner(createDir("langtools/src/java.compiler")), FileOwnerQuery.EXTERNAL_ALGORITHM_TRANSIENT);

        File target = BuildUtils.findTargetJavaHome(testFile);

        assertEquals("/build/conf/jdk", target.getAbsolutePath().substring(getWorkDir().getAbsolutePath().length()));
    }

    public void testStackTracePattern() {
        List<Pair<String, StackTraceLine>> cases = new ArrayList<>();
        cases.add(Pair.of("	at com.sun.tools.javac.code.Scope$ScopeImpl.remove(Scope.java:406)",
                          new StackTraceLine("com/sun/tools/javac/code/Scope.java", 406)));
        cases.add(Pair.of("	at com.sun.tools.javac.code.Scope$ScopeImpl.<init>(Scope.java:402)",
                          new StackTraceLine("com/sun/tools/javac/code/Scope.java", 402)));
        cases.add(Pair.of("	at java.base/java.lang.reflect.Method.invoke(Method.java:537)",
                          new StackTraceLine("java/lang/reflect/Method.java", 537)));
        cases.add(Pair.of("        at org.netbeans.core.netigso//org.netbeans.core.netigso.Netigso.prepare(Netigso.java:167)",
                          new StackTraceLine("org/netbeans/core/netigso/Netigso.java", 167)));
        cases.add(Pair.of("        at org.netbeans.core.netigso/any/org.netbeans.core.netigso.Netigso.prepare(Netigso.java:167)",
                          new StackTraceLine("org/netbeans/core/netigso/Netigso.java", 167)));

        for (Pair<String, StackTraceLine> c : cases) {
            StackTraceLine parsed = ActionProviderImpl.matches(c.first());

            assertFalse(c.first() + ":" + String.valueOf(parsed), parsed == null ^ c.second() == null);

            if (parsed != null) {
                assertEquals(c.second().expectedFileName, parsed.expectedFileName);
                assertEquals(c.second().lineNumber, parsed.lineNumber);
            }
        }
    }

    public void testCoverage() throws IOException, InterruptedException {
        String repoPath = "/usr/local/home/lahvac/src/jdk/tl/langtools";
        FileObject repoPathFile = FileUtil.toFileObject(FileUtil.normalizeFile(new File(repoPath)));
        assertNotNull(repoPathFile);
        FileObject javaCompilerProjectLocation = repoPathFile.getFileObject("src/java.compiler");
        assertNotNull(javaCompilerProjectLocation);
        Project javaCompilerProject = ProjectManager.getDefault().findProject(javaCompilerProjectLocation);
        assertNotNull(javaCompilerProject);

//        OpenProjects.getDefault().open(new Project[] {javaCompilerProject}, false);
//
//        SourceUtils.waitScanFinished();

        FileObject testFile = repoPathFile.getFileObject("test/tools/javac/modules/util/ListBufferTest.java");

//        assertNotNull(testFile);
//
//        JavaSource.forFileObject(testFile).runUserActionTask(new Task<CompilationController>() {
//            @Override
//            public void run(CompilationController parameter) throws Exception {
//                parameter.toPhase(Phase.RESOLVED);
//                boolean hasError = false;
//                for (Diagnostic d : parameter.getDiagnostics()) {
//                    hasError |= d.getKind() == Diagnostic.Kind.ERROR;
//                }
//                assertFalse(parameter.getDiagnostics().toString(), hasError);
//            }
//        }, true);

        ContextAwareAction genericAction = FileUtil.getConfigObject("Actions/Debug/org-netbeans-modules-debugger-ui-actions-DebugTestFileAction.instance", ContextAwareAction.class);

        assertNotNull(genericAction);

        finished = new CountDownLatch(1);

        Action action = genericAction.createContextAwareInstance(Lookups.fixed(testFile, new ActionProgress() {
            @Override
            protected void started() {}
            @Override
            public void finished(boolean success) {
                outcome = success;
                finished.countDown();
            }
        }));

        assertTrue(action.isEnabled());

        action.actionPerformed(null);

        assertTrue(finished.await(10, TimeUnit.MINUTES));

        assertTrue(outcome);

//        OpenProjects.getDefault().close(new Project[] {javaCompilerProject});
    }

    private FileObject createDir(String dir) throws IOException {
        FileObject wd = FileUtil.toFileObject(getWorkDir());
        return FileUtil.createFolder(wd, dir);
    }

    private FileObject createFile(String file) throws IOException {
        FileObject wd = FileUtil.toFileObject(getWorkDir());
        return FileUtil.createData(wd, file);
    }

    private Set<String> relative(FileObject root, List<FileObject> files) {
        Set<String> result = new HashSet<>();

        for (FileObject f : files) {
            result.add(FileUtil.getRelativePath(root, f));
        }

        return result;
    }

    private static final class ProjectImpl implements Project {
        private final FileObject dir;

        public ProjectImpl(FileObject dir) {
            this.dir = dir;
        }

        @Override
        public FileObject getProjectDirectory() {
            return dir;
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }

    }

    @ServiceProvider(service=ProjectFactory.class)
    public static final class ProjectFactoryImpl implements ProjectFactory {

        @Override
        public boolean isProject(FileObject projectDirectory) {
            try {
                return loadProject(projectDirectory, null) != null;
            } catch (IOException ex) {
                return false;
            }
        }

        @Override
        public Project loadProject(FileObject projectDirectory, ProjectState state) throws IOException {
            if (projectDirectory.getFileObject("../../../modules.xml") != null) {
                return new ProjectImpl(projectDirectory);
            }

            if (projectDirectory.getNameExt().equals("langtools") &&
                projectDirectory.getParent().getNameExt().equals("netbeans") &&
                projectDirectory.getParent().getParent().getNameExt().equals("make")) {
                return new ProjectImpl(projectDirectory);
            }
            return null;
        }

        @Override
        public void saveProject(Project project) throws IOException, ClassCastException {
        }

    }

    public void XtestInstrument() throws Exception {
//        String jdkRoot = "/usr/local/home/lahvac/src/jdk/tl";
//
//        createDir(jdkRoot).setAttribute(ActionProviderImpl.NB_JDK_PROJECT_BUILD, FileUtil.toFile(createDir("/usr/local/home/lahvac/src/jdk/tl/build/linux-x86_64-normal-server-fastdebug")));
//
//        FileObject testFile =
//        FileOwnerQuery.markExternalOwner(testFile.getParent(), FileOwnerQuery.getOwner(createDir(jdkRoot + "/langtools/src/java.compiler")), FileOwnerQuery.EXTERNAL_ALGORITHM_TRANSIENT);
//
//
//        System.err.println(Class.forName("org.objectweb.asm.ClassReader").getProtectionDomain().getCodeSource().getLocation());
//        ActionProviderImpl.instrument(new File("/usr/local/home/lahvac/src/jdk/tl/langtools/build/modules"), new File("/usr/local/home/lahvac/src/jdk/tl/langtools/build/nb-jtreg/classes"));
//        ActionProviderImpl.instrument(new File("/usr/local/home/lahvac/src/jdk/tl/langtools/build/modules/java.compiler/javax/lang/model/element/Modifier.class"), new File("/usr/local/home/lahvac/src/jdk/tl/langtools/build/nb-jtreg/classes"));
//        ProductCoverage coverage = new ProductCoverage("/tmp/jcov.out");
//
//        for (PackageCoverage pcov : coverage.getPackages()) {
//            if (!pcov.getName().contains("com.sun.tools.javac.comp"))
//                continue;
//            for (ClassCoverage ccov : pcov.getClasses()) {
//                if (!ccov.getFullClassName().contains(".MemberEnter"))
//                    continue;
//                System.err.println(" class:" + ccov.getSource());
//                System.err.println(" class:" + ccov.getFullClassName());
//                System.err.println(" class:" + ccov.getFullClassNameFilename());
//                for (MethodCoverage mcov : ccov.getMethods()) {
////                    mcov.getStartLine()
//                    System.err.println("mcov: " + mcov.getReadableSignature());
//                    for (ItemCoverage icov : mcov.getItems()) {
////                        ccov.
//                        System.err.println("1. icov: " + icov.getDataType());
//                        System.err.println("1. icov: " + icov.getSourceLine());
//                        System.err.println("2. icov: " + icov.getStartLine());
//                        System.err.println("3. icov: " + icov.getEndLine());
//                        System.err.println("4. icov: " + icov.getCount());
//                        CoverageData data = icov.getData(DataType.BLOCK);
//                        System.err.println("5. icov: " + data + ":" + data.getAnc() + "; " + data.getCovered() + "; " + data.getTotal());
//                    }
//                }
//                System.err.println(" class:" + ccov.getFullClassNameFilename());
//            }
//        }

        DataRoot.read("/tmp/jcov.out");
    }
}
