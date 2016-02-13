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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.event.ChangeListener;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.jdk.project.ConfigurationImpl.ProviderImpl;
import org.netbeans.modules.jdk.project.ModuleDescription.ModuleRepository;
import org.netbeans.spi.project.ProjectConfigurationProvider;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectState;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.netbeans.spi.project.support.ant.PropertyProvider;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ChangeSupport;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Pair;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class JDKProject implements Project {

    public static final String PROJECT_KEY = "org-netbeans-modules-jdk-project-JDKProject";
    private final FileObject projectDir;
    private final Lookup lookup;
    private final List<Root> roots;
    private final URI fakeOutput;
            final ModuleRepository moduleRepository;
            final ModuleDescription currentModule;
    private final PropertyEvaluator evaluator;
            final MapPropertyProvider properties;
            final ProviderImpl configurations;

    public JDKProject(FileObject projectDir, @NullAllowed ModuleRepository moduleRepository, @NullAllowed ModuleDescription currentModule) {
        this.projectDir = projectDir;
        this.moduleRepository = moduleRepository;
        this.currentModule = currentModule;
        
        URI jdkDirURI = projectDir.toURI();

        properties = new MapPropertyProvider();
        
        properties.setProperty("basedir", stripTrailingSlash(jdkDirURI.toString()));
        properties.setProperty("module", projectDir.getNameExt());

        String osKey;
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("mac")) {
            osKey = "macosx";
        } else if (osName.contains("windows")) {
            osKey = "windows";
        } else if (osName.contains("solaris")) {
            osKey = "solaris";
        } else {
            osKey = "linux";
        }

        String legacyOsKey;
        String generalizedOsKey;

        switch (osKey) {
            case "macosx": generalizedOsKey = "unix"; legacyOsKey = "macosx"; break;
            case "solaris": generalizedOsKey = "unix"; legacyOsKey = "solaris"; break;
            case "linux": generalizedOsKey = "unix"; legacyOsKey = "solaris"; break;
            case "windows": generalizedOsKey = "no-such-key"; legacyOsKey = "windows"; break;
            default:
                throw new IllegalStateException(osKey);
        }

        properties.setProperty("os", osKey);
        properties.setProperty("generalized-os", generalizedOsKey);
        properties.setProperty("legacy-os", legacyOsKey);
        FileObject jdkRoot = moduleRepository != null ? projectDir.getFileObject("../../..") : projectDir.getFileObject("..");
        properties.setProperty("jdkRoot", stripTrailingSlash(jdkRoot.toURI().toString()));
        configurations = ConfigurationImpl.getProvider(jdkRoot);

        configurations.addPropertyChangeListener(new PropertyChangeListener() {
            @Override public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName() == null || evt.getPropertyName().equals(ProjectConfigurationProvider.PROP_CONFIGURATION_ACTIVE)) {
                    updateConfiguration();
                }
            }
        });

        updateConfiguration();
        
        evaluator = PropertyUtils.sequentialPropertyEvaluator(properties);
        
        boolean closed = projectDir.getFileObject("src/closed/share/classes/javax/swing/plaf/basic/icons/JavaCup16.png") != null;
        boolean modular = currentModule != null;
        Configuration configuration =  modular ? MODULAR_CONFIGURATION
                                               : closed ? LEGACY_CLOSED_CONFIGURATION : LEGACY_OPEN_CONFIGURATION;
        
        this.roots = new ArrayList<>(configuration.mainSourceRoots.size());
        
        addRoots(RootKind.MAIN_SOURCES, configuration.mainSourceRoots);
        addRoots(RootKind.TEST_SOURCES, configuration.testSourceRoots);

        URL fakeOutputURL;
        try {
            URI fakeOutputJar = new URI(evaluator.evaluate("${basedir}/fake-target.jar"));
            fakeOutput = FileUtil.getArchiveRoot(fakeOutputJar.toURL()).toURI();
            fakeOutputURL = fakeOutput.toURL();
        } catch (MalformedURLException | URISyntaxException ex) {
            throw new IllegalStateException(ex);
        }

        if (currentModule != null) {
            //XXX: hacks for modules that exist in more than one repository - would be better to handle them automatically.
            switch (currentModule.name) {
                case "java.base":
                    addRoots(RootKind.MAIN_SOURCES, Arrays.asList(Pair.<String, String>of("${jdkRoot}/langtools/src/java.base/share/classes/", null)));
                    //TODO: what to do with the test folder? make it part of java.base for now
                    addRoots(RootKind.TEST_SOURCES, Arrays.asList(Pair.<String, String>of("${jdkRoot}/jdk/test/", null)));
                    break;
                case "java.compiler":
                    //TODO: langtools tests
                    addRoots(RootKind.TEST_SOURCES, Arrays.asList(Pair.<String, String>of("${jdkRoot}/langtools/test/", null)));
                    break;
                case "java.xml":
                    //TODO: jaxp tests
                    addRoots(RootKind.TEST_SOURCES, Arrays.asList(Pair.<String, String>of("${jdkRoot}/jaxp/test/", null)));
                    break;
                case "jdk.compiler":
                    addRoots(RootKind.MAIN_SOURCES, Arrays.asList(Pair.<String, String>of("${jdkRoot}/jdk/src/jdk.compiler/share/classes/", null)));
                    break;
                case "jdk.dev":
                    addRoots(RootKind.MAIN_SOURCES, Arrays.asList(Pair.<String, String>of("${jdkRoot}/jdk/src/jdk.dev/share/classes/", null)));
                    break;
            }
        }

        ClassPathProviderImpl cpp = new ClassPathProviderImpl(this);
        this.lookup = Lookups.fixed(cpp,
                                    new OpenProjectHookImpl(cpp),
                                    new SourcesImpl(this),
                                    new LogicalViewProviderImpl(this),
                                    new SourceLevelQueryImpl(jdkRoot),
                                    new SourceForBinaryQueryImpl(fakeOutputURL, cpp.getSourceCP()),
                                    new ProjectInformationImpl(),
                                    configurations,
                                    new SubProjectProviderImpl(this),
                                    new ActionProviderImpl(this));
    }

    private void addRoots(RootKind kind, Iterable<Pair<String, String>> rootSpecifications) {
        for (Pair<String, String> sr : rootSpecifications) {
            roots.add(new Root(sr.first(), sr.first(), kind, evaluator, sr.second() != null ? Pattern.compile(sr.second()) : null));
        }
    }
    
    private static String stripTrailingSlash(String from) {
        if (from.endsWith("/")) return from.substring(0, from.length() - 1);
        else return from;
    }

    private void updateConfiguration() {
        ProjectManager.mutex().readAccess(new Runnable() {
            @Override public void run() {
                ConfigurationImpl activeConfig = configurations.getActiveConfiguration();
                File configurationDir = activeConfig != null ? activeConfig.getLocation() : null;
                properties.setProperty("outputRoot", configurationDir != null ? stripTrailingSlash(configurationDir.toURI().toString()) : "file:///non-existing");
            }
        });
    }
    
    @Override
    public FileObject getProjectDirectory() {
        return projectDir;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    public List<Root> getRoots() {
        return roots;
    }

    public URI getFakeOutput() {
        return fakeOutput;
    }

    public PropertyEvaluator evaluator() {
        return evaluator;
    }

    public static final class Root implements PropertyChangeListener {
        public final String relPath;
        public final String displayName;
        public final RootKind kind;
        public final Pattern excludes;
        private final PropertyEvaluator evaluator;
        private URL location;
        private final ChangeSupport cs = new ChangeSupport(this);
        private Root(String relPath, String displayName, RootKind kind, PropertyEvaluator evaluator, Pattern excludes) {
            this.relPath = relPath;
            this.displayName = displayName;
            this.kind = kind;
            this.evaluator = evaluator;
            this.excludes = excludes;
            this.evaluator.addPropertyChangeListener(this);

        }
        public URL getLocation() {
            if (location == null) {
                try {
                    location = new URL(evaluator.evaluate(relPath)).toURI().normalize().toURL();
                } catch (MalformedURLException | URISyntaxException ex) {
                    Exceptions.printStackTrace(ex); //XXX
                }
            }
            return location;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            location = null;
            cs.fireChange();
        }

        public void addChangeListener(ChangeListener l) {
            cs.addChangeListener(l);
        }

        public void removeChangeListener(ChangeListener l) {
            cs.removeChangeListener(l);
        }
    }
    
    public enum RootKind {
        MAIN_SOURCES,
        TEST_SOURCES;
    }

    private static final class Configuration {
        public final List<Pair<String, String>> mainSourceRoots;
        public final List<Pair<String, String>> testSourceRoots;

        public Configuration(List<Pair<String, String>> mainSourceRoots, List<Pair<String, String>> testSourceRoots) {
            this.mainSourceRoots = mainSourceRoots;
            this.testSourceRoots = testSourceRoots;
        }
    }

    private static final Configuration LEGACY_OPEN_CONFIGURATION = new Configuration(
            Arrays.asList(Pair.<String, String>of("${basedir}/src/share/classes/",
                                                  "com/sun/jmx/snmp/.*|com/sun/jmx/snmp|sun/management/snmp/.*|sun/management/snmp|sun/dc/.*|sun/dc"),
                          Pair.<String, String>of("${basedir}/src/${legacy-os}/classes/", null),
                          Pair.<String, String>of("${outputRoot}/jdk/gensrc/", null),
                          Pair.<String, String>of("${outputRoot}/jdk/impsrc/", null)),
            Arrays.asList(Pair.<String, String>of("${basedir}/test", null))
    );

    private static final Configuration LEGACY_CLOSED_CONFIGURATION = new Configuration(
            Arrays.asList(Pair.<String, String>of("${basedir}/src/share/classes/", null),
                          Pair.<String, String>of("${basedir}/src/${legacy-os}/classes/", null),
                          Pair.<String, String>of("${basedir}/src/closed/share/classes/", null),
                          Pair.<String, String>of("${basedir}/src/closed/${legacy-os}/classes/", null),
                          Pair.<String, String>of("${outputRoot}/jdk/gensrc/", null),
                          Pair.<String, String>of("${outputRoot}/jdk/impsrc/", null)),
            Arrays.asList(Pair.<String, String>of("${basedir}/test", null))
    );

    private static final Configuration MODULAR_CONFIGURATION = new Configuration(
            Arrays.asList(Pair.<String, String>of("${basedir}/share/classes/", null),
                          Pair.<String, String>of("${basedir}/${os}/classes/", null),
                          Pair.<String, String>of("${basedir}/${generalized-os}/classes/", null),
                          Pair.<String, String>of("${basedir}/../closed/${module}/share/classes/", null),
                          Pair.<String, String>of("${basedir}/../closed/${module}/${os}/classes/", null),
                          Pair.<String, String>of("${basedir}/../closed/${module}/${generalized-os}/classes/", null),
                          Pair.<String, String>of("${outputRoot}/jdk/gensrc/${module}/", null),
                          Pair.<String, String>of("${outputRoot}/support/gensrc/${module}/", null)),
            Arrays.<Pair<String, String>>asList()
    );

    static boolean isJDKProject(FileObject projectDirectory) {
        try {
            ModuleRepository repository = ModuleDescription.getModules(projectDirectory);

            if (repository != null) {
                return repository.findModule(projectDirectory.getNameExt()) != null;
            } else {
                return projectDirectory.getFileObject("src/share/classes/java/lang/Object.java") != null;
            }
        } catch (Exception ex) {
            Logger.getLogger(JDKProject.class.getName()).log(Level.FINE, null, ex);
            return false;
        }
    }

    @ServiceProvider(service = ProjectFactory.class)
    public static final class JDKProjectFactory implements ProjectFactory {

        @Override
        public boolean isProject(FileObject projectDirectory) {
            return isJDKProject(projectDirectory);
        }

        @Override
        public Project loadProject(FileObject projectDirectory, ProjectState state) throws IOException {
            if (isProject(projectDirectory)) {
                Project prj = loadModularProject(projectDirectory);

                if (prj != null)
                    return prj;

                if (projectDirectory.getFileObject("src/share/classes/java/lang/Object.java") != null) {
                    //legacy project:
                    return new JDKProject(projectDirectory, null, null);
                }
            }

            return null;
        }

        private Project loadModularProject(FileObject projectDirectory) {
            try {
                ModuleRepository repository = ModuleDescription.getModules(projectDirectory);
                
                if (repository == null) {
                    return null;
                }
                
                ModuleDescription thisModule = repository.findModule(projectDirectory.getNameExt());

                if (thisModule == null) {
                    return null;
                }

                if (!projectDirectory.equals(repository.findModuleRoot(thisModule.name))) {
                    return null;
                }


                return new JDKProject(projectDirectory, repository, thisModule);
            } catch (Exception ex) {
                ex.printStackTrace();
                Logger.getLogger(JDKProject.class.getName()).log(Level.FINE, null, ex);
                return null;
            }
        }

        @Override
        public void saveProject(Project project) throws IOException, ClassCastException {
            //no configuration yet.
        }
        
    }

    @Messages({
        "DN_Project=J2SE - {0}",
        "DN_Module=Module - {0} - {1}"
    })
    private final class ProjectInformationImpl implements ProjectInformation {

        @Override
        public String getName() {
            return currentModule != null ? getProjectDirectory().getNameExt()
                                         : "j2se";
        }

        @Override
        public String getDisplayName() {
            return currentModule != null ? Bundle.DN_Module(getProjectDirectory().getNameExt(), getProjectDirectory().getFileObject("../../..").getNameExt())
                                         : Bundle.DN_Project(getProjectDirectory().getParent().getNameExt());
        }

        @Override
        public Icon getIcon() {
            return ImageUtilities.loadImageIcon("org/netbeans/modules/jdk/project/resources/jdk-project.png", false);
        }

        @Override
        public Project getProject() {
            return JDKProject.this;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
        }

    }

    static final class MapPropertyProvider implements PropertyProvider {

        private final Map<String, String> properties = new HashMap<>();
        private final ChangeSupport cs = new ChangeSupport(this);
        
        @Override
        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public void addChangeListener(ChangeListener l) {
            cs.addChangeListener(l);
        }

        @Override
        public void removeChangeListener(ChangeListener l) {
            cs.removeChangeListener(l);
        }

        public void setProperty(String key, String value) {
            properties.put(key, value);
            cs.fireChange();
        }

    }
}
