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
package org.netbeans.modules.jdk.jtreg;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.java.hints.ErrorDescriptionFactory;
import org.netbeans.spi.java.hints.Hint;
import org.netbeans.spi.java.hints.HintContext;
import org.netbeans.spi.java.hints.TriggerTreeKind;
import org.netbeans.spi.project.SubprojectProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Pair;
import org.openide.util.TopologicalSortException;

@Hint(displayName = "#DN_ModulesHint", description = "#DESC_ModulesHint", category = "general")
@Messages({
    "DN_ModulesHint=Incorrect @modules tag",
    "DESC_ModulesHint=Incorrect @modules tag"
})
public class ModulesHint {

    private static final List<String> HEADER_TAGS_IN_ORDER = Arrays.asList("test", "bug", "summary", "library", "modules");

    @TriggerTreeKind(Kind.COMPILATION_UNIT)
    @Messages("ERR_ModulesHint=Incorrect @modules tag")
    public static ErrorDescription computeWarning(final HintContext ctx) {
        Pair<Fix, int[]> fix = computeChange(ctx.getInfo());
        if (fix == null)
            return null;
        ErrorDescription idealED = ErrorDescriptionFactory.forName(ctx, ctx.getPath(), Bundle.ERR_ModulesHint(), fix.first());
        return org.netbeans.spi.editor.hints.ErrorDescriptionFactory.createErrorDescription(idealED.getSeverity(), idealED.getDescription(), idealED.getFixes(), ctx.getInfo().getFileObject(), fix.second()[0], fix.second()[1]);
    }

    static Pair<Fix, int[]> computeChange(CompilationInfo info) {
        Map<String, List<Tag>> tags = TagParser.parseTags(info);

        if (!tags.containsKey("test"))
            return null; //TODO: test

        Pair<Map<String, Set<String>>, Set<Project>> computeModulesAndPackagesAndProjects = computeModulesAndPackages(info, tags);
        Map<String, Set<String>> projectDependencies = projectDependencies(computeModulesAndPackagesAndProjects.second());
        Map<String, Set<String>> expected = normalize(computeModulesAndPackagesAndProjects.first(), projectDependencies);
        Map<String,Set<String>> actual = normalize(readModulesAndPackages(tags).first(), projectDependencies);

        if (Objects.equals(expected, actual))
            return null;

        List<Tag> markTag = tags.get("modules");

        if (markTag == null || markTag.isEmpty()) {
            markTag = tags.get("test");
        }

        return Pair.<Fix, int[]>of(new FixImpl(info.getFileObject(), expected), new int[] {markTag.get(0).getTagStart(), markTag.get(0).getTagEnd()});
    }

    private static Map<String, Set<String>> projectDependencies(Set<Project> projects) {
        Map<String, Set<String>> project2DirectDependencies = new HashMap<>();
        List<Project> todoList = new LinkedList<>(projects);

        while (!todoList.isEmpty()) {
            Project prj = todoList.remove(0);
            String projectName = prj.getProjectDirectory().getNameExt();

            if (project2DirectDependencies.containsKey(projectName))
                continue;

            SubprojectProvider subProject = prj.getLookup().lookup(SubprojectProvider.class);

            if (subProject == null) {
                project2DirectDependencies.put(projectName, Collections.<String>emptySet());
                continue;
            }

            Set<String> deps = new HashSet<>();

            for (Project dep : subProject.getSubprojects()) {
                if (Objects.equals(projectName, dep.getProjectDirectory().getNameExt())) //XXX - should be fixed in the provider
                    continue;
                todoList.add(dep);
                deps.add(dep.getProjectDirectory().getNameExt());
            }

            project2DirectDependencies.put(projectName, deps);
        }

        try {
            List<String> sortedProjects = org.openide.util.Utilities.topologicalSort(project2DirectDependencies.keySet(), project2DirectDependencies);
            Map<String, Set<String>> project2AllDependencies = new HashMap<>();

            Collections.reverse(sortedProjects);

            for (String prj : sortedProjects) {
                Set<String> dependencies = new HashSet<>();

                for (String dep : project2DirectDependencies.get(prj)) {
                    dependencies.add(dep);
                    dependencies.addAll(project2AllDependencies.get(dep));
                }

                project2AllDependencies.put(prj, dependencies);
            }

            return project2AllDependencies;
        } catch (TopologicalSortException ex) {
            Exceptions.printStackTrace(ex);
            return Collections.emptyMap();
        }
    }

