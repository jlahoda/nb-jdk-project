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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.openide.filesystems.FileObject;
import org.openide.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author lahvac
 */
public class ModuleDescription {

    public final String name;
    public final List<Dependency> depend;
    public final Map<String, List<String>> exports;

    public ModuleDescription(String name, List<Dependency> depend, Map<String, List<String>> exports) {
        this.name = name;
        this.depend = depend;
        this.exports = exports;
    }

    @Override
    public String toString() {
        return "ModuleDescription{" + "name=" + name + ", depend=" + depend + ", exports=" + exports + '}';
    }

    private static final Map<FileObject, ModuleRepository> jdkRoot2Repository = new HashMap<>();

    public static ModuleRepository getModules(FileObject project) throws Exception {
        FileObject jdkRoot = findJDKRoot(project);

        if (jdkRoot == null)
            return null;

        ModuleRepository repository = jdkRoot2Repository.get(jdkRoot);

        if (repository != null)
            return repository;

        List<ModuleDescription> moduleDescriptions;
        FileObject modulesXML = jdkRoot.getFileObject("modules.xml");

        if (modulesXML != null) {
            moduleDescriptions = new ArrayList<>();
            readModulesXml(modulesXML, moduleDescriptions);
            readModulesXml(jdkRoot.getFileObject("closed/modules.xml"), moduleDescriptions);
        } else {
            moduleDescriptions = readModuleInfos(jdkRoot);
        }

        if (moduleDescriptions.isEmpty())
            return null;
        
        jdkRoot2Repository.put(jdkRoot, repository = new ModuleRepository(jdkRoot, moduleDescriptions));

        return repository;
    }

    private static FileObject findJDKRoot(FileObject projectDirectory) {
        if (projectDirectory.getFileObject("../../../modules.xml") != null || projectDirectory.getFileObject("../../../jdk/src/java.base/share/classes/module-info.java") != null)
            return projectDirectory.getFileObject("../../..");
        if (projectDirectory.getFileObject("../../../../modules.xml") != null || projectDirectory.getFileObject("../../../../jdk/src/java.base/share/classes/module-info.java") != null)
            return projectDirectory.getFileObject("../../../..");

        return null;
    }

    private static void readModulesXml(FileObject modulesXML, List<ModuleDescription> moduleDescriptions) throws SAXException, IOException {
        if (modulesXML == null)
            return ;

        try (InputStream in = modulesXML.getInputStream()) {
            Document doc = XMLUtil.parse(new InputSource(in), false, true, null, null);
            NodeList modules = doc.getDocumentElement().getElementsByTagName("module");

            for (int i = 0; i < modules.getLength(); i++) {
                moduleDescriptions.add(parseModule((Element) modules.item(i)));
            }
        }
    }

    private static ModuleDescription parseModule(Element moduleEl) {
        NodeList children = moduleEl.getChildNodes();
        String name = null;
        List<Dependency> depend = new ArrayList<>();
        Map<String, List<String>> exports = new HashMap<>();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() != Node.ELEMENT_NODE)
                continue;

            Element childEl = (Element) child;

