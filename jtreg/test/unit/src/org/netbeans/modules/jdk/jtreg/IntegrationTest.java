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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.swing.Action;
import javax.tools.Diagnostic;
import junit.framework.Test;
import org.apache.tools.ant.module.spi.AntEvent;
import org.apache.tools.ant.module.spi.AntLogger;
import org.apache.tools.ant.module.spi.AntSession;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ContextAwareAction;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class IntegrationTest extends NbTestCase {

    public IntegrationTest(String name) {
        super(name);
    }

    public static Test suite() {
        return NbModuleSuite.createConfiguration(IntegrationTest.class)
                            .gui(false)
                            .failOnException(Level.WARNING)
                            .suite();
    }

    public void testLegacy() throws IOException, InterruptedException {
        String jdk8uPath = System.getProperty("jdk8uPath");
        assertNotNull(jdk8uPath);
        doTestLangtoolsTest(jdk8uPath);
    }

    public void testModular() throws IOException, InterruptedException {
        String modularizedJdk9Path = System.getProperty("modularizedJdk9Path");
        assertNotNull(modularizedJdk9Path);
        doTestLangtoolsTest(modularizedJdk9Path);
    }

    private void doTestLangtoolsTest(String repoPath) throws IOException, InterruptedException {
        FileObject repoPathFile = FileUtil.toFileObject(FileUtil.normalizeFile(new File(repoPath)));
        assertNotNull(repoPathFile);
        FileObject langtoolsProjectPath = repoPathFile.getFileObject("langtools/make/netbeans/langtools");
        assertNotNull(langtoolsProjectPath);
        Project langtoolsProject = ProjectManager.getDefault().findProject(langtoolsProjectPath);
        assertNotNull(langtoolsProject);

        OpenProjects.getDefault().open(new Project[] {langtoolsProject}, false);

        SourceUtils.waitScanFinished();

        FileObject testFile = repoPathFile.getFileObject(LANGTOOLS_TEST_PATH);

        assertNotNull(testFile);

        JavaSource.forFileObject(testFile).runUserActionTask(new Task<CompilationController>() {
            @Override
            public void run(CompilationController parameter) throws Exception {
                parameter.toPhase(Phase.RESOLVED);
                boolean hasError = false;
                for (Diagnostic d : parameter.getDiagnostics()) {
                    hasError |= d.getKind() == Diagnostic.Kind.ERROR;
                }
                assertFalse(hasError);
            }
        }, true);

        ContextAwareAction genericAction = FileUtil.getConfigObject("Actions/Debug/org-netbeans-modules-debugger-ui-actions-DebugTestFileAction.instance", ContextAwareAction.class);

        assertNotNull(genericAction);

        Action action = genericAction.createContextAwareInstance(Lookups.fixed(testFile));

        assertTrue(action.isEnabled());

        finished = new CountDownLatch(1);

        action.actionPerformed(null);

        assertTrue(finished.await(1, TimeUnit.MINUTES));
        
        assertTrue(outcome);

        OpenProjects.getDefault().close(new Project[] {langtoolsProject});
    }

    static boolean outcome;
    static CountDownLatch finished;

    @ServiceProvider(service=AntLogger.class, position=10)
    public static class TestAntLogger extends AntLogger {

        @Override
        public void buildFinished(AntEvent event) {
            outcome = event.getException() == null;
            finished.countDown();
        }

        @Override
        public boolean interestedInSession(AntSession session) {
            return true;
        }

        @Override
        public String[] interestedInTargets(AntSession session) {
            return ALL_TARGETS;
        }

        @Override
        public String[] interestedInTasks(AntSession session) {
            return ALL_TASKS;
        }

        @Override
        public boolean interestedInAllScripts(AntSession session) {
            return true;
        }

        @Override
        public boolean interestedInScript(File script, AntSession session) {
            return true;
        }

        @Override
        public int[] interestedInLogLevels(AntSession session) {
            return new int[] {AntEvent.LOG_INFO};
        }

    }

    private static final String LANGTOOLS_TEST_PATH = "langtools/test/tools/javac/4241573/T4241573.java";
}
