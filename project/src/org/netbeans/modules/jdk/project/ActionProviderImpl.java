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
package org.netbeans.modules.jdk.project;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.tools.ant.module.api.support.ActionUtils;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.spi.project.ActionProgress;
import org.netbeans.spi.project.ActionProvider;
import org.openide.execution.ExecutorTask;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.Pair;
import org.openide.util.Task;
import org.openide.util.TaskListener;

/**
 *
 * @author lahvac
 */
public class ActionProviderImpl implements ActionProvider {

    private static final String COMMAND_BUILD_FAST = "build-fast";
    private static final Map<Pair<String, RootKind>, String[]> command2Targets = new HashMap<Pair<String, RootKind>, String[]>() {{
        put(Pair.of(COMMAND_BUILD, RootKind.SOURCE), new String[] {"build"});
        put(Pair.of(COMMAND_BUILD, RootKind.TEST), new String[] {"build"});
        put(Pair.of(COMMAND_BUILD_FAST, RootKind.SOURCE), new String[] {"build-fast"});
        put(Pair.of(COMMAND_BUILD_FAST, RootKind.TEST), new String[] {"build-fast"});
        put(Pair.of(COMMAND_CLEAN, RootKind.SOURCE), new String[] {"clean"});
        put(Pair.of(COMMAND_CLEAN, RootKind.TEST), new String[] {"clean"});
        put(Pair.of(COMMAND_REBUILD, RootKind.SOURCE), new String[] {"clean", "build"});
        put(Pair.of(COMMAND_REBUILD, RootKind.TEST), new String[] {"clean", "build"});
        put(Pair.of(COMMAND_COMPILE_SINGLE, RootKind.SOURCE), new String[] {"compile-single"});
        put(Pair.of(COMMAND_COMPILE_SINGLE, RootKind.TEST), new String[] {"compile-single"});
//    COMMAND_RUN;
        put(Pair.of(COMMAND_RUN_SINGLE, RootKind.SOURCE), new String[] {"run-single"});
        put(Pair.of(COMMAND_RUN_SINGLE, RootKind.TEST), new String[] {"jtreg"});
//    COMMAND_TEST;
//    COMMAND_TEST_SINGLE;
//    COMMAND_DEBUG;
        put(Pair.of(COMMAND_DEBUG_SINGLE, RootKind.SOURCE), new String[] {"debug-single"});
        put(Pair.of(COMMAND_DEBUG_SINGLE, RootKind.TEST), new String[] {"debug-jtreg"});
//    COMMAND_DEBUG_TEST_SINGLE;
//    COMMAND_DEBUG_STEP_INTO;
    }};

    private static final Map<Pair<String, RootKind>, RunSingleConfig> command2Properties = new HashMap<Pair<String, RootKind>, RunSingleConfig>() {{
        put(Pair.of(COMMAND_COMPILE_SINGLE, RootKind.SOURCE), new RunSingleConfig("includes", RunSingleConfig.Type.RELATIVE, ","));
        put(Pair.of(COMMAND_COMPILE_SINGLE, RootKind.TEST), new RunSingleConfig("includes", RunSingleConfig.Type.RELATIVE, ","));
        put(Pair.of(COMMAND_RUN_SINGLE, RootKind.SOURCE), new RunSingleConfig("run.classname", RunSingleConfig.Type.CLASSNAME, null));
        put(Pair.of(COMMAND_RUN_SINGLE, RootKind.TEST), new RunSingleConfig("jtreg.tests", RunSingleConfig.Type.RELATIVE, " "));
        put(Pair.of(COMMAND_DEBUG_SINGLE, RootKind.SOURCE), new RunSingleConfig("debug.classname", RunSingleConfig.Type.CLASSNAME, null));
        put(Pair.of(COMMAND_DEBUG_SINGLE, RootKind.TEST), new RunSingleConfig("jtreg.tests", RunSingleConfig.Type.RELATIVE, " "));
    }};

    private final JDKProject project;
    private final FileObject repository;
    private final FileObject script;
    private final String[] supportedActions;

