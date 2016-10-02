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

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.java.queries.SourceForBinaryQuery;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.modules.jdk.project.ModuleDescription.ModuleRepository;
import org.netbeans.spi.java.queries.SourceForBinaryQueryImplementation2;
import org.openide.filesystems.FileObject;
import org.openide.util.ChangeSupport;
import org.openide.util.Exceptions;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=SourceForBinaryQueryImplementation2.class, position=85)
public class GlobalSourceForBinaryQuery implements SourceForBinaryQueryImplementation2 {

    private static final Reference<Project> NO_PROJECT_CACHE = new WeakReference<Project>(null);

    private final Map<URL, Reference<Project>> projectCache = new HashMap<>();

    @Override
    public Result findSourceRoots2(URL binaryRoot) {
        Reference<Project> cachedProject = projectCache.get(binaryRoot);

        if (cachedProject == NO_PROJECT_CACHE) {
            return null;
        }

        Project prj = cachedProject != null ? cachedProject.get() : null;

        if (prj == null) {
            try {
                URI jdkRootCandidate = binaryRoot.toURI().resolve("../../../../../").normalize();

                if (jdkRootCandidate != null) {
                    ModuleRepository repository = ModuleDescription.getModuleRepository(jdkRootCandidate);

                    if (repository != null) {
                        String path = binaryRoot.getPath();
                        int lastSlash = path.lastIndexOf('/', path.length() - 2);
                        if (lastSlash >= 0) {
                            String moduleName = path.substring(lastSlash + 1, path.length() - 1);

                            FileObject root = repository.findModuleRoot(moduleName);

                            if (root != null) {
                                prj = FileOwnerQuery.getOwner(root);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        if (prj == null) {
            projectCache.put(binaryRoot, NO_PROJECT_CACHE);

            return null;
        } else {
            projectCache.put(binaryRoot, new WeakReference<>(prj));

            return new ResultImpl(prj);
        }
    }

    @Override
    public SourceForBinaryQuery.Result findSourceRoots(URL binaryRoot) {
        return findSourceRoots2(binaryRoot);
    }

    private static final class ResultImpl implements Result, ChangeListener {

        private final ChangeSupport cs = new ChangeSupport(this);
        private final Sources sources;

        public ResultImpl(Project prj) {
            sources = ProjectUtils.getSources(prj);
            sources.addChangeListener(WeakListeners.change(this, sources));
        }

        @Override
        public boolean preferSources() {
            return false;
        }

        @Override
        public FileObject[] getRoots() {
            List<FileObject> roots = new ArrayList<>();

            for (SourceGroup sg : sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA)) {
                roots.add(sg.getRootFolder());
            }

            return roots.toArray(new FileObject[0]);
        }

        @Override
        public void addChangeListener(ChangeListener l) {
            cs.addChangeListener(l);
        }

        @Override
        public void removeChangeListener(ChangeListener l) {
            cs.removeChangeListener(l);
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            cs.fireChange();
        }

    }

}