            switch (childEl.getLocalName()) {
                case "name":
                    name = childEl.getTextContent();
                    break;
                case "depend":
                    depend.add(new Dependency(childEl.getTextContent(), "true".equals(childEl.getAttribute("re-exports")), false));
                    break;
                case "export":
                    String exported = null;
                    List<String> exportedTo = null;
                    NodeList exportChildren = childEl.getChildNodes();

                    for (int j = 0; j < exportChildren.getLength(); j++) {
                        Node exportChild = exportChildren.item(j);

                        if (exportChild.getNodeType() != Node.ELEMENT_NODE) continue;
                        
                        switch (exportChild.getLocalName()) {
                            case "name":
                                exported = exportChild.getTextContent();
                                break;
                            case "to":
                                if (exportedTo == null) exportedTo = new ArrayList<>();
                                exportedTo.add(exportChild.getTextContent());
                                break;
                        }
                    }

                    exports.put(exported, exportedTo != null ? Collections.unmodifiableList(exportedTo) : null);
                    break;
            }
        }

        return new ModuleDescription(name, Collections.unmodifiableList(depend), Collections.unmodifiableMap(exports));
    }

    private static List<ModuleDescription> readModuleInfos(FileObject jdkRoot) throws Exception {
        List<ModuleDescription> result = new ArrayList<>();
        Enumeration<? extends FileObject> childEn = jdkRoot.getChildren(true);

        while (childEn.hasMoreElements()) {
            FileObject f = childEn.nextElement();

            if ("module-info.java".equals(f.getNameExt())) {
                ModuleDescription module = parseModuleInfo(f);

                if (module != null) {
                    result.add(module);
                }
            }
        }

        return result;
    }

    private static final Pattern MODULE = Pattern.compile("module\\s+(?<modulename>([a-zA-Z0-9]+\\.)*[a-zA-Z0-9]+)");
    private static final Pattern REQUIRES = Pattern.compile("requires\\s+(?<flags>(transitive\\s+|public\\s+|static\\s+)*)(?<dependency>([a-zA-Z0-9]+\\.)*[a-zA-Z0-9]+)\\s*;");
    private static ModuleDescription parseModuleInfo(FileObject f) throws IOException {
        try (Reader r = new InputStreamReader(f.getInputStream())) {
            ModuleDescription desc = parseModuleInfo(r);

            if (desc == null || !desc.name.equals(f.getFileObject("../../..").getNameExt()))
                return null;

            return desc;
        }
    }

    static ModuleDescription parseModuleInfo(Reader r) throws IOException {
        TokenHierarchy<Reader> th = TokenHierarchy.create(r,
                                                          JavaTokenId.language(),
                                                          EnumSet.of(JavaTokenId.BLOCK_COMMENT, JavaTokenId.ERROR,
                                                                     JavaTokenId.INVALID_COMMENT_END, JavaTokenId.JAVADOC_COMMENT,
                                                                     JavaTokenId.LINE_COMMENT, JavaTokenId.STRING_LITERAL),
                                                          new InputAttributes());
        TokenSequence<JavaTokenId> ts = th.tokenSequence(JavaTokenId.language());

        ts.moveStart();

        StringBuilder content = new StringBuilder();

        while (ts.moveNext()) {
            if (ts.token().id() == JavaTokenId.WHITESPACE) {
                content.append(' ');
            } else {
                content.append(ts.token().text());
            }
        }

        Matcher moduleMatcher = MODULE.matcher(content);

        if (!moduleMatcher.find())
            return null;

        String moduleName = moduleMatcher.group("modulename");

        List<Dependency> depends = new ArrayList<>();
        boolean hasJavaBaseDependency = false;
        Matcher requiresMatcher = REQUIRES.matcher(content);

        while (requiresMatcher.find()) {
            String depName = requiresMatcher.group("dependency");
            boolean isPublic = false;
            boolean isStatic = false;
            String flags = requiresMatcher.group("flags");

            if (flags != null) {
                isPublic = flags.contains("transitive") || flags.contains("public");
                isStatic = flags.contains("static");
            }

            depends.add(new Dependency(depName, isPublic, isStatic));

            hasJavaBaseDependency |= depName.equals("java.base");
        }

        if (!hasJavaBaseDependency)
            depends.listIterator().add(new Dependency("java.base", false, false));

        return new ModuleDescription(moduleName, depends, Collections.<String, List<String>>emptyMap());
    }

    public static class ModuleRepository {
        private final FileObject root;
        public final List<ModuleDescription> modules;

        private ModuleRepository(FileObject root, List<ModuleDescription> modules) {
            this.root = root;
            this.modules = modules;
        }

        public ModuleDescription findModule(String moduleName) {
            for (ModuleDescription md : modules) {
                if (md.name.equals(moduleName))
                    return md;
            }

            return null;
        }

        public FileObject findModuleRoot(String moduleName) {
            for (FileObject repo : root.getChildren()) {
                if ("java.base".equals(moduleName) && !repo.getName().equals("jdk"))
                    continue;
                if ("jdk.compiler".equals(moduleName) && !repo.getName().equals("langtools"))
                    continue;
                if ("jdk.dev".equals(moduleName) && !repo.getName().equals("langtools"))
                    continue;
                FileObject module = repo.getFileObject("src/" + moduleName);

                if (module == null)
                    module = repo.getFileObject("src/closed/" + moduleName);

                if (module != null && module.isFolder())
                    return module;
            }
            
            return null;
        }

        public Collection<String> allDependencies(ModuleDescription module) {
            Set<String> result = new LinkedHashSet<>();

            allDependencies(module, result, false);

            return result;
        }

        private void allDependencies(ModuleDescription module, Set<String> result, boolean transitiveOnly) {
            for (Dependency dep : module.depend) {
                if (transitiveOnly && !dep.requiresPublic)
                    continue;

                ModuleDescription md = findModule(dep.moduleName);

                if (md == null) {
                    //XXX
                } else {
                    allDependencies(md, result, true);
                }

                result.add(dep.moduleName);
            }
        }
    }

    public static final class Dependency {
        public final String moduleName;
        public final boolean requiresPublic;
        public final boolean requiresStatic;

        public Dependency(String moduleName, boolean requiresPublic, boolean requiresStatic) {
            this.moduleName = moduleName;
            this.requiresPublic = requiresPublic;
            this.requiresStatic = requiresStatic;
        }

    }
}
