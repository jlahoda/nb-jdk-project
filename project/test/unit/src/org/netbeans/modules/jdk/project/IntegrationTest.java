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
import java.util.logging.Level;
import junit.framework.Test;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

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
        doTestLangtoolsTest(jdk8uPath, "jdk");
    }

    public void testModular() throws IOException, InterruptedException {
        String modularizedJdk9Path = System.getProperty("modularizedJdk9Path");
        assertNotNull(modularizedJdk9Path);
        doTestLangtoolsTest(modularizedJdk9Path, "jdk/src/java.base", "jdk/src/java.desktop", "langtools/src/java.compiler");
    }

    public void testConsolidatedRepo() throws IOException, InterruptedException {
        String consolidatedRepoPath = System.getProperty("consolidatedRepoPath");
        assertNotNull(consolidatedRepoPath);
        doTestLangtoolsTest(consolidatedRepoPath, "src/java.base", "src/java.desktop", "src/java.compiler");
    }

    private void doTestLangtoolsTest(String repoPath, String... projectsToOpen) throws IOException, InterruptedException {
        FileObject repoPathFile = FileUtil.toFileObject(FileUtil.normalizeFile(new File(repoPath)));
        assertNotNull(repoPathFile);
        for (String projectPath : projectsToOpen) {
            FileObject projectDir = repoPathFile.getFileObject(projectPath);
            assertNotNull(projectDir);
            Project project = ProjectManager.getDefault().findProject(projectDir);
            assertNotNull(project);
        }
    }
}
