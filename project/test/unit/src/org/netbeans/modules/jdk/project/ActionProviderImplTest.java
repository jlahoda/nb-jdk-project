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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import junit.framework.Test;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.netbeans.spi.project.ActionProgress;
import org.netbeans.spi.project.ActionProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author lahvac
 */
public class ActionProviderImplTest extends NbTestCase {

    public ActionProviderImplTest(String name) {
        super(name);
    }

    public static Test suite() {
        return NbModuleSuite.createConfiguration(ActionProviderImplTest.class)
                            .gui(false)
                            .failOnException(Level.WARNING)
                            .suite();
    }

    @Override
    protected void setUp() throws Exception {
        String antHomePath = System.getProperty("antHomePath"); //XXX: validate the antHomePath!!!!!!!!!
        Preferences antPrefs = NbPreferences.root().node("org/apache/tools/ant/module");
        antPrefs.put("antHome", antHomePath);
    }

    public void testModularLangtools() throws Exception {
        String modularizedJdk9Path = System.getProperty("modularizedJdk9Path");
        assertNotNull(modularizedJdk9Path);
        runLangtoolsTests(modularizedJdk9Path);
    }

    private void runLangtoolsTests(String repoPath) throws Exception {
        FileObject repoPathFile = FileUtil.toFileObject(FileUtil.normalizeFile(new File(repoPath)));
        assertNotNull(repoPathFile);
        doRunAction(repoPathFile,
                    "langtools/src/java.compiler",
                    ActionProvider.COMMAND_CLEAN);
        assertNull(repoPathFile.getFileObject("langtools/build/classes"));
        doRunAction(repoPathFile,
                    "langtools/src/java.compiler",
                    ActionProvider.COMMAND_BUILD);
        assertNotNull(repoPathFile.getFileObject("langtools/build/classes")); //TODO: more precise path
        //TODO: verify disabled:
//        doRunAction(repoPathFile,
//                    "langtools/src/java.compiler",
//                    ActionProvider.COMMAND_COMPILE_SINGLE,
//                    "langtools/test/tools/javac/4241573/T4241573.java");
        doRunAction(repoPathFile,
                    "langtools/src/java.compiler",
                    ActionProvider.COMMAND_RUN_SINGLE,
                    "langtools/test/tools/javac/4241573/T4241573.java");
        //TODO: debug does not actually work (but the below at least ensures the correct script is called):
        doRunAction(repoPathFile,
                    "langtools/src/java.compiler",
                    ActionProvider.COMMAND_DEBUG_SINGLE,
                    "langtools/test/tools/javac/4241573/T4241573.java");
        doRunAction(repoPathFile,
                    "langtools/src/jdk.compiler",
                    ActionProvider.COMMAND_COMPILE_SINGLE,
                    "langtools/src/jdk.compiler/share/classes/com/sun/tools/javac/Main.java");
        doRunAction(repoPathFile,
                    "langtools/src/jdk.compiler",
                    ActionProvider.COMMAND_RUN_SINGLE,
                    "langtools/src/jdk.compiler/share/classes/com/sun/tools/javac/Main.java");
        doRunAction(repoPathFile,
                    "langtools/src/jdk.compiler",
                    ActionProvider.COMMAND_DEBUG_SINGLE,
                    "langtools/src/jdk.compiler/share/classes/com/sun/tools/javac/Main.java");
    }

    public void testModularJDK() throws Exception {
        String modularizedJdk9Path = System.getProperty("modularizedJdk9Path");
        assertNotNull(modularizedJdk9Path);
        runJDKTests(modularizedJdk9Path);
    }

    private void runJDKTests(String repoPath) throws Exception {
        FileObject repoPathFile = FileUtil.toFileObject(FileUtil.normalizeFile(new File(repoPath)));
        assertNotNull(repoPathFile);
        doRunAction(repoPathFile,
                    "jdk/src/java.base",
                    ActionProvider.COMMAND_CLEAN);
        //TODO: verify cleaned:
//        assertNull(repoPathFile.getFileObject("langtools/build/classes"));
        doRunAction(repoPathFile,
                    "jdk/src/java.base",
                    ActionProvider.COMMAND_BUILD);
        //TODO: verify cleaned:
//        assertNotNull(repoPathFile.getFileObject("langtools/build/classes"));
    }

    private void doRunAction(FileObject repoPathFile, String projectPath, String command, String... forFiles) throws Exception {
        FileObject langtoolsProjectPath = repoPathFile.getFileObject(projectPath);
        assertNotNull(langtoolsProjectPath);
        Project langtoolsProject = ProjectManager.getDefault().findProject(langtoolsProjectPath);
        assertNotNull(langtoolsProject);

        OpenProjects.getDefault().open(new Project[] {langtoolsProject}, false);

        //scan does not need to be finished:
//        SourceUtils.waitScanFinished();

        final boolean[] outcome = new boolean[1];
        final CountDownLatch finished = new CountDownLatch(1);

        List<Object> lookupContent = new ArrayList<>();
        for (String path : forFiles) {
            FileObject file = repoPathFile.getFileObject(path);
            assertNotNull(file);
            lookupContent.add(file);
        }

        lookupContent.add(new ActionProgress() {
            @Override
            protected void started() {}
            @Override
            public void finished(boolean success) {
                outcome[0] = success;
                finished.countDown();
            }
        });

        Lookup testLookup = Lookups.fixed(lookupContent.toArray());
        ActionProvider ap = langtoolsProject.getLookup().lookup(ActionProvider.class);

        assertNotNull(ap);
        assertTrue("action supported: " + command, Arrays.asList(ap.getSupportedActions()).contains(command));
        assertTrue(ap.isActionEnabled(command, testLookup));

        ap.invokeAction(command, testLookup);

        assertTrue(finished.await(60, TimeUnit.MINUTES));
        
        assertTrue(outcome[0]);

        OpenProjects.getDefault().close(new Project[] {langtoolsProject});
    }

    @Override
    protected int timeOut() {
        return 60 * 60 * 1000;
    }

}
