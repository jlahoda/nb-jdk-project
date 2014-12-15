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

import java.io.IOException;
import java.io.OutputStream;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author lahvac
 */
public class FilterStandardProjectsTest extends NbTestCase {

    public FilterStandardProjectsTest(String name) {
        super(name);
    }

    public void testProjectsFilteredModularized() throws IOException {
        clearWorkDir();

        FileObject workDir = FileUtil.toFileObject(getWorkDir());

        assertNotNull(workDir);

        FileObject javaBase = FileUtil.createFolder(workDir, "langtools/src/java.compiler");
        FileObject modulesXml = FileUtil.createData(workDir, "modules.xml");
        try (OutputStream out = modulesXml.getOutputStream()) {
            out.write(("<?xml version=\"1.0\" encoding=\"us-ascii\"?>\n" +
                       "<modules>\n" +
                       "  <module>\n" +
                       "    <name>java.compiler</name>\n" +
                       "  </module>\n" +
                       "</modules>\n").getBytes("UTF-8"));
        }
        FileUtil.createFolder(workDir, "langtools/src/java.compiler/share/classes");
        FileObject langtoolsPrj = FileUtil.createFolder(workDir, "langtools/make/netbeans/langtools");
        FileUtil.createData(workDir, "langtools/make/netbeans/langtools/nbproject/project.xml");
        Project javaBaseProject = ProjectManager.getDefault().findProject(javaBase);

        assertNotNull(javaBaseProject);

        OpenProjects.getDefault().open(new Project[] {javaBaseProject}, false);

        try {
            ProjectManager.getDefault().findProject(langtoolsPrj);
        } catch (IOException ex) {
            assertEquals(FilterStandardProjects.MSG_FILTER, ex.getMessage());
        }

        OpenProjects.getDefault().close(new Project[] {javaBaseProject});
    }

    static {
        System.setProperty("nb.jdk.project.block.langtools", "true");
        System.setProperty("netbeans.dirs", System.getProperty("cluster.path.final"));
    }
}
