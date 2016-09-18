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
package org.netbeans.modules.jdk.project.bridge.cnd;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.cnd.api.project.DefaultSystemSettings;
import org.netbeans.modules.cnd.api.project.IncludePath;
import org.netbeans.modules.cnd.api.project.NativeFileItem;
import org.netbeans.modules.cnd.api.project.NativeFileItem.Language;
import org.netbeans.modules.cnd.api.project.NativeProject;
import org.netbeans.modules.cnd.api.project.NativeProjectItemsListener;
import org.netbeans.modules.cnd.api.project.NativeProjectRegistry;
import org.netbeans.modules.cnd.utils.FSPath;
import org.netbeans.modules.cnd.utils.NamedRunnable;
import org.netbeans.modules.jdk.project.ConfigurationImpl;
import org.netbeans.modules.jdk.project.JDKProject;
import org.netbeans.modules.jdk.project.JDKProject.Root;
import org.netbeans.modules.jdk.project.JDKProject.RootKind;
import org.netbeans.spi.project.ProjectConfigurationProvider;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.netbeans.spi.project.ui.ProjectOpenedHook;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.openide.util.Lookup.Provider;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

/**
 *
 * @author lahvac
 */
public class NativeProjectImpl implements NativeProject {

    private static final Logger LOG = Logger.getLogger(NativeProjectImpl.class.getName());

    private final JDKProject project;
    private final FileObject jdkRoot;
    private final List<NativeProjectItemsListener> listeners = new ArrayList<>();
    private final RequestProcessor WORKER = new RequestProcessor(NativeProjectImpl.class.getName(), 1, false, false);

    public NativeProjectImpl(JDKProject project, FileObject jdkRoot) {
        this.project = project;
        this.jdkRoot = jdkRoot;

        systemIncludes = new ArrayList<>();
        for (String path : DefaultSystemSettings.getDefault().getSystemIncludes(Language.C, this)) {
            FileObject fo = FileUtil.toFileObject(new File(path));
            if (fo != null)
                systemIncludes.add(new IncludePath(FSPath.toFSPath(fo)));
        }
    }

    @Override
    public Provider getProject() {
        return project;
    }

    @Override
    public FileSystem getFileSystem() {
        try {
            return project.getProjectDirectory().getFileSystem();
        } catch (FileStateInvalidException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public String getProjectRoot() {
        return project.getProjectDirectory().getPath(); //XXX!!!$$$
    }

    @Override
    public List<String> getSourceRoots() {
        return sourceRoots;
    }

    @Override
    public String getProjectDisplayName() {
        return ProjectUtils.getInformation(project).getDisplayName();
    }

    private List<String> sourceRoots = Collections.emptyList();
    private final Map<FileObject, NativeFileItem> file2Item = new ConcurrentHashMap<>();
    private List<IncludePath> includes = Collections.emptyList();
    private List<String> macros = Collections.emptyList();

    private boolean initialized;

    private void initialize() {
        if (initialized) return;
        initialized = true;
        ConfigurationImpl.getProvider(jdkRoot).addPropertyChangeListener(new PropertyChangeListener() {
            @Override public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName() == null ||
                    ProjectConfigurationProvider.PROP_CONFIGURATION_ACTIVE.equals(evt.getPropertyName())) {
                    updateASync();
                }
            }
        });
        WORKER.post(new Runnable() {
            @Override
            public void run() {
                update();
            }
        }).waitFinished();
    }

    private final Set<File> listeningOn = new HashSet<>();
    private final FileChangeListener fileListener = new FileChangeListener() {
        @Override public void fileFolderCreated(FileEvent fe) {
            updateASync();
        }
        @Override public void fileDataCreated(FileEvent fe) {
            updateASync();
        }
        @Override public void fileChanged(FileEvent fe) {}
        @Override public void fileDeleted(FileEvent fe) {
            updateASync();
        }
        @Override public void fileRenamed(FileRenameEvent fe) {
            updateASync();
        }
        @Override public void fileAttributeChanged(FileAttributeEvent fe) {}
    };

    private void updateASync() {
        WORKER.post(new Runnable() {
            @Override
            public void run() {
                update();
            }
        });
    }