    public ActionProviderImpl(JDKProject project) {
        this.project = project;

        FileObject repo = project.currentModule != null ? project.getProjectDirectory().getParent().getParent()
                                                              : project.getProjectDirectory().getParent();
        File scriptFile = InstalledFileLocator.getDefault().locate("scripts/build-" + repo.getNameExt() + ".xml", "org.netbeans.modules.jdk.project", false);
        if (scriptFile == null) {
            scriptFile = InstalledFileLocator.getDefault().locate("scripts/build-generic.xml", "org.netbeans.modules.jdk.project", false);
            repo = repo.getParent();
        }
        repository = repo;
        script = FileUtil.toFileObject(scriptFile);

        String[] supported = new String[0];

        try {
            for (String l : script.asLines("UTF-8")) {
                if (l.contains("SUPPORTED_ACTIONS:")) {
                    String[] actions = l.substring(l.indexOf(':') + 1).trim().split(",");
                    Set<String> filteredActions = new HashSet<>();

                    for (Pair<String, RootKind> k : command2Targets.keySet()) {
                        filteredActions.add(k.first());
                    }

                    filteredActions.retainAll(Arrays.asList(actions));
                    supported = filteredActions.toArray(new String[0]);
                    break;
                }
            }
        } catch (IOException ex) {
            //???
            Exceptions.printStackTrace(ex);
        }
        
        supportedActions = supported;
    }

    @Override
    public String[] getSupportedActions() {
        return supportedActions;
    }

    @Override
    public void invokeAction(String command, Lookup context) throws IllegalArgumentException {
        Properties props = new Properties();
        props.put("basedir", FileUtil.toFile(repository).getAbsolutePath());
        props.put("CONF", project.configurations.getActiveConfiguration().getLocation().getName());
        RootKind kind = getKind(context);
        RunSingleConfig singleFileProperty = command2Properties.get(Pair.of(command, kind));
        if (singleFileProperty != null) {
            String srcdir = "";
            StringBuilder value = new StringBuilder();
            String sep = "";
            for (FileObject file : context.lookupAll(FileObject.class)) {
                value.append(sep);
                ClassPath sourceCP;
                switch (kind) {
                    case SOURCE:
                        sourceCP = ClassPath.getClassPath(file, ClassPath.SOURCE);
                        break;
                    case TEST:
                        sourceCP = ClassPathSupport.createClassPath(project.getProjectDirectory().getFileObject("../../test"));
                        break;
                    default:
                        throw new IllegalStateException(kind.name());
                }
                value.append(singleFileProperty.valueType.convert(sourceCP, file));
                sep = singleFileProperty.separator;
                srcdir = FileUtil.getRelativePath(project.getProjectDirectory().getFileObject("../.."), sourceCP.findOwnerRoot(file));
            }
            props.put(singleFileProperty.propertyName, value.toString());
            props.put("srcdir", srcdir);
        }
        final ActionProgress progress = ActionProgress.start(context);
        try {
            ActionUtils.runTarget(script, command2Targets.get(Pair.of(command, kind)), props)
                       .addTaskListener(new TaskListener() {
                @Override
                public void taskFinished(Task task) {
                    progress.finished(((ExecutorTask) task).result() == 0);
                }
            });
        } catch (IOException ex) {
            //???
            Exceptions.printStackTrace(ex);
            progress.finished(false);
        }
    }

    @Override
    public boolean isActionEnabled(String command, Lookup context) throws IllegalArgumentException {
        RootKind kind = getKind(context);
        RunSingleConfig singleFileProperty = command2Properties.get(Pair.of(command, kind));
        if (singleFileProperty != null) {
            int fileCount = context.lookupAll(FileObject.class).size();

            if (fileCount == 0 || (fileCount > 1 && singleFileProperty.separator == null))
                return false;
        }

        return true;
    }

    private RootKind getKind(Lookup context) {
        FileObject aFile = context.lookup(FileObject.class);
        FileObject testDir = project.getProjectDirectory().getFileObject("../../test");
        return aFile != null && testDir != null && FileUtil.isParentOf(testDir, aFile) ? RootKind.TEST : RootKind.SOURCE;
    }

    private static final class RunSingleConfig {
        public final String propertyName;
        public final Type valueType;
        public final String separator;

        public RunSingleConfig(String propertyName, Type valueType, String separator) {
            this.propertyName = propertyName;
            this.valueType = valueType;
            this.separator = separator;
        }

        enum Type {
            RELATIVE {
                @Override
                public String convert(ClassPath sourceCP, FileObject file) {
                    return sourceCP.getResourceName(file);
                }
            },
            CLASSNAME {
                @Override
                public String convert(ClassPath sourceCP, FileObject file) {
                    return sourceCP.getResourceName(file, '.', false);
                }
            };
            public abstract String convert(ClassPath sourceCP, FileObject file);
        }

    }

    private enum RootKind {
        SOURCE,
        TEST;
    }

}
