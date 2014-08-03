/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2013 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyrighted 2013 Sun Microsystems, Inc.
 */
package org.netbeans.modules.jdk.project;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.project.libraries.Library;
import org.netbeans.api.project.libraries.LibraryManager;
import org.netbeans.modules.jdk.project.JDKProject.Root;
import org.netbeans.modules.jdk.project.JDKProject.RootKind;
import org.netbeans.modules.parsing.spi.indexing.PathRecognizerRegistration;
import org.netbeans.spi.java.classpath.ClassPathImplementation;
import org.netbeans.spi.java.classpath.ClassPathProvider;
import org.netbeans.spi.java.classpath.FilteringPathResourceImplementation;
import org.netbeans.spi.java.classpath.PathResourceImplementation;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class ClassPathProviderImpl implements ClassPathProvider {

    private static final Logger LOG = Logger.getLogger(ClassPathProviderImpl.class.getName());
    private static final String[] JDK_CLASSPATH = new String[] {
        "{outputRoot}/jaxp/dist/lib/classes.jar",
        "{outputRoot}/corba/dist/lib/classes.jar",
    };
    
    private final ClassPath bootCP;
    private final ClassPath compileCP;
    private final ClassPath sourceCP;
    private final ClassPath testsCompileCP;
    private final ClassPath testsRegCP;

    public ClassPathProviderImpl(JDKProject project) {
        bootCP = ClassPath.EMPTY;
        
        List<URL> compileElements = new ArrayList<>();
        
        for (String cp : JDK_CLASSPATH) {
            try {
                compileElements.add(FileUtil.getArchiveRoot(project.resolve(cp).toURL()));
            } catch (MalformedURLException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        
        compileCP = ClassPathSupport.createClassPath(compileElements.toArray(new URL[0]));
        
        List<PathResourceImplementation> sourceRoots = new ArrayList<>();
        List<PathResourceImplementation> testsRegRoots = new ArrayList<>();
        
        for (Root root : project.getRoots()) {
            if (root.kind == RootKind.MAIN_SOURCES) {
                sourceRoots.add(new PathResourceImpl(root));
            } else {
                testsRegRoots.add(new PathResourceImpl(root));
            }
        }
        
        sourceCP = ClassPathSupport.createClassPath(sourceRoots);
        List<URL> testCompileRoots = new ArrayList<>();
        try {
            testCompileRoots.add(project.getFakeOutput().toURL());
        } catch (MalformedURLException ex) {
            LOG.log(Level.FINE, null, ex);
        }
        Library testng = LibraryManager.getDefault().getLibrary("testng");
        if (testng != null) {
            testCompileRoots.addAll(testng.getContent("classpath"));
        }
        File fakeJdk = InstalledFileLocator.getDefault().locate("modules/ext/fakeJdkClasses.zip", "org.netbeans.modules.jdk.project", false);
        if (fakeJdk != null) {
            testCompileRoots.add(FileUtil.urlForArchiveOrDir(fakeJdk));
        }
        testsCompileCP = ClassPathSupport.createClassPath(testCompileRoots.toArray(new URL[0]));
        testsRegCP = ClassPathSupport.createClassPath(testsRegRoots);
    }
    
    @Override
    public ClassPath findClassPath(FileObject file, String type) {
        if (sourceCP.findOwnerRoot(file) != null) {
            if (ClassPath.BOOT.equals(type)) {
                return bootCP;
            } else if (ClassPath.COMPILE.equals(type)) {
                return compileCP;
            } else if (ClassPath.SOURCE.equals(type)) {
                return sourceCP;
            }
        } else {
            if (file.isFolder()) return null;

            if (ClassPath.BOOT.equals(type)) {
                return ClassPath.EMPTY;
            } else if (ClassPath.COMPILE.equals(type)) {
                return testsCompileCP;
            }

        }
        
        return null;
    }

    public ClassPath getSourceCP() {
        return sourceCP;
    }
    
    private static final String TEST_SOURCE = "jdk-project-test-source";

    public void registerClassPaths() {
        GlobalPathRegistry.getDefault().register(ClassPath.BOOT, new ClassPath[] {bootCP});
        GlobalPathRegistry.getDefault().register(ClassPath.COMPILE, new ClassPath[] {compileCP});
        GlobalPathRegistry.getDefault().register(ClassPath.SOURCE, new ClassPath[] {sourceCP});
        GlobalPathRegistry.getDefault().register(TEST_SOURCE, new ClassPath[] {testsRegCP});
    }
    
    public void unregisterClassPaths() {
        GlobalPathRegistry.getDefault().unregister(ClassPath.BOOT, new ClassPath[] {bootCP});
        GlobalPathRegistry.getDefault().unregister(ClassPath.COMPILE, new ClassPath[] {compileCP});
        GlobalPathRegistry.getDefault().unregister(ClassPath.SOURCE, new ClassPath[] {sourceCP});
        GlobalPathRegistry.getDefault().unregister(TEST_SOURCE, new ClassPath[] {testsRegCP});
    }

    @PathRecognizerRegistration(sourcePathIds=TEST_SOURCE)
    private static final class PathResourceImpl implements FilteringPathResourceImplementation {

        private final Root root;

        public PathResourceImpl(Root root) {
            this.root = root;
        }

        @Override
        public boolean includes(URL rootURL, String resource) {
            return root.excludes == null || !root.excludes.matcher(resource).matches();
        }

        @Override
        public URL[] getRoots() {
            return new URL[] { root.location };
        }

        @Override
        public ClassPathImplementation getContent() {
            return null;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
        }

    }

}