    private void update() {
        for (NativeProjectItemsListener l : listeners) {
            l.fileOperationsStarted(this);
        }

        try {
            Map<FileObject, NativeFileItem> removedItems = new HashMap<>(file2Item);
            Set<NativeFileItem> addedItems = new HashSet<>();
            Set<FSPath> newIncludes = new LinkedHashSet<>();
            List<String> newMacros = new ArrayList<>();
            List<String> newSourceRoots = new ArrayList<>();

            Set<File> oldListeningOn = new HashSet<>(listeningOn);

            for (Root root : project.getRoots()) {
                if (root.kind != RootKind.NATIVE_SOURCES)
                    continue;

                URL location = root.getLocation();

                if ("file".equals(location.getProtocol())) {
                    try {
                        File rootFile = Utilities.toFile(location.toURI());

                        if (listeningOn.add(rootFile)) {
                            FileUtil.addRecursiveListener(fileListener, rootFile);
                        } else {
                            oldListeningOn.remove(rootFile);
                        }
                    } catch (URISyntaxException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }

                FileObject rootFO = URLMapper.findFileObject(root.getLocation());

                if (rootFO == null)
                    continue;

                newSourceRoots.add(root.getLocation().getPath()); //TODO: format?

                Enumeration<? extends FileObject> children = rootFO.getChildren(true);

                while (children.hasMoreElements()) {
                    FileObject child = children.nextElement();

                    if (child.isData()) {
                        NativeFileItem item = file2Item.get(child);

                        if (item == null) {
                            item = new NativeFileItemImpl(this, child);

                            file2Item.put(child, item);
                            addedItems.add(item);
                        } else {
                            removedItems.remove(child);
                        }

                        if (item.getLanguage() == Language.C_HEADER) {
                            newIncludes.add(FSPath.toFSPath(child.getParent()));
                        }
                    }
                }
            }

            for (FileObject r : removedItems.keySet()) {
                file2Item.remove(r);
            }

            for (NativeProjectItemsListener l : listeners) {
                l.filesAdded(new ArrayList<>(addedItems));
                l.filesRemoved(new ArrayList<>(removedItems.values()));
            }

            ConfigurationImpl activeConfig = ConfigurationImpl.getProvider(jdkRoot).getActiveConfiguration();

            if (activeConfig != null) {
                File buildDir = activeConfig.getLocation();
                File spec = new File(buildDir, "spec.gmk");

                if (listeningOn.add(spec)) {
                    FileUtil.addRecursiveListener(fileListener, spec);
                } else {
                    oldListeningOn.remove(spec);
                }

                try {
                    for (String line : Files.readAllLines(spec.toPath())) {
                        if (line.startsWith("CFLAGS_JDKLIB:=")) {
                            for (String part : line.split("[ ]+")) { //XXX: quoting
                                if (part.startsWith("-I")) {
                                    File includeDirFile = new File(part.substring(2));
                                    if (listeningOn.add(includeDirFile)) {
                                        FileUtil.addRecursiveListener(fileListener, includeDirFile);
                                    } else {
                                        oldListeningOn.remove(includeDirFile);
                                    }
                                    FileObject includeDirFO = FileUtil.toFileObject(includeDirFile);
                                    if (includeDirFO != null)
                                        newIncludes.add(FSPath.toFSPath(includeDirFO));
                                } else if (part.startsWith("-D")) {
                                    newMacros.add(part.substring(2));
                                }
                            }
                        }
                    }
                } catch (IOException ex) {
                    //ignore...
                }
            } else {
                newIncludes = Collections.emptySet();
                newMacros = Collections.emptyList();
            }

            for (File oldListening : oldListeningOn) {
                FileUtil.removeRecursiveListener(fileListener, oldListening);
                listeningOn.remove(oldListening);
            }

            synchronized (this) {
                List<IncludePath> includesList = newIncludes.stream()
                                                            .map(IncludePath :: new)
                                                            .collect(Collectors.toList());

                if (!equalsIncludePathLists(includes, includesList) || !Objects.equals(newMacros, macros)) {
                    includes = includesList;
                    macros = newMacros;
                    for (NativeProjectItemsListener l : listeners) {
                        l.filesPropertiesChanged(new ArrayList<>(file2Item.values()));
                    }
                }

                sourceRoots = newSourceRoots;
            }
        } finally {
            for (NativeProjectItemsListener l : listeners) {
                l.fileOperationsFinished(this);
            }
        }
    }

    private boolean equalsIncludePathLists(List<IncludePath> l1, List<IncludePath> l2) {
        if (l1.size() != l2.size()) {
            return false;
        }

        Iterator<IncludePath> i1 = l1.iterator();
        Iterator<IncludePath> i2 = l2.iterator();

        while (i1.hasNext() && i2.hasNext()) {
            if (!Objects.equals(i1.next().getFSPath().getFileObject(),
                                i2.next().getFSPath().getFileObject()))
                return false;
        }

        return true;
    }

    @Override
    public List<NativeFileItem> getAllFiles() {
        initialize();
        return new ArrayList<>(file2Item.values());
    }

    @Override
    public void addProjectItemsListener(final NativeProjectItemsListener listener) {
        WORKER.post(new Runnable() {
            @Override public void run() {
                listeners.add(listener);
            }
        }).waitFinished();
    }

    @Override
    public void removeProjectItemsListener(final NativeProjectItemsListener listener) {
        WORKER.post(new Runnable() {
            @Override public void run() {
                listeners.remove(listener);
            }
        }).waitFinished();
    }

    @Override
    public NativeFileItem findFileItem(FileObject fileObject) {
        initialize();
        return file2Item.get(fileObject);
    }

    private final List<IncludePath> systemIncludes;

    @Override
    public List<IncludePath> getSystemIncludePaths() {
        return systemIncludes;
    }

    @Override
    public List<IncludePath> getUserIncludePaths() {
        return includes;
    }

    @Override
    public List<FSPath> getIncludeFiles() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getSystemMacroDefinitions() {
        return DefaultSystemSettings.getDefault().getSystemMacros(Language.C, this);
    }

    @Override
    public List<String> getUserMacroDefinitions() {
        return macros;
    }

    public List<FSPath> getSystemIncludeHeaders() {
        return Collections.emptyList();
    }

    @Override
    public List<NativeFileItem> getStandardHeadersIndexers() {
        return Collections.emptyList();
    }

    @Override
    public List<NativeProject> getDependences() {
        return Collections.emptyList();
    }

    @Override
    public void runOnProjectReadiness(NamedRunnable task) {
        //TODO
        initialize();
        task.run();
    }

    @Override
    public void fireFilesPropertiesChanged() {
        //huhl...
    }

    private static class NativeFileItemImpl implements NativeFileItem {
        private final NativeProjectImpl project;
        private final FileObject file;

        public NativeFileItemImpl(NativeProjectImpl project, FileObject child) {
            this.project = project;
            this.file = child;
        }

        @Override
        public NativeProject getNativeProject() {
            return project;
        }

        @Override
        public String getAbsolutePath() {
            //XXX:
            return file.getPath();
        }

        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public FileObject getFileObject() {
            return file;
        }

        @Override
        public List<IncludePath> getSystemIncludePaths() {
            return project.getSystemIncludePaths();
        }

        @Override
        public List<IncludePath> getUserIncludePaths() {
            return project.getUserIncludePaths();
        }

        @Override
        public List<FSPath> getIncludeFiles() {
            return Collections.emptyList();
        }

        public List<FSPath> getSystemIncludeHeaders() {
            return Collections.emptyList();
        }

        @Override
        public List<String> getSystemMacroDefinitions() {
            return project.getSystemMacroDefinitions();
        }

        @Override
        public List<String> getUserMacroDefinitions() {
            return project.getUserMacroDefinitions();
        }

        @Override
        public Language getLanguage() {
            //XXX
            switch (file.getExt()) {
                case "h": return Language.C_HEADER;
                case "c": return Language.C;
                case "cc":
                case "cpp": return Language.CPP;
            }

            return Language.C;
        }

        @Override
        public LanguageFlavor getLanguageFlavor() {
            return LanguageFlavor.DEFAULT;//well???
        }

        @Override
        public boolean isExcluded() {
            return false;
        }
    }

    @ProjectServiceProvider(projectType="org-netbeans-modules-jdk-project-JDKProject", service=NativeProject.class)
    public static NativeProject createNativeProject(Project prj) {
        try {
            JDKProject jdkPrj = (JDKProject) prj;
            FileObject jdkRoot = URLMapper.findFileObject(new URL(jdkPrj.evaluator().evaluate("${jdkRoot}")));
            return new NativeProjectImpl(jdkPrj, jdkRoot);
        } catch (MalformedURLException ex) {
            Logger.getLogger(NativeProjectImpl.class.getName()).log(Level.FINE, null, ex);
            return null;
        }
    }

    @ProjectServiceProvider(projectType="org-netbeans-modules-jdk-project-JDKProject", service=ProjectOpenedHook.class)
    public static ProjectOpenedHook createOpenedHook(final Project prj) {
        return new ProjectOpenedHook() {
            @Override protected void projectOpened() {
                NativeProject nativePrj = prj.getLookup().lookup(NativeProject.class);

                if (nativePrj != null) {
                    NativeProjectRegistry.getDefault().register(nativePrj);
                }
            }
            @Override protected void projectClosed() {
                NativeProject nativePrj = prj.getLookup().lookup(NativeProject.class);

                if (nativePrj != null) {
                    NativeProjectRegistry.getDefault().unregister(nativePrj, false);
                }
            }
        };
    }
}
