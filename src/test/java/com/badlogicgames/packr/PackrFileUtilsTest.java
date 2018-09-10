package com.badlogicgames.packr;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PackrFileUtilsTest {

    @Test
    void unpack() throws IOException {

        // prepare
        File jdkFile = new File(getClass().getResource("/openjdk-stripped-for-test.tar.gz").getFile());
        File tempDirectory = Files.createTempDirectory(getClass().getSimpleName()).toFile();

        // test
        PackrFileUtils.unpack(jdkFile, tempDirectory);

        // verify
        File[] tempDirFiles = tempDirectory.listFiles();
        assertEquals(1, tempDirFiles.length);
        assertEquals("jdk-10.0.2-stripped-for-test", tempDirFiles[0].getName());
        String[] subDirFileNames = tempDirFiles[0].list();
        assertEquals(4, subDirFileNames.length);
        Arrays.sort(subDirFileNames);
        assertArrayEquals(new String[]{"conf", "include", "legal", "release"}, subDirFileNames);
    }
}