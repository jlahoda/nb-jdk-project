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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.MapFormat;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class JDKProject implements Project {

    private final static String[] MAIN_SOURCE_ROOTS = new String[] {
        "{basedir}/src/share/classes/",
        "{basedir}/src/solaris/classes/",
        "{outputRoot}/jdk/gensrc/",
        "{outputRoot}/jdk/impsrc/",
    };
    
    private final FileObject jdkDir;
    private final Lookup lookup;
    private final List<Root> roots;
    private final MapFormat resolver;

    public JDKProject(FileObject jdkDir) {
        this.jdkDir = jdkDir;
        
        URI jdkDirURI = jdkDir.toURI();
        Map<String, String> variables = new HashMap<String, String>();
        
        variables.put("basedir", stripTrailingSlash(jdkDirURI.toString()));
        
        FileObject configLog = jdkDir.getFileObject("../config.log");
        
        if (configLog == null) {
            throw new IllegalStateException("Currently requires configLog");
        }
        
        Pattern outputRootPattern = Pattern.compile("OUTPUT_ROOT='(.*)'");
        
        try {
            for (String line : configLog.asText().split("\n")) {
                Matcher m = outputRootPattern.matcher(line);
                if (m.matches()) {
                    variables.put("outputRoot", stripTrailingSlash("file://" + m.group(1)));
                    break;
                }
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        
        this.roots = new ArrayList<Root>(MAIN_SOURCE_ROOTS.length);
        this.resolver = new MapFormat(variables);
        
        resolver.setLeftBrace("{");
        resolver.setRightBrace("}");
        
        for (String sr : MAIN_SOURCE_ROOTS) {
            try {
                URI rootURI = new URI(resolver.format(sr));
                
                roots.add(new Root(sr, sr, RootKind.MAIN_SOURCES, rootURI.toURL()));
                FileOwnerQuery.markExternalOwner(rootURI, this, FileOwnerQuery.EXTERNAL_ALGORITHM_TRANSIENT);
            } catch (MalformedURLException ex) {
                Exceptions.printStackTrace(ex);
            } catch (URISyntaxException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        
        ClassPathProviderImpl cpp = new ClassPathProviderImpl(this);
        this.lookup = Lookups.fixed(cpp,
                                    new OpenProjectHookImpl(cpp),
                                    new SourcesImpl(this),
                                    new LogicalViewProviderImpl(this),
                                    new SourceLevelQueryImpl());
    }
    
    private static String stripTrailingSlash(String from) {
        if (from.endsWith("/")) return from.substring(0, from.length() - 1);
        else return from;
    }
    
    @Override
    public FileObject getProjectDirectory() {
        return jdkDir;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    public List<Root> getRoots() {
        return roots;
    }
    
    public static final class Root {
        public final String relPath;
        public final String displayName;
        public final RootKind kind;
        public final URL location;
        private Root(String relPath, String displayName, RootKind kind, URL location) {
            this.relPath = relPath;
            this.displayName = displayName;
            this.kind = kind;
            this.location = location;
        }
    }
    
    public enum RootKind {
        MAIN_SOURCES,
        TEST_SOURCES;
    }
    
    @ServiceProvider(service = ProjectFactory.class)
    public static final class JDKProjectFactory implements ProjectFactory {

        @Override
        public boolean isProject(FileObject projectDirectory) {
            return projectDirectory.getFileObject("src/share/classes/java/lang/Object.java") != null; //TODO: better criterion to find out if this is a JDK project
        }

        @Override
        public Project loadProject(FileObject projectDirectory, ProjectState state) throws IOException {
            return isProject(projectDirectory) ? new JDKProject(projectDirectory) : null;
        }

        @Override
        public void saveProject(Project project) throws IOException, ClassCastException {
            //no configuration yet.
        }
        
    }
}
