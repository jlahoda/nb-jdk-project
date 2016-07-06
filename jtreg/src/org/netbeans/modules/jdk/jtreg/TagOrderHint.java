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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreePath;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.modules.jdk.jtreg.TagParser.Result;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.java.hints.ErrorDescriptionFactory;
import org.netbeans.spi.java.hints.Hint;
import org.netbeans.spi.java.hints.HintContext;
import org.netbeans.spi.java.hints.JavaFix;
import org.netbeans.spi.java.hints.TriggerTreeKind;
import org.openide.util.NbBundle.Messages;

@Hint(displayName = "#DN_TagOrderHint", description = "#DESC_TagOrderHint", category = "general")
@Messages({
    "DN_TagOrderHint=Tag Order",
    "DESC_TagOrderHint=Checks jtreg tag order"
})
public class TagOrderHint {

    @TriggerTreeKind(Kind.COMPILATION_UNIT)
    @Messages("ERR_TagOrderHint=Incorrect tag order")
    public static ErrorDescription computeWarning(HintContext ctx) {
        Result tags = TagParser.parseTags(ctx.getInfo());
        List<Tag> sorted = sortTags(tags);

        if (!tags.getTags().equals(sorted)) {
            ErrorDescription idealED = ErrorDescriptionFactory.forName(ctx, ctx.getPath(), Bundle.ERR_ModulesHint(), new FixImpl(ctx.getInfo(), ctx.getPath()).toEditorFix());
            List<Tag> test = tags.getName2Tag().get("test");

            return org.netbeans.spi.editor.hints.ErrorDescriptionFactory.createErrorDescription(idealED.getSeverity(), idealED.getDescription(), idealED.getFixes(), ctx.getInfo().getFileObject(), test.get(0).getTagStart(), test.get(0).getTagEnd());
        }

        return null;
    }

    private static List<Tag> sortTags(Result tags) {
        List<Tag> sorted = new ArrayList<>(tags.getTags());
        Collections.sort(sorted, new Comparator<Tag>() {
            @Override public int compare(Tag o1, Tag o2) {
                int pos1 = TagParser.RECOMMENDED_TAGS_ORDER.indexOf(o1.getName());
                int pos2 = TagParser.RECOMMENDED_TAGS_ORDER.indexOf(o2.getName());

                if (pos1 < 0) pos1 = Integer.MAX_VALUE;
                if (pos2 < 0) pos2 = Integer.MAX_VALUE;

                return pos1 - pos2;
            }
        });
        return sorted;
    }

    private static final class FixImpl extends JavaFix {

        public FixImpl(CompilationInfo info, TreePath tp) {
            super(info, tp);
        }

        @Override
        @Messages("FIX_TagOrderHint=Fix tag order")
        protected String getText() {
            return Bundle.FIX_TagOrderHint();
        }

        @Override
        protected void performRewrite(TransformationContext ctx) {
            List<Tag> sorted = sortTags(TagParser.parseTags(ctx.getWorkingCopy()));

            StringBuilder newText = new StringBuilder();
            int min = Integer.MAX_VALUE;
            int max = 0;

            for (Tag t : sorted) {
                min = Math.min(min, t.getStart());
                max = Math.max(max, t.getEnd());

                newText.append(ctx.getWorkingCopy().getText().substring(t.getStart(), t.getEnd()));
            }

            ctx.getWorkingCopy().rewriteInComment(min, max - min, newText.toString());
        }
    }
}
