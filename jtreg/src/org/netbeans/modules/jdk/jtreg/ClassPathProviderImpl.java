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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.spi.java.classpath.ClassPathProvider;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
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
        if (!ClassPath.SOURCE.equals(type)) return null;

        FileObject search = file.getParent();
        FileObject testProperties = null;

        while (search != null) {
            if (testProperties == null) {
                testProperties =  search.getFileObject("TEST.properties");
            }

            if (search.getName().equals("test") && search.getFileObject("../src/share/classes") != null) {
                Set<FileObject> roots = new HashSet<>();

                if (testProperties != null) {
                    roots.add(testProperties.getParent());

                    try (InputStream in = testProperties.getInputStream()) {
                        Properties p = new Properties();
                        p.load(in);
                        String libDirsText = p.getProperty("lib.dirs");
                        FileObject libDirsRoot = libDirsText != null ? resolve(file, search, libDirsText) : null;

                        if (libDirsRoot != null) roots.add(libDirsRoot);
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                } else {
                    roots.add(file.getParent());
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
