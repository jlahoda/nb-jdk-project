/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2016 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2016 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jdk.project;

import java.io.IOException;
import java.io.OutputStream;

import org.netbeans.api.java.queries.SourceLevelQuery;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author lahvac
 */
public class SourceLevelQueryImplTest extends NbTestCase {

    public SourceLevelQueryImplTest(String name) {
        super(name);
    }

    private FileObject root;

    @Override
    protected void setUp() throws IOException {
        clearWorkDir();

        root = FileUtil.toFileObject(getWorkDir());
    }

    public void testLegacyProject() throws IOException {
        FileObject jlObject = FileUtil.createData(root, "jdk/src/share/classes/java/lang/Object.java");
        copyString2File(jlObject, "");
        copyString2File(FileUtil.createData(root, ".jcheck/conf"), "project=jdk8\n");

        Project legacyProject = FileOwnerQuery.getOwner(root.getFileObject("jdk"));

        assertNotNull(legacyProject);

        assertEquals("1.8", SourceLevelQuery.getSourceLevel(jlObject));
    }
    
    public void testModuleInfoProject() throws IOException {
        FileObject javaBase = FileUtil.createFolder(root, "jdk/src/java.base");
        FileObject jlObject = FileUtil.createData(javaBase, "share/classes/java/lang/Object.java");
        copyString2File(jlObject, "");
        copyString2File(FileUtil.createData(javaBase, "share/classes/module-info.java"), "module java.base {}");
        copyString2File(FileUtil.createData(root, ".jcheck/conf"), "project=jdk3\n");

        Project javaBaseProject = FileOwnerQuery.getOwner(javaBase);

        assertNotNull(javaBaseProject);

        assertEquals("1.3", SourceLevelQuery.getSourceLevel(jlObject));
    }

    public void testNoJCheck() throws IOException {
        FileObject javaBase = FileUtil.createFolder(root, "jdk/src/java.base");
        FileObject jlObject = FileUtil.createData(javaBase, "share/classes/java/lang/Object.java");
        copyString2File(jlObject, "");
        copyString2File(FileUtil.createData(javaBase, "share/classes/module-info.java"), "module java.base {}");

        Project javaBaseProject = FileOwnerQuery.getOwner(javaBase);

        assertNotNull(javaBaseProject);

        assertEquals("9", SourceLevelQuery.getSourceLevel(jlObject));
    }

    private void copyString2File(FileObject file, String content) throws IOException {
        try (OutputStream out = file.getOutputStream()) {
            out.write(content.getBytes("UTF-8"));
        }
    }

    static {
        System.setProperty("netbeans.dirs", System.getProperty("cluster.path.final", ""));
    }
}
