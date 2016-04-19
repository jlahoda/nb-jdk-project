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

import com.sun.javatest.Harness;
import com.sun.javatest.regtest.Action;
import com.sun.javatest.regtest.ActionCallBack;
import com.sun.javatest.regtest.BadArgs;
import com.sun.javatest.regtest.Main;
import com.sun.javatest.regtest.Main.Fault;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.jdk.project.common.api.ShortcutUtils;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.project.ActionProgress;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.netbeans.spi.project.support.ant.PropertyProvider;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.openide.cookies.LineCookie;
import org.openide.cookies.OpenCookie;
import org.openide.execution.ExecutionEngine;
import org.openide.execution.ExecutorTask;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.text.Line;
import org.openide.text.Line.ShowOpenType;
import org.openide.text.Line.ShowVisibilityType;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.Mutex;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputEvent;
import org.openide.windows.OutputListener;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=ActionProvider.class)
public class ActionProviderImpl implements ActionProvider {

    static final String NB_JDK_PROJECT_BUILD = "nb-jdk-project-build";
    
    private static final String[] ACTIONS = new String[] {
        COMMAND_TEST_SINGLE,
        COMMAND_DEBUG_TEST_SINGLE,
    };

    @Override
    public String[] getSupportedActions() {
        return ACTIONS;
    }

