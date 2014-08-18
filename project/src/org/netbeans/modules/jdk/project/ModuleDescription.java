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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openide.filesystems.FileObject;
import org.openide.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 *
 * @author lahvac
 */
public class ModuleDescription {

    public final String name;
    public final List<String> depend;
    public final Map<String, List<String>> exports;

    public ModuleDescription(String name, List<String> depend, Map<String, List<String>> exports) {
        this.name = name;
        this.depend = depend;
        this.exports = exports;
    }

    @Override
    public String toString() {
        return "ModuleDescription{" + "name=" + name + ", depend=" + depend + ", exports=" + exports + '}';
    }

    private static final Map<FileObject, ModuleRepository> jdkRoot2Repository = new HashMap<>();

    public static ModuleRepository getModules(FileObject jdkRoot) throws Exception {
        ModuleRepository repository = jdkRoot2Repository.get(jdkRoot);

        if (repository != null)
            return repository;

        FileObject modulesXML = jdkRoot.getFileObject("modules.xml");

        if (modulesXML == null)
            return null;
        
        try (InputStream in = modulesXML.getInputStream()) {
            Document doc = XMLUtil.parse(new InputSource(in), false, true, null, null);
            NodeList modules = doc.getDocumentElement().getElementsByTagName("module");
            List<ModuleDescription> result = new ArrayList<>();

            for (int i = 0; i < modules.getLength(); i++) {
                result.add(parseModule((Element) modules.item(i)));
            }

            jdkRoot2Repository.put(jdkRoot, repository = new ModuleRepository(jdkRoot, result));

            return repository;
        }
    }

    private static ModuleDescription parseModule(Element moduleEl) {
        NodeList children = moduleEl.getChildNodes();
        String name = null;
        List<String> depend = new ArrayList<>();
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
                    depend.add(childEl.getTextContent());
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

                if (module != null && module.isFolder())
                    return module;
            }
            
            return null;
        }
    }
}
