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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.netbeans.api.project.Project;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.jdk.project.ConfigurationImpl.ProviderImpl;
import org.netbeans.spi.project.AuxiliaryProperties;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem.AtomicAction;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class ConfigurationImplTest extends NbTestCase {

    public ConfigurationImplTest(String testName) {
        super(testName);
    }

    public void testModularized() throws Exception {
        doTestConfigurationChanges("jdk/src/java.base");
    }

    public void testLegacy() throws Exception {
        doTestConfigurationChanges("jdk");
    }

    private void doTestConfigurationChanges(String jdkProjectDir) throws Exception {
        clearWorkDir();

        File jdkRoot = getWorkDir();
        final File buildDir = new File(jdkRoot, "build");
        FileObject jdkRootFO = FileUtil.toFileObject(jdkRoot);
        FileObject jdkProject = FileUtil.createFolder(jdkRootFO, jdkProjectDir);

        dir2Project.put(jdkProject, new TestProject(jdkProject));

        try {
            ProviderImpl provider = new ProviderImpl(jdkRootFO, buildDir);

            assertConfigurations(provider, null);

            FileObject buildDirFO = FileUtil.createFolder(buildDir);
            FileObject conf1 = FileUtil.createData(buildDirFO, "conf1/Makefile").getParent();

            assertConfigurations(provider, "conf1", "conf1");

            FileObject conf0 = FileUtil.createData(buildDirFO, "conf0/Makefile").getParent();

            assertConfigurations(provider, "conf1", "conf0", "conf1");

            conf0.delete();

            assertConfigurations(provider, "conf1", "conf1");

            conf0 = FileUtil.createData(buildDirFO, "conf0/Makefile").getParent();

            assertConfigurations(provider, "conf1", "conf0", "conf1");

            conf1.delete();

            assertConfigurations(provider, "conf1", "conf0", "conf1");

            provider = new ProviderImpl(jdkRootFO, buildDir);

            assertConfigurations(provider, "conf1", "conf0", "conf1");

            buildDirFO.delete();

            //verify no listeners inside build directory:
            Class<?> fileChangeImpl = Class.forName("org.openide.filesystems.FileChangeImpl");
            Field holders = fileChangeImpl.getDeclaredField("holders");
            holders.setAccessible(true);
            Map listeningOn = (Map) ((Map) holders.get(null)).get(provider);
            assertTrue(listeningOn.toString(), listeningOn.size() == 1 && listeningOn.containsKey(buildDir));

            //create one-by-one:
            buildDirFO = FileUtil.createFolder(buildDir);

            FileObject conf2 = FileUtil.createFolder(buildDirFO, "conf2");

            assertConfigurations(provider, "conf1", "conf1");

            FileUtil.createData(conf2, "Makefile");

            assertConfigurations(provider, "conf1", "conf1", "conf2");
            
            buildDirFO.delete();

            //attempt to create all at once:
            FileUtil.runAtomicAction(new AtomicAction() {
                @Override
                public void run() throws IOException {
                    File conf3 = new File(new File(buildDir, "conf3"), "Makefile");

                    FileUtil.createData(conf3);
                }
            });

            assertConfigurations(provider, "conf1", "conf1", "conf3");
        } finally {
            dir2Project.remove(jdkProject);
        }
    }

    private void assertConfigurations(ProviderImpl configProvider, String goldenActive, String... goldenConfigurations) {
        ConfigurationImpl actualActive = configProvider.getActiveConfiguration();
        String actualActiveName = actualActive != null ? actualActive.getLocation().getName() : null;

        assertEquals(goldenActive, actualActiveName);

        List<String> actualConfigurations = new ArrayList<>();

        for (ConfigurationImpl config : configProvider.getConfigurations()) {
            actualConfigurations.add(config.getLocation().getName());
        }

        assertEquals(Arrays.asList(goldenConfigurations), actualConfigurations);
    }

    private static final Map<FileObject, Project> dir2Project = new HashMap<>();

    @ServiceProvider(service=ProjectFactory.class)
    public static final class HardcodedProjectFactory implements ProjectFactory {

        @Override
        public boolean isProject(FileObject projectDirectory) {
            return dir2Project.containsKey(projectDirectory);
        }

        @Override
        public Project loadProject(FileObject projectDirectory, ProjectState state) throws IOException {
            return dir2Project.get(projectDirectory);
        }

        @Override
        public void saveProject(Project project) throws IOException, ClassCastException {
        }

    }

    private static class TestProject implements Project {

        private final Lookup l = Lookups.singleton(new AuxiliaryProperties() {
            private final Properties propertiesShared = new Properties();
            private final Properties propertiesPrivate = new Properties();
            @Override
            public String get(String key, boolean shared) {
                return shared ? propertiesShared.getProperty(key)
                              : propertiesPrivate.getProperty(key);
            }

            @Override
            public void put(String key, String value, boolean shared) {
                if (shared) {
                    propertiesShared.setProperty(key, value);
                } else {
                    propertiesPrivate.setProperty(key, value);
                }
            }

            @Override
            public Iterable<String> listKeys(boolean shared) {
                return shared ? propertiesShared.stringPropertyNames()
                              : propertiesPrivate.stringPropertyNames();
            }

        });

        private final FileObject projectDir;

        public TestProject(FileObject projectDir) {
            this.projectDir = projectDir;
        }

        @Override
        public FileObject getProjectDirectory() {
            return projectDir;
        }

        @Override
        public Lookup getLookup() {
            return l;
        }
    }
}
