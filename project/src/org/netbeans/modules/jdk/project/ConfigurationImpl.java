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
package org.netbeans.modules.jdk.project;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.jdk.project.common.api.BuildUtils;
import org.netbeans.spi.project.ProjectConfiguration;
import org.netbeans.spi.project.ProjectConfigurationProvider;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class ConfigurationImpl implements ProjectConfiguration {

    private final File location;
    private final boolean missing;

    public ConfigurationImpl(File location, boolean missing) {
        this.location = location;
        this.missing = missing;
    }

    @Override
    public String getDisplayName() {
        return (missing ? "<html><font color='#FF0000'>" : "") + location.getName();
    }

    public File getLocation() {
        return location;
    }

    private static final Map<FileObject, ProviderImpl> jdkRoot2ConfigurationProvider = new HashMap<>();

    public static ProviderImpl getProvider(FileObject jdkRoot) {
        ProviderImpl provider = jdkRoot2ConfigurationProvider.get(jdkRoot);

        if (provider == null) {
            jdkRoot2ConfigurationProvider.put(jdkRoot, provider = new ProviderImpl(jdkRoot, null));
        }

        return provider;
    }

    public static final class ProviderImpl implements ProjectConfigurationProvider<ConfigurationImpl>, FileChangeListener {

        private final FileObject jdkRoot;
        private final File buildDir;
        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        private final Set<File> buildDirsWithListeners = new HashSet<>();
        private List<ConfigurationImpl> configurations = Collections.emptyList();
        private ConfigurationImpl active;

        public ProviderImpl(FileObject jdkRoot, File buildDir) {
            this.jdkRoot = jdkRoot;
            this.buildDir = new File(FileUtil.toFile(jdkRoot), "build");
            ProjectManager.mutex().postWriteRequest(new Runnable() {
                @Override
                public void run() {
                    FileUtil.addFileChangeListener(ProviderImpl.this, ProviderImpl.this.buildDir);
                    updateConfigurations();
                }
            });
        }

        private static final String PROP_ACTIVE_CONFIGURATION = "activeConfiguration";
        
        private synchronized void updateConfigurations() {
            File[] dirs = buildDir.listFiles(new FileFilter() {
                @Override public boolean accept(File pathname) {
                    return pathname.isDirectory();
                }
            });
            
            Map<File, ConfigurationImpl> configurations2Remove = new HashMap<>();

            for (ConfigurationImpl c : configurations) {
                configurations2Remove.put(c.getLocation(), c);
            }

            File dirToSelect;

            if (active != null) {
                dirToSelect = active.getLocation();
            } else {
                Preferences prefs = prefs();
                String activeConfig = prefs != null ? prefs.get(PROP_ACTIVE_CONFIGURATION, null) : null;

                if (activeConfig != null) {
                    dirToSelect = new File(activeConfig);
                } else {
                    dirToSelect = null;
                }
            }

            Set<File> missingBuildDirs = new HashSet<>(buildDirsWithListeners);
            List<ConfigurationImpl> newConfigurations = new ArrayList<>();
            ConfigurationImpl newActive = null;

            if (dirs != null) {
                for (File dir : dirs) {
                    if (!missingBuildDirs.remove(dir)) {
                        FileUtil.addFileChangeListener(this, dir);
                        buildDirsWithListeners.add(dir);
                    }
                    if (!new File(dir, "Makefile").canRead())
                        continue;
                    
                    ConfigurationImpl current = configurations2Remove.remove(dir);

                    if (current != null) newConfigurations.add(current);
                    else newConfigurations.add(current = new ConfigurationImpl(dir, false));

                    if (dir.equals(dirToSelect) || (dirToSelect == null && newActive == null)) {
                        newActive = current;
                    }
                }
            }

            for (File removedDir : missingBuildDirs) {
                FileUtil.removeFileChangeListener(this, removedDir);
                buildDirsWithListeners.remove(removedDir);
            }

            if (newActive == null && dirToSelect != null) {
                newActive = new ConfigurationImpl(dirToSelect, true);
                newConfigurations.add(0, newActive);
            }

            Collections.sort(newConfigurations, new Comparator<ConfigurationImpl>() {
                @Override public int compare(ConfigurationImpl o1, ConfigurationImpl o2) {
                    return o1.getLocation().getName().compareTo(o2.getLocation().getName());
                }
            });

            configurations = newConfigurations;
            
            pcs.firePropertyChange(PROP_CONFIGURATIONS, null, null);
            if (active != newActive) {
                setActiveConfiguration(newActive);
            }
        }

        @Override
        public synchronized Collection<ConfigurationImpl> getConfigurations() {
            return configurations;
        }

        @Override
        public synchronized ConfigurationImpl getActiveConfiguration() {
            return active;
        }

        @Override
        public synchronized void setActiveConfiguration(ConfigurationImpl configuration) {
            this.active = configuration;
            try {
                jdkRoot.setAttribute(BuildUtils.NB_JDK_PROJECT_BUILD, configuration.location);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            pcs.firePropertyChange(PROP_CONFIGURATION_ACTIVE, null, active);
            Preferences prefs = prefs();
            if (prefs != null)
                prefs.put(PROP_ACTIVE_CONFIGURATION, configuration.getLocation().getAbsolutePath());
        }

        private Preferences prefs() {
            FileObject javaBase = jdkRoot.getFileObject("jdk/src/java.base");
            if (javaBase == null)
                javaBase = jdkRoot.getFileObject("jdk");
            Project javaBaseProject = javaBase != null ? FileOwnerQuery.getOwner(javaBase) : null;

            if (javaBaseProject != null) {
                return ProjectUtils.getPreferences(javaBaseProject, ConfigurationImpl.class, false);
            } else {
                return null;
            }
        }

        @Override
        public boolean hasCustomizer() {
            return false;
        }

        @Override
        public void customize() {
        }

        @Override
        public boolean configurationsAffectAction(String command) {
            return false;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener lst) {
            pcs.addPropertyChangeListener(lst);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener lst) {
            pcs.removePropertyChangeListener(lst);
        }

        @Override
        public void fileFolderCreated(FileEvent fe) {
            updateConfigurations();
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
            updateConfigurations();
        }

        @Override
        public void fileChanged(FileEvent fe) {
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            updateConfigurations();
        }

        @Override
        public void fileRenamed(FileRenameEvent fe) {
            updateConfigurations();
        }

        @Override
        public void fileAttributeChanged(FileAttributeEvent fe) {
        }

    }

}
