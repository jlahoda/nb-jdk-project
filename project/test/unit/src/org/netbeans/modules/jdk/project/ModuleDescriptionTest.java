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
package org.netbeans.modules.jdk.project;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.netbeans.modules.jdk.project.ModuleDescription.Dependency;

/**
 *
 * @author lahvac
 */
public class ModuleDescriptionTest {

    @Test
    public void testModuleInfoParsing() throws IOException {
        ModuleDescription d;

        d = ModuleDescription.parseModuleInfo(new StringReader("/* module wrong { requires wrong; } */ module right { requires right2; }"));

        assertEquals(d, new ModuleDescription("right", Arrays.asList(new Dependency("java.base", false, false), new Dependency("right2", false, false)), Collections.<String, List<String>>emptyMap()));

        d = ModuleDescription.parseModuleInfo(new StringReader("/* module wrong { requires wrong; } */ module right { requires public right2; }"));

        assertEquals(d, new ModuleDescription("right", Arrays.asList(new Dependency("java.base", false, false), new Dependency("right2", true, false)), Collections.<String, List<String>>emptyMap()));

        d = ModuleDescription.parseModuleInfo(new StringReader("/* module wrong { requires wrong; } */ module right { requires static right2; }"));

        assertEquals(d, new ModuleDescription("right", Arrays.asList(new Dependency("java.base", false, false), new Dependency("right2", false, true)), Collections.<String, List<String>>emptyMap()));

        d = ModuleDescription.parseModuleInfo(new StringReader("/* module wrong { requires wrong; } */ module right { requires public static right2; }"));

        assertEquals(d, new ModuleDescription("right", Arrays.asList(new Dependency("java.base", false, false), new Dependency("right2", true, true)), Collections.<String, List<String>>emptyMap()));

        d = ModuleDescription.parseModuleInfo(new StringReader("/* module wrong { requires wrong; } */ module right { requires java.base; }"));

        assertEquals(d, new ModuleDescription("right", Arrays.asList(new Dependency("java.base", false, false)), Collections.<String, List<String>>emptyMap()));

        d = ModuleDescription.parseModuleInfo(new StringReader("/* module wrong { requires wrong; } */ module right { requires right2 ; }"));

        assertEquals(d, new ModuleDescription("right", Arrays.asList(new Dependency("java.base", false, false), new Dependency("right2", false, false)), Collections.<String, List<String>>emptyMap()));

        d = ModuleDescription.parseModuleInfo(new StringReader("/* module wrong { requires wrong; } */ module\nright\n{\nrequires\nright2\n;\n}"));

        assertEquals(d, new ModuleDescription("right", Arrays.asList(new Dependency("java.base", false, false), new Dependency("right2", false, false)), Collections.<String, List<String>>emptyMap()));

        d = ModuleDescription.parseModuleInfo(new StringReader("/* module wrong { requires wrong; } */ module right { requires right.right; }"));

        assertEquals(d, new ModuleDescription("right", Arrays.asList(new Dependency("java.base", false, false), new Dependency("right.right", false, false)), Collections.<String, List<String>>emptyMap()));
    }

    private static void assertEquals(ModuleDescription d1, ModuleDescription d2) {
        Assert.assertEquals(d1.name, d2.name);

        Iterator<Dependency> d1Req = d1.depend.iterator();
        Iterator<Dependency> d2Req = d2.depend.iterator();

        while (d1Req.hasNext() && d2Req.hasNext()) {
            assertEquals(d1Req.next(), d2Req.next());
        }

        Assert.assertFalse(d1Req.hasNext());
        Assert.assertFalse(d2Req.hasNext());

        Assert.assertEquals(d1.exports, d2.exports);
    }

    private static void assertEquals(Dependency d1, Dependency d2) {
        Assert.assertEquals(d1.moduleName, d2.moduleName);
        Assert.assertEquals(d1.requiresPublic, d2.requiresPublic);
        Assert.assertEquals(d1.requiresStatic, d2.requiresStatic);
    }
}