    private static Map<String, Set<String>> normalize(Map<String, Set<String>> modulesAndPackages, Map<String, Set<String>> projectDependencies) {
        for (Map.Entry<String, Set<String>> e : projectDependencies.entrySet()) {
            if (modulesAndPackages.containsKey(e.getKey())) {
                for (String dep : e.getValue()) {
                    Set<String> depPackages = modulesAndPackages.get(dep);
                    if (depPackages != null && depPackages.isEmpty()) {
                        modulesAndPackages.remove(dep);
                    }
                }
            }
        }
        Set<String> javaBasePackages = modulesAndPackages.get("java.base");
        if (javaBasePackages != null && javaBasePackages.isEmpty()) {
            modulesAndPackages.remove("java.base");
        }
        return modulesAndPackages;
    }

    static Pair<Map<String, Set<String>>, Set<Project>> computeModulesAndPackages(final CompilationInfo info, Map<String, List<Tag>> tags) {
        Map<String, Set<String>> module2UsedUnexportedPackages = new HashMap<>();
        Set<TypeElement> seenClasses = new HashSet<>();
        Set<Project> seenProjects = new HashSet<>();
        Set<CompilationUnitTree> seen = new HashSet<>();

        computeModulesAndPackages(info, info.getCompilationUnit(), module2UsedUnexportedPackages, seenClasses, seenProjects, seen);

        List<Tag> buildTags = tags.get("build");

        if (buildTags != null) {
            for (Tag buildTag : buildTags) {
                String[] classNames = buildTag.getValue().split("\\s+");

                for (String className : classNames) {
                    TypeElement built = info.getElements().getTypeElement(className);

                    if (built == null)
                        continue;

                    TreePath builtPath = info.getTrees().getPath(built);

                    if (builtPath == null) //XXX: can do something?
                        continue;

                    computeModulesAndPackages(info, builtPath.getCompilationUnit(), module2UsedUnexportedPackages, seenClasses, seenProjects, seen);
                }
            }
        }

        return Pair.of(module2UsedUnexportedPackages, seenProjects);
    }

    static void computeModulesAndPackages(final CompilationInfo info,
                                          CompilationUnitTree cut,
                                          Map<String, Set<String>> module2UsedUnexportedPackages,
                                          final Set<TypeElement> seenClasses,
                                          Set<Project> seenProjects,
                                          Set<CompilationUnitTree> seen) {
        if (!seen.add(cut))
            return ;

        final Set<TypeElement> classes = new HashSet<>();

        new TreePathScanner<Void, Void>() {
            @Override
            public Void scan(Tree tree, Void p) {
                if (tree == null)
                    return null;

                Element el = info.getTrees().getElement(new TreePath(getCurrentPath(), tree));

                if (el != null && el.getKind() != ElementKind.PACKAGE) {
                    TypeElement outermost = info.getElementUtilities().outermostTypeElement(el);
                    if (outermost != null) {
                        if (seenClasses.add(outermost)) {
                            classes.add(outermost);
                        }
                    } else ;//XXX: array .length!
                }

                return super.scan(tree, p);
            }
        }.scan(cut, null);

        Map<FileObject, Set<String>> module2Packages = new HashMap<>();

        for (TypeElement outtermost : classes) {
            FileObject file = SourceUtils.getFile(ElementHandle.create(outtermost), info.getClasspathInfo());
            if (file == null) {
                continue;
            }

            Project prj = FileOwnerQuery.getOwner(file);

            if (prj != null && /*XXX*/FileUtil.isParentOf(prj.getProjectDirectory(), file)) {
                FileObject prjDir = prj.getProjectDirectory();
                Set<String> currentModulePackages = module2Packages.get(prjDir);

                if (currentModulePackages == null) {
                    module2Packages.put(prjDir, currentModulePackages = new HashSet<>());
                }

                currentModulePackages.add(info.getElements().getPackageOf(outtermost).getQualifiedName().toString());

                seenProjects.add(prj);
            } else {
                TreePath tp = info.getTrees().getPath(outtermost);

                if (tp != null) {
                    computeModulesAndPackages(info, tp.getCompilationUnit(), module2UsedUnexportedPackages, seenClasses, seenProjects, seen);
                }
            }
        }

        for (Map.Entry<FileObject, Set<String>> e : module2Packages.entrySet()) {
            FileObject moduleInfo = e.getKey().getFileObject("share/classes/module-info.java");
            if (moduleInfo == null) { //XXX
                continue;
            }
            Set<String> exported = readExports(moduleInfo);

            for (Iterator<String> it = e.getValue().iterator(); it.hasNext();) {
                String pack = it.next();

                if (exported.contains(pack)) {
                    it.remove();
                }
            }

            String moduleName = e.getKey().getNameExt();
            Set<String> currentUnexportedpackages = module2UsedUnexportedPackages.get(moduleName);

            if (currentUnexportedpackages == null) {
                module2UsedUnexportedPackages.put(moduleName, currentUnexportedpackages = new HashSet<>());
            }

            currentUnexportedpackages.addAll(e.getValue());
        }
    }

