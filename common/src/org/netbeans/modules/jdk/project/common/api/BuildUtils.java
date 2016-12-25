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
package org.netbeans.modules.jdk.project.common.api;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.netbeans.spi.project.support.ant.PropertyProvider;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author lahvac
 */
public class BuildUtils {

    private BuildUtils() {}

    public static File findTargetJavaHome(FileObject file) {
        File buildDir = getBuildTargetDir(file);

        if (buildDir != null) {
            File candidate = new File(buildDir, "images/j2sdk-image");

            if (candidate.isDirectory()) {
                return candidate;
            } else {
                return new File(buildDir, "jdk");
           }
        }

        Project prj = FileOwnerQuery.getOwner(file);
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

    public static File getBuildTargetDir(FileObject file) {
        return getBuildTargetDir(FileOwnerQuery.getOwner(file));
    }

    public static File getBuildTargetDir(Project prj) {
        for (String possibleRootLocation : new String[] {"../../..", "../.."}) {
            FileObject possibleJDKRoot = prj.getProjectDirectory().getFileObject(possibleRootLocation);
            Object buildAttr = possibleJDKRoot != null ? possibleJDKRoot.getAttribute(NB_JDK_PROJECT_BUILD) : null;

            if (buildAttr instanceof File) {
                return (File) buildAttr;
            }
        }

        return null;
    }

    public static final String NB_JDK_PROJECT_BUILD = "nb-jdk-project-build";

}
