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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import javax.swing.text.BadLocationException;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.project.ActionProgress;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.netbeans.spi.project.support.ant.PropertyProvider;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.openide.execution.ExecutionEngine;
import org.openide.execution.ExecutorTask;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=ActionProvider.class)
public class ActionProviderImpl implements ActionProvider {

    static final String NB_JDK_PROJECT_BUILD = "nb-jdk-project-build";
    
    private static final String[] ACTIONS = new String[] {
        COMMAND_DEBUG_TEST_SINGLE,
    };

    @Override
    public String[] getSupportedActions() {
        return ACTIONS;
    }

    @Override
    public void invokeAction(String command, Lookup context) throws IllegalArgumentException {
        try {
            createAndRunTestUnderDebugger(context);
        } catch (BadLocationException | IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    //public for test
    @Messages({"# {0} - simple file name", "DN_Debugging=Debugging ({0})"})
    public static ExecutorTask createAndRunTestUnderDebugger(Lookup context) throws BadLocationException, IOException {
        final FileObject file = context.lookup(FileObject.class);
        String ioName = Bundle.DN_Debugging(file.getName());
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
                    Main.callBack = new ActionCallBack() {
                        @Override
                        public void actionStarted(Action action) {
                        }
                        @Override
                        public Collection<? extends String> getAdditionalVMJavaOptions(Action action) {
                            JPDAStart s = new JPDAStart(io, COMMAND_DEBUG_SINGLE); //XXX command
                            ClassPath testSourcePath = ClassPath.getClassPath(file, ClassPath.SOURCE);
                            ClassPath extraSourcePath = allSources(file);
                            s.setAdditionalSourcePath(ClassPathSupport.createProxyClassPath(testSourcePath, extraSourcePath));
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
                    options.add("-Xbootclasspath/p:" + builtClassesDirs(file));
                    options.add(FileUtil.toFile(file).getAbsolutePath());
                    try {
                        new Main().run(options.toArray(new String[options.size()]));
                        success = true;
                    } catch (BadArgs | Fault | Harness.Fault | InterruptedException ex) {
                        ex.printStackTrace(io.getErr());
                    }
                    io.getOut().close();
                    io.getErr().close();
                    try {
                        io.getIn().close();
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                } finally {
                    progress.finished(success);
                }
            }
        }, io);
    }

    static ClassPath allSources(FileObject file) {
        Project prj = FileOwnerQuery.getOwner(file);
        FileObject jdkRoot;
        List<String> sourceDirPaths;

        if (prj.getProjectDirectory().getFileObject("../../../modules.xml") != null) {
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

    static File findTargetJavaHome(FileObject testFile) {
        File buildDir = getBuildTargetDir(testFile);

        if (buildDir != null) {
            File candidate = new File(buildDir, "images/j2sdk-image");

            if (candidate.isDirectory()) {
                return candidate;
            } else {
                return new File(buildDir, "images/jdk");
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

    static String builtClassesDirs(FileObject testFile) {
        File buildDir = getBuildTargetDir(testFile);
        Project prj = FileOwnerQuery.getOwner(testFile);
        List<FileObject> roots = new ArrayList<>();

        if (buildDir != null) {
            if (prj.getProjectDirectory().getParent().getParent().getNameExt().equals("langtools")) {
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

    static File jtregOutputDir(FileObject testFile) {
        File buildDir = getBuildTargetDir(testFile);
        Project prj = FileOwnerQuery.getOwner(testFile);

        if (buildDir != null) {
            if (prj.getProjectDirectory().getParent().getParent().getNameExt().equals("langtools")) {
                buildDir = new File(FileUtil.toFile(prj.getProjectDirectory()), "../../build");
            }
        } else {
            buildDir = new File(FileUtil.toFile(prj.getProjectDirectory()), "../../../build");
        }

        return new File(buildDir, "nb-jtreg").toPath().normalize().toFile();
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
