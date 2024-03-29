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

import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.jdk.project.common.api.BuildUtils;
import org.netbeans.modules.jdk.project.common.api.ShortcutUtils;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author lahvac
 */
public class Utilities {

    public static boolean isJDKRepository(FileObject root) {
        if (root == null)
            return false;

        FileObject srcDir = root.getFileObject("src");

        if (srcDir == null)
            return false;

        if (srcDir.getFileObject("share/classes") != null)
            return true;

        for (FileObject mod : srcDir.getChildren()) {
            if (mod.getFileObject("share/classes") != null)
                return true;
        }

        return false;
    }

    public static boolean isLangtoolsRepository(FileObject root) {
        return (root.getFileObject("src/share/classes/com/sun/tools/javac/main/Main.java") != null ||
                root.getFileObject("src/jdk.compiler/share/classes/com/sun/tools/javac/main/Main.java") != null) &&
                root.getFileObject("src/java.base/share/classes/java/lang/Object.java") == null;
    }

    public static FileObject getLangtoolsKeyRoot(FileObject root) {
        FileObject javaBase = root.getFileObject("src/jdk.compiler/share/classes");

        if (javaBase != null)
            return javaBase;

        return root.getFileObject("src/share/classes");
    }
    
    public static File jtregOutputDir(FileObject testFile) {
        File buildDir = BuildUtils.getBuildTargetDir(testFile);
        Project prj = FileOwnerQuery.getOwner(testFile);

        if (buildDir != null) {
            FileObject repo = prj.getProjectDirectory().getParent().getParent();
            if (repo.getNameExt().equals("langtools") &&
                ShortcutUtils.getDefault().shouldUseCustomTest(repo.getNameExt(), FileUtil.getRelativePath(repo, testFile))) {
                buildDir = new File(FileUtil.toFile(prj.getProjectDirectory()), "../../build");
            } else if ("langtools".equals(ShortcutUtils.getDefault().inferLegacyRepository(prj)) &&
                ShortcutUtils.getDefault().shouldUseCustomTest(repo.getNameExt(), FileUtil.getRelativePath(repo, testFile))) {
                buildDir = new File(FileUtil.toFile(prj.getProjectDirectory()), "../../build/langtools");
            }
        } else {
            buildDir = new File(FileUtil.toFile(prj.getProjectDirectory()), "../../../build");
        }

        return new File(buildDir, "nb-jtreg").toPath().normalize().toFile();
    }

}
