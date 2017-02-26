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
import com.sun.javatest.regtest.ActionCallBack;
import com.sun.javatest.regtest.BadArgs;
import com.sun.javatest.regtest.Main.Fault;
import com.sun.javatest.regtest.config.JDK;
import com.sun.javatest.regtest.exec.Action;
import com.sun.javatest.regtest.tool.Tool;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;

import com.sun.tdk.jcov.Grabber;
import com.sun.tdk.jcov.Instr;

import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.editor.coverage.spi.CoverageProvider;
import org.netbeans.modules.jdk.project.common.api.BuildUtils;
import org.netbeans.modules.jdk.project.common.api.ShortcutUtils;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.project.ActionProgress;
import org.netbeans.spi.project.ActionProvider;
import org.openide.cookies.LineCookie;
import org.openide.cookies.OpenCookie;
import org.openide.execution.ExecutionEngine;
import org.openide.execution.ExecutorTask;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.text.Line;
import org.openide.text.Line.ShowOpenType;
import org.openide.text.Line.ShowVisibilityType;
import org.openide.util.EditableProperties;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
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
        StopAction newStop = new StopAction();
        ReRunAction newReRun = new ReRunAction(false);
        ReRunAction newReDebug = new ReRunAction(true);
        final InputOutput io = IOProvider.getDefault().getIO(ioName, false, new javax.swing.Action[] {newReRun, newReDebug, newStop}, null);
        final StopAction stop = StopAction.record(io, newStop);
        final ReRunAction rerun = ReRunAction.recordRun(io, newReRun);
        final ReRunAction redebug = ReRunAction.recordDebug(io, newReDebug);
        rerun.setFile(file);
        redebug.setFile(file);
        final File jtregOutput = Utilities.jtregOutputDir(file);
        final File jtregWork = new File(jtregOutput, "work");
        final File jtregReport = new File(jtregOutput, "report");
        final ActionProgress progress = ActionProgress.start(context);
        return ExecutionEngine.getDefault().execute(ioName, new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                File jcovTempData = null;
                File jcovData = null;
                try {
                    try {
                        io.getOut().reset();
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    rerun.disable();
                    redebug.disable();
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
                    List<String> options = new ArrayList<>();
                    options.add("-timeout:10");
                    File targetJavaHome = BuildUtils.findTargetJavaHome(file);
                    options.add("-jdk:" + targetJavaHome.getAbsolutePath());
                    options.add("-retain:all");
                    options.add("-ignore:quiet");
                    options.add("-verbose:summary,nopass");
                    options.add("-w");
                    options.add(jtregWork.getAbsolutePath());
                    options.add("-r");
                    options.add(jtregReport.getAbsolutePath());
                    options.add("-xml:verify");
                    options.add("-javacoptions:-g");
                    final List<String> extraVMOptions = new ArrayList<>();
                    Set<File> toRefresh = new HashSet<>();
                    if (hasXPatch(targetJavaHome)) {
                        boolean jcov = CoverageProvider.isCoverageEnabled();
                        if (jcov) {
                            io.getOut().print("Instrumenting classfiles...");
                            File jcovDir = new File(jtregOutput, "jcov");
                            File buildDir = BuildUtils.getBuildTargetDir(file);

                            if (buildDir == null) {
                                io.getErr().println("Cannot run test under jcov - build dir missing!");
                                return ;
                            }

                            List<File> buildDirs = new ArrayList<>();

                            if (!fullBuild(file)) {
                                buildDirs.add(builtClassesDirsForXOverride(file));
                            }

                            buildDirs.add(new File(new File(buildDir, "jdk"), "modules"));

                            Map<String, File> project2Classes = new HashMap<>();

                            for (File bd : buildDirs) {
                                File[] modules = bd != null ? bd.listFiles() : null;
                                if (modules != null) {
                                    for (File module : modules) {
                                        String moduleName = module.getName();

                                        if (!project2Classes.containsKey(moduleName)) {
                                            project2Classes.put(moduleName, module);
                                        }
                                    }
                                }
                            }

                            File xPatchClasses = new File(jcovDir, "instrumented-classes");
                            File template = new File(jcovDir, "template.xml");
                            jcovData = new File(jcovDir, "jcov");
                            try {
                                jcovDir.mkdirs();
                                jcovTempData = File.createTempFile("jcov", "temp", jcovDir);
                            } catch (IOException ex) {
                                Exceptions.printStackTrace(ex);
                                return ;
                            }

                            toRefresh.add(jcovData);

                            instrument(buildDirs.get(0), xPatchClasses, template, project2Classes.values());

                            io.getOut().println(" done.");

                            File fileSaver = InstalledFileLocator.getDefault().locate("modules/ext/jcov_file_saver.jar", "org.netbeans.modules.jdk.jtreg.lib", false);
                            extraVMOptions.addAll(Arrays.asList(
                                "--patch-module=java.base=" + fileSaver.getAbsolutePath(),
                                "-Djcov.target.file=" + jcovTempData.getAbsolutePath()));
                            for (File module : xPatchClasses.listFiles()) {
                                extraVMOptions.add("--add-exports=java.base/com.sun.tdk.jcov.runtime=" + module.getName());
                            }

                            genXPatchForDir(file, xPatchClasses, extraVMOptions);
                        } else if (!fullBuild(file)) {
                            File buildClasses = builtClassesDirsForXOverride(file);
                            
                            genXPatchForDir(file, buildClasses, options);
                        }
                    } else {
                        options.add("-Xbootclasspath/p:" + builtClassesDirsForBootClassPath(file));
                    }
                    options.add(FileUtil.toFile(file).getAbsolutePath());
                    try {
                        stop.started();
                        JDK.clearCache();
                        PrintWriter outW = new PrintWriter(io.getOut());
                        PrintWriter errW = new PrintWriter(io.getErr());
                        Tool.callBack = new ActionCallBack() {
                            @Override
                            public void actionStarted(Action action) {
                            }
                            @Override
                            public List<String> getAdditionalVMJavaOptions(Action action) {
                                if (!debug) return extraVMOptions;

                                List<String> options = new ArrayList<>();

                                options.addAll(extraVMOptions);

                                JPDAStart s = new JPDAStart(io, COMMAND_DEBUG_SINGLE); //XXX command
                                s.setAdditionalSourcePath(fullSourcePath);
                                try {
                                    Project prj = FileOwnerQuery.getOwner(file);
                                    String connectTo = s.execute(prj);
                                    options.addAll(Arrays.asList("-Xdebug", "-Xrunjdwp:transport=dt_socket,address=localhost:" + connectTo));
                                } catch (Throwable ex) {
                                    Exceptions.printStackTrace(ex);
                                }

                                return options;
                            }
                            @Override
                            public void actionEnded(Action action) {
                            }
                        };
                        new Tool(outW, errW).run(options.toArray(new String[options.size()]));
                        outW.flush();
                        errW.flush();
                        success = true;
                        printJTR(io, jtregWork, fullSourcePath, file);

                        for (File refresh : toRefresh) {
                            FileUtil.refreshFor(refresh);
                        }
                    } catch (BadArgs | Fault | Harness.Fault | InterruptedException ex) {
                        ex.printStackTrace(io.getErr());
                    } finally {
                        stop.finished();
                    }
                } finally {
                    if (jcovTempData != null) {
                        jcovTempData.renameTo(jcovData);
                    }
                    io.getOut().close();
                    io.getErr().close();
                    try {
                        io.getIn().close();
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                    progress.finished(success);
                    rerun.enable();
                    redebug.enable();
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
            File buildTarget = BuildUtils.getBuildTargetDir(file);
            jdkRoot = buildTarget != null ? FileUtil.toFileObject(buildTarget.getParentFile().getParentFile()) : null;
            if (jdkRoot == null) {
                //should not happen, just last resort:
                jdkRoot = prj.getProjectDirectory().getFileObject("../../..");
            }
            if (jdkRoot.getFileObject("src/java.base/share/classes/module-info.java") != null) {
                sourceDirPaths = Arrays.asList("src", "*", "*", "classes");
            } else {
                sourceDirPaths = Arrays.asList("*", "src", "*", "*", "classes");
            }
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

    private static boolean hasXPatch(File targetJavaHome) {
        return new File(targetJavaHome, "conf").isDirectory();
    }

    private static boolean newStyleXPatch(FileObject testFile) {
        Project prj = FileOwnerQuery.getOwner(testFile);
        FileObject testRoot = prj.getProjectDirectory().getFileObject("../../test/TEST.ROOT");

        if (testRoot == null)
            return false;

        try (InputStream in = testRoot.getInputStream()) {
            EditableProperties ep = new EditableProperties(true);
            ep.load(in);
            return "true".equals(ep.get("useNewOptions"));
        } catch (IOException ex) {
            Logger.getLogger(ActionProviderImpl.class.getName()).log(Level.FINE, null, ex);
            return false;
        }
    }

    static String builtClassesDirsForBootClassPath(FileObject testFile) {
        File buildDir = BuildUtils.getBuildTargetDir(testFile);
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
        File buildDir = BuildUtils.getBuildTargetDir(testFile);
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
            File buildDir = BuildUtils.getBuildTargetDir(testFile);
            FileObject buildDirFO = FileUtil.toFileObject(buildDir);
            buildClasses = buildDirFO != null ? buildDirFO.getFileObject("jdk/modules") : null;
        }

        return buildClasses != null ? FileUtil.toFile(buildClasses).getAbsoluteFile() : null;
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

    private static void genXPatchForDir(FileObject testFile, File dir, List<String> options) {
        boolean newStyleXPatch = newStyleXPatch(testFile);
        File[] modules = dir != null ? dir.listFiles() : null;
        if (modules != null) {
            for (File module : modules) {
                if (newStyleXPatch) {
                    options.add("--patch-module");
                    options.add(module.getName() + "=" + module.getAbsolutePath());
                } else {
                    options.add("-Xpatch:" + module.getName() + "=" + module.getAbsolutePath());
                }
            }
        }
    }

    //XXX: cleaning-up template xml for deleted classes!
    private static void instrument(File originalDir, File instrumentedDir, File template, final Iterable<File> classpath) {
        File[] children = originalDir.listFiles();

        if (children == null) return ; //TODO: log?

        for (File c : children) {
            Instr instr = new Instr();

            instr.setClassLookup(name -> {
                for (File e : classpath) {
                    File f = new File(e, name.replace("/", File.separator) + ".class");

                    if (f.canRead()) {
                        try {
                            return new FileInputStream(f);
                        } catch (FileNotFoundException ex) {
                            //should not happen
                            throw new IllegalStateException(ex);
                        }
                    }
                }

                return null;
            });

            instr.run(new String[] {"-o", new File(instrumentedDir, c.getName()).getAbsolutePath(), c.getAbsolutePath(), "-template", template.getAbsolutePath()});
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

    private static final class StopAction extends AbstractAction {

        private static final long serialVersionUID = 1L;
        private static final Map<InputOutput, StopAction> actions = new WeakHashMap<>();

        public static StopAction record(InputOutput io, StopAction ifAbsent) {
            StopAction res = actions.get(io);

            if (res == null) {
                actions.put(io, res = ifAbsent);
            }

            return res;
        }

        private Thread executor;

        @Messages("DESC_Stop=Stop")
        public StopAction() {
            setEnabledEQ(false);
            putValue(SMALL_ICON, ImageUtilities.loadImageIcon("org/netbeans/modules/jdk/jtreg/resources/stop.png", true));
            putValue(SHORT_DESCRIPTION, Bundle.DESC_Stop());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            executor.interrupt();
            setEnabledEQ(false);
        }

        private void started() {
            executor = Thread.currentThread();
            setEnabledEQ(true);
        }

        private void finished() {
            executor = null;
            setEnabledEQ(false);
        }

        private void setEnabledEQ(final boolean enabled) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    setEnabled(enabled);
                }
            });
        }
    }

    private static final class ReRunAction extends AbstractAction {

        private static final long serialVersionUID = 1L;
        private static final Map<InputOutput, ReRunAction> runActions = new WeakHashMap<>();
        private static final Map<InputOutput, ReRunAction> debugActions = new WeakHashMap<>();

        public static ReRunAction recordRun(InputOutput io, ReRunAction ifAbsent) {
            return record(io, runActions, ifAbsent);
        }

        public static ReRunAction recordDebug(InputOutput io, ReRunAction ifAbsent) {
            return record(io, debugActions, ifAbsent);
        }

        private static ReRunAction record(InputOutput io, Map<InputOutput, ReRunAction> actions, ReRunAction ifAbsent) {
            ReRunAction res = actions.get(io);

            if (res == null) {
                actions.put(io, res = ifAbsent);
            }

            return res;
        }

        private FileObject file;
        private final boolean debug;

        @Messages({
            "DESC_ReRun=Run test again",
            "DESC_ReDebug=Run test again under debugger"
        })
        public ReRunAction(boolean debug) {
            setEnabledEQ(false);
            putValue(SMALL_ICON, ImageUtilities.loadImageIcon(debug ? "org/netbeans/modules/jdk/jtreg/resources/redebug.png" : "org/netbeans/modules/jdk/jtreg/resources/rerun.png", true));
            putValue(SHORT_DESCRIPTION, debug ? Bundle.DESC_ReDebug() : Bundle.DESC_ReRun());
            this.debug = debug;
        }

        public void setFile(FileObject file) {
            this.file = file;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                ActionProviderImpl.createAndRunTest(Lookups.singleton(file), debug);
            } catch (BadLocationException | IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        private void enable() {
            setEnabledEQ(true);
        }

        private void disable() {
            setEnabledEQ(false);
        }

        private void setEnabledEQ(final boolean enabled) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    setEnabled(enabled);
                }
            });
        }
    }

}