    static Pair<Map<String, Set<String>>, int[]> readModulesAndPackages(Map<String, List<Tag>> tags) {
        List<Tag> modulesTags = tags.get("modules");

        if (modulesTags == null || modulesTags.isEmpty())
            return Pair.of(Collections.<String, Set<String>>emptyMap(), new int[] {-1, -1});

        Tag modules = modulesTags.get(0);
        String textModules = modules.getValue().replaceAll("\\s+", " ").trim();
        String[] parts = textModules.split("\\s");
        Map<String, Set<String>> module2UsedUnexportedPackages = new HashMap<>();

        for (String part : parts) {
            String[] moduleAndPackage = part.split("/");

            Set<String> packages = module2UsedUnexportedPackages.get(moduleAndPackage[0]);

            if (packages == null) {
                module2UsedUnexportedPackages.put(moduleAndPackage[0], packages = new HashSet<>());
            }

            if (moduleAndPackage.length > 1)
                packages.add(moduleAndPackage[1]);
        }

        return Pair.of(module2UsedUnexportedPackages, new int[] {modules.getStart(), modules.getEnd()});
    }

    private static final Pattern EXPORTS = Pattern.compile("exports\\s+(([a-zA-Z0-9]+\\.)*[a-zA-Z0-9]+)(?<to>\\s+to)?");

    private static Set<String> readExports(FileObject moduleInfo) {
        Set<String> exported = new HashSet<>();

        try (Reader r = new InputStreamReader(moduleInfo.getInputStream())) {
            StringBuilder content = new StringBuilder();
            int read;

            while ((read = r.read()) != (-1)) {
                content.append((char) read);
            }

            Matcher exportsMatcher = EXPORTS.matcher(content);

            while (exportsMatcher.find()) {
                if (exportsMatcher.group("to") != null) //Ignore qualified exports. How about -Xmodule in @compile?
                    continue;

                String pack = exportsMatcher.group(1);

                exported.add(pack);
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        return exported;
    }

    private static final class FixImpl implements Fix {

        private final FileObject file;
        private final Map<String, Set<String>> expected;

        private FixImpl(FileObject file, Map<String, Set<String>> expected) {
            this.file = file;
            this.expected = expected;
        }

        @Override
        @Messages("FIX_ModulesHint=Fix @modules tag")
        public String getText() {
            return Bundle.FIX_ModulesHint();
        }

        @Override
        public ChangeInfo implement() throws Exception {
            final Document doc = Source.create(file).getDocument(false);

            if (doc == null) {
                return null;
            }

            final List<String> records = new ArrayList<>();

            for (Map.Entry<String, Set<String>> e : expected.entrySet()) {
                if (e.getValue().isEmpty()) {
                    records.add(e.getKey());
                } else {
                    for (String pack : e.getValue()) {
                        records.add(e.getKey() + "/" + pack);
                    }
                }
            }

            Collections.sort(records);

            final StringBuilder atModules = new StringBuilder();

            atModules.append(" * "); //XXX
            atModules.append("@modules ");

            boolean first = true;

            for (String r : records) {
                if (!first) {
                    atModules.append(" * "); //XXX
                    atModules.append("         ");
                }
                first = false;
                atModules.append(r).append("\n");
            }

            NbDocument.runAtomic((StyledDocument) doc, new Runnable() {
                @Override
                public void run() {
                    try {
                    Map<String, List<Tag>> tags = TagParser.parseTags(doc);
                    int[] span = readModulesAndPackages(tags).second();
                    int pos;

                    if (span[0] != (-1)) {
                        doc.remove(span[0], span[1] - span[0]);
                        pos = span[0];
                    } else {
                        Map<Integer, Tag> end2Tag = new TreeMap<>();

                        for (List<Tag> tagsForName : tags.values()) {
                            for (Tag tag : tagsForName) {
                                end2Tag.put(tag.getEnd(), tag);
                            }
                        }

                        int modulesIndex = HEADER_TAGS_IN_ORDER.indexOf("modules");
                        pos = -1;

                        for (Map.Entry<Integer, Tag> e : end2Tag.entrySet()) {
                            int currentIndex = HEADER_TAGS_IN_ORDER.indexOf(e.getValue().getName());

                            if (currentIndex == (-1) || currentIndex > modulesIndex)
                                break;

                            pos = e.getKey();
                        }
                    }

                    if (!records.isEmpty())
                        doc.insertString(pos, atModules.toString(), null);

                    } catch (BadLocationException ble) {
                        throw new IllegalStateException(ble);
                    }
                }
            });

            return null;
        }
    }
}
