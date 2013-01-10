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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.event.ChangeListener;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import static org.netbeans.api.project.Sources.TYPE_GENERIC;
import org.netbeans.modules.jdk.project.JDKProject.Root;
import org.netbeans.spi.project.support.GenericSources;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.ChangeSupport;

/**
 *
 * @author lahvac
 */
public class SourcesImpl implements Sources, FileChangeListener {

    private final ChangeSupport cs = new ChangeSupport(this);
    private final JDKProject project;
    private final Map<Root, SourceGroup> root2SourceGroup = new HashMap<Root, SourceGroup>();

    public SourcesImpl(JDKProject project) {
        this.project = project;
        
        for (Root r : project.getRoots()) {
            if (!"file".equals(r.location.getProtocol())) continue;
            
            File rootFile = new File(r.location.getPath());
            
            System.err.println("rootFile=" + rootFile.getAbsolutePath());
            FileUtil.addFileChangeListener(this, rootFile);
        }
    }

    private boolean initialized;
    private SourceGroup[] genericSourceGroup;
    
    @Override
    public synchronized SourceGroup[] getSourceGroups(String type) {
        if (!initialized) {
            this.genericSourceGroup = GenericSources.genericOnly(project).getSourceGroups(TYPE_GENERIC);
            recompute();
            initialized = true;
        }
        
        if (TYPE_GENERIC.equals(type)) {
            return genericSourceGroup;
        }
        if (JavaProjectConstants.SOURCES_TYPE_JAVA.equals(type)) {
            List<SourceGroup> sourceGroups = new ArrayList<SourceGroup>();

            for (Root root : project.getRoots()) {
                SourceGroup sg = root2SourceGroup.get(root);
                if (sg != null)
                    sourceGroups.add(sg);
            }

            return sourceGroups.toArray(new SourceGroup[0]);
        }

        return new SourceGroup[0];
    }
    
    private synchronized void recompute() {
        for (Root root : project.getRoots()) {
            System.err.println(root.location);
            FileObject src = URLMapper.findFileObject(root.location);
            if (src == null) {
                System.err.println("removing: " + root.location);
                root2SourceGroup.remove(root);
            } else if (!root2SourceGroup.containsKey(root)) {
                System.err.println("adding: " + root.location);
                root2SourceGroup.put(root, GenericSources.group(project, src, root.displayName, root.displayName, null, null));
            } else {
                System.err.println("keeping: " + root.location);
            }
        }
        cs.fireChange();
    }

    @Override public void addChangeListener(ChangeListener listener) {
        cs.addChangeListener(listener);
    }

    @Override public void removeChangeListener(ChangeListener listener) {
        cs.removeChangeListener(listener);
    }

    @Override
    public void fileFolderCreated(FileEvent fe) {
        recompute();
    }

    @Override
    public void fileDataCreated(FileEvent fe) { }

    @Override
    public void fileChanged(FileEvent fe) { }

    @Override
    public void fileDeleted(FileEvent fe) {
        recompute();
    }

    @Override
    public void fileRenamed(FileRenameEvent fe) {
        recompute();
    }

    @Override
    public void fileAttributeChanged(FileAttributeEvent fe) { }
    
}
