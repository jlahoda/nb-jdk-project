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
package org.netbeans.modules.jdk.jtreg;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.project.libraries.Library;
import org.netbeans.api.project.libraries.LibraryManager;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.modules.jdk.project.common.api.ShortcutUtils;
import org.netbeans.spi.java.classpath.ClassPathProvider;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
@ServiceProvider(service=ClassPathProvider.class, position=9999)
public class ClassPathProviderImpl implements ClassPathProvider {

    @Override
    public ClassPath findClassPath(FileObject file, String type) {
        FileObject search = file.getParent();
        FileObject testProperties = null;

        while (search != null) {
            if (testProperties == null) {
                testProperties =  search.getFileObject("TEST.properties");
            }

            if (search.getName().equals("test") && Utilities.isJDKRepository(search.getParent())) {
                boolean javac = Utilities.isLangtoolsRepository(search.getParent()) &&
                                ShortcutUtils.getDefault().shouldUseCustomTest(search.getParent().getNameExt(), FileUtil.getRelativePath(search.getParent(), file));
                //XXX: hack to make things work for langtools:
                switch (type) {
                    case ClassPath.COMPILE:
                        if (javac) {
                            ClassPath langtoolsCP = ClassPath.getClassPath(Utilities.getLangtoolsKeyRoot(search.getParent()), ClassPath.COMPILE);
                            Library testngLib = LibraryManager.getDefault().getLibrary("testng");

                            if (testngLib != null) {
                                return ClassPathSupport.createProxyClassPath(ClassPathSupport.createClassPath(testngLib.getContent("classpath").toArray(new URL[0])),
                                                                             langtoolsCP);
                            }

                            if (langtoolsCP == null)
                                return ClassPath.EMPTY;
                            else
                                return langtoolsCP;
                        }
                        else return null;
                    case ClassPath.BOOT:
                        if (javac) {
                            try {
                                ClassPath langtoolsBCP = ClassPath.getClassPath(Utilities.getLangtoolsKeyRoot(search.getParent()), ClassPath.BOOT);
                                List<URL> roots = new ArrayList<>();
                                for (String rootPaths : new String[] {"build/classes/",
                                                                      "build/java.compiler/classes/",
                                                                      "build/jdk.compiler/classes/",
                                                                      "build/jdk.javadoc/classes/",
                                                                      "build/jdk.dev/classes/"}) {
                                    roots.add(search.getParent().toURI().resolve(rootPaths).toURL());
                                }
                                return ClassPathSupport.createProxyClassPath(ClassPathSupport.createClassPath(roots.toArray(new URL[roots.size()])), langtoolsBCP);
                            } catch (MalformedURLException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                        return null;
                    case ClassPath.SOURCE:
                        break;
                    default:
                        return null;
                }

                Set<FileObject> roots = new HashSet<>();

                if (testProperties != null) {
                    roots.add(testProperties.getParent());

                    try (InputStream in = testProperties.getInputStream()) {
                        Properties p = new Properties();
                        p.load(in);
                        String libDirsText = p.getProperty("lib.dirs");
                        FileObject libDirsRoot = libDirsText != null ? resolve(testProperties, search, libDirsText) : null;

                        if (libDirsRoot != null) roots.add(libDirsRoot);
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                } else {
                    if (file.isFolder()) return null;
                    
                    roots.add(file.getParent());
                    try (Reader r = new InputStreamReader(file.getInputStream(), FileEncodingQuery.getEncoding(file))) {
                        StringBuilder content = new StringBuilder();
                        int read;
                        
                        while ((read = r.read()) != (-1)) {
                            content.append((char) read);
                        }
                        
                        Pattern library = Pattern.compile("@library (.*)\n");
                        Matcher m = library.matcher(content.toString());

                        if (m.find()) {
                            String libraryPaths = m.group(1).trim();
                            for (String libraryPath : libraryPaths.split(" ")) {
                                FileObject libFO = resolve(file, search, libraryPath);

                                if (libFO != null) {
                                    roots.add(libFO);
                                }
                            }
                        }
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }

                //XXX:
                for (FileObject root : roots) {
                    initializeUsagesQuery(root);
                }

                return ClassPathSupport.createClassPath(roots.toArray(new FileObject[0]));
            }

            search = search.getParent();
        }
        
        return null;
    }

    private FileObject resolve(FileObject file, FileObject root, String spec) {
        if (spec.startsWith("/")) {
            return root.getFileObject(spec.substring(1));
        } else {
            return file.getParent().getFileObject(spec);
        }
    }

    private void initializeUsagesQuery(FileObject root) {
        try {
            ClassLoader cl = JavaSource.class.getClassLoader();
            Class<?> transactionContextClass = Class.forName("org.netbeans.modules.java.source.indexing.TransactionContext", false, cl);
            Class<?> serviceClass = Class.forName("org.netbeans.modules.java.source.indexing.TransactionContext$Service", false, cl);
            Method beginTrans = transactionContextClass.getDeclaredMethod("beginTrans");
            Method commit = transactionContextClass.getDeclaredMethod("commit");
            Method register = transactionContextClass.getDeclaredMethod("register", Class.class, serviceClass);
            Class<?> classIndexEventsTransactionClass = Class.forName("org.netbeans.modules.java.source.usages.ClassIndexEventsTransaction", false, cl);
            Method cietcCreate = classIndexEventsTransactionClass.getDeclaredMethod("create", boolean.class);
            Class<?> classIndexManagerClass = Class.forName("org.netbeans.modules.java.source.usages.ClassIndexManager", false, cl);
            Method cimcGetDefault = classIndexManagerClass.getDeclaredMethod("getDefault");
            Method createUsagesQuery = classIndexManagerClass.getDeclaredMethod("createUsagesQuery", URL.class, boolean.class);
            Class<?> classIndexImplClass = Class.forName("org.netbeans.modules.java.source.usages.ClassIndexImpl", false, cl);
            Class<?> stateClass = Class.forName("org.netbeans.modules.java.source.usages.ClassIndexImpl$State", false, cl);
            Method setState = classIndexImplClass.getDeclaredMethod("setState", stateClass);
            Field initialized = stateClass.getDeclaredField("INITIALIZED");

            Object transaction = beginTrans.invoke(null);
            register.invoke(transaction, classIndexEventsTransactionClass, cietcCreate.invoke(null, true));
            try {
                Object classIndexImpl = createUsagesQuery.invoke(cimcGetDefault.invoke(null), root.toURL(), true);
                setState.invoke(classIndexImpl, initialized.get(null));
            } finally {
                commit.invoke(transaction);
            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
