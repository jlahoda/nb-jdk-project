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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Assert;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author lahvac
 */
public class ClassPathProviderImplTest extends NbTestCase {

    public ClassPathProviderImplTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        clearWorkDir();
    }

    public void testSimple() throws Exception {
        File workDir = getWorkDir();

        FileUtil.createFolder(new File(workDir, "src/share/classes"));
        FileObject testFile = FileUtil.createData(new File(workDir, "test/feature/Test.java"));
        ClassPath sourceCP = new ClassPathProviderImpl().findClassPath(testFile, ClassPath.SOURCE);

        Assert.assertArrayEquals(new FileObject[] {testFile.getParent()}, sourceCP.getRoots());
    }

    public void testJTRegLibrary() throws Exception {
        File workDir = getWorkDir();

        FileUtil.createFolder(new File(workDir, "src/share/classes"));
        FileObject testFile = createData("test/feature/inner/Test.java", "/** @test\n * @library ../lib /lib2\n */");
        FileObject testLib = FileUtil.createData(new File(workDir, "test/feature/lib/Lib.java"));
        FileObject testLib2 = FileUtil.createData(new File(workDir, "test/lib2/Lib2.java"));
        ClassPath sourceCP = new ClassPathProviderImpl().findClassPath(testFile, ClassPath.SOURCE);

        System.err.println("sourceCP=" + Arrays.toString(sourceCP.getRoots()));
        Assert.assertEquals(new HashSet<>(Arrays.asList(testFile.getParent(), testLib.getParent(), testLib2.getParent())),
                            new HashSet<>(Arrays.asList(sourceCP.getRoots())));
    }

    public void testTestProperties() throws Exception {
        File workDir = getWorkDir();

        FileUtil.createFolder(new File(workDir, "src/share/classes"));
        FileObject testUse = FileUtil.createData(new File(workDir, "test/dir/use/org/Use.java"));
        FileObject testLib = FileUtil.createData(new File(workDir, "test/dir/lib/org/Lib.java"));
        FileObject testProperties = createData("test/dir/use/TEST.properties", "lib.dirs=../lib");
        ClassPath sourceCP = new ClassPathProviderImpl().findClassPath(testUse, ClassPath.SOURCE);

        Assert.assertEquals(new HashSet<>(Arrays.asList(testUse.getParent().getParent(), testLib.getParent().getParent())),
                            new HashSet<>(Arrays.asList(sourceCP.getRoots())));
    }

    private FileObject createData(String relPath, String content) throws IOException {
        File workDir = getWorkDir();
        FileObject file = FileUtil.createData(new File(workDir, relPath));

        try (Writer w = new OutputStreamWriter(file.getOutputStream())) {
            w.write(content);
        }

        return file;
    }

}
