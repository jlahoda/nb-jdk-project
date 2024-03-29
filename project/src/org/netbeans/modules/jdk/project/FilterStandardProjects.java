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
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=ProjectFactory.class, position=10)
public class FilterStandardProjects implements ProjectFactory {

    private static final boolean BLOCK_LANGTOOLS_PROJECT = Boolean.getBoolean("nb.jdk.project.block.langtools");
    
    @Override
    public boolean isProject(FileObject projectDirectory) {
        FileObject jdkRoot;
        return projectDirectory.getFileObject("nbproject/project.xml") != null &&
               (jdkRoot = projectDirectory.getFileObject("../../..")) != null &&
               (JDKProject.isJDKProject(jdkRoot) || jdkRoot.getFileObject("../modules.xml") != null) &&
               projectDirectory.getParent().equals(jdkRoot.getFileObject("make/netbeans")) &&
               "netbeans".equals(projectDirectory.getParent().getName());
    }

    public static final String MSG_FILTER = "This project cannot be load while the NetBeans JDK project is open.";
    
    @Override
    public Project loadProject(FileObject projectDirectory, ProjectState state) throws IOException {
        if (!isProject(projectDirectory)) return null;

        FileObject repository;
        String project2Repository;

        if ("langtools".equals(projectDirectory.getNameExt())) {
            if (!BLOCK_LANGTOOLS_PROJECT)
                return null;
            repository = projectDirectory.getFileObject("../../../../langtools");
            project2Repository = "../..";
        } else {
            repository = projectDirectory.getFileObject("../../..");
            project2Repository = "";
        }
        
        if (repository != null) {
            for (Project prj : OpenProjects.getDefault().getOpenProjects()) {
                if (repository.equals(prj.getProjectDirectory().getFileObject(project2Repository))) {
                    throw new IOException(MSG_FILTER);
                }
            }
        }

        return null;
    }

    @Override
    public void saveProject(Project project) throws IOException, ClassCastException {
    }

}
