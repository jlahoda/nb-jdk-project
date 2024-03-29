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

import java.awt.Image;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.spi.java.project.support.ui.PackageView;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.netbeans.spi.project.ui.support.CommonProjectActions;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.ImageUtilities;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author lahvac
 */
public class LogicalViewProviderImpl implements LogicalViewProvider  {

    private final Project p;

    public LogicalViewProviderImpl(Project p) {
        this.p = p;
    }
    
    @Override
    public Node createLogicalView() {
        return new RootNode(p);
    }

    //from java.api.common's LogicalViewProviders:
    @Override
    public Node findPath(Node root, Object target) {
        Project prj = root.getLookup().lookup(Project.class);
        if (prj == null) {
            return null;
        }

        if (target instanceof FileObject) {
            FileObject fo = (FileObject) target;
            if (isOtherProjectSource(fo, prj)) {
                return null; // Don't waste time if project does not own the fo among sources
            }

            for (Node n : root.getChildren().getNodes(true)) {
                Node result = PackageView.findPath(n, target);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private static boolean isOtherProjectSource(
            @NonNull final FileObject fo,
            @NonNull final Project me) {
        final Project owner = FileOwnerQuery.getOwner(fo);
        if (owner == null) {
            return false;
        }
        if (me.equals(owner)) {
            return false;
        }
        for (SourceGroup sg : ProjectUtils.getSources(owner).getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA)) {
            if (FileUtil.isParentOf(sg.getRootFolder(), fo)) {
                return true;
            }
        }
        return false;
    }

    private static final class RootNode extends AbstractNode {

        private final Project project;
        
        public RootNode(Project p) {
            super(Children.create(new RootChildFactory(p), true), Lookups.fixed(p));
            this.project = p;
            setDisplayName();
            setIconBaseWithExtension("org/netbeans/modules/jdk/project/resources/jdk-project.png");
        }

        private void setDisplayName() {
            setDisplayName(ProjectUtils.getInformation(project).getDisplayName());
        }

        @Override
        public String getHtmlDisplayName() {
            return null;
        }

        @Override
        public Action[] getActions(boolean context) {
            return CommonProjectActions.forType(JDKProject.PROJECT_KEY);
        }

    }

    private static final class RootChildFactory extends ChildFactory<RootChildFactory.Key> implements ChangeListener {

        private final Sources sources;

        public RootChildFactory(Project project) {
            this.sources = ProjectUtils.getSources(project);
            this.sources.addChangeListener(WeakListeners.change(this, this.sources));
        }

        @Override
        protected boolean createKeys(List<Key> toPopulate) {
            Set<SourceGroup> javaSourceGroups = Collections.newSetFromMap(new IdentityHashMap<SourceGroup, Boolean>());

            javaSourceGroups.addAll(Arrays.asList(sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA)));

            for (SourceGroup sg : sources.getSourceGroups(SourcesImpl.SOURCES_TYPE_JDK_PROJECT)) {
                if (javaSourceGroups.contains(sg)) {
                    toPopulate.add(new Key(sg) {
                        @Override public Node createNode() {
                            return PackageView.createPackageView(group);
                        }
                    });
                } else {
                    toPopulate.add(new Key(sg) {
                        @Override public Node createNode() {
                            try {
                                DataObject od = DataObject.find(group.getRootFolder());
                                return new FilterNode(od.getNodeDelegate()) {
                                    @Override public Image getIcon(int type) {
                                        return ImageUtilities.loadImage("org/netbeans/modules/jdk/project/resources/nativeFilesFolder.gif");
                                    }
                                    @Override public Image getOpenedIcon(int type) {
                                        return ImageUtilities.loadImage("org/netbeans/modules/jdk/project/resources/nativeFilesFolderOpened.gif");
                                    }
                                    @Override public String getDisplayName() {
                                        return group.getDisplayName();
                                    }
                                };
                            } catch (DataObjectNotFoundException ex) {
                                return Node.EMPTY;
                            }
                        }
                    });
                }
            }

            return true;
        }

        @Override
        protected Node createNodeForKey(Key key) {
            return key.createNode();
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            refresh(false);
        }

        private static abstract class Key {
            public final SourceGroup group;

            public Key(SourceGroup group) {
                this.group = group;
            }

            public abstract Node createNode();

            @Override
            public int hashCode() {
                int hash = 7;
                hash = 53 * hash + Objects.hashCode(this.group);
                return hash;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final Key other = (Key) obj;
                if (!Objects.equals(this.group, other.group)) {
                    return false;
                }
                return true;
            }
            
        }
    }
}
