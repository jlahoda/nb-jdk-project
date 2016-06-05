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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.Document;

import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;

/**
 *
 * @author lahvac
 */
public class TagParser {

    public static final List<String> RECOMMENDED_TAGS_ORDER = Arrays.asList(
            "test", "bug", "summary", "library", "author", "modules", "requires", "key", "library", "modules"
    );

    private static final Pattern TAG_PATTERN = Pattern.compile("@([a-zA-Z]+)(\\s+|$)");

    public static Result parseTags(CompilationInfo info) {
        return parseTags(info.getTokenHierarchy().tokenSequence(JavaTokenId.language()));
    }

    public static Result parseTags(Document doc) {
        return parseTags(TokenHierarchy.get(doc).tokenSequence(JavaTokenId.language()));
    }

    private static Result parseTags(TokenSequence<JavaTokenId> ts) {
        while (ts.moveNext()) {
            if (ts.token().id() == JavaTokenId.BLOCK_COMMENT || ts.token().id() == JavaTokenId.JAVADOC_COMMENT) {
                String text = ts.token().text().toString();

                if (text.contains("@test")) {
                    List<Tag> tags = new ArrayList<>();
                    int start = -1;
                    int end = -1;
                    int tagStart = -1;
                    int tagEnd = -1;

                    text = text.substring(0, text.length() - 2);

                    String tagName = null;
                    StringBuilder tagText = new StringBuilder();
                    int prefix = ts.token().id() == JavaTokenId.BLOCK_COMMENT ? 2 : 3;
                    String[] lines = text.substring(prefix).split("\n");
                    int pos = ts.offset() + prefix;

                    for (String line : lines) {
                        if (line.replaceAll("[*\\s]+", "").isEmpty()) {
                            pos += line.length() + 1;
                            continue;
                        }
                        Matcher m = TAG_PATTERN.matcher(line);
                        if (m.find()) {
                            if (tagName != null) {
                                tags.add(new Tag(start, pos, tagStart, tagEnd, tagName, tagText.toString()));
                                tagText.delete(0, tagText.length());
                            }

                            tagName = m.group(1);

                            start = pos;
                            tagStart = pos + m.start();
                            tagEnd = pos + m.end(1);
                            tagText.append(line.substring(m.end()));
                        } else if (tagName != null) {
                            int asterisk = line.indexOf('*');
                            tagText.append(line.substring(asterisk + 1));
                        }

                        pos += line.length() + 1;

                        if (tagName != null) {
                            end = pos;
                        }
                    }

                    if (tagName != null) {
                        tags.add(new Tag(start, end, tagStart, tagEnd, tagName, tagText.toString()));
                    }

                    Map<String, List<Tag>> result = new HashMap<>();

                    for (Tag tag : tags) {
                        List<Tag> innerTags = result.get(tag.getName());

                        if (innerTags == null) {
                            result.put(tag.getName(), innerTags = new ArrayList<>());
                        }

                        innerTags.add(tag);
                    }

                    return new Result(tags, result);
                }
            }
        }

        return new Result(Collections.<Tag>emptyList(), Collections.<String, List<Tag>>emptyMap());
    }

    public static final class Result {
        private final List<Tag> tags;
        private final Map<String, List<Tag>> name2Tag;

        public Result(List<Tag> tags, Map<String, List<Tag>> name2Tag) {
            this.tags = tags;
            this.name2Tag = name2Tag;
        }

        public List<Tag> getTags() {
            return tags;
        }

        public Map<String, List<Tag>> getName2Tag() {
            return name2Tag;
        }
        
    }
}
