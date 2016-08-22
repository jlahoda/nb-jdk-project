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

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.swing.text.Document;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreePath;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.UiUtils;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;
import org.netbeans.modules.cnd.api.model.CsmFile;
import org.netbeans.modules.cnd.api.model.CsmFunction;
import org.netbeans.modules.cnd.api.model.CsmFunctionDefinition;
import org.netbeans.modules.cnd.api.model.CsmMethod;
import org.netbeans.modules.cnd.api.model.CsmModelAccessor;
import org.netbeans.modules.cnd.api.model.CsmOffsetableDeclaration;
import org.netbeans.modules.cnd.api.model.CsmProject;
import org.netbeans.modules.cnd.api.model.services.CsmCacheManager;
import org.netbeans.modules.cnd.api.project.NativeProject;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
@MimeRegistration(mimeType="text/x-java", position=0, service=HyperlinkProviderExt.class)
public class JavaToCHyperlinkProvider implements HyperlinkProviderExt {

    @Override
    public Set<HyperlinkType> getSupportedHyperlinkTypes() {
        return EnumSet.of(HyperlinkType.GO_TO_DECLARATION);
    }

    @Override
    public boolean isHyperlinkPoint(Document doc, int offset, HyperlinkType type) {
        return findNext().isHyperlinkPoint(doc, offset, type);
    }

    @Override
    public int[] getHyperlinkSpan(Document doc, int offset, HyperlinkType type) {
        return findNext().getHyperlinkSpan(doc, offset, type);
    }

    @Override
    public void performClickAction(Document doc, final int offset, HyperlinkType type) {
        FileObject file = NbEditorUtilities.getFileObject(doc);
        Project prj = file != null ? FileOwnerQuery.getOwner(file) : null;
        NativeProject np = prj != null ? prj.getLookup().lookup(NativeProject.class) : null;

        if (!(np instanceof NativeProjectImpl)) {
            findNext().performClickAction(doc, offset, type);
            return ;
        }

        final String[][] lookFor = new String[1][];

        try {
            JavaSource.forDocument(doc).runUserActionTask(new Task<CompilationController>() {
                @Override
                public void run(CompilationController parameter) throws Exception {
                    parameter.toPhase(Phase.PARSED);

                    TreePath tp = parameter.getTreeUtilities().pathFor(offset);

                    if (tp.getLeaf().getKind() != Kind.METHOD || !((MethodTree) tp.getLeaf()).getModifiers().getFlags().contains(Modifier.NATIVE))
                        return ;

                    int[] nameSpan = parameter.getTreeUtilities().findNameSpan((MethodTree) tp.getLeaf());

                    if (nameSpan[0] <= offset && offset <= nameSpan[1]) {
                        parameter.toPhase(Phase.RESOLVED);
                        Element el = parameter.getTrees().getElement(tp);
                        if (el != null && el.getKind() == ElementKind.METHOD) {
                            lookFor[0] = SourceUtils.getJVMSignature(ElementHandle.create(el));
                        }
                    }
                }
            }, true);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        if (lookFor[0] == null) {
            findNext().performClickAction(doc, offset, type);
            return ;
        }

        String mangledBase = "Java_" + mangle(lookFor[0][0]) + "_" + mangle(lookFor[0][1]);
        String mangledWithParameters = mangledBase + "__" + mangle(lookFor[0][2].replaceAll("\\((.*)\\).*", "$1"));

        FileObject fileToOpen = null;
        int targetOffset = -1;

        CsmCacheManager.enter();

        try {
            CsmProject csmProject = CsmModelAccessor.getModel().getProject(np);

            SEARCH: for (CsmFile csmFile : csmProject.getAllFiles()) {
                for (CsmOffsetableDeclaration decl : csmFile.getDeclarations()) {
                    if (decl instanceof CsmFunction) {
                        CsmFunction m = (CsmFunction) decl;
                        String name = m.getName().toString();

                        if (name.equals(mangledBase) || name.equals(mangledWithParameters)) {
                            //found:

                            fileToOpen = m.getContainingFile().getFileObject();
                            targetOffset = m.getStartOffset();

                            if (m instanceof CsmFunctionDefinition) {
                                break SEARCH;
                            }
                        }
                    }
                }
            }
        } finally {
            CsmCacheManager.leave();
        }

        if (fileToOpen != null) {
            UiUtils.open(fileToOpen, targetOffset);
        }
    }

    private String mangle(String original) {
        //no unicode mangling - is that needed?
        StringBuilder mangled = new StringBuilder();

        for (char c : original.toCharArray()) {
            switch (c) {
                case '.':
                case '/': mangled.append("_"); break;
                case '_': mangled.append("_1"); break;
                case ';': mangled.append("_2"); break;
                case '[': mangled.append("_3"); break;
                default: mangled.append(c); break;
            }
        }

        return mangled.toString();
    }

    @Override
    public String getTooltipText(Document doc, int offset, HyperlinkType type) {
        return findNext().getTooltipText(doc, offset, type);
    }

    private HyperlinkProviderExt findNext() {
        boolean seenMe = false;

        for (HyperlinkProviderExt p : MimeLookup.getLookup("text/x-java").lookupAll(HyperlinkProviderExt.class)) {
            if (seenMe) return p;
            seenMe = p == this;
        }

        return new HyperlinkProviderExt() {
            @Override public Set<HyperlinkType> getSupportedHyperlinkTypes() {
                return EnumSet.noneOf(HyperlinkType.class);
            }
            @Override public boolean isHyperlinkPoint(Document doc, int offset, HyperlinkType type) {
                return false;
            }
            @Override public int[] getHyperlinkSpan(Document doc, int offset, HyperlinkType type) {
                return new int[] {0, 0};
            }
            @Override public void performClickAction(Document doc, int offset, HyperlinkType type) {
            }
            @Override public String getTooltipText(Document doc, int offset, HyperlinkType type) {
                return null;
            }
        };
    }
}
