//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.thundra.plugin.maven.test.instrumentation.checker;

import org.apache.logging.log4j.Logger;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

public interface Checker {
    void checkProfiles(Logger var1, MavenXpp3Reader var2, String var3, String var4, Boolean var5);

    void checkPom(Logger var1, MavenXpp3Reader var2, String var3, String var4, Boolean var5);
}
