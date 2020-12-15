package org.gk.reach;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

public class ReachProperties {
    private Path reachConf = null;
    private Path reachJar = null;
    private Path reachRoot = null;

    public ReachProperties() {
    }

    public ReachProperties(Properties prop) {
        reachConf = strToPath(prop.getProperty("reachConf"));
        reachJar = strToPath(prop.getProperty("reachJar"));
        reachRoot = strToPath(prop.getProperty("reachRoot"));
    }

    private Path strToPath(String str) {
        if (str == null)
            return null;

        return Paths.get(str);
    }

    void setReachConf(Path reachConf) {
        this.reachConf = reachConf;
    }

    Path getReachConf() {
        return reachConf;
    }

    void setReachJar(Path reachJar) {
        this.reachJar = reachJar;
    }

    Path getReachJar() {
        return reachJar;
    }

    void setReachRoot(Path reachRoot) {
        this.reachRoot = reachRoot;
    }

    Path getReachRoot() {
        return reachRoot;
    }

    boolean anyNull() {
        return Arrays.asList(reachConf, reachJar, reachRoot)
                     .stream()
                     .anyMatch(path -> path == null);
    }

}
