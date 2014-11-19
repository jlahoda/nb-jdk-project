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

import java.util.List;
import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.spi.java.project.support.ui.PackageView;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.netbeans.spi.project.ui.support.CommonProjectActions;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
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

    @Override
    public Node findPath(Node root, Object target) {
        return null; //XXX
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

    private static final class RootChildFactory extends ChildFactory<Object> implements ChangeListener {

        private final Sources sources;

        public RootChildFactory(Project project) {
            this.sources = ProjectUtils.getSources(project);
            this.sources.addChangeListener(WeakListeners.change(this, this.sources));
        }

        @Override
        protected boolean createKeys(List<Object> toPopulate) {
            for (SourceGroup src : sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA)) {
                toPopulate.add(src);
            }

            return true;
        }

        @Override
        protected Node createNodeForKey(Object key) {
            if (key instanceof SourceGroup) {
                return PackageView.createPackageView((SourceGroup) key);
            }

            throw new IllegalStateException();
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            refresh(false);
        }

    }
}