    @Override
    public void invokeAction(String command, Lookup context) throws IllegalArgumentException {
        try {
            createAndRunTest(context, COMMAND_DEBUG_TEST_SINGLE.equals(command));
        } catch (BadLocationException | IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final String COMMAND_BUILD_FAST = "build-fast";
    private static final String COMMAND_BUILD_GENERIC_FAST = "build-generic-fast";

    //public for test
    @Messages({"# {0} - simple file name",
               "DN_Debugging=Debugging ({0})",
               "# {0} - simple file name",
               "DN_Running=Running ({0})"})
    public static ExecutorTask createAndRunTest(Lookup context, final boolean debug) throws BadLocationException, IOException {
        final FileObject file = context.lookup(FileObject.class);
        String ioName = debug ? Bundle.DN_Debugging(file.getName()) : Bundle.DN_Running(file.getName());
        final InputOutput io = IOProvider.getDefault().getIO(ioName, false);
        File jtregOutput = jtregOutputDir(file);
        final File jtregWork = new File(jtregOutput, "work");
        final File jtregReport = new File(jtregOutput, "report");
        final ActionProgress progress = ActionProgress.start(context);
        return ExecutionEngine.getDefault().execute(ioName, new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                try {
                    try {
                        io.getOut().reset();
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    Project prj = FileOwnerQuery.getOwner(file);
                    ActionProvider prjAP = prj != null ? prj.getLookup().lookup(ActionProvider.class) : null;
                    if (prjAP != null) {
                        Lookup targetContext = Lookup.EMPTY;
                        Set<String> supported = new HashSet<>(Arrays.asList(prjAP.getSupportedActions()));
                        String toRun = null;
                        
                        for (String command : new String[] {idealBuildTarget(file), ActionProvider.COMMAND_BUILD}) {
                            if (supported.contains(command) && prjAP.isActionEnabled(command, targetContext)) {
                                toRun = command;
                                break;
                            }
                        }

                        if (toRun != null) {
                            final CountDownLatch wait = new CountDownLatch(1);
                            final boolean[] state = new boolean[1];
                            targetContext = Lookups.singleton(new ActionProgress() {
                                @Override
                                protected void started() {
                                    state[0] = true;
                                }
                                @Override
                                public void finished(boolean success) {
                                    state[0] = success;
                                    wait.countDown();
                                }
                            });
                            prjAP.invokeAction(toRun, targetContext);

                            if (!state[0]) {
                                io.getErr().println("Cannot build project!");
                                return ;
                            }

                            try {
                                wait.await();
                            } catch (InterruptedException ex) {
                                Exceptions.printStackTrace(ex);
                                return ;
                            }

                            if (!state[0]) {
                                io.getErr().println("Cannot build project!");
                                return ;
                            }

                            io.select();
                        }
                    }
                    ClassPath testSourcePath = ClassPath.getClassPath(file, ClassPath.SOURCE);
                    ClassPath extraSourcePath = allSources(file);
                    final ClassPath fullSourcePath = ClassPathSupport.createProxyClassPath(testSourcePath, extraSourcePath);
                    Main.callBack = new ActionCallBack() {
                        @Override
                        public void actionStarted(Action action) {
                        }
                        @Override
                        public Collection<? extends String> getAdditionalVMJavaOptions(Action action) {
                            if (!debug) return Collections.emptyList();

                            JPDAStart s = new JPDAStart(io, COMMAND_DEBUG_SINGLE); //XXX command
                            s.setAdditionalSourcePath(fullSourcePath);
                            try {
                                Project prj = FileOwnerQuery.getOwner(file);
                                String connectTo = s.execute(prj);
                                return Arrays.asList("-Xdebug", "-Xrunjdwp:transport=dt_socket,address=localhost:" + connectTo);
                            } catch (Throwable ex) {
                                Exceptions.printStackTrace(ex);
                                return Arrays.asList();
                            }
                        }
                        @Override
                        public void actionEnded(Action action) {
                        }
                    };
                    List<String> options = new ArrayList<>();
                    options.add("-timeout:10");
                    options.add("-jdk:" + findTargetJavaHome(file).getAbsolutePath());
                    options.add("-retain:all");
                    options.add("-ignore:quiet");
                    options.add("-verbose:summary,nopass");
                    options.add("-w");
                    options.add(jtregWork.getAbsolutePath());
                    options.add("-r");
                    options.add(jtregReport.getAbsolutePath());
                    options.add("-xml:verify");
                    options.add("-javacoptions:-g");
                    if (hasXPatch(file)) {
                        if (!fullBuild(file)) {
                            File buildClasses = builtClassesDirsForXOverride(file);
                            File[] modules = buildClasses != null ? buildClasses.listFiles() : null;
                            if (modules != null) {
                                for (File module : modules) {
                                    options.add("-Xpatch:" + module.getName() + "=" + module.getAbsolutePath());
                                }
                            }
                        }
                    } else {
                        options.add("-Xbootclasspath/p:" + builtClassesDirsForBootClassPath(file));
                    }
                    options.add(FileUtil.toFile(file).getAbsolutePath());
                    try {
                        PrintWriter outW = new PrintWriter(io.getOut());
                        PrintWriter errW = new PrintWriter(io.getErr());
                        new Main(outW, errW).run(options.toArray(new String[options.size()]));
                        outW.flush();
                        errW.flush();
                        success = true;
                        printJTR(io, jtregWork, fullSourcePath, file);
                    } catch (BadArgs | Fault | Harness.Fault | InterruptedException ex) {
                        ex.printStackTrace(io.getErr());
                    }
                } finally {
                    io.getOut().close();
                    io.getErr().close();
                    try {
                        io.getIn().close();
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    progress.finished(success);
                }
            }
        }, io);
    }

    static ClassPath allSources(FileObject file) {
        Project prj = FileOwnerQuery.getOwner(file);
        FileObject jdkRoot;
        List<String> sourceDirPaths;

        if (prj.getProjectDirectory().getFileObject("../../../modules.xml") != null ||
            prj.getProjectDirectory().getFileObject("share/classes/module-info.java") != null) {
            jdkRoot = prj.getProjectDirectory().getFileObject("../../..");
            sourceDirPaths = Arrays.asList("*", "src", "*", "*", "classes");
        } else {
            jdkRoot = prj.getProjectDirectory().getFileObject("../../..");
            sourceDirPaths = Arrays.asList("src", "*", "*", "classes");
        }

        //find: */src/*/*/classes
        List<FileObject> roots = new ArrayList<>();

        listAllRoots(jdkRoot, new LinkedList<>(sourceDirPaths), roots);

        return ClassPathSupport.createClassPath(roots.toArray(new FileObject[roots.size()]));
    }

    private static void listAllRoots(FileObject currentDir, List<String> remainders, List<FileObject> roots) {
        if (remainders.isEmpty() && currentDir.isFolder()) {
            roots.add(currentDir);
            return ;
        }

        String current = remainders.remove(0);

        if ("*".equals(current)) {
            for (FileObject c : currentDir.getChildren()) {
                listAllRoots(c, remainders, roots);
            }
        } else {
            FileObject child = currentDir.getFileObject(current);

            if (child != null) {
                listAllRoots(child, remainders, roots);
            }
        }

        remainders.add(0, current);
    }

    private static File getBuildTargetDir(FileObject testFile) {
        Project prj = FileOwnerQuery.getOwner(testFile);
        FileObject possibleJDKRoot = prj.getProjectDirectory().getFileObject("../../..");

        Object buildAttr = possibleJDKRoot.getAttribute(NB_JDK_PROJECT_BUILD);

        if (buildAttr instanceof File) {
            return (File) buildAttr;
        }

        return null;
    }

    private static boolean hasXPatch(FileObject testFile) {
        Project prj = FileOwnerQuery.getOwner(testFile);
        FileObject possibleJDKRoot = prj.getProjectDirectory().getFileObject("../../..");

        return possibleJDKRoot.getFileObject("jdk/src/java.base/share/classes/module-info.java") != null;
    }

    static File findTargetJavaHome(FileObject testFile) {
        File buildDir = getBuildTargetDir(testFile);

        if (buildDir != null) {
            File candidate = new File(buildDir, "images/j2sdk-image");

            if (candidate.isDirectory()) {
                return candidate;
            } else {
                return new File(buildDir, "jdk");
           }
        }

        Project prj = FileOwnerQuery.getOwner(testFile);
        File projectDirFile = FileUtil.toFile(prj.getProjectDirectory());
        File userHome = new File(System.getProperty("user.home"));
        List<PropertyProvider> properties = new ArrayList<>();

        properties.add(PropertyUtils.propertiesFilePropertyProvider(new File(projectDirFile, "build.properties")));
        properties.add(PropertyUtils.propertiesFilePropertyProvider(new File(userHome, ".openjdk/langtools-build.properties")));
        properties.add(PropertyUtils.propertiesFilePropertyProvider(new File(userHome, ".openjdk/build.properties")));
        properties.add(PropertyUtils.propertiesFilePropertyProvider(new File(projectDirFile, "make/build.properties")));

        PropertyEvaluator evaluator = PropertyUtils.sequentialPropertyEvaluator(PropertyUtils.globalPropertyProvider(), properties.toArray(new PropertyProvider[0]));

        return new File(evaluator.evaluate("${target.java.home}"));
    }

    static String builtClassesDirsForBootClassPath(FileObject testFile) {
        File buildDir = getBuildTargetDir(testFile);
        Project prj = FileOwnerQuery.getOwner(testFile);
        List<FileObject> roots = new ArrayList<>();

        if (buildDir != null) {
            FileObject repo = prj.getProjectDirectory().getParent().getParent();
            if (repo.getNameExt().equals("langtools") &&
                ShortcutUtils.getDefault().shouldUseCustomTest(repo.getNameExt(), FileUtil.getRelativePath(repo, testFile))) {
                listAllRoots(prj.getProjectDirectory().getFileObject("../.."), new LinkedList<>(Arrays.asList("build", "classes")), roots);
                listAllRoots(prj.getProjectDirectory().getFileObject("../.."), new LinkedList<>(Arrays.asList("build", "*", "classes")), roots);
            } else {
                listAllRoots(FileUtil.toFileObject(buildDir), new LinkedList<>(Arrays.asList("jdk", "modules", "*")), roots);
            }
        } else {
            listAllRoots(prj.getProjectDirectory().getFileObject("../../.."), new LinkedList<>(Arrays.asList("build", "classes")), roots);
            listAllRoots(prj.getProjectDirectory().getFileObject("../../.."), new LinkedList<>(Arrays.asList("build", "*", "classes")), roots);
        }

        StringBuilder built = new StringBuilder();
        String sep = "";

        for (FileObject fo : roots) {
            built.append(sep);
            built.append(FileUtil.toFile(fo).getAbsoluteFile());

            sep = File.pathSeparator;
        }

        return built.toString();
    }

    static boolean fullBuild(FileObject testFile) {
        File buildDir = getBuildTargetDir(testFile);
        Project prj = FileOwnerQuery.getOwner(testFile);

        if (buildDir != null) {
            FileObject repo = prj.getProjectDirectory().getParent().getParent();
            return !(repo.getNameExt().equals("langtools") &&
                    ShortcutUtils.getDefault().shouldUseCustomTest(repo.getNameExt(), FileUtil.getRelativePath(repo, testFile)));
        }

        return false;
    }

    private static File builtClassesDirsForXOverride(FileObject testFile) {
        Project prj = FileOwnerQuery.getOwner(testFile);
        FileObject buildClasses;

        FileObject repo = prj.getProjectDirectory().getParent().getParent();
        if (repo.getNameExt().equals("langtools") &&
            ShortcutUtils.getDefault().shouldUseCustomTest(repo.getNameExt(), FileUtil.getRelativePath(repo, testFile))) {
            buildClasses = prj.getProjectDirectory().getFileObject("../../build/modules");
            if (buildClasses == null) {
                //old style:
                buildClasses = prj.getProjectDirectory().getFileObject("../../build/classes");
            }
        } else {
            File buildDir = getBuildTargetDir(testFile);
            FileObject buildDirFO = FileUtil.toFileObject(buildDir);
            buildClasses = buildDirFO != null ? buildDirFO.getFileObject("jdk/modules") : null;
        }

        return buildClasses != null ? FileUtil.toFile(buildClasses).getAbsoluteFile() : null;
    }

    static File jtregOutputDir(FileObject testFile) {
        File buildDir = getBuildTargetDir(testFile);
        Project prj = FileOwnerQuery.getOwner(testFile);

        if (buildDir != null) {
            FileObject repo = prj.getProjectDirectory().getParent().getParent();
            if (repo.getNameExt().equals("langtools") &&
                ShortcutUtils.getDefault().shouldUseCustomTest(repo.getNameExt(), FileUtil.getRelativePath(repo, testFile))) {
                buildDir = new File(FileUtil.toFile(prj.getProjectDirectory()), "../../build");
            }
        } else {
            buildDir = new File(FileUtil.toFile(prj.getProjectDirectory()), "../../../build");
        }

        return new File(buildDir, "nb-jtreg").toPath().normalize().toFile();
    }

    static void printJTR(InputOutput io, File jtregWork, ClassPath fullSourcePath, FileObject testFile) {
        try {
            FileObject testRoot = testFile;
            while (testRoot != null && testRoot.getFileObject("TEST.ROOT") == null)
                testRoot = testRoot.getParent();
            if (testRoot != null) {
                String relPath = FileUtil.getRelativePath(testRoot, testFile);
                relPath = relPath.replaceAll(".java$", ".jtr");
                File jtr = new File(jtregWork, relPath);
                if (jtr.canRead()) {
                    FileUtil.refreshFor(jtr);
                    for (String line : FileUtil.toFileObject(jtr).asLines()) {
                        final StackTraceLine stl = matches(line);
                        if (stl != null) {
                            final FileObject source = fullSourcePath.findResource(stl.expectedFileName);
                            if (source != null) {
                                io.getOut().println(line, new OutputListener() {
                                    @Override
                                    public void outputLineSelected(OutputEvent ev) {}
                                    @Override
                                    public void outputLineAction(OutputEvent ev) {
                                        Mutex.EVENT.readAccess(new Runnable() {
                                            @Override public void run() {
                                                open(source, stl.lineNumber - 1);
                                            }
                                        });
                                    }
                                    @Override
                                    public void outputLineCleared(OutputEvent ev) {}
                                });
                            }
                        } else {
                            io.getOut().println(line);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    static String idealBuildTarget(FileObject testFile) {
        Project prj = FileOwnerQuery.getOwner(testFile);
        FileObject repo = prj.getProjectDirectory().getParent().getParent();
        if (ShortcutUtils.getDefault().shouldUseCustomTest(repo.getNameExt(), FileUtil.getRelativePath(repo, testFile))) {
            return COMMAND_BUILD_FAST;
        } else {
            return COMMAND_BUILD_GENERIC_FAST;
        }
    }

    private static void open(FileObject file, int line) {
        LineCookie lc = file.getLookup().lookup(LineCookie.class);

        if (lc != null) {
            Line.Set ls = lc.getLineSet();
            try {
                Line originalLine = ls.getOriginal(line);
                originalLine.show(ShowOpenType.OPEN, ShowVisibilityType.FOCUS);
            } catch (IndexOutOfBoundsException ex) {
                Logger.getLogger(ActionProviderImpl.class.getName()).log(Level.FINE, null, ex);
            }
        }

        OpenCookie oc = file.getLookup().lookup(OpenCookie.class);

        if (oc != null) {
            oc.open();
        }
    }

    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile("\\s*at\\s*(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*(\\.<init>|\\.<clinit>)?)\\s*\\([^:)]*(:([0-9]+))?\\)");

    static StackTraceLine matches(String line) {
        Matcher m = STACK_TRACE_PATTERN.matcher(line);
        if (m.matches()) {
            String className = m.group(1);
            className = className.substring(0, className.lastIndexOf('.'));
            int dollar = className.lastIndexOf('$');
            if (dollar != (-1))
                className = className.substring(0, dollar);
            className = className.replace('.', '/') + ".java";
            String lineNumber = m.group(5);
            return new StackTraceLine(className, lineNumber != null ? Integer.parseInt(lineNumber) : -1);
        } else {
            return null;
        }
    }

    static final class StackTraceLine {
        public final String expectedFileName;
        public final int lineNumber;
        public StackTraceLine(String expectedFileName, int lineNumber) {
            this.expectedFileName = expectedFileName;
            this.lineNumber = lineNumber;
        }
    }

    @Override
    public boolean isActionEnabled(String command, Lookup context) throws IllegalArgumentException {
        FileObject file = context.lookup(FileObject.class);

        if (file == null)
            return false;
        
        while (!file.isRoot()) {
            if (Utilities.isJDKRepository(file))
                return true;
            file = file.getParent();
        }

        return false;
    }

}
